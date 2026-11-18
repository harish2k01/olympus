package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"sync"
	"time"
)

// CertificateSummary feeds the Cluster card: how many certificates exist and when the next one renews.
type CertificateSummary struct {
	Total       int    `json:"total"`
	NextExpiry  string `json:"nextExpiry,omitempty"`
	NextName    string `json:"nextName,omitempty"`
}

func (s *server) clusterExtras(ctx context.Context, out *Snapshot) {
	if rows, err := s.query(ctx, `count by(node) (kube_pod_info{node!=""})`); err == nil {
		pods := map[string]int{}
		for _, row := range rows {
			if n := number(row); n != nil {
				pods[row.Metric["node"]] = int(*n)
			}
		}
		for i := range out.Nodes {
			if n, ok := pods[out.Nodes[i].Name]; ok {
				v := n
				out.Nodes[i].Pods = &v
			}
		}
	}
	if rows, err := s.query(ctx, `certmanager_certificate_expiration_timestamp_seconds`); err == nil && len(rows) > 0 {
		summary := CertificateSummary{}
		next := 0.0
		for _, row := range rows {
			n := number(row)
			if n == nil {
				continue
			}
			summary.Total++
			if next == 0 || *n < next {
				next, summary.NextName = *n, row.Metric["name"]
			}
		}
		if next > 0 {
			summary.NextExpiry = time.Unix(int64(next), 0).UTC().Format(time.RFC3339)
		}
		out.Certificates = &summary
	}
	out.Snoozes = s.snoozes.active(time.Now())
}

// snoozeStore is shared by every paired device and survives restarts when SNOOZE_FILE is set.
type snoozeStore struct {
	sync.Mutex
	until map[string]time.Time
	file  string
}

func (z *snoozeStore) load(file string) {
	z.Lock()
	defer z.Unlock()
	z.file, z.until = file, map[string]time.Time{}
	if file == "" {
		return
	}
	if raw, err := os.ReadFile(file); err == nil {
		_ = json.Unmarshal(raw, &z.until)
	}
}

func (z *snoozeStore) active(now time.Time) map[string]string {
	z.Lock()
	defer z.Unlock()
	out := map[string]string{}
	for key, at := range z.until {
		if at.After(now) {
			out[key] = at.UTC().Format(time.RFC3339)
		} else {
			delete(z.until, key)
		}
	}
	return out
}

func (z *snoozeStore) set(key string, until time.Time, now time.Time) {
	z.Lock()
	defer z.Unlock()
	if z.until == nil {
		z.until = map[string]time.Time{}
	}
	if until.After(now) {
		z.until[key] = until
	} else {
		delete(z.until, key)
	}
	if z.file != "" {
		if raw, err := json.Marshal(z.until); err == nil {
			if err := os.WriteFile(z.file, raw, 0o600); err != nil {
				slog.Warn("snooze_save_failed", "failure", err.Error())
			}
		}
	}
}

const maxSnoozeMinutes = 7 * 24 * 60

func (s *server) snoozeEndpoint(w http.ResponseWriter, r *http.Request) {
	if !s.readAuthorized(r.Header.Get("Authorization")) {
		write(w, 401, map[string]string{"error": "Unauthorized"})
		return
	}
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Key     string `json:"key"`
		Minutes int    `json:"minutes"`
	}
	r.Body = http.MaxBytesReader(w, r.Body, 1024)
	if json.NewDecoder(r.Body).Decode(&req) != nil || req.Key == "" || len(req.Key) > 300 || req.Minutes < 0 || req.Minutes > maxSnoozeMinutes {
		write(w, 400, map[string]string{"error": "Invalid snooze"})
		return
	}
	now := time.Now()
	s.snoozes.set(req.Key, now.Add(time.Duration(req.Minutes)*time.Minute), now)
	s.signal()
	write(w, 200, map[string]any{"snoozes": s.snoozes.active(now)})
}

