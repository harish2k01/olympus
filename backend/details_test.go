package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestAppUsageFollowsOwnedDeploymentNotNamespace(t *testing.T) {
	h := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/pods":
			w.Write([]byte(`{"items":[{"metadata":{"name":"sonarr-pod","namespace":"media","ownerReferences":[{"kind":"ReplicaSet","name":"sonarr-rs"}]},"status":{"phase":"Running","containerStatuses":[{"ready":true}]}},{"metadata":{"name":"other-pod","namespace":"media","ownerReferences":[{"kind":"ReplicaSet","name":"other-rs"}]},"status":{"phase":"Running"}}]}`))
		case "/apis/apps/v1/replicasets":
			w.Write([]byte(`{"items":[{"metadata":{"name":"sonarr-rs","namespace":"media","ownerReferences":[{"kind":"Deployment","name":"sonarr"}]}}]}`))
		case "/api/v1/query":
			if strings.Contains(r.URL.Query().Get("query"), "cpu") {
				w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"namespace":"media","pod":"sonarr-pod"},"value":[1,"0.25"]},{"metric":{"namespace":"media","pod":"other-pod"},"value":[1,"99"]}]}}`))
			} else {
				w.Write([]byte(`{"status":"success","data":{"result":[{"metric":{"namespace":"media","pod":"sonarr-pod"},"value":[1,"128"]}]}}`))
			}
		default:
			w.Write([]byte(`{"items":[]}`))
		}
	}))
	defer h.Close()
	s := &server{client: h.Client(), kube: h.Client(), prom: h.URL, kubeURL: h.URL}
	snapshot := Snapshot{Apps: []App{{Name: "sonarr", Namespace: "media", Resources: []Resource{{Kind: "Deployment", Name: "sonarr", Namespace: "media"}}}}}
	s.enrichApps(context.Background(), &snapshot)
	a := snapshot.Apps[0]
	if a.Pods != 1 || a.Ready != 1 || a.CPU == nil || *a.CPU != .25 || a.Memory == nil || *a.Memory != 128 {
		t.Fatalf("incorrect ownership aggregation: %+v", a)
	}
}
func TestChartMetadataIconsLoaded(t *testing.T) {
	if !strings.Contains(iconName("sonarranime"), "sonarr.png") || !strings.Contains(iconName("bentopdf"), "bentopdf.png") {
		t.Fatal("chart icon mapping unavailable")
	}
}
