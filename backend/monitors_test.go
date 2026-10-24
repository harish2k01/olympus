package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestMonitorStatusAndLatencyJoinByIdentity(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.Query().Get("query"), "response_time") {
			w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"monitor_id":"2"},"value":[0,"46"]}]}}`))
			return
		}
		w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"monitor_id":"1","monitor_name":"A"},"value":[0,"1"]},{"metric":{"monitor_id":"2","monitor_name":"B"},"value":[0,"0"]}]}}`))
	}))
	defer upstream.Close()
	s := &server{client: upstream.Client(), prom: upstream.URL}
	rows := s.monitorList(context.Background())
	if len(rows) != 2 || rows[0].Name != "B" || rows[0].Status != "Down" || rows[0].Response == nil || *rows[0].Response != 46 || rows[1].Response != nil {
		t.Fatal("monitor identity or missing latency was misrepresented")
	}
}
