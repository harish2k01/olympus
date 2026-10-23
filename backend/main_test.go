package main

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestAuthFailsClosed(t *testing.T) {
	if auth("Bearer ", "") || auth("Bearer other", "secret") || !auth("Bearer secret", "secret") {
		t.Fatal("invalid authentication decision")
	}
}
func TestWebhookRequiresAuthenticationAndRetainsEvent(t *testing.T) {
	s := &server{hook: strings.Repeat("x", 32)}
	r := httptest.NewRequest("POST", "/hooks/Sonarr", strings.NewReader(`{"eventType":"Download"}`))
	w := httptest.NewRecorder()
	s.webhook(w, r)
	if w.Code != 401 {
		t.Fatal(w.Code)
	}
	r = httptest.NewRequest("POST", "/hooks/Sonarr", strings.NewReader(`{"eventType":"Download"}`))
	r.SetBasicAuth("olympus", s.hook)
	w = httptest.NewRecorder()
	s.webhook(w, r)
	if w.Code != 202 || len(s.events) != 1 || s.events[0].Source != "Sonarr" {
		t.Fatal("event not accepted")
	}
}
