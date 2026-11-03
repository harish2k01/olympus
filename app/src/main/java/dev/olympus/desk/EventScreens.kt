package dev.olympus.desk

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import kotlinx.coroutines.*

private val EventPaper: Color @Composable get() = DeskColors.text
private val EventMuted: Color @Composable get() = DeskColors.muted
private val EventWarm: Color @Composable get() = DeskColors.apricot
private fun sourceApp(source:String)=LiveRepository.services.find{it.name.replace("-","").equals(source.replace(" ","").replace("-",""),true)}
private fun eventTag(event: LabEvent): String = when (event.state) {
 "Firing" -> "FIRING"
 "Pending" -> "PENDING"
 "Resolved", "Recovery" -> "RESOLVED"
 "Unknown" -> "UNCONFIRMED"
 else -> if (event.source == "Kubernetes") "EVENT" else "NOTICE"
}

private fun groupingKey(e: LabEvent) =
 listOf(e.source, e.title, e.detail, e.kind, eventTag(e), e.namespace, e.resource, e.pod, e.container)

@Composable internal fun NotificationInbox(initialSource:String="All",initialSection:String="All",recentOnly:Boolean=false,deskPane:Boolean=false,open:(LabEvent)->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var source by rememberSaveable(initialSource){mutableStateOf(initialSource)}
 var historyJob by remember{mutableStateOf<Job?>(null)}
 LaunchedEffect(Unit){Pairing.read(context)?.let{LiveRepository.loadDeliveries(it)}}

 var older by remember{mutableStateOf(emptyList<LabEvent>())};var next by remember{mutableIntStateOf(0)};var total by remember{mutableIntStateOf(-1)};var loading by remember{mutableStateOf(false)};var historyError by remember{mutableStateOf(false)}
 fun more(){historyJob?.cancel();val requestSource=source;historyJob=scope.launch{loading=true;historyError=false;try{val pair=Pairing.read(context)?:error("Pairing required");val page=withContext(Dispatchers.IO){JSONObject(LiveRepository.request(pair,"/v1/events/history?offset=$next&source="+java.net.URLEncoder.encode(requestSource,"UTF-8")))};if(requestSource!=source)return@launch;val list=page.getJSONArray("events");older=(older+(0 until list.length()).map{val e=list.getJSONObject(it);LiveRepository.event(e)}).distinctBy{it.id};next=page.getInt("next");total=page.getInt("total")}catch(e:CancellationException){throw e}catch(_:Exception){historyError=true}finally{loading=false}}}
 var query by rememberSaveable{mutableStateOf("")};var period by rememberSaveable(initialSection){mutableStateOf(initialSection)}
 var kindFilter by rememberSaveable { mutableStateOf("All") }
 val showTests=LocalShowTests.current
 LaunchedEffect(period){kindFilter="All"}
 LaunchedEffect(source,period){historyJob?.cancel();older=emptyList();next=0;total=-1;more()}


 if (period == "History" && recentOnly) Text("Last 24 hours", color = EventMuted, fontSize = 11.sp)
 val all = (LiveRepository.active + LiveRepository.events + older).distinctBy { it.id }
 val filters = listOf("All", "Firing", "Pending", "Resolved", "Notices", "Events")
 val activeIds = LiveRepository.active.map { it.id }.toSet()
 val scopedEvents = all.filter { event ->
  val inWindow = !recentOnly || runCatching {
   java.time.Instant.parse(event.resolvedAt.ifBlank { event.time }).isAfter(java.time.Instant.now().minusSeconds(86400))
  }.getOrDefault(false)
  inWindow && (period != "History" || event.id !in activeIds) &&
   (query.isBlank() || event.title.contains(query, true) || event.detail.contains(query, true) || event.source.contains(query, true)) &&
   (source == "All" || event.source == source) && (showTests || !(event.kind == "Test" || event.source == "Test" || event.title == "Test"))
 }
 val events = scopedEvents.filter { event ->
  val tag = eventTag(event)
  val matchesKind = when (kindFilter) {
   "Notices" -> tag == "NOTICE"
   "Events" -> tag == "EVENT"
   "All" -> true
   else -> tag.equals(kindFilter, ignoreCase = true)
  }
  matchesKind
 }
 if (!deskPane) InboxFilters(filters, kindFilter, events = scopedEvents, onSelect = { kindFilter = it })
 if(events.isEmpty())DeskEmpty(if(query.isNotBlank()||source!="All"||kindFilter!="All")"No matching records"else if(period=="Active")"No active incidents"else"No activity received",when{query.isNotBlank()||source!="All"->"Try another source or search.";!LiveRepository.state.startsWith("Live")->"Connection unavailable · showing last known state.";LiveRepository.signals.values.any{!it}->"Some sources are unavailable. Check source delivery below.";period=="Active"->"Connected sources report no active incidents.";else->"Received notifications will appear here."})
 if (events.isNotEmpty()) {
  val groups = events.groupBy(::groupingKey).values
   .sortedByDescending { it.first().resolvedAt.ifBlank { it.first().time } }
  val groupModifier = if (deskPane) Modifier.fillMaxWidth() else Modifier.fillMaxWidth()
   .clip(RoundedCornerShape(24.dp)).background(DeskColors.surface).padding(horizontal = 14.dp, vertical = 7.dp)
  Column(groupModifier) {
   groups.forEachIndexed { index, group ->
    if (index > 0) HorizontalDivider(color = DeskColors.raised)
    NotificationRow(group.first(), group.size, deskPane) { open(group.first()) }
   }
  }
  if (!deskPane && events.any { eventTag(it) == "EVENT" }) Text(
   "Kubernetes event counts describe recorded observations.", color = EventMuted, fontSize = 12.sp
  )
 }
 DeskDisclosure("Search and sources") {
  DeskSearch(query, { query = it }, "Search events")
  DeskSegments(listOf("All") + (all.map { it.source } + LiveRepository.deliveries.map { it.name })
   .filter { showTests || it != "Test" }.distinct().sorted(), source, { source = it })
 }
 if(total<0||next<total||historyError)DeskAction(if(loading)"Loading…"else if(historyError)"Retry history"else"Load history",enabled=!loading){more()}
 DeskDisclosure("Source delivery"){
  LiveRepository.signals.forEach{(name,available)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(name,color=EventPaper,fontSize=12.sp);Text(if(available)"Collection available"else"Collection unavailable",color=if(available)DeskColors.green else EventWarm,fontSize=11.sp)}}
  LiveRepository.deliveries.forEach{delivery->Row(Modifier.fillMaxWidth().padding(vertical=6.dp).clickable{source=delivery.name;period="History"},horizontalArrangement=Arrangement.SpaceBetween){Text("${delivery.name} · ${delivery.count}",color=EventPaper,fontSize=12.sp);Text("Last received · ${localTime(delivery.lastReceived)}",color=EventMuted,fontSize=11.sp)}}
  if(LiveRepository.deliveryError){Text("Source history unavailable",color=EventWarm,fontSize=12.sp);DeskAction("Retry source history"){scope.launch{Pairing.read(context)?.let{LiveRepository.loadDeliveries(it,true)}}}}

  Text("Collection health and webhook delivery are measured separately.",color=EventMuted,fontSize=11.sp)
 }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun InboxFilters(filters: List<String>, selected: String, events: List<LabEvent>, onSelect: (String) -> Unit) {
 FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
  filters.forEach { filter ->
   val count = if (filter == "Firing" || filter == "Pending") events.count { it.state == filter } else 0
   val active = selected == filter
   Text(
    if (count > 0) "$filter $count" else filter,
    modifier = Modifier.clip(CircleShape)
     .background(if (active) MaterialTheme.colorScheme.secondaryContainer else DeskColors.raised)
     .clickable { onSelect(filter) }.padding(horizontal = 12.dp, vertical = 9.dp),
    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else EventPaper,
    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
   )
  }
 }
}

