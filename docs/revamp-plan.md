# Olympus revamp plan

Olympus is an always-on display: an old phone propped next to the server, read from across the room.
Success = a glance answers "is the lab fine, and if not, what needs me and how urgently?" without
picking anything up. Investigation and actions are secondary: you walk up and tap.

Design canvas: https://claude.ai/artifact/CCCRNeenwimTcfMFzc8ZVs

## Principles

- One verdict, readable at 2–3 m: **All clear** / **N need you** / **Unknown**.
- State uses colour, shape and a word, never colour alone (green circle-check, amber square, red triangle, grey dashed).
- Two tiers. **Now**: firing incidents, which change the verdict. **Later**: chores (upgrades, certs about to renew, disk growth), which never turn the screen amber.
- Stale data looks stale: greyed, dashed outlines, with its age shown. A missing source is never "fine".
- Built to stay on: night mode, dimming, burn-in pixel shift, wake bright on a new critical.
- Actions are few, allowlisted, confirmed and logged. They are off by default on the display.

## Screens

| Screen | Purpose |
|---|---|
| Glance: all clear | Clock, verdict, 4 vitals (nodes, apps synced, services up, restarts in 24 h), live strip |
| Glance: needs you | Verdict count + up to 3 Now items as large cards; one Later line |
| Glance: night | Black, dim clock + verdict dot; wakes on critical |
| Glance: stale | "Unknown" verdict, faded vitals, data age, retry countdown |
| Glance: portrait | Same content stacked |
| Attention | Now / Later / Snoozed tabs; Snooze and Open on each row |
| Item detail | What happened, last success, source, suggestion, allowlisted actions |
| Cluster | Node cards (CPU, mem, pods), disk forecast, certs, restarts |
| Services | Monitors sorted slowest first, Arr counters |
| Settings | Night window, brightness, burn-in, wake on critical, actions toggle, QR pairing |

## What changes

**Remove:** the free-form widget editor (drag, resize, previews, layout import/export, v0.6 layout restore), the mood face and welcome animation, and the bottom dock. The glance becomes two fixed, designed layouts: landscape and portrait.

**Keep, simplified:** the incident inbox (becomes the Attention list plus a History tab), node, app and workload detail, logs, monitor history, and live alerts.

**Keep as is:** the Go backend, SSE stream, collectors, incident state model (pending, firing, unconfirmed, resolved) and auth.

## Backend work

1. `attention` model: `{id, tier: now|later, severity, title, subtitle, since, source, suggestion, actions[]}`, derived server-side so the phone only renders it.
2. New "Later" collectors: cert-manager expiry, Talos/Kubernetes/Helm chart version drift, PVC/Longhorn growth forecast, Argo out-of-sync, open Dependabot PRs (optional GitHub token).
3. Snooze state stored server-side, with an expiry.
4. Allowlisted actions behind a separate token scope and narrow RBAC: Argo app sync, rollout restart of a deployment, delete a stuck pod. Every action is audit-logged and confirmed on the device.
5. Pairing: the backend prints a QR code (URL + one-time code) on first start, and the app exchanges the code for a token.

## Status

| Phase | State |
|---|---|
| 1. Glance: theme, all-clear / needs-you / stale / portrait, Space Grotesk numerals; widget editor removed | Done (#1, #5, #8) |
| 2. Always-on: night window (configurable), room-light brightness, pixel shift, wake on critical | Done (#1, #7, #9) |
| 3. Attention + Detail: Now / Later / Snoozed / History, severity hero, suggestion, action column | Done (#3, #7) |
| Actions: restart, delete pod, Argo CD sync, all confirmed and audit logged | Done (#1, #9) |
| Snoozes shared through the backend (SNOOZE_FILE) | Done (#9) |
| Later chores: certificates, volumes (full and 30-day forecast), Talos releases, Dependabot | Done (#3, #9) |
| 4. Cluster + Services: node cards with pods, certificates, restarts; slowest-first grid, media counters | Done (#7, #9) |
| 5. QR pairing and Settings: burn-in, wake, night hours, room light, allow actions | Done (#7, #9) |
| Dark-only theme (light theme removed) | Done (#9) |
| 6. Device soak: 72 h on the Redmi Note 7 (reconnection, night transitions, burn-in shift, MIUI) | Not started |
| Signed release build | Waiting on a signing key |
| Dependabot chore | Needs GITHUB_OWNER and GITHUB_TOKEN in olympus-auth |

## Decisions

- Target device: Redmi Note 7 (1080×2340, about 393×851 dp; MIUI battery limits apply). The landscape glance at 851×393 dp is the primary layout.
- Actions such as restart are wanted, behind the allowlist, confirmation and audit log.
- There are no backups in the cluster yet, so backup tiles and collectors are left out. Add a backup source when backups are planned.
