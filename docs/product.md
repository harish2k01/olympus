# Product experience

Olympus is a personal Android desk console for a Kubernetes homelab. Its core workflow is **glance → investigate → act on context**, supported by a configurable home screen. Widgets are entry points into the companion, rather than the whole product.

## Destinations

- **Home:** current attention, cluster metrics, GitOps readiness, monitors and recent changes. Independent portrait and landscape canvases support pinned resources and integration fields.
- **Cluster:** nodes and applications. Node pages retain Kubernetes conditions, allocations, workloads and events. Applications expose resource relationships, usage and related activity.
- **Services:** monitor availability and response times, plus connected integration counters and app notifications.
- **Inbox:** current conditions and retained history. Pending, Firing, Resolved, Observed and Unconfirmed remain distinct; occurrence counts describe grouped records rather than active-alert totals.

## Investigation

An attention widget links to its incident. The incident shows source, state, available measurements, timestamps and affected resources. When a pod and node are identified, **Inspect workload** opens current readiness, restarts and resource allocations from the node API. **Open node** reaches detailed node evidence. Back returns to the previous investigation route.

A monitor opens current availability, latency, recent session response-time samples and matching received notifications. These samples do not imply a historical uptime percentage. An integration opens selected counters, collection time, app notifications and an application-resource link when that application is available.

Original incident payloads remain under Technical details. Source-provided CPU limits and throttled-period measurements are preserved. Missing values remain unavailable. The client does not fabricate thresholds, historical charts, uptime percentages or notifications.

## Customization and desk behavior

Long-press selects editing and permits dragging. Selecting a widget exposes its live preview and independent width/height controls. Horizontal and vertical handles resize on the grid. Configuration includes title and applicable source/fields. Removal can be undone. The dock is hidden during editing.

The v0.6 layouts use separate storage keys. Older layouts are retained and can be restored from the overflow menu. Layout export/import remains supported. The dock occupies a separate region while shown; hiding it releases that space. In idle mode the first touch reveals navigation rather than activating an underlying moving target.

The welcome animation reflects current cluster observations. Informational records alone do not produce an unhealthy expression. Incomplete or stale data has an unknown state. Reduced motion stops decorative animation.

## Visual targets

These approved concept images contain illustrative data. They establish direction, not a claim of pixel-perfect implementation, measured chart accuracy or release readiness.

- [Home](design/home-landscape.png)
- [Incident investigation](design/incident-landscape.png)
- [Widget editor](design/editor-landscape.png)

## Product boundaries

The current app exposes read-only inspection and relevant external links. Kubernetes write actions, Android background push delivery and motion-triggered screen wake are not implemented. Broader monitor history and incident metric time series require corresponding backend capabilities; they are not substituted with demo data. Release quality requires physical-device verification across orientation, reconnection, editing and resource investigation, in addition to automated checks.
