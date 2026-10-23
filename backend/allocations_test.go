package main

import (
	"encoding/json"
	"math"
	"testing"
)

func TestQuantityUnits(t *testing.T) {
	for value, want := range map[string]float64{"250m": .25, "1Gi": 1073741824, "2e3": 2000, "128Mi": 134217728, "0": 0} {
		if math.Abs(quantity(value)-want) > .00001 {
			t.Fatalf("%s: %v", value, quantity(value))
		}
	}
}
func TestAllocationIncludesInitPeakSidecarsAndOverhead(t *testing.T) {
	var p pod
	if json.Unmarshal([]byte(`{"spec":{"containers":[{"resources":{"requests":{"cpu":"250m","memory":"128Mi"}}}],"initContainers":[{"resources":{"requests":{"cpu":"1","memory":"256Mi"}}},{"restartPolicy":"Always","resources":{"requests":{"cpu":"100m","memory":"32Mi"}}}],"overhead":{"cpu":"50m"}}}`), &p) != nil {
		t.Fatal("fixture")
	}
	r := podAllocation(p, false)
	if math.Abs(r["cpu"]-1.05) > .00001 || r["memory"] != 268435456 {
		t.Fatalf("incorrect init peak: %v", r)
	}
}
func TestDescendantsFollowOperatorControllers(t *testing.T) {
	known := map[string]bool{"Prometheus/monitoring/main": true}
	nodes := []TreeNode{{ID: "ReplicaSet/monitoring/child-rs", Parents: []string{"Deployment/monitoring/child"}}, {ID: "Deployment/monitoring/child", Parents: []string{"Prometheus/monitoring/main"}}}
	descendants(known, nodes)
	if !known["ReplicaSet/monitoring/child-rs"] {
		t.Fatal("operator descendant missing")
	}
}
