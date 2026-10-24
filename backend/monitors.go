package main

import (
	"context"
	"sort"
)

type Monitor struct {
	ID       string   `json:"id"`
	Name     string   `json:"name"`
	Type     string   `json:"type"`
	Status   string   `json:"status"`
	Response *float64 `json:"responseMs"`
}

func (s *server) monitorList(ctx context.Context) []Monitor {
	status, err := s.query(ctx, "monitor_status")
	if err != nil {
		return nil
	}
	response, _ := s.query(ctx, "monitor_response_time")
	latency := map[string]*float64{}
	for _, v := range response {
		latency[v.Metric["monitor_id"]] = number(v)
	}
	out := []Monitor{}
	seen := map[string]bool{}
	for _, v := range status {
		id := v.Metric["monitor_id"]
		if id == "" || seen[id] {
			continue
		}
		seen[id] = true
		state := "Unknown"
		if n := number(v); n != nil {
			switch *n {
			case 0:
				state = "Down"
			case 1:
				state = "Up"
			case 2:
				state = "Pending"
			case 3:
				state = "Maintenance"
			}
		}
		out = append(out, Monitor{id, v.Metric["monitor_name"], v.Metric["monitor_type"], state, latency[id]})
	}
	sort.Slice(out, func(i, j int) bool {
		if (out[i].Status == "Up") != (out[j].Status == "Up") {
			return out[i].Status != "Up"
		}
		return out[i].Name < out[j].Name
	})
	return out
}
