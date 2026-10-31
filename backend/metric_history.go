package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"time"
)

type historyPoint struct {
	Time int64 `json:"time"`
	Value *float64 `json:"value"`
}
type historySeries struct {
	Name string `json:"name"`
	Unit string `json:"unit"`
	Points []historyPoint `json:"points"`
}
type metricHistory struct {
	Start int64 `json:"start"`
	End int64 `json:"end"`
	Step int64 `json:"step"`
	Series []historySeries `json:"series"`
}
type metricCacheEntry struct { Value metricHistory; At time.Time }
type historyQuery struct { Name, Unit, Expression string }

var historyIdentity = regexp.MustCompile(`^[a-zA-Z0-9_.-]{1,253}$`)

// The phone selects a supported measurement, never arbitrary PromQL or a URL.
func historyQueries(v url.Values) ([]historyQuery, error) {
	valid := func(key string) bool { return historyIdentity.MatchString(v.Get(key)) }
	switch v.Get("kind") {
	case "monitor":
		if !valid("id") { return nil, fmt.Errorf("invalid monitor") }
		selector := `{monitor_id=`+strconv.Quote(v.Get("id"))+`}`
		return []historyQuery{{"Response time", "ms", `max(monitor_response_time`+selector+`)`}, {"Availability state", "state", `max(monitor_status`+selector+`)`}}, nil
	case "throttling":
		if !valid("namespace") || !valid("pod") || !valid("container") { return nil, fmt.Errorf("invalid resource") }
		selector := `{namespace=`+strconv.Quote(v.Get("namespace"))+`,pod=`+strconv.Quote(v.Get("pod"))+`,container=`+strconv.Quote(v.Get("container"))+`}`
		return []historyQuery{{"Throttled periods", "%", `100*sum(rate(container_cpu_cfs_throttled_periods_total`+selector+`[5m]))/sum(rate(container_cpu_cfs_periods_total`+selector+`[5m]))`}, {"CPU usage", "cores", `sum(rate(container_cpu_usage_seconds_total`+selector+`[5m]))`}}, nil
	default:
		return nil, fmt.Errorf("unsupported measurement")
	}
}

func historyWindow(value string) (time.Duration, int64, bool) {
	switch value {
	case "", "1h": return time.Hour, 30, true
	case "6h": return 6*time.Hour, 60, true
	case "24h": return 24*time.Hour, 300, true
	default: return 0, 0, false
	}
}

func (s *server) rangeHistory(ctx context.Context, q historyQuery, start, end, step int64) (historySeries, error) {
	out := historySeries{Name:q.Name, Unit:q.Unit, Points:[]historyPoint{}}
	var response struct {
		Status string
		Data struct { Result []struct { Values [][]json.RawMessage } }
	}
	params := url.Values{"query":{q.Expression}, "start":{strconv.FormatInt(start,10)}, "end":{strconv.FormatInt(end,10)}, "step":{strconv.FormatInt(step,10)}, "limit":{"1"}, "timeout":{"6s"}}
	if err:=s.get(ctx,s.client,s.prom+"/api/v1/query_range?"+params.Encode(),&response,false); err!=nil { return out,err }
	if response.Status!="success" { return out,fmt.Errorf("upstream query failed") }
	if len(response.Data.Result)==0 { return out,nil }
	for _, pair:=range response.Data.Result[0].Values {
		if len(pair)!=2 { continue }
		var at float64; var raw string
		if json.Unmarshal(pair[0],&at)!=nil || json.Unmarshal(pair[1],&raw)!=nil || math.IsNaN(at) || math.IsInf(at,0) { continue }
		if at<float64(start) || at>float64(end) { continue }
		point:=historyPoint{Time:int64(at)}
		if number,err:=strconv.ParseFloat(raw,64); err==nil && !math.IsNaN(number) && !math.IsInf(number,0) { point.Value=&number }
		out.Points=append(out.Points,point)
		if len(out.Points)>=400 { break }
	}
	return out,nil
}

func (s *server) metricHistory(w http.ResponseWriter,r *http.Request,ctx context.Context) {
	queries,err:=historyQueries(r.URL.Query())
	duration,step,ok:=historyWindow(r.URL.Query().Get("range"))
	if err!=nil || !ok { write(w,400,map[string]string{"error":"Unsupported measurement, identity or time range"}); return }
	key:=r.URL.Query().Encode()
	if len(key)>1200 { write(w,400,map[string]string{"error":"Request too long"}); return }
	now:=time.Now()
	s.mu.Lock()
	entry,hit:=s.metricCache[key]
	s.mu.Unlock()
	if hit && now.Sub(entry.At)<30*time.Second { write(w,200,entry.Value); return }
	end:=now.Unix()/step*step
	out:=metricHistory{Start:end-int64(duration.Seconds()),End:end,Step:step,Series:[]historySeries{}}
	for _,query:=range queries {
		series,err:=s.rangeHistory(ctx,query,out.Start,out.End,step)
		if err!=nil {
			requestID,_:=ctx.Value(requestIDKey{}).(string)
			slog.Warn("metric_history_failed","request_id",requestID,"measurement",r.URL.Query().Get("kind"))
			write(w,502,map[string]string{"error":"Metric history unavailable"}); return
		}
		out.Series=append(out.Series,series)
	}
	s.mu.Lock()
	if s.metricCache==nil { s.metricCache=map[string]metricCacheEntry{} }
	if len(s.metricCache)>=64 {
		oldestKey:=""; oldest:=now
		for key,entry:=range s.metricCache { if entry.At.Before(oldest) { oldest=entry.At;oldestKey=key } }
		delete(s.metricCache,oldestKey)
	}
	s.metricCache[key]=metricCacheEntry{out,now}
	s.mu.Unlock()
	write(w,200,out)
}
