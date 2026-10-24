# Olympus

A native Android desk companion for a Kubernetes homelab, built with Kotlin and Jetpack Compose. The Cobalt & Apricot interface includes configurable desk modules, capacity-weighted CPU and memory summaries, individual node views, and a filtered event feed. Immersive landscape mode uses compact header navigation.

## Current status

The app receives live state over one authenticated Server-Sent Events connection while visible. The Go backend shares collectors between clients, samples metrics every 15 seconds, and receives Kubernetes watch changes and authenticated webhooks. CPU is a five-minute rate; freshness is bounded by the upstream scrape interval. Reconnection uses exponential backoff and a fresh full snapshot. Missing readings remain unavailable; cached readings are marked when disconnected. Cluster write actions are not exposed.

Home supports long-press editing, grid placement, independent width and height sizing, a two-axis resize handle, widget removal, and an add-widget picker. Portrait and landscape layouts are independent and can be exported or imported as JSON. An aspect-aware grid fills the available viewport without reserving a dock gutter. Live widget previews remain visible while editing; each widget supports size, title, and applicable app, node, or event-source configuration. A status-colored clock/greeting card, readable metric cards, optional backup/attention widgets, and pinned apps and nodes form the desk view. The clock card is not a service launcher. A floating navigation dock hides after a configurable idle delay (four seconds by default) and returns on interaction. Focus mode also hides the header. Reduced motion disables decorative transitions. Apps use a searchable grid with health filters and compact resource previews.

Node details include condition tables, capacity and allocatable values, allocated requests/limits, non-terminated workloads, addresses, system information, and events. Application resource maps follow Argo-managed resources and Kubernetes controller ownership, including operator-created workloads. Secrets appear only as resource metadata; their contents are never read for the map. CPU/memory sparklines are available under application usage.

Webhook history persists on a 1 GiB volume and is bounded to 2,000 records and 30 days when compacted. Active incidents are prioritized and exempt from the age limit. Firing includes pending or unconfirmed states; recovery requires a successful authoritative source observation. A missing source or resource cannot confirm recovery. History separates resolved incidents from notifications and observed warnings. The live feed returns the latest 100 items, combining received webhooks with current Kubernetes warnings and Prometheus alerts. Observed Kubernetes warnings and Prometheus alerts are retained alongside webhooks. Events that occurred before collection started cannot be reconstructed from this store. Identical inbox entries are grouped; tapping reveals timestamps, operational fields, related events, and a service link when available. Test notifications are hidden by default; Settings can reveal them. Older records load in pages of 100. Source webhook destinations must be configured separately; installation does not automatically connect Uptime Kuma, Robusta, or Arr applications. Android background notifications are not implemented.

Backup destination availability, recorded volume backup coverage, and the last recorded backup are separate values. Backup records do not establish restore verification. App icons use checked-in public Helm chart icon metadata, with an upstream icon or initials fallback. Images are cached locally. In-session sparklines retain up to 60 samples; the node detail CPU chart queries 15 minutes of Prometheus history.

Settings groups display, Home, event history, and pairing controls. Keep-awake, desk brightness, reduced motion, dock delay, and grid spacing are configurable. The monitor view shows each Uptime Kuma check with current status and response time from Prometheus; a missing latency is not shown as zero. Motion-based screen wake is not implemented.

## Development

Requires Android Studio, its compatible JDK, Android SDK Platform 37, and Build Tools 36.0.0. Android 10 (API 29) or later is supported. Open this directory in Android Studio or run `gradlew.bat assembleDebug testDebugUnitTest lintDebug` on Windows. Connect a device with USB debugging enabled for installation. Android CI builds the APK, runs unit tests, and checks lint without requiring a laptop emulator. CI APKs are development builds; release signing and device soak testing are separate release steps.

## Deployment and pairing

`deploy/kubernetes.yaml` defines the service and read-only cluster permissions. Adapt the ingress hostname and gateway to your environment. Create the `olympus-auth` Secret in the `olympus` namespace with independently generated `API_TOKEN` and `HOOK_TOKEN` values of at least 32 characters before deploying. Never commit this Secret. `gitops/application.yaml` provides an Argo CD Application example.

The API exposes authenticated `GET /v1/snapshot`, `GET /v1/events`, `GET /v1/events/history?offset=0`, `GET /v1/stream`, `GET /v1/nodes/{name}`, and `GET /v1/apps/{name}`. Webhooks use `POST /hooks/{source}` with the hook token as a Bearer token or Basic authentication password. Publish the mobile API through HTTPS and keep webhook routes within a trusted network. A proxy must allow long-lived streaming responses without buffering.

The development build accepts `backendUrl` and `pairToken` intent extras for initial pairing. The token is encrypted using Android Keystore. This development pairing mechanism is not a finished onboarding interface. Kubernetes credentials remain inside the backend pod.

## Backend debugging

The backend writes structured JSON logs to stdout. `LOG_LEVEL` accepts `DEBUG`, `INFO`, `WARN`, or `ERROR` (default `INFO`). Request logs include a generated request ID, route class, method, status, and elapsed milliseconds. Collection logs include a collection ID, duration, resource counts, and partial-data status. Upstream errors carry the corresponding request/collection ID; debug logging adds upstream response timing. Stream connections, webhook acceptance, and history failures have distinct event names.

Request headers, query strings, request bodies, credentials, and webhook message text are excluded from logs. `X-Request-ID` is returned to clients. Health checks are omitted from access logs.

```sh
kubectl logs -n olympus deployment/olympus-backend --since=10m
kubectl logs -n olympus deployment/olympus-backend --previous
kubectl get pods,pvc -n olympus
```

Backend checks: run `go test ./...` and `go vet ./...` from `backend/`. Tests cover authentication, stream delivery, log redaction, persistent-history recovery, resource ownership, and resource-quantity allocation calculations.

### Service widget integrations

The optional `olympus-integrations` Secret supplies `INTEGRATIONS_JSON`, an array of objects with `name`, `kind`, `url`, and `token`. Supported kinds are `sonarr`, `radarr`, and `prowlarr`. Use internal service addresses and upstream API keys; never commit the Secret. The backend reads queue, wanted, series/movie, indexer, and health counters through read-only upstream requests on a shared 30-second cache. The phone receives selected counters and availability state; it never receives API keys. Missing counters remain unavailable rather than becoming zero. Configure visible fields in each Service widget. Monitors have a dedicated dock destination with live status and response time.
