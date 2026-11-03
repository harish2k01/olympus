package main

import (
	"context"
	"math"
	"sync"
	"time"
)

func (s *server) collectMetricObservations(ctx context.Context, out *Snapshot) {
	metrics := []struct {
		name, query string
	}{
		{"CPU", "min(" + clusterNodeMetric(`timestamp(node_cpu_seconds_total{job="node-exporter",mode="idle"})`) + ")"},
		{"Memory", "min(" + clusterNodeMetric(`timestamp(node_memory_MemAvailable_bytes{job="node-exporter"})`) + ")"},
		{"Uptime Kuma", "min(timestamp(monitor_status))"},
	}
	times := make([]*float64, len(metrics))
	var wait sync.WaitGroup
	for i, metric := range metrics {
		wait.Add(1)
		go func() {
			defer wait.Done()
			times[i] = s.scalar(ctx, metric.query)
		}()
	}
	wait.Wait()
	for i, seconds := range times {
		if seconds != nil && *seconds > 0 && !math.IsNaN(*seconds) && !math.IsInf(*seconds, 0) {
			out.ObservedAt[metrics[i].name] = time.Unix(int64(*seconds), 0).UTC().Format(time.RFC3339)
		}
	}
}
