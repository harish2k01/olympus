package main

import "time"

func recentActivity(events []Event, now time.Time) (map[string]int, map[string]int) {
	visible, all := map[string]int{"All": 0}, map[string]int{"All": 0}
	cutoff := now.Add(-24 * time.Hour)
	for _, e := range events {
		stamp, err := time.Parse(time.RFC3339Nano, eventOrder(e))
		if err != nil || stamp.Before(cutoff) || stamp.After(now) {
			continue
		}
		all["All"]++
		all[e.Source]++
		if e.Kind != "Test" && e.Source != "Test" && e.Title != "Test" {
			visible["All"]++
			visible[e.Source]++
		}
	}
	return visible, all
}
func (s *server) activitySummary(out *Snapshot, now time.Time) {
	s.mu.Lock()
	defer s.mu.Unlock()
	out.RecentActivity, out.RecentActivityWithTests = recentActivity(s.events, now)
}
