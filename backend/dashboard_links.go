package main

import (
	"net/url"
	"os"
)

func dashboardLinks() map[string]string {
	links := map[string]string{}
	for name, variable := range map[string]string{"argo": "ARGOCD_URL", "grafana": "GRAFANA_URL"} {
		link, err := url.Parse(os.Getenv(variable))
		if err == nil && link.Scheme == "https" && link.Hostname() != "" && link.User == nil &&
			link.RawQuery == "" && !link.ForceQuery && link.Fragment == "" {
			links[name] = link.String()
		}
	}
	return links
}
