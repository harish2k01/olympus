package main

import (
	"context"
	"sync"
)

// Follow Kubernetes controller references, including operator-created workloads.
func (s *server) workloadNodes(ctx context.Context) ([]TreeNode, bool) {
	endpoints := map[string]string{"Deployment": "/apis/apps/v1/deployments", "StatefulSet": "/apis/apps/v1/statefulsets", "DaemonSet": "/apis/apps/v1/daemonsets", "ReplicaSet": "/apis/apps/v1/replicasets", "Job": "/apis/batch/v1/jobs", "CronJob": "/apis/batch/v1/cronjobs"}
	var lock sync.Mutex
	var wait sync.WaitGroup
	nodes := []TreeNode{}
	partial := false
	for kind, path := range endpoints {
		wait.Add(1)
		go func(kind, path string) {
			defer wait.Done()
			var list struct {
				Items []struct {
					Metadata struct {
						Name, Namespace string
						OwnerReferences []struct{ Kind, Name string }
					}
				}
			}
			err := s.get(ctx, s.kube, s.kubeURL+path, &list, true)
			lock.Lock()
			defer lock.Unlock()
			if err != nil {
				partial = true
				return
			}
			for _, item := range list.Items {
				parents := []string{}
				for _, owner := range item.Metadata.OwnerReferences {
					parents = append(parents, resourceID(owner.Kind, item.Metadata.Namespace, owner.Name))
				}
				nodes = append(nodes, TreeNode{ID: resourceID(kind, item.Metadata.Namespace, item.Metadata.Name), Kind: kind, Name: item.Metadata.Name, Namespace: item.Metadata.Namespace, Parents: parents})
			}
		}(kind, path)
	}
	wait.Wait()
	return nodes, partial
}
func descendants(known map[string]bool, nodes []TreeNode) {
	for pass := 0; pass < 10; pass++ {
		changed := false
		for _, node := range nodes {
			if known[node.ID] {
				continue
			}
			for _, parent := range node.Parents {
				if known[parent] {
					known[node.ID] = true
					changed = true
					break
				}
			}
		}
		if !changed {
			return
		}
	}
}