@Composable private fun NotificationRow(event: LabEvent, count: Int, deskPane: Boolean, click: () -> Unit) {
 val tag = eventTag(event)
 val tint = when (tag) {
  "FIRING" -> if (event.kind.equals("critical", true)) DeskColors.critical else EventWarm
  "NOTICE" -> DeskColors.blue
  else -> EventMuted
 }
 val target = listOf(event.namespace, event.pod.ifBlank { event.resource })
  .filter(String::isNotBlank).distinct().joinToString("/").ifBlank { event.node }
 val subtitle = listOf(if (deskPane && target.isNotBlank()) "" else event.source, target, if (count > 1) "$count records" else "")
  .filter(String::isNotBlank).joinToString(" · ")
 Row(
  Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = click).padding(vertical = 10.dp),
  verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
 ) {
  Column(Modifier.weight(1f)) {
   Text(incidentTitle(event), color = EventPaper, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
   Text(subtitle, color = EventMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
  }
  Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
   val shape = RoundedCornerShape(6.dp)
   Text(tag, Modifier.border(1.dp, tint, shape).padding(horizontal = 7.dp, vertical = 3.dp),
    color = tint, fontSize = 10.sp, letterSpacing = .4.sp, fontWeight = FontWeight.Bold)
   Text(inboxTime(event.resolvedAt.ifBlank { event.received.ifBlank { event.time } }), color = EventMuted, fontSize = 12.sp)
  }
 }
}

