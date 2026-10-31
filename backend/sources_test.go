package main
import("net/http/httptest";"encoding/json";"testing";"fmt")
func TestRetainedSourcesAndFilteredHistory(t *testing.T){
 s:=&server{token:"token"};for i:=0;i<150;i++ {s.events=append(s.events,Event{ID:fmt.Sprint(i),Source:"Kubernetes",Time:"2026-11-01T10:00:00Z"})};s.events=append(s.events,Event{ID:"robusta",Source:"Robusta",Time:"2026-10-25T10:00:00Z",Received:"2026-10-25T10:00:01Z"})
 r:=httptest.NewRequest("GET","/v1/events/history?source=Robusta",nil);r.Header.Set("Authorization","Bearer token");w:=httptest.NewRecorder();s.api(w,r);var result struct{Events []Event;Total int};if json.Unmarshal(w.Body.Bytes(),&result)!=nil||result.Total!=1||len(result.Events)!=1||result.Events[0].Source!="Robusta"{t.Fatal("older source hidden behind unrelated history")}
 sources:=retainedSources(s.events);if len(sources)!=2||sources[1].LastReceived!="2026-10-25T10:00:01Z"{t.Fatal("incomplete source index")}
}
