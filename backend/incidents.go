package main

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"
)

// Only a successful authoritative observation can confirm recovery.
// Failure to read an integration makes its previously active incidents Unknown.
func (s *server) reconcileIncidents(current []Event, complete map[string]bool, now string) []Event {
	s.mu.Lock()
	defer s.mu.Unlock()
	incoming := map[string]Event{}
	for _, e := range current {
		e.LastSeen = now
		incoming[e.ID] = e
	}
	for i, old := range s.events {
		if old.Domain == "" {
			continue
		}
		if next, ok := incoming[old.ID]; ok {
			if old.State == "Resolved" {
				archived := old
				archived.ID = old.ID + ":resolved:" + old.ResolvedAt
				archived.Domain = ""
				s.events = append(s.events, archived)
			} else {
				next.Time = old.Time
				next.Received = old.Received
			}
			if next.Received == "" {
				next.Received = now
			}
			s.events[i] = next
			delete(incoming, old.ID)
		} else if old.State == "Firing" || old.State == "Unknown" || old.State == "Pending" {
			if known, tracked := complete[old.Domain]; tracked && known && (old.Domain == "Prometheus" || old.Domain == "Workloads" || complete["resource:"+old.ID]) {
				old.State = "Resolved"
				old.ResolvedAt = now
				old.LastSeen = now
				old.Evidence = "Condition no longer active in a successful " + old.Domain + " snapshot"
			} else {
				old.State = "Unknown"
				old.Evidence = "Current state unavailable; recovery has not been confirmed"
			}
			s.events[i] = old
		}
	}
	for _, e := range incoming {
		if e.Received == "" {
			e.Received = now
		}
		s.events = append(s.events, e)
	}
	active := []Event{}
	for _, e := range s.events {
		if e.Domain != "" && (e.State == "Firing" || e.State == "Pending" || e.State == "Unknown") {
			active = append(active, e)
		}
	}
	return active
}

func incident(id, source, title, detail, domain, resource, now string) Event {
	return Event{ID: id, Source: source, Title: title, Detail: detail, Kind: "Warning", Time: now, Received: now, State: "Firing", LastSeen: now, Domain: domain, Resource: resource, Evidence: "Confirmed by the latest successful " + domain + " observation"}
}

