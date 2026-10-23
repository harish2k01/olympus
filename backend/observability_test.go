package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestAccessLogsDoNotContainSecrets(t *testing.T) {
	var output bytes.Buffer
	old := slog.Default()
	slog.SetDefault(slog.New(slog.NewJSONHandler(&output, nil)))
	defer slog.SetDefault(old)
	handler := accessLog(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(401) }))
	r := httptest.NewRequest("POST", "/hooks/private-source?token=query-secret", strings.NewReader("body-secret"))
	r.Header.Set("Authorization", "Bearer header-secret")
	handler.ServeHTTP(httptest.NewRecorder(), r)
	log := output.String()
	for _, secret := range []string{"private-source", "query-secret", "body-secret", "header-secret"} {
		if strings.Contains(log, secret) {
			t.Fatal("secret in logs")
		}
	}
	if !strings.Contains(log, "request_id") || !strings.Contains(log, `"status":401`) {
		t.Fatal("missing structured fields")
	}
}
func TestWebhookHistorySurvivesRestartAndPrunes(t *testing.T) {
	file := filepath.Join(t.TempDir(), "events.json")
	now := time.Now().UTC().Format(time.RFC3339)
	s := &server{history: file, events: []Event{{ID: "recent", Time: now}, {ID: "old", Time: time.Now().Add(-31 * 24 * time.Hour).Format(time.RFC3339)}}}
	s.saveHistory()
	restarted := &server{history: file}
	restarted.loadHistory()
	if len(restarted.events) != 1 || restarted.events[0].ID != "recent" {
		t.Fatal("history was not preserved or bounded")
	}
}
func TestStreamAuthenticatesAndDeliversInitialState(t *testing.T) {
	s := &server{token: strings.Repeat("x", 32)}
	s.live.publish(liveFrame{Snapshot: Snapshot{Time: "now"}, Events: []Event{}})
	h := httptest.NewServer(accessLog(http.HandlerFunc(s.stream)))
	defer h.Close()
	denied, err := http.Get(h.URL)
	if err != nil {
		t.Fatal(err)
	}
	denied.Body.Close()
	if denied.StatusCode != 401 {
		t.Fatal(denied.StatusCode)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	r, _ := http.NewRequestWithContext(ctx, "GET", h.URL, nil)
	r.Header.Set("Authorization", "Bearer "+s.token)
	response, err := http.DefaultClient.Do(r)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	scanner := bufio.NewScanner(response.Body)
	found := false
	for scanner.Scan() {
		if strings.HasPrefix(scanner.Text(), "data: ") {
			var frame liveFrame
			if json.Unmarshal([]byte(strings.TrimPrefix(scanner.Text(), "data: ")), &frame) != nil || frame.Snapshot.Time != "now" {
				t.Fatal("invalid initial frame")
			}
			found = true
			break
		}
	}
	if !found {
		t.Fatal("stream did not flush")
	}
}
func TestPayloadAllowlistDropsCredentials(t *testing.T) {
	detail := safeDetail(map[string]any{"eventType": "Download", "apiKey": "private", "movie": map[string]any{"title": "Example", "path": "private-path"}})
	if strings.Contains(detail, "private") || !strings.Contains(detail, "Example") {
		t.Fatal("payload filtering failed")
	}
}
