package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestIncidentRecoveryRequiresAuthoritativeResource(t *testing.T) {
	s := &server{events: []Event{incident("node:n", "Kubernetes", "not ready", "", "Nodes", "n", "2026-10-24T00:00:00Z")}}
	active := s.reconcileIncidents(nil, map[string]bool{"Nodes": false}, "2026-10-24T00:01:00Z")
	if len(active) != 1 || active[0].State != "Unknown" || s.events[0].ResolvedAt != "" {
		t.Fatal("source failure must not resolve")
	}
	active = s.reconcileIncidents(nil, map[string]bool{"Nodes": true}, "2026-10-24T00:02:00Z")
	if len(active) != 1 {
		t.Fatal("missing node must not resolve")
	}
	active = s.reconcileIncidents(nil, map[string]bool{"Nodes": true, "resource:node:n": true}, "2026-10-24T00:03:00Z")
	if len(active) != 0 || s.events[0].State != "Resolved" || s.events[0].ResolvedAt == "" {
		t.Fatal("healthy observation must resolve")
	}
	active = s.reconcileIncidents([]Event{incident("node:n", "Kubernetes", "not ready", "", "Nodes", "n", "2026-10-24T00:04:00Z")}, map[string]bool{"Nodes": true}, "2026-10-24T00:04:00Z")
	if len(active) != 1 || active[0].ResolvedAt != "" || active[0].Time != "2026-10-24T00:04:00Z" {
		t.Fatal("reopened incident must have new episode time")
	}
}
func TestIntegrationCountersAndUnavailableFields(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Api-Key") != "private" {
			t.Error("missing auth")
		}
		w.Header().Set("Content-Type", "application/json")
		switch {
		case strings.Contains(r.URL.Path, "queue"):
			w.Write([]byte(`{"totalRecords":7}`))
		case strings.Contains(r.URL.Path, "missing"):
			w.Write([]byte(`{}`))
		default:
			w.Write([]byte(`[{},{}]`))
		}
	}))
	defer upstream.Close()
	got := collectService(context.Background(), IntegrationConfig{Name: "series", Kind: "sonarr", URL: upstream.URL, Token: "private"})
	if got.Fields["Queued"] == nil || *got.Fields["Queued"] != 7 || got.Fields["Series"] == nil || *got.Fields["Series"] != 2 {
		t.Fatal("incorrect counters")
	}
	if got.Fields["Wanted"] != nil || got.Error == "" {
		t.Fatal("missing total must not become zero")
	}
}
