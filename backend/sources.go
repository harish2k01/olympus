package main
import "sort"
type SourceDelivery struct { Name string `json:"name"`; Count int `json:"count"`; LastReceived string `json:"lastReceived"` }
func retainedSources(events []Event) []SourceDelivery {
 index:=map[string]SourceDelivery{}
 for _,event:=range events {entry:=index[event.Source];entry.Name=event.Source;entry.Count++;at:=event.Received;if at==""{at=event.Time};if at>entry.LastReceived{entry.LastReceived=at};index[event.Source]=entry}
 out:=[]SourceDelivery{};for _,entry:=range index{out=append(out,entry)};sort.Slice(out,func(i,j int)bool{return out[i].Name<out[j].Name});return out
}

