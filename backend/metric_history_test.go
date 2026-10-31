package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"
)

func TestHistoryRejectsUnboundedAndInjectedQueries(t *testing.T) {
	for _, raw := range []string{"kind=anything", "kind=monitor&id=x%22%7D", "kind=throttling&namespace=media&pod=sonarr", "kind=monitor&id=1&range=365d"} {
		s := &server{}
		w := httptest.NewRecorder()
		s.metricHistory(w, httptest.NewRequest("GET", "/v1/metrics/history?"+raw, nil), context.Background())
		if w.Code != 400 { t.Fatalf("accepted unsupported request: %s", raw) }
	}
}

func TestHistoryRequiresAuthentication(t *testing.T) {
	s := &server{token:strings.Repeat("x",32)}
	w := httptest.NewRecorder()
	s.api(w,httptest.NewRequest("GET","/v1/metrics/history?kind=monitor&id=1",nil))
	if w.Code != http.StatusUnauthorized { t.Fatal("history bypasses authentication") }
}

func TestHistoryCachesSamplesAndPreservesMissingValues(t *testing.T) {
	calls := 0
	now := time.Now().Unix()/30*30
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){
		calls++
		if r.URL.Path!="/api/v1/query_range" || r.URL.Query().Get("step")!="30" || r.URL.Query().Get("limit")!="1" { t.Error("query was not bounded") }
		fmt.Fprintf(w,`{"status":"success","data":{"result":[{"values":[[%d,"42"],[%d,"NaN"]]}]}}`,now-30,now)
	}))
	defer upstream.Close()
	s := &server{client:upstream.Client(),prom:upstream.URL}
	for i:=0;i<2;i++ {
		w:=httptest.NewRecorder()
		s.metricHistory(w,httptest.NewRequest("GET","/v1/metrics/history?kind=monitor&id=1&range=1h",nil),context.Background())
		var result metricHistory
		if w.Code!=200 || json.Unmarshal(w.Body.Bytes(),&result)!=nil || len(result.Series)!=2 { t.Fatal("history response unavailable") }
		points:=result.Series[0].Points
		if len(points)!=2 || points[0].Value==nil || *points[0].Value!=42 || points[1].Value!=nil { t.Fatal("missing value became a zero or disappeared") }
	}
	if calls!=2 { t.Fatalf("expected shared cache, got %d upstream calls",calls) }
}

func TestThrottlingHistoryUsesExactWorkloadSelectors(t *testing.T) {
	q,err:=historyQueries(url.Values{"kind":{"throttling"},"namespace":{"media"},"pod":{"sonarr-abc"},"container":{"sonarr"}})
	if err!=nil || len(q)!=2 || !strings.Contains(q[0].Expression,`namespace="media",pod="sonarr-abc",container="sonarr"`) || !strings.Contains(q[0].Expression,"container_cpu_cfs_periods_total") { t.Fatal("throttling query lost resource identity or its denominator") }
}

func TestHistoryUpstreamFailureDoesNotReturnEmptySuccess(t *testing.T) {
	upstream:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){w.WriteHeader(503)}))
	defer upstream.Close()
	s:=&server{client:upstream.Client(),prom:upstream.URL}
	w:=httptest.NewRecorder()
	s.metricHistory(w,httptest.NewRequest("GET","/v1/metrics/history?kind=monitor&id=1",nil),context.Background())
	if w.Code!=502 { t.Fatal("upstream failure presented as an empty healthy history") }
}
