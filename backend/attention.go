package main

import (
	"context"
	"fmt"
	"strings"
)

type Issue struct {
	Title    string `json:"title"`
	Target   string `json:"target"`
	Severity string `json:"severity"`
}

func (s *server) attention(ctx context.Context, out *Snapshot) {
	out.Issues = []Issue{}
	for _, node := range out.Nodes {
		if !node.Ready {
			out.Issues = append(out.Issues, Issue{node.Name + " is not ready", "node:" + node.Name, "Warning"})
		}
		for _, condition := range node.Pressure {
			out.Issues = append(out.Issues, Issue{node.Name + " · " + condition, "node:" + node.Name, "Warning"})
		}
	}
	for _, app := range out.Apps {
		if app.Health != "Healthy" || app.Sync != "Synced" {
			out.Issues = append(out.Issues, Issue{app.Name + " · " + app.Health + " / " + app.Sync, "app:" + app.Name, "Warning"})
		}
	}
	if out.MonitorsUp != nil && out.MonitorsTotal != nil && *out.MonitorsTotal > *out.MonitorsUp {
		out.Issues = append(out.Issues, Issue{fmt.Sprintf("%.0f uptime checks down", *out.MonitorsTotal-*out.MonitorsUp), "app:uptime-kuma", "Warning"})
	}
	rows, err := s.query(ctx, `topk(10, sum by(namespace,pod) (increase(kube_pod_container_status_restarts_total[30m])) > 3)`)
	if err != nil {
		return
	}
	for _, row := range rows {
		if n := number(row); n != nil {
			target := "Events"
			for _, app := range out.Apps {
				if app.Namespace == row.Metric["namespace"] && strings.HasPrefix(row.Metric["pod"], app.Name+"-") {
					target = "app:" + app.Name
					break
				}
			}
			out.Issues = append(out.Issues, Issue{fmt.Sprintf("%s/%s · %.0f restarts / 30 min", row.Metric["namespace"], row.Metric["pod"], *n), target, "Warning"})
		}
	}
}
