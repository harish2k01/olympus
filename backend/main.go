package main

import (
	"context"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Node struct {
	Name     string   `json:"name"`
	Role     string   `json:"role"`
	Cores    int      `json:"cores"`
	CPU      *float64 `json:"cpuPercent"`
	Used     *float64 `json:"memoryUsedGiB"`
	Total    *float64 `json:"memoryTotalGiB"`
	Ready    bool     `json:"ready"`
	Pressure []string `json:"pressure"`
}
type Event struct {
	ID       string `json:"id"`
	Source   string `json:"source"`
	Title    string `json:"title"`
	Detail   string `json:"detail"`
	Kind     string `json:"kind"`
	Time     string `json:"time"`
	Received string `json:"receivedAt,omitempty"`
}
type App struct {
	Name      string     `json:"name"`
	Namespace string     `json:"namespace"`
	Health    string     `json:"health"`
	Sync      string     `json:"sync"`
	CPU       *float64   `json:"cpuCores"`
	Memory    *float64   `json:"memoryMiB"`
	Pods      int        `json:"pods"`
	Ready     int        `json:"ready"`
	Icon      string     `json:"icon"`
	URL       string     `json:"url"`
	Resources []Resource `json:"resources"`
}
type Snapshot struct {
	Time            string   `json:"time"`
	Nodes           []Node   `json:"nodes"`
	Apps            []App    `json:"apps"`
	MonitorsUp      *float64 `json:"monitorsUp"`
	MonitorsTotal   *float64 `json:"monitorsTotal"`
	Download        *float64 `json:"downloadMiB"`
	BackupAvailable *bool    `json:"backupAvailable"`
	BackupVolumes   *int     `json:"backupVolumes"`
	BackupRecorded  *int     `json:"backupRecorded"`
	BackupLast      string   `json:"backupLast"`
	Errors          []string `json:"errors"`
	Issues          []Issue  `json:"issues"`
}
type vector struct {
	Metric map[string]string `json:"metric"`
	Value  []json.RawMessage `json:"value"`
}
type server struct {
	client                                  *http.Client
	kube                                    *http.Client
	prom, kubeURL, sa, token, hook, history string
	mu                                      sync.Mutex
	events                                  []Event
	cached                                  Snapshot
	cacheTime                               time.Time
	collect                                 sync.Mutex
	live                                    broker
	wake                                    chan struct{}
}

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}
func number(v vector) *float64 {
	if len(v.Value) < 2 {
		return nil
	}
	var x string
	if json.Unmarshal(v.Value[1], &x) != nil {
		return nil
	}
	n, e := strconv.ParseFloat(x, 64)
	if e != nil || math.IsNaN(n) || math.IsInf(n, 0) {
		return nil
	}
	return &n
}
func (s *server) get(ctx context.Context, client *http.Client, address string, dst any, kube bool) error {
	r, e := http.NewRequestWithContext(ctx, "GET", address, nil)
	if e != nil {
		return e
	}
	if kube {
		r.Header.Set("Authorization", "Bearer "+s.sa)
	}
	resp, e := client.Do(r)
	if e != nil {
		slog.Warn("upstream_failed", "upstream", map[bool]string{true: "kubernetes", false: "prometheus"}[kube], "operation", strings.Split(r.URL.Path, "?")[0], "failure", "connection_or_timeout")
		return e
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		slog.Warn("upstream_failed", "upstream", map[bool]string{true: "kubernetes", false: "prometheus"}[kube], "status", resp.StatusCode)
		return fmt.Errorf("upstream status %d", resp.StatusCode)
	}
	return json.NewDecoder(io.LimitReader(resp.Body, 8<<20)).Decode(dst)
}
func (s *server) query(ctx context.Context, q string) ([]vector, error) {
	var out struct {
		Status string `json:"status"`
		Data   struct {
			Result []vector `json:"result"`
		} `json:"data"`
	}
	e := s.get(ctx, s.client, s.prom+"/api/v1/query?query="+url.QueryEscape(q), &out, false)
	if e == nil && out.Status != "success" {
		e = fmt.Errorf("Prometheus query failed")
	}
	return out.Data.Result, e
}
func (s *server) scalar(ctx context.Context, q string) *float64 {
	rows, e := s.query(ctx, q)
	if e != nil || len(rows) == 0 {
		return nil
	}
	return number(rows[0])
}
func (s *server) snapshot(ctx context.Context) Snapshot {
	s.collect.Lock()
	defer s.collect.Unlock()
	if time.Since(s.cacheTime) < 5*time.Second {
		return s.cached
	}
	out := Snapshot{Time: time.Now().UTC().Format(time.RFC3339), Nodes: []Node{}, Apps: []App{}, Errors: []string{}}
	var nodes struct {
		Items []struct {
			Metadata struct {
				Name   string            `json:"name"`
				Labels map[string]string `json:"labels"`
			} `json:"metadata"`
			Status struct {
				Addresses  []struct{ Type, Address string } `json:"addresses"`
				Capacity   map[string]string                `json:"capacity"`
				Conditions []struct{ Type, Status string }  `json:"conditions"`
			} `json:"status"`
		} `json:"items"`
	}
	if e := s.get(ctx, s.kube, s.kubeURL+"/api/v1/nodes", &nodes, true); e != nil {
		out.Errors = append(out.Errors, "Kubernetes nodes unavailable")
	}
	// A metric's instance is explicitly joined to node addresses; external exporters never enter cluster totals.
	queries := []string{`100 * (1 - avg by(instance) (rate(node_cpu_seconds_total{job="node-exporter",mode="idle"}[5m])))`, `(node_memory_MemTotal_bytes{job="node-exporter"} - node_memory_MemAvailable_bytes{job="node-exporter"}) / 1073741824`, `node_memory_MemTotal_bytes{job="node-exporter"} / 1073741824`}
	metrics := make([]map[string]*float64, 3)
	for i, q := range queries {
		metrics[i] = map[string]*float64{}
		rows, e := s.query(ctx, q)
		if e != nil {
			out.Errors = append(out.Errors, fmt.Sprintf("Node metric %d unavailable", i))
			continue
		}
		for _, r := range rows {
			metrics[i][strings.Split(r.Metric["instance"], ":")[0]] = number(r)
		}
	}
	for _, n := range nodes.Items {
		node := Node{Name: n.Metadata.Name, Role: "Worker"}
		if _, ok := n.Metadata.Labels["node-role.kubernetes.io/control-plane"]; ok {
			node.Role = "Control plane"
		}
		node.Cores, _ = strconv.Atoi(n.Status.Capacity["cpu"])
		for _, c := range n.Status.Conditions {
			if c.Status == "True" && (strings.HasSuffix(c.Type, "Pressure") || c.Type == "NetworkUnavailable") {
				node.Pressure = append(node.Pressure, c.Type)
			}
			if c.Type == "Ready" {
				node.Ready = c.Status == "True"
			}
		}
		for _, a := range n.Status.Addresses {
			if a.Type == "InternalIP" {
				node.CPU = metrics[0][a.Address]
				node.Used = metrics[1][a.Address]
				node.Total = metrics[2][a.Address]
			}
		}
		out.Nodes = append(out.Nodes, node)
	}
	sort.Slice(out.Nodes, func(i, j int) bool { return out.Nodes[i].Name < out.Nodes[j].Name })
	var apps struct {
		Items []struct {
			Metadata struct{ Name string }
			Spec     struct{ Destination struct{ Namespace string } }
			Status   struct {
				Health    struct{ Status string }
				Sync      struct{ Status string }
				Resources []Resource
			}
		} `json:"items"`
	}
	if e := s.get(ctx, s.kube, s.kubeURL+"/apis/argoproj.io/v1alpha1/applications", &apps, true); e != nil {
		out.Errors = append(out.Errors, "Argo applications unavailable")
	}
	for _, a := range apps.Items {
		out.Apps = append(out.Apps, App{Name: a.Metadata.Name, Namespace: a.Spec.Destination.Namespace, Health: a.Status.Health.Status, Sync: a.Status.Sync.Status, Resources: a.Status.Resources})
	}
	out.MonitorsUp = s.scalar(ctx, `sum(monitor_status == bool 1)`)
	out.MonitorsTotal = s.scalar(ctx, `count(monitor_status)`)
	out.Download = s.scalar(ctx, `sum(qbittorrent_global_download_speed_bytes) / 1048576`)
	var targets struct {
		Items []struct{ Status struct{ Available bool } } `json:"items"`
	}
	if s.get(ctx, s.kube, s.kubeURL+"/apis/longhorn.io/v1beta2/namespaces/longhorn-system/backuptargets", &targets, true) == nil && len(targets.Items) > 0 {
		ok := false
		for _, t := range targets.Items {
			ok = ok || t.Status.Available
		}
		out.BackupAvailable = &ok
	}
	var volumes struct {
		Items []struct {
			Status struct{ LastBackup, LastBackupAt string }
		}
	}
	if s.get(ctx, s.kube, s.kubeURL+"/apis/longhorn.io/v1beta2/namespaces/longhorn-system/volumes", &volumes, true) == nil {
		total, recorded := len(volumes.Items), 0
		for _, volume := range volumes.Items {
			if volume.Status.LastBackup != "" && volume.Status.LastBackupAt != "" {
				recorded++
				if volume.Status.LastBackupAt > out.BackupLast {
					out.BackupLast = volume.Status.LastBackupAt
				}
			}
		}
		out.BackupVolumes = &total
		out.BackupRecorded = &recorded
	}
	s.enrichApps(ctx, &out)
	s.attention(ctx, &out)
	s.cached = out
	s.cacheTime = time.Now()
	return out
}
func (s *server) feed(ctx context.Context) []Event {
	s.mu.Lock()
	out := append([]Event{}, s.events...)
	s.mu.Unlock()
	var list struct {
		Items []struct {
			Metadata                             struct{ UID string }
			Reason, Message, Type, LastTimestamp string
			InvolvedObject                       struct{ Namespace, Name string }
		} `json:"items"`
	}
	if s.get(ctx, s.kube, s.kubeURL+"/api/v1/events", &list, true) == nil {
		for _, e := range list.Items {
			if e.Type != "Warning" {
				continue
			}
			out = append(out, Event{e.Metadata.UID, "Kubernetes", e.Reason, e.InvolvedObject.Namespace + "/" + e.InvolvedObject.Name + ": " + e.Message, "Warning", e.LastTimestamp, ""})
		}
	}
	var alerts struct {
		Data struct {
			Alerts []struct {
				Labels      map[string]string
				Annotations map[string]string
				ActiveAt    string
			}
		}
	}
	if s.get(ctx, s.client, s.prom+"/api/v1/alerts", &alerts, false) == nil {
		for _, a := range alerts.Data.Alerts {
			if a.Labels["alertname"] == "Watchdog" || a.Labels["alertname"] == "InfoInhibitor" {
				continue
			}
			identity, _ := json.Marshal(a.Labels)
			out = append(out, Event{string(identity), "Prometheus", a.Labels["alertname"], a.Annotations["summary"], a.Labels["severity"], a.ActiveAt, ""})
		}
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].Time > out[j].Time })
	if len(out) > 100 {
		out = out[:100]
	}
	return out
}
func write(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}
func auth(header, expected string) bool {
	return expected != "" && subtle.ConstantTimeCompare([]byte(header), []byte("Bearer "+expected)) == 1
}
func (s *server) api(w http.ResponseWriter, r *http.Request) {
	if !auth(r.Header.Get("Authorization"), s.token) {
		write(w, 401, map[string]string{"error": "Unauthorized"})
		return
	}
	if r.Method != "GET" {
		w.WriteHeader(405)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	switch r.URL.Path {
	case "/v1/snapshot":
		out := s.snapshot(ctx)
		if len(out.Nodes) == 0 {
			write(w, 503, out)
			return
		}
		write(w, 200, out)
	case "/v1/events":
		write(w, 200, s.feed(ctx))
	default:
		if strings.HasPrefix(r.URL.Path, "/v1/apps/") {
			s.appTree(w, r, ctx)
			return
		}
		if strings.HasPrefix(r.URL.Path, "/v1/nodes/") {
			s.nodeDetail(w, r, ctx)
			return
		}
		w.WriteHeader(404)
	}
}
func (s *server) webhook(w http.ResponseWriter, r *http.Request) {
	_, password, basic := r.BasicAuth()
	if !auth(r.Header.Get("Authorization"), s.hook) && !(basic && s.hook != "" && subtle.ConstantTimeCompare([]byte(password), []byte(s.hook)) == 1) {
		w.WriteHeader(401)
		return
	}
	if r.Method != "POST" {
		w.WriteHeader(405)
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, 256<<10)
	var payload map[string]any
	if json.NewDecoder(r.Body).Decode(&payload) != nil {
		w.WriteHeader(400)
		return
	}
	source := strings.TrimPrefix(r.URL.Path, "/hooks/")
	if source == "" {
		w.WriteHeader(400)
		return
	}
	title := "Notification received"
	for _, key := range []string{"eventType", "title", "msg", "message", "text"} {
		if x, ok := payload[key].(string); ok && x != "" {
			title = x
			break
		}
	}
	if len(title) > 500 {
		title = title[:500]
	}
	now := time.Now().UTC()
	event := Event{fmt.Sprint(now.UnixNano()), source, title, safeDetail(payload), "Activity", now.Format(time.RFC3339), now.Format(time.RFC3339)}
	s.mu.Lock()
	s.events = append([]Event{event}, s.events...)
	if len(s.events) > 2000 {
		s.events = s.events[:2000]
	}
	s.mu.Unlock()
	if s.saveHistory() != nil {
		write(w, 503, map[string]string{"error": "Event storage unavailable"})
		return
	}
	s.signal()
	slog.Info("webhook_accepted", "event_id", event.ID)
	write(w, 202, map[string]string{"status": "accepted"})
}
func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})))
	token := os.Getenv("API_TOKEN")
	hook := os.Getenv("HOOK_TOKEN")
	if len(token) < 32 || len(hook) < 32 {
		log.Fatal("API_TOKEN and HOOK_TOKEN must each contain at least 32 characters")
	}
	sa, _ := os.ReadFile("/var/run/secrets/kubernetes.io/serviceaccount/token")
	ca, _ := os.ReadFile("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
	pool := x509.NewCertPool()
	pool.AppendCertsFromPEM(ca)
	s := &server{client: &http.Client{Timeout: 8 * time.Second}, kube: &http.Client{Timeout: 8 * time.Second, Transport: &http.Transport{TLSClientConfig: &tls.Config{RootCAs: pool, MinVersion: tls.VersionTLS12}}}, prom: env("PROMETHEUS_URL", "http://kube-prometheus-stack-prometheus.monitoring.svc:9090"), kubeURL: env("KUBE_URL", "https://kubernetes.default.svc"), sa: strings.TrimSpace(string(sa)), token: token, hook: hook, events: []Event{}, wake: make(chan struct{}, 1), history: os.Getenv("HISTORY_FILE")}
	s.loadHistory()
	s.live.changed = make(chan struct{})
	go s.run(context.Background())
	for _, path := range []string{"/api/v1/nodes", "/api/v1/events", "/apis/argoproj.io/v1alpha1/applications"} {
		go s.watch(context.Background(), path)
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("ok")) })
	mux.HandleFunc("/v1/stream", s.stream)
	mux.HandleFunc("/v1/", s.api)
	mux.HandleFunc("/hooks/", s.webhook)
	slog.Info("server_started", "port", 8080, "history_enabled", s.history != "")
	log.Fatal((&http.Server{Addr: ":8080", Handler: accessLog(mux), ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 20 * time.Second, WriteTimeout: 30 * time.Second, IdleTimeout: 60 * time.Second}).ListenAndServe())
}
