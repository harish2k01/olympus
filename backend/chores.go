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
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Chore is non-urgent work: it never changes the glance verdict, it waits in "Later".
type Chore struct {
	ID     string `json:"id"`
	Kind   string `json:"kind"` // certificate, volume, upgrade, dependency
	Title  string `json:"title"`
	Detail string `json:"detail"`
	Due    string `json:"due,omitempty"` // RFC 3339, when the chore becomes urgent
	Link   string `json:"link,omitempty"`
}

const certWarningDays = 21
const volumeWarningRatio = 0.8

// externalCache keeps slow third-party lookups (GitHub) off the 15 s collection path.
type externalCache struct {
	sync.Mutex
	values map[string]cachedExternal
}
type cachedExternal struct {
	at    time.Time
	value string
	ok    bool
}

func (c *externalCache) get(key string, ttl time.Duration, now time.Time, fetch func() (string, error)) (string, bool) {
	c.Lock()
	if c.values == nil {
		c.values = map[string]cachedExternal{}
	}
	entry, found := c.values[key]
	c.Unlock()
	if found && now.Sub(entry.at) < ttl {
		return entry.value, entry.ok
	}
	previous := entry
	value, err := fetch()
	entry = cachedExternal{at: now, value: value, ok: err == nil}
	if err != nil {
		slog.Warn("chore_source_failed", "source", key, "failure", err.Error())
		if found && previous.ok {
			entry.value, entry.ok = previous.value, true // keep the last good answer until the source recovers
		}
	}
	c.Lock()
	c.values[key] = entry
	c.Unlock()
	return entry.value, entry.ok
}

func (s *server) chores(ctx context.Context, out *Snapshot) {
	now := time.Now()
	out.Later = []Chore{}
	out.Later = append(out.Later, s.certificateChores(ctx, now)...)
	out.Later = append(out.Later, s.volumeChores(ctx)...)
	out.Later = append(out.Later, s.talosChore(ctx, now)...)
	out.Later = append(out.Later, s.dependabotChore(ctx, now)...)
	sort.SliceStable(out.Later, func(i, j int) bool {
		if out.Later[i].Due == "" || out.Later[j].Due == "" {
			return out.Later[i].Due != ""
		}
		return out.Later[i].Due < out.Later[j].Due
	})
}

func (s *server) certificateChores(ctx context.Context, now time.Time) []Chore {
	rows, err := s.query(ctx, fmt.Sprintf(`certmanager_certificate_expiration_timestamp_seconds - time() < %d`, certWarningDays*86400))
	if err != nil {
		return nil
	}
	chores := []Chore{}
	for _, row := range rows {
		left := number(row)
		if left == nil {
			continue
		}
		name, ns := row.Metric["name"], row.Metric["namespace"]
		days := int(*left / 86400)
		title := fmt.Sprintf("Certificate %s renews in %d days", name, days)
		if *left <= 0 {
			title = fmt.Sprintf("Certificate %s has expired", name)
		}
		chores = append(chores, Chore{ID: "cert:" + ns + "/" + name, Kind: "certificate", Title: title,
			Detail: ns + " · cert-manager renews automatically; check it if this stays", Due: now.Add(time.Duration(*left) * time.Second).UTC().Format(time.RFC3339)})
	}
	return chores
}

func (s *server) volumeChores(ctx context.Context) []Chore {
	rows, err := s.query(ctx, fmt.Sprintf(`max by(namespace,persistentvolumeclaim) (kubelet_volume_stats_used_bytes / kubelet_volume_stats_capacity_bytes) > %g`, volumeWarningRatio))
	if err != nil {
		return nil
	}
	chores := []Chore{}
	for _, row := range rows {
		used := number(row)
		if used == nil {
			continue
		}
		ns, claim := row.Metric["namespace"], row.Metric["persistentvolumeclaim"]
		chores = append(chores, Chore{ID: "volume:" + ns + "/" + claim, Kind: "volume",
			Title: fmt.Sprintf("Volume %s is %.0f%% full", claim, *used*100), Detail: ns + " · expand the claim or clean up"})
	}
	return chores
}

var talosVersion = regexp.MustCompile(`v(\d+)\.(\d+)\.(\d+)`)

// newerVersion reports whether latest is a higher semantic version than current (both "vX.Y.Z" somewhere in the string).
func newerVersion(current, latest string) bool {
	a, b := talosVersion.FindStringSubmatch(current), talosVersion.FindStringSubmatch(latest)
	if a == nil || b == nil {
		return false
	}
	for i := 1; i <= 3; i++ {
		x, _ := strconv.Atoi(a[i])
		y, _ := strconv.Atoi(b[i])
		if x != y {
			return y > x
		}
	}
	return false
}

func (s *server) talosChore(ctx context.Context, now time.Time) []Chore {
	rows, err := s.query(ctx, `count by(os_image) (kube_node_info)`)
	if err != nil || len(rows) == 0 {
		return nil
	}
	current := ""
	for _, row := range rows {
		image := row.Metric["os_image"]
		if !strings.Contains(image, "Talos") {
			return nil
		}
		if v := talosVersion.FindString(image); v != "" && (current == "" || newerVersion(v, current)) {
			current = v // oldest version in the cluster
		}
	}
	if current == "" {
		return nil
	}
	latest, ok := s.external.get("talos-release", 6*time.Hour, now, func() (string, error) {
		var release struct {
			TagName string `json:"tag_name"`
		}
		err := s.githubJSON(ctx, "https://api.github.com/repos/siderolabs/talos/releases/latest", "", &release)
		return release.TagName, err
	})
	if !ok || !newerVersion(current, latest) {
		return nil
	}
	return []Chore{{ID: "upgrade:talos:" + latest, Kind: "upgrade", Title: "Talos " + current + " → " + latest + " available",
		Detail: "Read the release notes before upgrading", Link: "https://github.com/siderolabs/talos/releases/tag/" + latest}}
}

// dependabotChore counts open Dependabot PRs for GITHUB_OWNER. It is skipped without GITHUB_TOKEN.
func (s *server) dependabotChore(ctx context.Context, now time.Time) []Chore {
	owner, token := os.Getenv("GITHUB_OWNER"), os.Getenv("GITHUB_TOKEN")
	if owner == "" || token == "" {
		return nil
	}
	count, ok := s.external.get("dependabot", 30*time.Minute, now, func() (string, error) {
		var result struct {
			TotalCount int `json:"total_count"`
		}
		q := url.QueryEscape("is:pr is:open author:app/dependabot user:" + owner)
		err := s.githubJSON(ctx, "https://api.github.com/search/issues?per_page=1&q="+q, token, &result)
		return strconv.Itoa(result.TotalCount), err
	})
	n, _ := strconv.Atoi(count)
	if !ok || n == 0 {
		return nil
	}
	return []Chore{{ID: "dependency:dependabot", Kind: "dependency", Title: fmt.Sprintf("%d Dependabot PRs open", n),
		Detail: "Review and merge dependency updates", Link: "https://github.com/pulls?q=" + url.QueryEscape("is:pr is:open author:app/dependabot user:"+owner)}}
}

func (s *server) githubJSON(ctx context.Context, address, token string, dst any) error {
	ctx, cancel := context.WithTimeout(ctx, 6*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "olympus-backend")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("status %d", resp.StatusCode)
	}
	return json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(dst)
}
