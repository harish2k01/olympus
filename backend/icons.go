package main

import (
	"bytes"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const maxIconBytes = 512 << 10

var iconClient = &http.Client{
	Timeout:       5 * time.Second,
	Transport:     iconTransport(),
	CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
}

func iconTransport() *http.Transport {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.MaxConnsPerHost = 6
	return transport
}

func iconUpstream(name string) string {
	switch strings.ToLower(name) {
	case "argocd":
		return "https://raw.githubusercontent.com/selfhst/icons/main/png/argo-cd.png"
	case "alloy":
		return "https://raw.githubusercontent.com/selfhst/icons/main/png/grafana-alloy.png"
	case "kubernetes", "prometheus":
		return "https://raw.githubusercontent.com/selfhst/icons/main/png/" + strings.ToLower(name) + ".png"
	default:
		return iconName(strings.ToLower(name))
	}
}

func serveAppIcon(w http.ResponseWriter, r *http.Request) {
	name := r.URL.Query().Get("name")
	if !historyIdentity.MatchString(name) {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	address, err := url.Parse(iconUpstream(name))
	if err != nil || address.Scheme != "https" ||
		(address.Host != "raw.githubusercontent.com" && address.Host != "cdn.jsdelivr.net") ||
		address.User != nil || !strings.HasSuffix(address.Path, ".png") {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, address.String(), nil)
	if err != nil {
		w.WriteHeader(http.StatusBadGateway)
		return
	}
	response, err := iconClient.Do(req)
	if err != nil {
		w.WriteHeader(http.StatusBadGateway)
		return
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	png, err := io.ReadAll(io.LimitReader(response.Body, maxIconBytes+1))
	if err != nil || len(png) > maxIconBytes || !bytes.HasPrefix(png, []byte("\x89PNG\r\n\x1a\n")) {
		w.WriteHeader(http.StatusBadGateway)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Cache-Control", "private, max-age=86400")
	_, _ = w.Write(png)
}
