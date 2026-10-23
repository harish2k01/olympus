# Olympus

A native Android desk companion for a Kubernetes homelab, built with Kotlin and Jetpack Compose. The Cobalt & Apricot interface includes configurable desk modules, capacity-weighted CPU and memory summaries, individual node views, and a filtered event feed. Immersive landscape mode uses compact header navigation.

## Current status

The app reads live telemetry from the Go service in `backend/`, refreshing every five seconds while visible. The service collects Prometheus metrics, Kubernetes node state and warning events, Argo CD application health, and Longhorn backup availability. Authenticated webhooks accept events from external services. Missing readings remain unavailable; a failed connection retains the last known readings and marks the connection disconnected. Cluster write actions are not exposed.

Webhook history is held in memory and is lost when the backend restarts. Source webhook destinations must be configured separately; installing this service does not automatically connect Uptime Kuma, Robusta, or Arr applications. Android background notifications are not implemented.

## Development

Requires Android Studio, its compatible JDK, Android SDK Platform 37, and Build Tools 36.0.0. Android 10 (API 29) or later is supported. Open this directory in Android Studio or run `gradlew.bat assembleDebug testDebugUnitTest lintDebug` on Windows. Connect a device with USB debugging enabled for installation.

## Deployment and pairing

`deploy/kubernetes.yaml` defines the service and read-only cluster permissions. Adapt the ingress hostname and gateway to your environment. Create the `olympus-auth` Secret in the `olympus` namespace with independently generated `API_TOKEN` and `HOOK_TOKEN` values of at least 32 characters before deploying. Never commit this Secret. `gitops/application.yaml` provides an Argo CD Application example.

The API exposes authenticated `GET /v1/snapshot` and `GET /v1/events`. Webhooks use `POST /hooks/{source}` with the hook token as a Bearer token or Basic authentication password. Publish the mobile API through HTTPS and keep webhook routes within a trusted network.

The development build accepts `backendUrl` and `pairToken` intent extras for initial pairing. The token is encrypted using Android Keystore. This development pairing mechanism is not a finished onboarding interface. Kubernetes credentials remain inside the backend pod.