@Composable internal fun NotificationDetail(event:LabEvent,open:(String)->Unit={}){
 val context=LocalContext.current
 val data=try{JSONObject(event.detail)}catch(_:Exception){null}
 val app=sourceApp(event.source)
 val resolved=event.state=="Resolved"
 val firing = event.state == "Firing"
 val severity = if (event.kind.equals("critical", true)) Verdict.Critical else Verdict.Attention
 val heroInk = if (firing) DeskColors.onAttention else EventPaper
 Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(24.dp))
  .background(if (firing) DeskColors.attentionSurface else DeskColors.surface).padding(16.dp),
  verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
   Row(Modifier.clip(CircleShape).background(DeskColors.raised).padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    VerdictMark(if (firing) severity else if (resolved) Verdict.Calm else Verdict.Unknown)
    Text(event.state.ifBlank { "Observed" }, color = EventPaper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
   }
   if (event.kind.isNotBlank()) Text(
    event.kind.replaceFirstChar { it.titlecase() },
    Modifier.clip(CircleShape).background(DeskColors.raised).padding(horizontal = 12.dp, vertical = 8.dp),
    color = EventPaper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
   )
  }
  Text(incidentTitle(event), color = heroInk, fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
  val description = data?.optString("message").orEmpty().ifBlank { data?.optString("description").orEmpty() }
  if (description.isNotBlank() && description != event.title) Text(description, color = heroInk, fontSize = 14.sp)
 }
 val resource = listOf(event.namespace, event.pod).filter(String::isNotBlank).joinToString(" / ").ifBlank { event.resource }
 if (resource.isNotBlank() || event.node.isNotBlank()) Row(
  Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
 ) {
  if (resource.isNotBlank()) Box(Modifier.weight(1f)) {
   CompanionPanel("Workload") { Text(resource, color = EventPaper, fontWeight = FontWeight.Bold) }
  }
  if (event.node.isNotBlank()) Box(Modifier.weight(1f)) {
   CompanionPanel("Node") { Text(event.node, color = EventPaper, fontWeight = FontWeight.Bold) }
  }
 }

 @Composable fun measurements(){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DeskColors.surface).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
  Text(listOf(event.namespace,event.pod,event.container).filter{it.isNotBlank()}.joinToString(" / ").ifBlank{event.resource.ifBlank{incidentTitle(event)}},color=EventPaper,fontSize=13.sp)
  val message=data?.optString("message").orEmpty().ifBlank{data?.optString("description").orEmpty()}.ifBlank{data?.optString("msg").orEmpty()}
  if(message.isNotBlank()&&message!=event.title)Text(message,color=EventMuted,fontSize=12.sp)
  listOf("movie","series","episode","release","monitor","heartbeat").forEach{key->data?.optJSONObject(key)?.let{item->val summary=item.keys().asSequence().mapNotNull{field->item.opt(field)?.takeIf{it is String||it is Number||it is Boolean}?.let{"$field · $it"}}.take(6).joinToString(" · ");if(summary.isNotBlank())Text(summary,color=EventMuted,fontSize=12.sp)}}
  event.throttledPeriodsPercent?.let{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text("${String.format(java.util.Locale.US,"%.1f%%",it)}",color=EventPaper,fontSize=26.sp);Text("Last reported throttled periods",color=EventMuted,fontSize=11.sp)}}
  if(event.cpuLimit.isNotBlank())Text("CPU limit · ${event.cpuLimit}",color=EventMuted,fontSize=12.sp)
  if(resolved)Text("Resolved · ${localTime(event.resolvedAt.ifBlank{event.lastSeen})}",color=DeskColors.green,fontSize=12.sp)
  else if(event.evidence.isNotBlank())Text(event.evidence,color=EventMuted,fontSize=12.sp)
 }}
 val hasContainer = event.namespace.isNotBlank() && event.pod.isNotBlank() && event.container.isNotBlank()
 val historyKind = when {
  event.title.contains("throttl", true) -> "throttling"
  event.title.contains("OOMKilled", true) || event.labels["reason"] == "OOMKilled" -> "container-memory"
  else -> null
 }
 if (hasContainer && historyKind != null) {
  fun encoded(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
  MetricHistoryPanel("kind=$historyKind&namespace=${encoded(event.namespace)}&pod=${encoded(event.pod)}&container=${encoded(event.container)}")
 }
 if (event.nextStep.isNotBlank()) CompanionPanel("Next step") {
  Text(event.nextStep, color = EventPaper, fontSize = 14.sp)
 }
 CompanionPanel("Timeline") {
  val moments = listOf(
   event.state.ifBlank { "Observed" } to event.time,
   "Received" to event.received,
   "Last confirmed" to event.lastSeen,
   "Resolved" to event.resolvedAt
  ).filter { it.second.isNotBlank() }.distinctBy { it.second }
  moments.forEach { (label, time) ->
   Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    VerdictMark(if (label == "Firing") severity else if (label == "Resolved") Verdict.Calm else Verdict.Unknown)
    Text(label, Modifier.weight(1f), color = EventPaper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Text(inboxTime(time), color = EventMuted, fontSize = 12.sp)
   }
  }
 }
 Row(Modifier.fillMaxWidth().padding(vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  if(event.pod.isNotBlank()&&event.namespace.isNotBlank())DeskAction("Inspect workload",true){open("pod:${event.namespace}/${event.pod}@${event.node}")}
  if(event.node.isNotBlank())DeskAction("Node · ${event.node}"){open("node:${event.node}")}
  if(app!=null)DeskAction("Application"){open("app:${app.name}")}
  if(app?.url?.startsWith("https://")==true)DeskAction("Open source"){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(app.url)))}
 }
 DashboardActions(argoApplication = app?.name)
 DeskDisclosure("Reported details") { measurements() }
 if(event.title.contains("throttl",true))DeskDisclosure("About throttling"){Text("A container can reach its CPU quota without high node CPU usage. The reported percentage counts throttled scheduling periods, not node CPU utilization.",color=EventMuted,fontSize=12.sp)}
 DeskDisclosure("Technical details"){
  DetailTable("Reference",listOf("Field","Value"),listOf(180,560),listOf(listOf("Event ID",event.id),listOf("Resource",event.resource),listOf("Original title",event.title),listOf("Original message",cleanEventMessage(event.detail)))+event.labels.map{listOf(it.key,it.value)})
 }
 val related=LiveRepository.events.filter{it.id!=event.id&&it.source==event.source}.take(8)
 if(related.isNotEmpty())DeskDisclosure("Related activity · ${related.size}"){related.forEach{e->Text("${localTime(e.time)} · ${incidentTitle(e)}",color=EventMuted,fontSize=12.sp,modifier=Modifier.padding(vertical=8.dp))}}
}

