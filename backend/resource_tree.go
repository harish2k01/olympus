package main

import (
	"context"
	"net/http"
	"net/url"
	"strings"
)

type TreeNode struct {
	ID        string   `json:"id"`
	Kind      string   `json:"kind"`
	Name      string   `json:"name"`
	Namespace string   `json:"namespace"`
	Health    string   `json:"health"`
	Sync      string   `json:"sync"`
	Parents   []string `json:"parents"`
}

func resourceID(kind, namespace, name string) string { return kind + "/" + namespace + "/" + name }
func (s *server) appTree(w http.ResponseWriter, r *http.Request, ctx context.Context) {
	name := strings.TrimPrefix(r.URL.Path, "/v1/apps/")
	if name == "" || strings.Contains(name, "/") {
		w.WriteHeader(404)
		return
	}
	var app struct {
		Metadata struct{ Name, Namespace string }
		Status   struct {
			Resources []struct {
				Kind, Name, Namespace, Status string
				Health                        struct{ Status string }
			}
		}
	}
	if s.get(ctx, s.kube, s.kubeURL+"/apis/argoproj.io/v1alpha1/namespaces/argocd/applications/"+url.PathEscape(name), &app, true) != nil {
		write(w, 502, map[string]string{"error": "Application unavailable"})
		return
	}
	root := resourceID("Application", app.Metadata.Namespace, name)
	nodes := []TreeNode{{ID: root, Kind: "Application", Name: name, Namespace: app.Metadata.Namespace, Parents: []string{}}}
	known := map[string]bool{root: true}
	for _, resource := range app.Status.Resources {
		id := resourceID(resource.Kind, resource.Namespace, resource.Name)
		if known[id] {
			continue
		}
		known[id] = true
		nodes = append(nodes, TreeNode{id, resource.Kind, resource.Name, resource.Namespace, resource.Health.Status, resource.Status, []string{root}})
	}
	workloads, partial := s.workloadNodes(ctx)
	descendants(known, workloads)
	indices := map[string]int{}
	for i, node := range nodes {
		indices[node.ID] = i
	}
	for _, node := range workloads {
		if !known[node.ID] {
			continue
		}
		parents := []string{}
		for _, parent := range node.Parents {
			if known[parent] {
				parents = append(parents, parent)
			}
		}
		if len(parents) == 0 {
			continue
		}
		node.Parents = parents
		if index, ok := indices[node.ID]; ok {
			nodes[index].Parents = parents
		} else {
			indices[node.ID] = len(nodes)
			nodes = append(nodes, node)
		}
	}
	var pods struct{ Items []pod }
	if s.get(ctx, s.kube, s.kubeURL+"/api/v1/pods", &pods, true) != nil {
		partial = true
	}
	for _, p := range pods.Items {
		parents := []string{}
		for _, owner := range p.Metadata.OwnerReferences {
			id := resourceID(owner.Kind, p.Metadata.Namespace, owner.Name)
			if known[id] {
				parents = append(parents, id)
			}
		}
		if len(parents) == 0 {
			continue
		}
		id := resourceID("Pod", p.Metadata.Namespace, p.Metadata.Name)
		health := p.Status.Phase
		if p.ready() {
			health = "Healthy"
		}
		if !known[id] {
			known[id] = true
			nodes = append(nodes, TreeNode{id, "Pod", p.Metadata.Name, p.Metadata.Namespace, health, "", parents})
		}
	}
	write(w, 200, map[string]any{"nodes": nodes, "partial": partial})
}
