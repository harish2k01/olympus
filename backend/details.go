package main

import (
	"context"
	_ "embed"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
)

//go:embed icons.json
var chartIcons []byte
var icons = func() map[string]string { m := map[string]string{}; _ = json.Unmarshal(chartIcons, &m); return m }()

type Resource struct {
	Kind      string `json:"kind"`
	Name      string `json:"name"`
	Namespace string `json:"namespace"`
}
type pod struct {
	Metadata struct {
		Name, Namespace, UID string
		Labels               map[string]string
		OwnerReferences      []struct{ Kind, Name string }
	} `json:"metadata"`
	Spec struct {
		NodeName   string
		Containers []struct {
			Name      string
			Resources struct{ Requests, Limits map[string]string }
		}
	} `json:"spec"`
	Status struct {
		Phase             string
		ContainerStatuses []struct {
			Name         string
			Ready        bool
			RestartCount int
		}
	} `json:"status"`
}

func (p pod) ready() bool {
	if p.Status.Phase != "Running" || len(p.Status.ContainerStatuses) == 0 {
		return false
	}
	for _, c := range p.Status.ContainerStatuses {
		if !c.Ready {
			return false
		}
	}
	return true
}
func (s *server) enrichApps(ctx context.Context, out *Snapshot) {
	var routes struct {
		Items []struct {
			Metadata struct{ Name, Namespace string }
			Spec     struct{ Hostnames []string }
		}
	}
	_ = s.get(ctx, s.kube, s.kubeURL+"/apis/gateway.networking.k8s.io/v1/httproutes", &routes, true)
	var pods struct{ Items []pod }
	if s.get(ctx, s.kube, s.kubeURL+"/api/v1/pods", &pods, true) != nil {
		out.Errors = append(out.Errors, "Workload data unavailable")
		return
	}
	var replicas struct {
		Items []struct {
			Metadata struct {
				Name, Namespace string
				OwnerReferences []struct{ Kind, Name string }
			}
		}
	}
	_ = s.get(ctx, s.kube, s.kubeURL+"/apis/apps/v1/replicasets", &replicas, true)
	owners := map[string]string{}
	for _, rs := range replicas.Items {
		for _, o := range rs.Metadata.OwnerReferences {
			if o.Kind == "Deployment" {
				owners[rs.Metadata.Namespace+"/"+rs.Metadata.Name] = o.Name
			}
		}
	}
	cpu, e1 := s.query(ctx, `sum by(namespace,pod) (rate(container_cpu_usage_seconds_total{container!="",container!="POD"}[5m]))`)
	memory, e2 := s.query(ctx, `sum by(namespace,pod) (container_memory_working_set_bytes{container!="",container!="POD"}) / 1048576`)
	cm, mm := map[string]*float64{}, map[string]*float64{}
	for _, v := range cpu {
		cm[v.Metric["namespace"]+"/"+v.Metric["pod"]] = number(v)
	}
	for _, v := range memory {
		mm[v.Metric["namespace"]+"/"+v.Metric["pod"]] = number(v)
	}
	for i := range out.Apps {
		a := &out.Apps[i]
		a.Icon = iconName(a.Name)
		for _, resource := range a.Resources {
			if resource.Kind == "HTTPRoute" {
				for _, route := range routes.Items {
					if resource.Name == route.Metadata.Name && resource.Namespace == route.Metadata.Namespace && len(route.Spec.Hostnames) > 0 {
						host := route.Spec.Hostnames[0]
						if !strings.ContainsAny(host, "/*:@") {
							a.URL = "https://" + host
						}
					}
				}
			}
		}
		c, m := 0.0, 0.0
		completeCPU, completeMem := e1 == nil, e2 == nil
		for _, p := range pods.Items {
			owned := false
			for _, resource := range a.Resources {
				if resource.Namespace != p.Metadata.Namespace {
					continue
				}
				if resource.Kind == "Pod" && resource.Name == p.Metadata.Name {
					owned = true
				}
				for _, owner := range p.Metadata.OwnerReferences {
					if resource.Kind == owner.Kind && resource.Name == owner.Name {
						owned = true
					}
					if owner.Kind == "ReplicaSet" && resource.Kind == "Deployment" && resource.Name == owners[p.Metadata.Namespace+"/"+owner.Name] {
						owned = true
					}
				}
			}
			if !owned {
				continue
			}
			if p.Status.Phase == "Succeeded" {
				continue
			}
			a.Pods++
			if p.ready() {
				a.Ready++
			}
			if p.Status.Phase == "Succeeded" || p.Status.Phase == "Failed" {
				continue
			}
			key := p.Metadata.Namespace + "/" + p.Metadata.Name
			if v := cm[key]; v != nil {
				c += *v
			} else {
				completeCPU = false
			}
			if v := mm[key]; v != nil {
				m += *v
			} else {
				completeMem = false
			}
		}
		if a.Pods > 0 && completeCPU {
			a.CPU = &c
		}
		if a.Pods > 0 && completeMem {
			a.Memory = &m
		}
	}
}
func iconName(name string) string {
	if strings.Contains(name, "gh-runner") {
		return "https://raw.githubusercontent.com/selfhst/icons/main/png/github.png"
	}
	if icon := icons[name]; icon != "" {
		return icon
	}
	for _, suffix := range []string{"4k", "anime", "-exporter"} {
		if icon := icons[strings.TrimSuffix(name, suffix)]; icon != "" {
			return icon
		}
	}
	for _, known := range []string{"sonarr", "radarr", "prowlarr", "bazarr", "jellyfin", "qbittorrent", "argocd", "longhorn", "grafana", "prometheus", "uptime-kuma", "immich", "ghost", "vault", "traefik", "paperless-ngx", "home-assistant", "seerr"} {
		if strings.Contains(name, known) {
			return "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/png/" + known + ".png"
		}
	}
	return ""
}

