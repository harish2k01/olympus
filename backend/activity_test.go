package main

import (
	"testing"
	"time"
)

func TestRecentActivityUsesFullRollingWindowAndHidesTests(t *testing.T) {
	now := time.Date(2026, 10, 24, 12, 0, 0, 0, time.UTC)
	events := []Event{{Source: "Kubernetes", Time: now.Add(-time.Hour).Format(time.RFC3339)}, {Source: "Kubernetes", Time: now.Add(-25 * time.Hour).Format(time.RFC3339)}, {Source: "Robusta", Title: "Test", Time: now.Format(time.RFC3339)}, {Source: "Prometheus", Time: now.Add(-26 * time.Hour).Format(time.RFC3339), ResolvedAt: now.Add(-time.Hour).Format(time.RFC3339)}, {Source: "Kubernetes", Time: now.Add(time.Hour).Format(time.RFC3339)}}
	for i := 0; i < 150; i++ {
		events = append(events, Event{Source: "Sonarr", Time: now.Add(-2 * time.Hour).Format(time.RFC3339)})
	}
	visible, all := recentActivity(events, now)
	if visible["All"] != 152 || all["All"] != 153 || visible["Kubernetes"] != 1 || visible["Sonarr"] != 150 || visible["Robusta"] != 0 {
		t.Fatal(visible, all)
	}
}
func TestAlertNodeContextNeverGuessesFromReporter(t *testing.T) {
	labels := map[string]string{"namespace": "ghost", "pod": "ghost-abc", "container": "ghost", "instance": "reporter:10250", "severity": "info"}
	var e Event
	enrichAlertContext(&e, labels, map[string]string{"ghost/ghost-abc": "hermes"})
	if e.Node != "hermes" || e.Pod != "ghost-abc" || e.Container != "ghost" || e.NodeSource != "Observed pod placement" {
		t.Fatal(e)
	}
	enrichAlertContext(&e, labels, nil)
	if e.Node != "" {
		t.Fatal("missing pod must not use reporting instance as node")
	}
	labels["node"] = "apollo"
	enrichAlertContext(&e, labels, map[string]string{"ghost/ghost-abc": "hermes"})
	if e.Node != "apollo" || e.NodeSource != "Alert label" {
		t.Fatal("explicit affected node must take precedence")
	}
}
