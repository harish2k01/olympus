package main

import (
	"math"
	"regexp"
	"strconv"
)

var quantityPattern = regexp.MustCompile(`^([+-]?[0-9.]+(?:[eE][+-]?[0-9]+)?)([a-zA-Z]*)$`)

func quantity(value string) float64 {
	parts := quantityPattern.FindStringSubmatch(value)
	if len(parts) != 3 {
		return 0
	}
	n, err := strconv.ParseFloat(parts[1], 64)
	if err != nil {
		return 0
	}
	scales := map[string]float64{"": 1, "n": 1e-9, "u": 1e-6, "m": 1e-3, "k": 1e3, "K": 1e3, "M": 1e6, "G": 1e9, "T": 1e12, "P": 1e15, "E": 1e18, "Ki": 1024, "Mi": 1048576, "Gi": 1073741824, "Ti": 1099511627776, "Pi": 1125899906842624, "Ei": 1152921504606846976}
	return n * scales[parts[2]]
}

type allocation struct {
	Resource       string   `json:"resource"`
	Capacity       string   `json:"capacity"`
	Allocatable    string   `json:"allocatable"`
	Requests       float64  `json:"requests"`
	Limits         float64  `json:"limits"`
	RequestPercent *float64 `json:"requestPercent"`
	LimitPercent   *float64 `json:"limitPercent"`
}

func addResources(to map[string]float64, from map[string]string) {
	for key, value := range from {
		to[key] += quantity(value)
	}
}
func podAllocation(p pod, limits bool) map[string]float64 {
	sum, sidecars, peak := map[string]float64{}, map[string]float64{}, map[string]float64{}
	for _, c := range p.Spec.Containers {
		if limits {
			addResources(sum, c.Resources.Limits)
		} else {
			addResources(sum, c.Resources.Requests)
		}
	}
	for _, c := range p.Spec.InitContainers {
		values := c.Resources.Requests
		if limits {
			values = c.Resources.Limits
		}
		if c.RestartPolicy == "Always" {
			addResources(sidecars, values)
			for key, value := range sidecars {
				peak[key] = math.Max(peak[key], value)
			}
		} else {
			for key, value := range values {
				peak[key] = math.Max(peak[key], quantity(value)+sidecars[key])
			}
		}
	}
	for key, value := range sidecars {
		sum[key] += value
	}
	for key, value := range peak {
		sum[key] = math.Max(sum[key], value)
	}
	for key, value := range p.Spec.Overhead {
		if !limits || sum[key] > 0 {
			sum[key] += quantity(value)
		}
	}
	return sum
}
