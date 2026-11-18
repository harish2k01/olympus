package main

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestNewerVersion(t *testing.T) {
	cases := []struct {
		current, latest string
		want            bool
	}{
		{"v1.12.4", "v1.13.0", true},
		{"Talos (v1.12.4)", "v1.12.10", true},
		{"v1.12.4", "v1.12.4", false},
		{"v1.13.0", "v1.12.9", false},
		{"v1.12.4", "nightly", false},
	}
	for _, c := range cases {
		if got := newerVersion(c.current, c.latest); got != c.want {
			t.Errorf("newerVersion(%q,%q)=%v", c.current, c.latest, got)
		}
	}
}

func TestExternalCacheKeepsLastGoodValueOnFailure(t *testing.T) {
	var c externalCache
	now := time.Now()
	if v, ok := c.get("k", time.Hour, now, func() (string, error) { return "v1", nil }); !ok || v != "v1" {
		t.Fatal(v, ok)
	}
	if v, _ := c.get("k", time.Hour, now.Add(time.Minute), func() (string, error) { t.Fatal("refetched inside ttl"); return "", nil }); v != "v1" {
		t.Fatal(v)
	}
	if v, ok := c.get("k", time.Hour, now.Add(2*time.Hour), func() (string, error) { return "", errors.New("down") }); v != "v1" || !ok {
		t.Fatal("last good value lost", v, ok)
	}
}

func TestChoresFromPrometheus(t *testing.T) {
	prom := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query().Get("query")
		switch {
		case strings.Contains(q, "predict_linear"):
			w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"namespace":"media","persistentvolumeclaim":"immich-library"},"value":[0,"-1"]}]}}`))
		case strings.Contains(q, "certmanager"):
			w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"name":"wildcard-lab","namespace":"gateway"},"value":[0,"777600"]}]}}`))
		case strings.Contains(q, "kubelet_volume_stats"):
			w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"namespace":"media","persistentvolumeclaim":"jellyfin-config"},"value":[0,"0.86"]}]}}`))
		default:
			w.Write([]byte(`{"status":"success","data":{"result":[]}}`))
		}
	}))
	defer prom.Close()
	s := &server{client: prom.Client(), prom: prom.URL}
	var out Snapshot
	s.chores(context.Background(), &out)
	if len(out.Later) != 3 {
		t.Fatal(out.Later)
	}
	if out.Later[0].Kind != "certificate" || out.Later[0].Title != "Certificate wildcard-lab renews in 9 days" {
		t.Fatal(out.Later[0])
	}
	if out.Later[1].Title != "Volume jellyfin-config is 86% full" {
		t.Fatal(out.Later[1])
	}
	if out.Later[2].Title != "Volume immich-library fills within 30 days" {
		t.Fatal(out.Later[2])
	}
}
