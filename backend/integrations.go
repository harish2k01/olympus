package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

type IntegrationConfig struct {
	Name  string `json:"name"`
	Kind  string `json:"kind"`
	URL   string `json:"url"`
	Token string `json:"token"`
}
type IntegrationState struct {
	Name   string              `json:"name"`
	Kind   string              `json:"kind"`
	Fields map[string]*float64 `json:"fields"`
	Time   string              `json:"time"`
	Error  string              `json:"error,omitempty"`
}

func (s *server) loadIntegrations() {
	raw := os.Getenv("INTEGRATIONS_JSON")
	if raw == "" {
		return
	}
	var configs []IntegrationConfig
	if json.Unmarshal([]byte(raw), &configs) != nil {
		slog.Error("integration_configuration_invalid")
		return
	}
	for _, c := range configs {
		u, e := url.Parse(c.URL)
		if e != nil || u.Host == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || (u.Scheme != "http" && u.Scheme != "https") || c.Token == "" {
			slog.Error("integration_configuration_invalid")
			continue
		}
		if c.Kind != "sonarr" && c.Kind != "radarr" && c.Kind != "prowlarr" {
			continue
		}
		c.URL = strings.TrimRight(c.URL, "/")
		s.integrations = append(s.integrations, c)
	}
	slog.Info("integrations_configured", "count", len(s.integrations))
}

func (s *server) serviceIntegrations(ctx context.Context) []IntegrationState {
	if time.Since(s.integrationTime) < 30*time.Second {
		return s.integrationCache
	}
	out := make([]IntegrationState, len(s.integrations))
	var wg sync.WaitGroup
	for i, c := range s.integrations {
		wg.Add(1)
		go func(i int, c IntegrationConfig) {
			defer wg.Done()
			bounded, cancel := context.WithTimeout(ctx, 6*time.Second)
			defer cancel()
			out[i] = collectService(bounded, c)
		}(i, c)
	}
	wg.Wait()
	s.integrationCache = out
	s.integrationTime = time.Now()
	return out
}

func collectService(ctx context.Context, c IntegrationConfig) IntegrationState {
	out := IntegrationState{Name: c.Name, Kind: c.Kind, Fields: map[string]*float64{}, Time: time.Now().UTC().Format(time.RFC3339)}
	client := &http.Client{Timeout: 4 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	get := func(path string, dst any) error {
		r, e := http.NewRequestWithContext(ctx, "GET", c.URL+path, nil)
		if e != nil {
			return e
		}
		r.Header.Set("X-Api-Key", c.Token)
		r.Header.Set("Accept", "application/json")
		response, e := client.Do(r)
		if e != nil {
			return fmt.Errorf("connection failed")
		}
		defer response.Body.Close()
		if response.StatusCode != 200 {
			return fmt.Errorf("upstream status %d", response.StatusCode)
		}
		return json.NewDecoder(io.LimitReader(response.Body, 8<<20)).Decode(dst)
	}
	count := func(key, path string, paged bool) {
		var n float64
		var err error
		if paged {
			var result struct{ TotalRecords *float64 }
			err = get(path, &result)
			if result.TotalRecords == nil {
				err = fmt.Errorf("missing totalRecords")
			} else {
				n = *result.TotalRecords
			}
		} else {
			var rows []json.RawMessage
			err = get(path, &rows)
			n = float64(len(rows))
		}
		if err != nil {
			out.Fields[key] = nil
			out.Error = "Some fields unavailable"
		} else {
			out.Fields[key] = &n
		}
	}
	if c.Kind == "prowlarr" {
		count("Indexers", "/api/v1/indexer", false)
		count("Health issues", "/api/v1/health", false)
	} else {
		count("Queued", "/api/v3/queue?pageSize=1", true)
		count("Wanted", "/api/v3/wanted/missing?pageSize=1", true)
		if c.Kind == "sonarr" {
			count("Series", "/api/v3/series", false)
		} else {
			count("Movies", "/api/v3/movie", false)
		}
	}
	if out.Error != "" {
		slog.Warn("integration_partial", "integration", c.Name)
	}
	return out
}