// Retain a small allowlist of operational fields, never arbitrary payloads or secrets.
func safeDetail(payload map[string]any) string {
	out := map[string]any{}
	for _, key := range []string{"eventType", "title", "message", "msg", "severity", "description"} {
		if value, ok := payload[key].(string); ok {
			if len(value) > 2000 {
				value = value[:2000]
			}
			out[key] = value
		}
	}
	for _, key := range []string{"movie", "series", "episode", "release", "monitor", "heartbeat"} {
		if item, ok := payload[key].(map[string]any); ok {
			fields := map[string]any{}
			for _, field := range []string{"title", "name", "year", "seasonNumber", "episodeNumber", "status", "time", "duration", "ping", "quality"} {
				if value, ok := item[field]; ok {
					switch value.(type) {
					case string, float64, bool:
						fields[field] = value
					}
				}
			}
			if len(fields) > 0 {
				out[key] = fields
			}
		}
	}
	if len(out) == 0 {
		return ""
	}
	data, _ := json.MarshalIndent(out, "", "  ")
	if len(data) > 6000 {
		return string(data[:6000]) + "…"
	}
	return string(data)
}
func (s *server) nodeDetail(w http.ResponseWriter, r *http.Request, ctx context.Context) {
	name := strings.TrimPrefix(r.URL.Path, "/v1/nodes/")
	if name == "" || strings.Contains(name, "/") {
		w.WriteHeader(404)
		return
	}
	var node struct {
		Metadata struct {
			Name   string
			Labels map[string]string
		}
		Spec struct {
			Taints        []map[string]any
			Unschedulable bool
		}
		Status struct {
			Capacity, Allocatable map[string]string
			Conditions            []map[string]any
			Addresses             []map[string]any
			NodeInfo              map[string]any
		}
	}
	if s.get(ctx, s.kube, s.kubeURL+"/api/v1/nodes/"+url.PathEscape(name), &node, true) != nil {
		write(w, 502, map[string]string{"error": "Node unavailable"})
		return
	}
	var pods struct{ Items []pod }
	if s.get(ctx, s.kube, s.kubeURL+"/api/v1/pods?fieldSelector="+url.QueryEscape("spec.nodeName="+name), &pods, true) != nil {
		write(w, 502, map[string]string{"error": "Workloads unavailable"})
		return
	}
	stats := map[string]*float64{}
	for _, address := range node.Status.Addresses {
		if address["type"] != "InternalIP" {
			continue
		}
		ip, _ := address["address"].(string)
		selector := `job="node-exporter",instance=~` + strconv.Quote(regexp.QuoteMeta(ip)+":.*")
		stats["diskUsedPercent"] = s.scalar(ctx, `max(100*(1-node_filesystem_avail_bytes{`+selector+`,mountpoint=~"/|/var",fstype!~"tmpfs|overlay"}/node_filesystem_size_bytes{`+selector+`,mountpoint=~"/|/var",fstype!~"tmpfs|overlay"}))`)
		stats["receiveMiBs"] = s.scalar(ctx, `sum(rate(node_network_receive_bytes_total{`+selector+`,device!~"lo|veth.*|cali.*|cilium.*|lxc.*"}[5m]))/1048576`)
		stats["transmitMiBs"] = s.scalar(ctx, `sum(rate(node_network_transmit_bytes_total{`+selector+`,device!~"lo|veth.*|cali.*|cilium.*|lxc.*"}[5m]))/1048576`)
	}
	var history struct {
		Data struct {
			Result []struct{ Values [][]json.RawMessage }
		}
	}
	query := ""
	for _, address := range node.Status.Addresses {
		if address["type"] == "InternalIP" {
			ip, _ := address["address"].(string)
			query = `100*(1-avg(rate(node_cpu_seconds_total{job="node-exporter",instance=~` + strconv.Quote(regexp.QuoteMeta(ip)+":.*") + `,mode="idle"}[5m])))`
		}
	}
	if query != "" {
		_ = s.get(ctx, s.client, s.prom+"/api/v1/query_range?query="+url.QueryEscape(query)+"&start="+fmt.Sprint(time.Now().Add(-15*time.Minute).Unix())+"&end="+fmt.Sprint(time.Now().Unix())+"&step=30", &history, false)
	}
	write(w, 200, map[string]any{"node": node, "pods": pods.Items, "stats": stats, "cpuHistory": history.Data.Result})
}
