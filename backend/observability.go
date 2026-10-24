package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

// Only route classes and generated IDs enter logs, never URLs, headers or bodies.
type responseLog struct {
	http.ResponseWriter
	status int
}
type requestIDKey struct{}

func (w *responseLog) Unwrap() http.ResponseWriter { return w.ResponseWriter }

func (w *responseLog) WriteHeader(code int) { w.status = code; w.ResponseWriter.WriteHeader(code) }
func (w *responseLog) Write(b []byte) (int, error) {
	if w.status == 0 {
		w.WriteHeader(200)
	}
	return w.ResponseWriter.Write(b)
}
func (w *responseLog) Flush() {
	if w.status == 0 {
		w.WriteHeader(200)
	}
	if f, ok := w.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}
func route(path string) string {
	switch {
	case path == "/healthz":
		return "health"
	case path == "/v1/stream":
		return "stream"
	case path == "/v1/snapshot":
		return "snapshot"
	case path == "/v1/events":
		return "events"
	case strings.HasPrefix(path, "/hooks/"):
		return "webhook"
	case strings.HasPrefix(path, "/v1/nodes/"):
		return "node_detail"
	case strings.HasPrefix(path, "/v1/apps/"):
		return "app_tree"
	default:
		return "unknown"
	}
}
func accessLog(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := make([]byte, 12)
		_, _ = rand.Read(id)
		requestID := hex.EncodeToString(id)
		r = r.WithContext(context.WithValue(r.Context(), requestIDKey{}, requestID))
		w.Header().Set("X-Request-ID", requestID)
		out := &responseLog{ResponseWriter: w}
		start := time.Now()
		defer func() {
			if recover() != nil {
				slog.Error("request_panic", "request_id", requestID, "route", route(r.URL.Path))
				if out.status == 0 {
					out.WriteHeader(500)
				}
			}
			if r.URL.Path != "/healthz" {
				status := out.status
				if status == 0 {
					status = 200
				}
				slog.Info("http_request", "request_id", requestID, "route", route(r.URL.Path), "method", r.Method, "status", status, "duration_ms", time.Since(start).Milliseconds())
			}
		}()
		next.ServeHTTP(out, r)
	})
}

type liveFrame struct {
	Snapshot Snapshot `json:"snapshot"`
	Events   []Event  `json:"events"`
}
type broker struct {
	sync.RWMutex
	frame    []byte
	revision uint64
	changed  chan struct{}
}

func (b *broker) publish(frame liveFrame) {
	data, _ := json.Marshal(frame)
	b.Lock()
	defer b.Unlock()
	if bytes.Equal(data, b.frame) {
		return
	}
	b.frame = data
	b.revision++
	if b.changed != nil {
		close(b.changed)
	}
	b.changed = make(chan struct{})
}
func (b *broker) current() ([]byte, uint64, chan struct{}) {
	b.RLock()
	defer b.RUnlock()
	return b.frame, b.revision, b.changed
}
func (s *server) stream(w http.ResponseWriter, r *http.Request) {
	if !auth(r.Header.Get("Authorization"), s.token) {
		write(w, 401, map[string]string{"error": "Unauthorized"})
		return
	}
	if r.Method != "GET" {
		w.WriteHeader(405)
		return
	}
	if _, ok := w.(http.Flusher); !ok {
		w.WriteHeader(500)
		return
	}
	rc := http.NewResponseController(w)
	_ = rc.SetWriteDeadline(time.Time{})
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("X-Accel-Buffering", "no")
	slog.Info("stream_connected", "request_id", w.Header().Get("X-Request-ID"))
	heartbeat := time.NewTicker(10 * time.Second)
	defer heartbeat.Stop()
	last := uint64(0)
	for {
		data, revision, changed := s.live.current()
		if len(data) > 0 && revision != last {
			_ = rc.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if _, e := fmt.Fprintf(w, "id: %d\nevent: state\ndata: %s\n\n", revision, data); e != nil {
				return
			}
			w.(http.Flusher).Flush()
			last = revision
		}
		select {
		case <-r.Context().Done():
			return
		case <-changed:
		case <-heartbeat.C:
			_ = rc.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if _, e := fmt.Fprint(w, ": heartbeat\n\n"); e != nil {
				return
			}
			w.(http.Flusher).Flush()
		}
	}
}
func (s *server) run(ctx context.Context) {
	tick := time.NewTicker(15 * time.Second)
	defer tick.Stop()
	for {
		start := time.Now()
		collectionID := fmt.Sprintf("collect-%d", start.UnixNano())
		collect, cancel := context.WithTimeout(context.WithValue(ctx, requestIDKey{}, collectionID), 14*time.Second)
		snapshot := s.snapshot(collect)
		events := s.feed(collect)
		cancel()
		s.live.publish(liveFrame{snapshot, events})
		slog.Info("collection_complete", "collection_id", collectionID, "duration_ms", time.Since(start).Milliseconds(), "nodes", len(snapshot.Nodes), "apps", len(snapshot.Apps), "events", len(events), "partial", len(snapshot.Errors) > 0)
		// Coalesce bursts of Kubernetes events before querying shared upstreams again.
		select {
		case <-ctx.Done():
			return
		case <-time.After(2 * time.Second):
		}
		select {
		case <-ctx.Done():
			return
		case <-tick.C:
		case <-s.wake:
		}
	}
}
func (s *server) signal() {
	select {
	case s.wake <- struct{}{}:
	default:
	}
}

