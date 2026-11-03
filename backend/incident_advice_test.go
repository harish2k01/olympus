package main

import (
	"encoding/json"
	"testing"
)

func TestIncidentAdviceRequiresFiringAndIdentifiedWorkload(t *testing.T) {
	event := Event{Title: "CPUThrottlingHigh", State: "Firing", Namespace: "media", Pod: "sonarr", Container: "sonarr"}
	for _, state := range []string{"Firing", "Resolved", "Pending"} {
		event.State = state
		raw, err := json.Marshal(event)
		var result map[string]any
		if err != nil || json.Unmarshal(raw, &result) != nil || result["title"] != event.Title {
			t.Fatal("incident payload did not retain original fields")
		}
		if _, present := result["nextStep"]; present != (state == "Firing") {
			t.Fatalf("unexpected advice for state %s", state)
		}
	}
	event.State, event.Pod = "Firing", ""
	if event.nextStep() != "" {
		t.Fatal("advice given without a verified target")
	}
}

func TestNodeReadinessRequiresAnExplicitFalseForCritical(t *testing.T) {
	for _, state := range []string{"False", "Unknown", ""} {
		event, present := nodeReadinessIncident(Node{Name: "apollo", Readiness: state}, "2026-11-03T12:00:00Z")
		if !present || event.Node != "apollo" || (event.Kind == "Critical") != (state == "False") ||
			(event.State == "Firing") != (state == "False") {
			t.Fatalf("misclassified node readiness %q: %+v", state, event)
		}
	}
	if _, present := nodeReadinessIncident(Node{Ready: true, Readiness: "True"}, "2026-11-03T12:00:00Z"); present {
		t.Fatal("ready node became an incident")
	}
}