// actionRequest validates the shared parts of an action call and returns false after writing the error.
func (s *server) actionRequest(w http.ResponseWriter, r *http.Request, dst any) bool {
	if !s.readAuthorized(r.Header.Get("Authorization")) {
		write(w, 401, map[string]string{"error": "Unauthorized"})
		return false
	}
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		w.WriteHeader(http.StatusMethodNotAllowed)
		return false
	}
	r.Body = http.MaxBytesReader(w, r.Body, 1024)
	if json.NewDecoder(r.Body).Decode(dst) != nil {
		write(w, 400, map[string]string{"error": "Invalid request"})
		return false
	}
	if s.actions == nil || !s.actions.enabled {
		write(w, 403, map[string]string{"error": "Actions are turned off"})
		return false
	}
	return true
}

func (s *server) kubeWrite(ctx context.Context, method, address, contentType string, body []byte) error {
	req, err := http.NewRequestWithContext(ctx, method, address, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+s.sa)
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	resp, err := s.kube.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 && resp.StatusCode != 202 {
		return fmt.Errorf("status %d", resp.StatusCode)
	}
	return nil
}

// deletePodAction removes one pod so its controller replaces it (for a pod stuck terminating or wedged).
func (s *server) deletePodAction(w http.ResponseWriter, r *http.Request) {
	var req struct{ Namespace, Name string }
	if !s.actionRequest(w, r, &req) {
		return
	}
	if !dnsLabel.MatchString(req.Namespace) || !validObjectName(req.Name) {
		write(w, 400, map[string]string{"error": "Invalid target"})
		return
	}
	if !s.actions.allows(req.Namespace) {
		slog.Warn("action_refused", "action", "delete-pod", "namespace", req.Namespace, "name", req.Name, "reason", "not_allowed")
		write(w, 403, map[string]string{"error": "Actions are not allowed for this namespace"})
		return
	}
	if !s.actions.claim("pod:"+req.Namespace+"/"+req.Name, time.Now()) {
		write(w, 429, map[string]string{"error": "Deleted less than a minute ago"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	address := fmt.Sprintf("%s/api/v1/namespaces/%s/pods/%s", s.kubeURL, url.PathEscape(req.Namespace), url.PathEscape(req.Name))
	if err := s.kubeWrite(ctx, http.MethodDelete, address, "", nil); err != nil {
		slog.Error("action_failed", "action", "delete-pod", "namespace", req.Namespace, "name", req.Name, "failure", err.Error())
		write(w, 502, map[string]string{"error": "Kubernetes refused the delete"})
		return
	}
	slog.Info("action_performed", "action", "delete-pod", "namespace", req.Namespace, "name", req.Name)
	write(w, 200, map[string]string{"status": "deleted"})
}

// syncAction asks Argo CD to sync an application, the same as pressing Sync in its UI.
func (s *server) syncAction(w http.ResponseWriter, r *http.Request) {
	var req struct{ Name string }
	if !s.actionRequest(w, r, &req) {
		return
	}
	if !validObjectName(req.Name) {
		write(w, 400, map[string]string{"error": "Invalid application"})
		return
	}
	if !s.actions.claim("sync:"+req.Name, time.Now()) {
		write(w, 429, map[string]string{"error": "Synced less than a minute ago"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	body, _ := json.Marshal(map[string]any{"operation": map[string]any{"initiatedBy": map[string]string{"username": "olympus"}, "sync": map[string]any{}}})
	address := fmt.Sprintf("%s/apis/argoproj.io/v1alpha1/namespaces/%s/applications/%s", s.kubeURL, url.PathEscape(env("ARGOCD_NAMESPACE", "argocd")), url.PathEscape(req.Name))
	if err := s.kubeWrite(ctx, http.MethodPatch, address, "application/merge-patch+json", body); err != nil {
		slog.Error("action_failed", "action", "sync", "name", req.Name, "failure", err.Error())
		write(w, 502, map[string]string{"error": "Argo CD refused the sync"})
		return
	}
	slog.Info("action_performed", "action", "sync", "name", req.Name)
	write(w, 200, map[string]string{"status": "syncing"})
}