func (s *server) collectIncidents(ctx context.Context, out *Snapshot) {
	now := out.Time
	current := []Event{}
	complete := map[string]bool{"Nodes": len(out.Nodes) > 0, "Argo CD": true, "Uptime Kuma": len(out.Monitors) > 0}
	for _, err := range out.Errors {
		if strings.Contains(err, "Argo") {
			complete["Argo CD"] = false
		}
	}
	for _, n := range out.Nodes {
		complete["resource:node:"+n.Name] = true
		for _, p := range []string{"MemoryPressure", "DiskPressure", "PIDPressure", "NetworkUnavailable"} {
			complete["resource:node:"+n.Name+":"+p] = true
		}
		if !n.Ready {
			current = append(current, incident("node:"+n.Name, "Kubernetes", n.Name+" is not ready", "Node readiness condition is not True", "Nodes", n.Name, now))
		}
		for _, p := range n.Pressure {
			current = append(current, incident("node:"+n.Name+":"+p, "Kubernetes", n.Name+" · "+p, "Node pressure condition is active", "Nodes", n.Name, now))
		}
	}
	for _, a := range out.Apps {
		complete["resource:app:"+a.Name] = true
		if a.Health != "Healthy" || a.Sync != "Synced" {
			e := incident("app:"+a.Name, "Argo CD", a.Name+" · "+a.Health+" / "+a.Sync, "Application health or desired revision requires attention", "Argo CD", a.Namespace+"/"+a.Name, now)
			if a.Health == "Unknown" || a.Health == "" {
				e.State = "Unknown"
			}
			current = append(current, e)
		}
	}
	for _, m := range out.Monitors {
		complete["resource:monitor:"+m.ID] = true
		if m.Status == "Down" {
			current = append(current, incident("monitor:"+m.ID, "UptimeKuma", m.Name+" is down", "Monitor currently reports Down", "Uptime Kuma", m.Name, now))
		} else if m.Status == "Unknown" || m.Status == "Pending" {
			e := incident("monitor:"+m.ID, "UptimeKuma", m.Name+" · "+m.Status, "Monitor is awaiting a confirmed health reading", "Uptime Kuma", m.Name, now)
			e.State = "Unknown"
			current = append(current, e)
		}
	}
	var alerts struct {
		Data struct {
			Alerts []struct {
				Labels, Annotations map[string]string
				ActiveAt, State     string
			}
		}
	}
	err := s.get(ctx, s.client, s.prom+"/api/v1/alerts", &alerts, false)
	complete["Prometheus"] = err == nil
	if err == nil {
		for _, a := range alerts.Data.Alerts {
			if a.Labels["alertname"] == "Watchdog" || a.Labels["alertname"] == "InfoInhibitor" {
				continue
			}
			identity, _ := json.Marshal(a.Labels)
			e := incident("alert:"+string(identity), "Prometheus", a.Labels["alertname"], a.Annotations["summary"], "Prometheus", a.Labels["namespace"]+"/"+a.Labels["pod"], now)
			e.Kind = a.Labels["severity"]
			if a.ActiveAt != "" {
				e.Time = a.ActiveAt
			}
			if a.State == "pending" {
				e.State = "Pending"
			}
			current = append(current, e)
		}
	}
	rows, err := s.query(ctx, `max by(namespace,pod) (kube_pod_container_status_waiting_reason{reason="CrashLoopBackOff"}) > 0`)
	exporter, exporterErr := s.query(ctx, `max(up{job=~".*kube-state-metrics.*"})`)
	complete["Workloads"] = err == nil && exporterErr == nil && len(exporter) > 0 && number(exporter[0]) != nil && *number(exporter[0]) > 0
	if err == nil {
		for _, r := range rows {
			resource := r.Metric["namespace"] + "/" + r.Metric["pod"]
			current = append(current, incident("crashloop:"+resource, "Kubernetes", "CrashLoopBackOff · "+r.Metric["pod"], "A container is repeatedly failing to start", "Workloads", resource, now))
		}
	}
	out.Active = s.reconcileIncidents(current, complete, now)
	out.Signals = complete
	if !complete["Prometheus"] {
		out.Errors = append(out.Errors, "Alert state unavailable")
	}
	if !complete["Workloads"] {
		out.Errors = append(out.Errors, "Workload state unavailable")
	}
	if !complete["Uptime Kuma"] {
		out.Errors = append(out.Errors, "Monitor state unavailable")
	}
	_ = s.saveHistory()
}

func applyWebhookState(e *Event, p map[string]any) {
	e.State = "Activity"
	if e.Kind == "Test" {
		e.State = "Test"
		return
	}
	if e.Source == "Robusta" {
		e.State = "Observed"
		if failure, ok := p["failure"].(bool); ok && !failure {
			e.Evidence = "Sender reports no active failure; not correlated with an earlier incident"
		}
	}
	if e.Kind == "Warning" || strings.EqualFold(e.Kind, "high") {
		e.State = "Observed"
	}
	if heartbeat, ok := p["heartbeat"].(map[string]any); ok {
		if status, ok := heartbeat["status"].(float64); ok {
			if status == 0 {
				e.State = "Observed"
				e.Evidence = "Down notification received; latest monitor snapshot determines current state"
			} else if status == 1 {
				e.State = "Recovery"
				e.Evidence = "Recovery notification received from Uptime Kuma"
			}
			e.LastSeen = time.Now().UTC().Format(time.RFC3339)
		}
	}
	if e.State == "Observed" && e.Evidence == "" {
		e.Evidence = fmt.Sprintf("%s notification recorded; current recovery state is unconfirmed", e.Source)
	}
}
