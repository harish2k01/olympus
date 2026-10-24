package main

import "testing"

func TestProviderKindsPreserveTestsAndRobustaSeverity(t *testing.T) {
	cases := []struct {
		payload map[string]any
		kind    string
	}{
		{map[string]any{"eventType": "Test"}, "Test"},
		{map[string]any{"msg": "Test"}, "Test"},
		{map[string]any{"title": "Test", "severity": map[string]any{"_name_": "INFO"}}, "Test"},
		{map[string]any{"severity": map[string]any{"_name_": "HIGH", "__objclass__": "private implementation"}}, "High"},
		{map[string]any{"heartbeat": map[string]any{"status": float64(0)}}, "Warning"},
	}
	for _, c := range cases {
		if got := eventKind(c.payload); got != c.kind {
			t.Fatalf("provider classification %s != %s", got, c.kind)
		}
	}
}