@Composable private fun DeskIncidentDetail(event: LabEvent, open: (String) -> Unit) {
 val context = LocalContext.current
 val data = remember(event.detail) { runCatching { JSONObject(event.detail) }.getOrNull() }
 val message = data?.optString("message").orEmpty().ifBlank { data?.optString("description").orEmpty() }
 val workload = listOf(event.namespace, event.pod).filter(String::isNotBlank).joinToString("/")
  .ifBlank { event.resource }
 Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
   Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    StatusPill(event.state.ifBlank { "Observed" }, if (event.state == "Firing") EventWarm else EventMuted)
    if (event.kind.isNotBlank()) StatusPill(event.kind.replaceFirstChar { it.titlecase() })
   }
   Text(incidentTitle(event), color = EventPaper, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
   if (message.isNotBlank() && message != event.title) Text(message, color = EventMuted, fontSize = 12.sp)
   val facts = listOf("Workload" to workload, "Node" to event.node, "Evidence" to event.evidence)
   facts.filter { it.second.isNotBlank() }.forEach { (label, value) ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
     Text(label, Modifier.weight(1f), color = EventMuted, fontSize = 12.sp)
     Text(value, Modifier.weight(3f), color = EventPaper, fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
   }
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   if (event.pod.isNotBlank() && event.namespace.isNotBlank()) DeskAction("Workload", true) {
    open("pod:${event.namespace}/${event.pod}@${event.node}")
   }
   if (event.node.isNotBlank()) DeskAction("Node") { open("node:${event.node}") }
   listOf("argo" to "Argo CD", "grafana" to "Grafana").forEach { (key, label) ->
    LiveRepository.dashboardLinks[key]?.let { address ->
     DeskAction(label) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(address))) } }
    }
   }
  }
 }
}