// Watch streams are shared by all mobile clients. Expired versions trigger a fresh list.
func (s *server) watch(ctx context.Context, path string) {
	for ctx.Err() == nil {
		var list struct {
			Metadata struct {
				ResourceVersion string `json:"resourceVersion"`
			}
		}
		if s.get(ctx, s.kube, s.kubeURL+path+"?limit=1", &list, true) != nil {
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
			continue
		}
		request, _ := http.NewRequestWithContext(ctx, "GET", s.kubeURL+path+"?watch=true&timeoutSeconds=240&resourceVersion="+list.Metadata.ResourceVersion, nil)
		request.Header.Set("Authorization", "Bearer "+s.sa)
		client := &http.Client{Transport: s.kube.Transport}
		response, err := client.Do(request)
		if err == nil {
			if response.StatusCode == 200 {
				scanner := bufio.NewScanner(response.Body)
				scanner.Buffer(make([]byte, 4096), 8<<20)
				for scanner.Scan() {
					s.signal()
				}
			}
			response.Body.Close()
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(2 * time.Second):
		}
	}
}

func (s *server) loadHistory() {
	if s.history == "" {
		return
	}
	data, err := os.ReadFile(s.history)
	if os.IsNotExist(err) {
		return
	}
	if err != nil {
		slog.Error("history_load_failed")
		return
	}
	if json.Unmarshal(data, &s.events) != nil {
		slog.Error("history_decode_failed")
		s.events = []Event{}
	}
	for i := range s.events {
		e := &s.events[i]
		if e.State == "" {
			e.State = "Activity"
			if e.Source == "Kubernetes" {
				e.State = "Observed"
			}
			if e.Source == "Prometheus" && strings.HasPrefix(e.ID, "{") {
				e.ID = "alert:" + e.ID
				e.Domain = "Prometheus"
				e.State = "Unknown"
				e.Evidence = "Legacy alert; awaiting a fresh observation"
			}
		}
	}
}
func eventOrder(e Event) string {
	if e.ResolvedAt != "" {
		return e.ResolvedAt
	}
	return e.Time
}
func (s *server) saveHistory() error {
	if s.history == "" {
		return nil
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	cutoff := time.Now().Add(-30 * 24 * time.Hour)
	sort.SliceStable(s.events, func(i, j int) bool {
		ai := s.events[i].Domain != "" && s.events[i].State != "Resolved"
		aj := s.events[j].Domain != "" && s.events[j].State != "Resolved"
		if ai != aj {
			return ai
		}
		return s.events[i].Time > s.events[j].Time
	})
	kept := s.events[:0]
	for _, event := range s.events {
		stampValue := event.Time
		if event.ResolvedAt != "" {
			stampValue = event.ResolvedAt
		}
		stamp, _ := time.Parse(time.RFC3339, stampValue)
		if (stamp.After(cutoff) || event.State == "Firing" || event.State == "Unknown" || event.State == "Pending") && len(kept) < 2000 && !(event.Source == "Test" && strings.HasPrefix(event.Title, "Olympus verification ")) {
			kept = append(kept, event)
		}
	}
	s.events = kept
	data, _ := json.Marshal(kept)
	tmp := s.history + ".tmp"
	if os.MkdirAll(filepath.Dir(s.history), 0700) != nil || os.WriteFile(tmp, data, 0600) != nil || os.Rename(tmp, s.history) != nil {
		slog.Error("history_save_failed")
		return fmt.Errorf("history storage unavailable")
	}
	return nil
}
