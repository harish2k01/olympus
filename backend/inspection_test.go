package main
import("net/http";"net/http/httptest";"strings";"testing")
func TestInspectionAuthAndIdentity(t *testing.T){
 s:=&server{token:"token"};w:=httptest.NewRecorder();s.api(w,httptest.NewRequest("GET","/v1/workload?namespace=media&pod=test",nil));if w.Code!=401{t.Fatal(w.Code)}
 for _,query:=range []string{"namespace=../&pod=test","namespace=media&pod=a%2fb","namespace=media&pod=test&container=x&previous=anything"}{r:=httptest.NewRequest("GET","/v1/workload/logs?"+query,nil);r.Header.Set("Authorization","Bearer token");w=httptest.NewRecorder();s.api(w,r);if w.Code!=400{t.Fatal(w.Code)}}
}
func TestInspectionBoundedLogsAndNoEnvironment(t *testing.T){
 upstream:=httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){if strings.HasSuffix(r.URL.Path,"/log"){q:=r.URL.Query();if q.Get("tailLines")!="200"||q.Get("limitBytes")!="65536"||q.Get("sinceSeconds")!="3600"||q.Get("container")!="main"||q.Get("previous")!="false"{t.Error("unbounded logs")};w.Write([]byte(strings.Repeat("x",70000)));return};w.Write([]byte(`{"metadata":{"name":"test","namespace":"media"},"spec":{"containers":[{"name":"main","image":"app:1","env":[{"name":"PASSWORD","value":"never-return-this"}]}]},"status":{"phase":"Running"}}`))}));defer upstream.Close()
 s:=&server{token:"token",kube:upstream.Client(),kubeURL:upstream.URL}
 for _,path:=range []string{"/v1/workload?namespace=media&pod=test","/v1/workload/logs?namespace=media&pod=test&container=main","/v1/workload/logs?namespace=media&pod=test&container=other"}{r:=httptest.NewRequest("GET",path,nil);r.Header.Set("Authorization","Bearer token");w:=httptest.NewRecorder();s.api(w,r);if strings.Contains(path,"other"){if w.Code!=404{t.Fatal(w.Code)}}else if w.Code!=200||strings.Contains(w.Body.String(),"never-return-this"){t.Fatal("invalid inspection response")};if strings.Contains(path,"container=main")&&!strings.Contains(w.Body.String(),`"truncated":true`){t.Fatal("missing truncation")}}
}
