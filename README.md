# Olympus

A native Android companion for a Kubernetes homelab, built with Kotlin and Jetpack Compose. Olympus shows a live verdict, resource usage, service health, and an incident inbox. The four destinations appear in a side rail on the desk and a bottom bar in handheld mode.

## Current status

The app receives live state over one authenticated Server-Sent Events connection while visible. The Go backend shares collectors between clients, samples metrics every 15 seconds, and receives Kubernetes watch changes and authenticated webhooks. CPU is a five-minute rate; freshness is bounded by the upstream scrape interval. Reconnection uses exponential backoff and a fresh full snapshot. Missing readings remain unavailable; cached readings are marked when disconnected. Cluster write actions are not exposed.

Home supports drag placement, independent width and height resizing, widget previews, and undo. Portrait and landscape layouts are stored separately and can be exported as JSON. Upgrades replace previous stock layouts and keep a copy available through **Restore previous layout**. Custom layouts keep their configuration.

The Home verdict uses firing warning and critical incidents and confirmed source availability. Pending conditions appear as watching. Desk mode opens a dim, black clock view after the configured idle time and returns to Home on touch or when a new incident fires.

**Cluster** shows CPU and memory history, node usage, and Argo CD application health. **Services** shows Uptime Kuma checks and integration data. **Inbox** filters firing, pending, resolved, notice, and Kubernetes event records. Incident details include related resources, timestamps, and backend-supplied investigation advice for supported incidents.


Node details include condition tables, capacity and allocatable values, allocated requests/limits, non-terminated workloads, addresses, system information, and events. Application resource maps follow Argo-managed resources and Kubernetes controller ownership, including operator-created workloads. Secrets appear only as resource metadata; their contents are never read for the map. CPU/memory sparklines are available under application usage.

Webhook history persists on a 1 GiB volume and is bounded to 2,000 records and 30 days when compacted. Active incidents are prioritized and exempt from the age limit. Firing, pending, and unconfirmed are distinct states. Recovery requires a successful observation from the owning source. A missing source or resource cannot confirm recovery. History separates resolved incidents from notifications and observed warnings. The live feed returns the latest 100 items, combining received webhooks with current Kubernetes warnings and Prometheus alerts. Observed Kubernetes warnings and Prometheus alerts are retained alongside webhooks. Events that occurred before collection started cannot be reconstructed from this store. Identical inbox entries are grouped; tapping reveals timestamps, operational fields, related events, and a service link when available. Test notifications are hidden by default; Settings can reveal them. Older records load in pages of 100. Source webhook destinations must be configured separately; installation does not automatically connect Uptime Kuma, Robusta, or Arr applications. Optional background live alerts use an ongoing connection and visible Android service notification; device battery restrictions can interrupt delivery.

Backup destination availability, recorded volume backup coverage, and the last recorded backup are separate values. Backup records do not establish restore verification. The backend serves allowlisted app icons from the existing chart metadata. Android caches these images locally and falls back to initials. In-session sparklines retain up to 60 samples; the node detail CPU chart queries 15 minutes of Prometheus history.

Settings includes appearance, desk timing, layout, notification channels, and pairing. Services displays the response time and 24-hour uptime reported by Uptime Kuma. The Home screen widgets display a dated snapshot, and the Desk mode tile toggles ambient behavior.

## Development

Requires Android Studio, its compatible JDK, Android SDK Platform 37, and Build Tools 36.0.0. Android 10 (API 29) or later is supported. Open this directory in Android Studio or run `gradlew.bat assembleDebug testDebugUnitTest lintDebug` on Windows. Connect a device with USB debugging enabled for installation. Android CI builds the APK, runs unit tests, and checks lint without requiring a laptop emulator. CI APKs are development builds; release signing and device soak testing are separate release steps.

## Deployment and pairing

`deploy/kubernetes.yaml` defines the service and read-only cluster permissions. Adapt the ingress hostname and gateway to your environment. Create the `olympus-auth` Secret in the `olympus` namespace with independently generated `API_TOKEN` and `HOOK_TOKEN` values of at least 32 characters before deploying. Never commit this Secret. `gitops/application.yaml` provides an Argo CD Application example.

