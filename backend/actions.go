package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

// actionPolicy gates the few cluster writes Olympus allows. Actions are off unless ACTIONS_ENABLED=true,
// and the service account also needs the separate olympus-actions role.
type actionPolicy struct {
	enabled    bool
	namespaces map[string]bool // empty = every namespace except the protected ones
	mu         sync.Mutex
	last       map[string]time.Time
}

var protectedNamespaces = map[string]bool{"kube-system": true, "kube-public": true, "kube-node-lease": true, "olympus": true, "argocd": true}
var dnsLabel = regexp.MustCompile(`^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$`)
var objectName = regexp.MustCompile(`^[a-z0-9]([-a-z0-9.]*[a-z0-9])?$`)

func validObjectName(name string) bool { return len(name) <= 253 && objectName.MatchString(name) }

const actionCooldown = time.Minute

func loadActionPolicy(enabled, namespaces string) *actionPolicy {
	p := &actionPolicy{enabled: strings.EqualFold(enabled, "true"), namespaces: map[string]bool{}, last: map[string]time.Time{}}
	for _, ns := range strings.Split(namespaces, ",") {
		if ns = strings.TrimSpace(ns); ns != "" {
			p.namespaces[ns] = true
		}
	}
	return p
}

func (p *actionPolicy) allows(namespace string) bool {
	if p == nil || !p.enabled || protectedNamespaces[namespace] {
		return false
	}
	return len(p.namespaces) == 0 || p.namespaces[namespace]
}

// claim enforces one action per target per cooldown window, so a double tap cannot restart twice.
func (p *actionPolicy) claim(key string, now time.Time) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	if at, ok := p.last[key]; ok && now.Sub(at) < actionCooldown {
		return false
	}
	p.last[key] = now
	return true
}

type restartRequest struct {
	Namespace string `json:"namespace"`
	Kind      string `json:"kind"`
	Name      string `json:"name"`
}

var restartKinds = map[string]string{"deployment": "deployments", "statefulset": "statefulsets", "daemonset": "daemonsets"}

// restartAction performs a rollout restart (the same patch kubectl rollout restart sends).
func (s *server) restartAction(w http.ResponseWriter, r *http.Request) {
	if !s.readAuthorized(r.Header.Get("Authorization")) {
		write(w, 401, map[string]string{"error": "Unauthorized"})
		return
	}
	if r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	var req restartRequest
	r.Body = http.MaxBytesReader(w, r.Body, 1024)
	if json.NewDecoder(r.Body).Decode(&req) != nil {
		write(w, 400, map[string]string{"error": "Invalid request"})
		return
	}
	resource, kindOK := restartKinds[strings.ToLower(req.Kind)]
	if !kindOK || !dnsLabel.MatchString(req.Namespace) || !validObjectName(req.Name) {
		write(w, 400, map[string]string{"error": "Invalid target"})
		return
	}
	if !s.actions.allows(req.Namespace) {
		slog.Warn("action_refused", "action", "restart", "namespace", req.Namespace, "kind", resource, "name", req.Name, "reason", "not_allowed")
		write(w, 403, map[string]string{"error": "Actions are not allowed for this namespace"})
		return
	}
	key := req.Namespace + "/" + resource + "/" + req.Name
	if !s.actions.claim(key, time.Now()) {
		write(w, 429, map[string]string{"error": "Restarted less than a minute ago"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	if err := s.patchRestart(ctx, req.Namespace, resource, req.Name, time.Now().UTC()); err != nil {
		slog.Error("action_failed", "action", "restart", "namespace", req.Namespace, "kind", resource, "name", req.Name, "failure", err.Error())
		write(w, 502, map[string]string{"error": "Kubernetes refused the restart"})
		return
	}
	slog.Info("action_performed", "action", "restart", "namespace", req.Namespace, "kind", resource, "name", req.Name)
	write(w, 200, map[string]string{"status": "restarting"})
}

func (s *server) patchRestart(ctx context.Context, namespace, resource, name string, at time.Time) error {
	body, _ := json.Marshal(map[string]any{"spec": map[string]any{"template": map[string]any{"metadata": map[string]any{
		"annotations": map[string]string{"kubectl.kubernetes.io/restartedAt": at.Format(time.RFC3339)}}}}})
	address := fmt.Sprintf("%s/apis/apps/v1/namespaces/%s/%s/%s", s.kubeURL, url.PathEscape(namespace), resource, url.PathEscape(name))
	req, err := http.NewRequestWithContext(ctx, http.MethodPatch, address, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+s.sa)
	req.Header.Set("Content-Type", "application/strategic-merge-patch+json")
	resp, err := s.kube.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("status %d", resp.StatusCode)
	}
	return nil
}
