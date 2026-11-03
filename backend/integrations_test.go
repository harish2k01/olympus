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
			case "/api/v3/queue":
				if r.URL.Query().Get("pageSize") != "10" {
					t.Error("queue request must stay bounded")
				}
				w.Write([]byte(`{"totalRecords":2,"records":[{"title":"Example secret-key","status":"downloading","size":100,"sizeleft":36},{"title":"Unknown size","status":"queued"}]}`))
			case "/api/v3/wanted/missing":
				w.Write([]byte(`{"totalRecords":0}`))
			default:
				w.Write([]byte(`[]`))
			}
		}))
		result := collectService(context.Background(), IntegrationConfig{Name: "Radarr", Kind: "radarr", URL: upstream.URL, Token: "secret-key"})
		upstream.Close()
		if len(result.Queue) != 2 || result.Fields["Queued"] == nil || *result.Fields["Queued"] != 2 ||
			result.Queue[0].Progress == nil || *result.Queue[0].Progress != 64 ||
			strings.Contains(result.Queue[0].Title, "secret-key") || result.Queue[1].Progress != nil {
			t.Fatal("queue projection lost progress, count, or redaction")
		}
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
