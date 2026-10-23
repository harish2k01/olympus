# Olympus

A native Android desk companion for a Kubernetes homelab, built with Kotlin and Jetpack Compose. The Cobalt & Apricot interface includes configurable desk modules, capacity-weighted CPU and memory summaries, individual node views, a filtered event feed, and an illustrative recovery workflow.

## Current status

This first build is a UI demo. Every screen identifies demonstration data. No backend, webhook receiver, cluster credentials, network permissions, or cluster write operations are included. Module visibility and panel order persist locally. Node and event examples are fixed, not live telemetry.

## Development

Requires Android Studio, its compatible JDK, Android SDK Platform 37, and Build Tools 36.0.0. Android 10 (API 29) or later is supported. Open this directory in Android Studio or run `gradlew.bat assembleDebug testDebugUnitTest lintDebug` on Windows. Connect a device with USB debugging enabled for installation.

## Planned integration

A separate in-cluster service will provide a versioned API for Prometheus metrics, Kubernetes and Argo resource state, Loki logs, and event ingestion from Arr applications, Uptime Kuma, and Robusta. Credentials remain server-side. Availability, protection findings, data freshness, and recovery verification remain distinct states.
