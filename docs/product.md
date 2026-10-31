# Product experience

Olympus is a personal Android desk console for a Kubernetes homelab. Its core workflow is **glance → investigate → act on context**, supported by a configurable home screen. Widgets are entry points into the companion, rather than the whole product.

## Destinations

- **Home:** current attention, cluster metrics, GitOps readiness, monitors and recent changes. Independent portrait and landscape canvases support pinned resources and integration fields.
- **Cluster:** nodes and applications. Node pages retain Kubernetes conditions, allocations, workloads and events. Applications expose resource relationships, usage and related activity.
- **Services:** monitor availability and response times, plus connected integration counters and app notifications.
- **Inbox:** current conditions and retained history. Pending, Firing, Resolved, Observed and Unconfirmed remain distinct; occurrence counts describe grouped records rather than active-alert totals.

## Investigation

An attention widget links to its incident. The incident shows source, state, available measurements, timestamps and affected resources. When a pod and node are identified, **Inspect workload** opens container readiness, restarts, images and current/previous logs. Node workload allocations remain under the node page. **Open node** reaches detailed node evidence. Back returns to the previous investigation route.

A monitor opens current availability, latency, Prometheus response-time and availability history over 1, 6 or 24 hours and matching received notifications. These samples do not imply a historical uptime percentage. An integration opens selected counters, collection time, app notifications and an application-resource link when that application is available.

Original incident payloads remain under Technical details. Source-provided CPU limits and throttled-period measurements are preserved. Missing values remain unavailable. The client does not fabricate thresholds, historical charts, uptime percentages or notifications.

## Customization and desk behavior

Long-press selects editing and permits dragging. Selecting a widget exposes its live preview and independent width/height controls. Horizontal and vertical handles resize on the grid. Configuration includes title and applicable source/fields. Removal can be undone. The dock is hidden during editing.

The v0.7 layouts use separate storage keys. The landscape default uses an eight-column grid with a compact status row; portrait places attention first. Older v0.6 layouts are retained and can be restored from the overflow menu with their six-column landscape geometry. Layout export/import remains supported. The dock occupies a separate region while shown; hiding it releases that space. In idle mode the first touch reveals navigation rather than activating an underlying moving target.

The welcome animation reflects current cluster observations. Informational records alone do not produce an unhealthy expression. Incomplete or stale data has an unknown state. Reduced motion stops decorative animation.

## Visual targets

These approved concept images contain illustrative data. They establish direction, not a claim of pixel-perfect implementation, measured chart accuracy or release readiness.

- [Home](design/home-landscape.png)
- [Incident investigation](design/incident-landscape.png)
- [Widget editor](design/editor-landscape.png)

## Product boundaries

The current app exposes read-only inspection, bounded container logs and relevant external links. Kubernetes write actions and motion-triggered screen wake are not implemented. Optional background live alerts use an ongoing Android foreground-service connection, not cloud push. Network loss, Android power management, MIUI restrictions and process termination can interrupt delivery. The service starts through an explicit setting or when an enabled app is reopened, and has a visible Stop action. Play distribution of the special-use foreground service requires the applicable platform review; newer Android versions need notification permission. Monitor history and CPU-throttling investigations use bounded Prometheus range queries. Throttling history requires the source namespace, pod and container; other incident types retain their available measurements and timeline. Missing samples break chart lines and unavailable history has a retry state. Release quality requires physical-device verification across orientation, reconnection, editing and resource investigation, in addition to automated checks.

## Live alerts and retained sources

Settings includes background live alerts, app/recovery notifications, per-source selection and configurable quiet hours. The first live frame establishes a baseline, so starting the watch does not page through old history. Unchanged incident states are suppressed; pending and informational conditions do not page. Notification taps open the incident in Olympus. Quiet hours suppress delivery rather than deferring a burst of old notifications.

Inbox sources come from retained history as well as the current feed. Selecting a source with no active incidents opens its history; server-side source filtering happens before pagination. Source delivery shows retained counts and last received times separately from collection availability. A successful collector does not prove that an upstream webhook is configured.

## Container inspection

The API returns selected pod/container operational fields without environment variables or secret-volume contents. Log requests select an existing container, current or previous instance, and are limited to the last hour, 200 lines and 64 KiB with a six-second upstream deadline. Logs are read on demand; there is no exec, shell or cluster mutation. A terminated pod or missing previous instance has an explicit unavailable state. Log text is not copied into backend request logs.
