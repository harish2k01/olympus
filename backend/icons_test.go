package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAppIconUsesOnlyKnownNamesAndRequiresAuthentication(t *testing.T) {
	s := &server{token: "operator-token"}
	for _, item := range []struct {
		query  string
		token  string
		status int
	}{
		{"name=radarr", "", http.StatusUnauthorized},
		{"name=https%3A%2F%2Fexample.org%2Ficon.png", s.token, http.StatusNotFound},
		{"name=an-unknown-name", s.token, http.StatusNotFound},
	} {
		r := httptest.NewRequest(http.MethodGet, "/v1/icons?"+item.query, nil)
		r.Header.Set("Authorization", "Bearer "+item.token)
		res := httptest.NewRecorder()
		s.api(res, r)
		if res.Code != item.status {
			t.Fatalf("%s: got %d, want %d", item.query, res.Code, item.status)
		}
	}
}
