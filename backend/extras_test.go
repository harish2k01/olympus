package main

import (
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestSnoozeStoreExpiresAndPersists(t *testing.T) {
	file := filepath.Join(t.TempDir(), "snoozes.json")
	var z snoozeStore
	z.load(file)
	now := time.Now()
	z.set("a", now.Add(time.Hour), now)
	z.set("b", now.Add(time.Minute), now)
	if got := z.active(now.Add(2 * time.Minute)); len(got) != 1 || got["a"] == "" {
		t.Fatal(got)
	}
	var reloaded snoozeStore
	reloaded.load(file)
	if got := reloaded.active(now); got["a"] == "" {
		t.Fatal("snooze not persisted", got)
	}
	z.set("a", now, now)
	if len(z.active(now)) != 0 {
		t.Fatal("zero minutes must clear")
	}
}

func TestSnoozeEndpointValidates(t *testing.T) {
	token := strings.Repeat("t", 32)
	s := &server{token: token, wake: make(chan struct{}, 1)}
	call := func(body string) int {
		r := httptest.NewRequest("POST", "/v1/snoozes", strings.NewReader(body))
		r.Header.Set("Authorization", "Bearer "+token)
		w := httptest.NewRecorder()
		s.snoozeEndpoint(w, r)
		return w.Code
	}
	if call(`{"key":"event:1","minutes":60}`) != 200 || call(`{"key":"","minutes":60}`) != 400 || call(`{"key":"x","minutes":99999}`) != 400 {
		t.Fatal("validation")
	}
}

func TestSyncAndDeleteActions(t *testing.T) {
	var calls []string
	kube := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		calls = append(calls, r.Method+" "+r.URL.Path+" "+r.Header.Get("Content-Type")+" "+string(b))
		w.WriteHeader(200)
	}))
	defer kube.Close()
	token := strings.Repeat("t", 32)
	s := &server{token: token, kube: kube.Client(), kubeURL: kube.URL, actions: loadActionPolicy("true", "")}
	post := func(h http.HandlerFunc, body string) int {
		r := httptest.NewRequest("POST", "/", strings.NewReader(body))
		r.Header.Set("Authorization", "Bearer "+token)
		w := httptest.NewRecorder()
		h(w, r)
		return w.Code
	}
	if c := post(s.syncAction, `{"name":"jellyfin"}`); c != 200 {
		t.Fatal(c)
	}
	if !strings.HasPrefix(calls[0], "PATCH /apis/argoproj.io/v1alpha1/namespaces/argocd/applications/jellyfin application/merge-patch+json") || !strings.Contains(calls[0], `"sync":{}`) {
		t.Fatal(calls[0])
	}
	if c := post(s.deletePodAction, `{"namespace":"media","name":"jellyfin-abc12-x2k9p"}`); c != 200 || !strings.HasPrefix(calls[1], "DELETE /api/v1/namespaces/media/pods/jellyfin-abc12-x2k9p") {
		t.Fatal(c, calls)
	}
	if c := post(s.deletePodAction, `{"namespace":"kube-system","name":"coredns-1"}`); c != 403 {
		t.Fatal("protected namespace", c)
	}
	s.actions = loadActionPolicy("", "")
	if c := post(s.syncAction, `{"name":"other"}`); c != 403 {
		t.Fatal("actions off", c)
	}
}
