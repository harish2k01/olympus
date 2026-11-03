package main

import (
	"encoding/json"
	"strings"
)

func (event Event) nextStep() string {
	if event.State != "Firing" || event.Namespace == "" || event.Pod == "" {
		return ""
	}
	switch {
	case event.Title == "CPUThrottlingHigh" && event.Container != "":
		return "Compare container CPU usage with its CPU limit over the same time range. Check whether throttling coincides with slower requests."
	case event.Source == "Kubernetes" && strings.HasPrefix(event.Title, "CrashLoopBackOff · "):
		return "Open the affected pod and inspect the previous container logs and last termination reason."
	default:
		return ""
	}
}

func (event Event) MarshalJSON() ([]byte, error) {
	type eventFields Event
	return json.Marshal(struct {
		eventFields
		NextStep string `json:"nextStep,omitempty"`
	}{eventFields: eventFields(event), NextStep: event.nextStep()})
}
