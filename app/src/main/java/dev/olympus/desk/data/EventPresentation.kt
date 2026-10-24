package dev.olympus.desk.data

internal fun isInformational(severity:String)=severity.lowercase() in listOf("info","informational","debug","notice")
internal fun needsAttention(e:LabEvent)=e.state=="Unknown" || (e.state in listOf("Firing","Pending")&&!isInformational(e.kind))
internal fun mergeAttention(legacy:List<AttentionItem>,active:List<LabEvent>):List<AttentionItem>{
 val relevant=active.filter(::needsAttention)
 val covered=relevant.mapNotNull{e->when{e.id.startsWith("node:")->"node:"+e.id.removePrefix("node:").substringBefore(":");e.id.startsWith("app:")->"app:"+e.id.removePrefix("app:");e.id.startsWith("monitor:")->"app:uptime-kuma";else->null}}.toSet()
 return (relevant.map{AttentionItem(it.title,"event:"+it.id,if(it.state=="Unknown")"Unconfirmed"else it.state+" · "+it.kind)}+legacy.filter{it.target !in covered}).distinctBy{it.title}
}