private fun inboxTime(value: String): String = runCatching {
 val zone = ZoneId.systemDefault()
 val dateTime = Instant.parse(value).atZone(zone)
 val pattern = if (dateTime.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "dd MMM · HH:mm"
 dateTime.format(DateTimeFormatter.ofPattern(pattern))
}.getOrDefault("—")

private fun cleanEventMessage(message:String)=message.replace(Regex("(?i)Response body:\\s*$"),"").trim()

@Composable internal fun EventRoute(id:String,fallback:LabEvent?,resolved:(LabEvent)->Unit={},deskPane:Boolean=false,open:(String)->Unit){
 val context=LocalContext.current
 val current=(LiveRepository.active+LiveRepository.events).find{it.id==id}?:fallback?.takeIf{it.id==id}
 var retained by remember(id){mutableStateOf<LabEvent?>(null)};var failed by remember(id){mutableStateOf(false)};var retry by remember{mutableIntStateOf(0)}
 LaunchedEffect(id,retry){if(current==null){failed=false;try{val pair=Pairing.read(context)?:error("Not paired");retained=withContext(Dispatchers.IO){LiveRepository.event(JSONObject(LiveRepository.request(pair,"/v1/events/item?id="+java.net.URLEncoder.encode(id,"UTF-8"))))}}catch(e:CancellationException){throw e}catch(_:Exception){failed=true}}}
 val event=current?:retained
 LaunchedEffect(event){event?.let(resolved)}
 if(event!=null){if(deskPane)DeskIncidentDetail(event,open)else NotificationDetail(event,open)}else if(failed){DeskEmpty("Record unavailable","It may have expired, or the backend is unreachable.");DeskAction("Retry"){retry++}}else Text("Loading incident…",color=DeskColors.muted)
}
