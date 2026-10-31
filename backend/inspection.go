package main

import (
 "context"
 "io"
 "net/http"
 "net/url"
 "regexp"
 "strings"
 "time"
)

var inspectionName = regexp.MustCompile(`^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$`)
func validInspectionName(value string, max int) bool { return len(value)>0 && len(value)<=max && inspectionName.MatchString(value) && !strings.Contains(value,"..") }

// Inspection returns selected operational fields only: never pod environment or secret volumes.
func (s *server) workloadInspection(w http.ResponseWriter,r *http.Request,parent context.Context) {
 q:=r.URL.Query(); namespace,name,container:=q.Get("namespace"),q.Get("pod"),q.Get("container")
 logs:=r.URL.Path=="/v1/workload/logs"
 if !validInspectionName(namespace,63)||!validInspectionName(name,253)||(logs&&!validInspectionName(container,63)) || (q.Get("previous")!=""&&q.Get("previous")!="true"&&q.Get("previous")!="false") {write(w,400,map[string]string{"error":"Invalid workload identity"});return}
 ctx,cancel:=context.WithTimeout(parent,6*time.Second);defer cancel()
 var pod struct {
  Metadata struct {Name,Namespace,CreationTimestamp string}
  Spec struct {NodeName string;Containers,InitContainers []struct{Name,Image string}}
  Status struct {Phase string;ContainerStatuses,InitContainerStatuses []struct{Name string;Ready bool;RestartCount int;State,LastTerminationState map[string]any}}
 }
 base:=s.kubeURL+"/api/v1/namespaces/"+url.PathEscape(namespace)+"/pods/"+url.PathEscape(name)
 if err:=s.get(ctx,s.kube,base,&pod,true);err!=nil {write(w,502,map[string]string{"error":"Workload unavailable"});return}
 found:=false
 for _,c:=range append(pod.Spec.Containers,pod.Spec.InitContainers...) {if c.Name==container {found=true}}
 if !logs {write(w,200,map[string]any{"name":pod.Metadata.Name,"namespace":pod.Metadata.Namespace,"createdAt":pod.Metadata.CreationTimestamp,"node":pod.Spec.NodeName,"phase":pod.Status.Phase,"containers":pod.Spec.Containers,"initContainers":pod.Spec.InitContainers,"statuses":pod.Status.ContainerStatuses,"initStatuses":pod.Status.InitContainerStatuses});return}
 if !found {write(w,404,map[string]string{"error":"Container not present"});return}
 params:=url.Values{"container":{container},"tailLines":{"200"},"limitBytes":{"65536"},"sinceSeconds":{"3600"},"timestamps":{"true"},"previous":{q.Get("previous")}}
 req,err:=http.NewRequestWithContext(ctx,"GET",base+"/log?"+params.Encode(),nil)
 if err!=nil {write(w,502,map[string]string{"error":"Logs unavailable"});return}
 req.Header.Set("Authorization","Bearer "+s.sa)
 response,err:=s.kube.Do(req)
 if err!=nil {write(w,502,map[string]string{"error":"Logs unavailable"});return};defer response.Body.Close()
 if response.StatusCode!=200 {write(w,502,map[string]string{"error":"No logs available for this container selection"});return}
 data,err:=io.ReadAll(io.LimitReader(response.Body,65537));if err!=nil {write(w,502,map[string]string{"error":"Logs unavailable"});return}
 truncated:=len(data)>65536;if truncated {data=data[:65536]}
 write(w,200,map[string]any{"text":string(data),"truncated":truncated,"previous":q.Get("previous")=="true","collectedAt":time.Now().UTC().Format(time.RFC3339)})
}
