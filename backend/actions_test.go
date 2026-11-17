package main

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestActionPolicyIsOffByDefaultAndProtectsSystemNamespaces(t *testing.T) {
	if loadActionPolicy("", "").allows("media") {
		t.Fatal("actions must be off unless enabled")
	}
	p := loadActionPolicy("true", "")
	if !p.allows("media") || p.allows("kube-system") || p.allows("olympus") {
		t.Fatal("unexpected namespace decision")
	}
	if loadActionPolicy("true", "media, home").allows("monitoring") {
		t.Fatal("allowlist ignored")
	}
}

func TestActionCooldown(t *testing.T) {
	p := loadActionPolicy("true", "")
	now := time.Now()
	if !p.claim("a", now) || p.claim("a", now.Add(30*time.Second)) || !p.claim("a", now.Add(61*time.Second)) {
		t.Fatal("cooldown not enforced")
	}
}

func TestRestartActionPatchesDeployment(t *testing.T) {
	var gotPath, gotType, gotBody string
	kube := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath, gotType = r.Method+" "+r.URL.Path, r.Header.Get("Content-Type")
		b, _ := io.ReadAll(r.Body)
		gotBody = string(b)
		w.WriteHeader(200)
	}))
	defer kube.Close()
	token := strings.Repeat("t", 32)
	s := &server{token: token, kube: kube.Client(), kubeURL: kube.URL, actions: loadActionPolicy("true", "")}
	call := func(body string) int {
		r := httptest.NewRequest("POST", "/v1/actions/restart", strings.NewReader(body))
		r.Header.Set("Authorization", "Bearer "+token)
		w := httptest.NewRecorder()
		s.restartAction(w, r)
		return w.Code
	}
	if code := call(`{"namespace":"media","kind":"deployment","name":"jellyfin"}`); code != 200 {
		t.Fatal(code)
	}
	if gotPath != "PATCH /apis/apps/v1/namespaces/media/deployments/jellyfin" || gotType != "application/strategic-merge-patch+json" || !strings.Contains(gotBody, "restartedAt") {
		t.Fatal(gotPath, gotType, gotBody)
	}
	if code := call(`{"namespace":"media","kind":"deployment","name":"jellyfin"}`); code != 429 {
		t.Fatal("second restart within cooldown", code)
	}
	if code := call(`{"namespace":"kube-system","kind":"deployment","name":"coredns"}`); code != 403 {
		t.Fatal("protected namespace", code)
	}
	if code := call(`{"namespace":"media","kind":"pod","name":"x"}`); code != 400 {
		t.Fatal("unknown kind", code)
	}
}