The API exposes authenticated `GET /v1/snapshot`, `GET /v1/events`, `GET /v1/events/history?offset=0`, `GET /v1/stream`, `GET /v1/nodes/{name}`, and `GET /v1/apps/{name}`, `GET /v1/metrics/history`, `GET /v1/events/sources`, `GET /v1/events/item?id=...`, `GET /v1/workload`, and `GET /v1/workload/logs`. Webhooks use `POST /hooks/{source}` with the hook token as a Bearer token or Basic authentication password. Publish the mobile API through HTTPS and keep webhook routes within a trusted network. A proxy must allow long-lived streaming responses without buffering.

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

The optional `olympus-integrations` Secret supplies `INTEGRATIONS_JSON`, an array of objects with `name`, `kind`, `url`, and `token`. Supported kinds are `sonarr`, `radarr`, and `prowlarr`. Use internal service addresses and upstream API keys; never commit the Secret. The backend reads queue, wanted, series/movie, indexer, and health counters through read-only upstream requests on a shared 30-second cache. The phone receives selected counters and availability state; it never receives API keys. Missing counters remain unavailable rather than becoming zero. Configure visible fields in each Service widget. Monitors appear in Services with live status and response time.

### Activity and incident meaning

The Recent activity widget shows the latest received event and its event/recovery timestamp. Full retained activity can be inspected in Inbox History. Repeated updates to one Kubernetes Event UID remain one record. History is retained for 30 days or 2,000 records, while active incidents are retained separately.

Firing alerts are current Prometheus or integration conditions, not a count of delivered Robusta/Alertmanager notifications. Informational conditions are separated from actionable warnings and failures. An alert clears when its authoritative source confirms recovery, rather than after an app timer; pending and unconfirmed states remain explicit. Alert details retain namespace, pod, container, and selected reporting labels. Nodes come from an explicit alert node label or observed pod placement, never by assuming that an exporter instance is the affected node. CPU throttling details include the observed CPU limit and the fraction of scheduling periods throttled when available.

### Investigation metric history

Monitor details offer 1-hour, 6-hour and 24-hour response-time and availability history from Prometheus. CPU-throttling incidents with a namespace, pod and container offer throttled-period percentage and CPU usage history. Tap a chart to inspect a sample. Missing samples remain gaps; these charts do not establish an uptime SLA or an alert threshold.

The authenticated history endpoint accepts cluster CPU or memory, monitor history by ID, and throttling or memory against limit for a specified container. Supported ranges are 1, 6, and 24 hours. Queries are bounded and cached. Cluster history marks the samples where Argo CD syncs and container restarts were detected.

### Companion investigation and alerts

Olympus 0.7 uses compact contextual controls, responsive node condition tables, a fitted application-resource overview and a portrait resource list. Workload investigation includes selectable containers and bounded current/previous logs. The backend role includes `get` on `pods/log`; it does not grant exec or write access. Log endpoints require the existing API authentication and never accept arbitrary URLs or commands.

`GET /v1/events/sources` indexes retained source counts and last delivery times. `GET /v1/events/history?source=Robusta&offset=0` filters before paging, so older integration notifications remain discoverable during busy Kubernetes activity.

Background live alerts are opt-in in Settings. An ongoing notification provides connection state and Stop. Settings controls app/recovery notifications, source selection and quiet hours. Existing records establish the initial baseline; unchanged incident states, pending conditions and informational alerts do not generate incident pages. This is a direct live connection, not a cloud push service; delivery depends on network access and device background restrictions. Special-use foreground-service declarations require review before Play distribution, and Android 13+ requires notification permission.

The default desk layout places the verdict alongside resource gauges, node bars, monitors, and GitOps status. The handheld layout stacks these sections above the bottom bar.

### QR pairing

An operator can request a one-time QR code using the backend API token. From PowerShell 7, enter the token at the secure prompt and display the PNG locally.

```powershell
$address = 'https://olympus.k8s.harish2k01.xyz'
$operatorToken = Read-Host 'Olympus operator token' -AsSecureString
$qrPath = Join-Path ([IO.Path]::GetTempPath()) 'olympus-pairing.png'
Invoke-WebRequest "$address/v1/pairing/code" -Method Post -Authentication Bearer -Token $operatorToken -Headers @{Accept = 'image/png'} -ContentType 'application/json' -Body (@{url = $address} | ConvertTo-Json -Compress) -OutFile $qrPath
Invoke-Item -LiteralPath $qrPath
```

In Olympus, open Settings → Connection → Scan pairing QR and confirm the backend address. The code expires after five minutes or its first use. Remove the PNG after pairing. The app stores a read credential in Android Keystore-backed storage. Device credentials cannot issue more pairing codes. Rotating the operator token invalidates previously issued device credentials.
