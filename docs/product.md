# Product experience

Olympus is an always-on display: an old phone beside the server, read from across the room. The core flow is **glance → walk up → act**.

Design canvas (all screens): https://claude.ai/artifact/CCCRNeenwimTcfMFzc8ZVs · Plan: [revamp-plan.md](revamp-plan.md)

## Glance

- Clock, date and one verdict: **All clear**, **N need you** or **Unknown**, each with its own shape so state never relies on colour alone.
- All clear shows nodes, apps synced, services up and restarts in 24 h. Otherwise up to three incident cards with their age.
- Stale data fades, uses dashed outlines and states its age. A missing source never reads as healthy.
- Pixel shift every minute; night view 23:00–07:00 at minimum backlight, ended by a critical incident.
- The bottom line shows live status, CPU, memory and how many chores wait for later.

## Walk-up screens

- **Attention:** Now (firing, not snoozed), Later (certificates, full volumes, Talos upgrades, Dependabot PRs), Snoozed and History.
- **Incident:** timeline, related resources, logs, Snooze 1 h / 8 h and, when the backend allows actions, Restart for the owning deployment or statefulset (always confirmed).
- **Cluster:** restarts and chores, CPU/memory history, nodes and Argo CD applications.
- **Services:** monitors with problems first, then slowest; Sonarr, Radarr and Prowlarr counters.

## Boundaries

- The only cluster write is a rollout restart: off unless `ACTIONS_ENABLED=true`, never in system namespaces, once per target per minute, audit logged.
- Snoozes live on the device.
- Background alerts use a foreground service, not cloud push; MIUI and Android power management can interrupt them.
- Chores never change the verdict.
