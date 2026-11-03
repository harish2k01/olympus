package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestIntegrationHealthKeepsMessagesAndMissingStateSeparate(t *testing.T) {
	for _, enabled := range []bool{true, false} {
		upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.URL.Path {
			case "/api/v3/health":
				if !enabled {
					w.WriteHeader(http.StatusServiceUnavailable)
					return
				}
				w.Write([]byte(`[{"source":"Indexers","type":"warning","message":"Check config secret-key","wikiUrl":"https://wiki.example/help"}]`))
			case "/api/v3/queue", "/api/v3/wanted/missing":
				w.Write([]byte(`{"totalRecords":0}`))
			default:
				w.Write([]byte(`[]`))
			}
		}))
		result := collectService(context.Background(), IntegrationConfig{Name: "Radarr", Kind: "radarr", URL: upstream.URL, Token: "secret-key"})
		upstream.Close()
		if enabled {
			if len(result.Health) != 1 || result.Fields["Health issues"] == nil || *result.Fields["Health issues"] != 1 ||
				strings.Contains(result.Health[0].Message, "secret-key") || result.Health[0].WikiURL == "" {
				t.Fatal("health response was not projected safely")
			}
		} else if result.Health != nil || result.Fields["Health issues"] != nil || result.Error == "" {
			t.Fatal("missing health response was reported as healthy")
		}
	}
}
