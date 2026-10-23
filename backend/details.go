package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/url"
	"strings"
)

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
	for _, known := range []string{"sonarr", "radarr", "prowlarr", "bazarr", "jellyfin", "qbittorrent", "argocd", "longhorn", "grafana", "prometheus", "uptime-kuma", "immich", "ghost", "vault", "traefik", "paperless-ngx", "home-assistant", "seerr"} {
		if strings.Contains(name, known) {
			return known
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
	write(w, 200, map[string]any{"node": node, "pods": pods.Items})
}
