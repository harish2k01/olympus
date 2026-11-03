package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestPairingIssuesOneUseReadCredential(t *testing.T) {
	s := &server{token: strings.Repeat("a", 32)}
	issue := func(token string) *httptest.ResponseRecorder {
		req := httptest.NewRequest("POST", "/v1/pairing/code", strings.NewReader(`{"url":"https://olympus.example"}`))
		req.Header.Set("Authorization", "Bearer "+token)
		res := httptest.NewRecorder()
		s.issuePairingCode(res, req)
		return res
	}
	if issue("invalid").Code != http.StatusUnauthorized {
		t.Fatal("issued a code without operator authorization")
	}
	response := issue(s.token)
	var payload struct{ Code string }
	if response.Code != http.StatusCreated || json.Unmarshal(response.Body.Bytes(), &payload) != nil || len(payload.Code) != 43 {
		t.Fatal("did not issue a valid code")
	}
	exchange := func(code string) *httptest.ResponseRecorder {
		body, _ := json.Marshal(map[string]string{"code": code})
		res := httptest.NewRecorder()
		s.exchangePairingCode(res, httptest.NewRequest("POST", "/v1/pairing/exchange", bytes.NewReader(body)))
		return res
	}
	result := exchange(payload.Code)
	var credential struct{ Token string }
	if result.Code != http.StatusOK || json.Unmarshal(result.Body.Bytes(), &credential) != nil ||
		credential.Token == s.token || !s.readAuthorized("Bearer "+credential.Token) {
		t.Fatal("did not issue a usable read credential")
	}
	if issue(credential.Token).Code != http.StatusUnauthorized || exchange(payload.Code).Code != http.StatusUnauthorized {
		t.Fatal("device credential issued another code or used code was accepted")
	}
	expired := strings.Repeat("e", 43)
	s.pairing.expires[sha256.Sum256([]byte(expired))] = time.Now().Add(-time.Second)
	if exchange(expired).Code != http.StatusUnauthorized {
		t.Fatal("expired code was accepted")
	}
}

func TestPairingPNGIsNotCached(t *testing.T) {
	s := &server{token: strings.Repeat("a", 32)}
	req := httptest.NewRequest("POST", "/v1/pairing/code", strings.NewReader(`{"url":"https://olympus.example/"}`))
	req.Header.Set("Authorization", "Bearer "+s.token)
	req.Header.Set("Accept", "image/png")
	res := httptest.NewRecorder()
	s.issuePairingCode(res, req)
	if res.Code != http.StatusCreated || res.Header().Get("Content-Type") != "image/png" ||
		res.Header().Get("Cache-Control") != "no-store" || !bytes.HasPrefix(res.Body.Bytes(), []byte("\x89PNG")) {
		t.Fatal("expected a non-cacheable PNG pairing code")
	}
}
