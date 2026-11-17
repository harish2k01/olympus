package dev.olympus.desk.data

internal fun isInformational(severity:String)=severity.lowercase() in listOf("info","informational","debug","notice")
internal fun needsAttention(e:LabEvent)=e.state=="Unknown" || (e.state in listOf("Firing","Pending")&&!isInformational(e.kind))

/** Current-state wording is separate from the original payload kept in technical details. */
internal fun incidentTitle(event:LabEvent):String {
 if(event.state=="Resolved"&&event.source=="Argo CD")return event.title.substringBefore(" · ").replace("requires attention","",ignoreCase=true).trim().let{if(it.endsWith("recovered",true))it else "$it recovered"}
 val title=when(event.title){"CPUThrottlingHigh"->"CPU throttling";"NodeDiskIOSaturation"->"Node disk I/O saturation";"NodeSystemSaturation"->"Node system saturation";else->event.title}
 return if(event.state=="Resolved")title.replace("requires attention","recovered",ignoreCase=true) else title
}
