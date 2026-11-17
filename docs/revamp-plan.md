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

## App work (phases)

1. **Glance:** new theme tokens (ground #0A0C0F, surface #13171C, ok #3DD68C, warn #F5A524, crit #FF6B5E, info #6FA8FF; Space Grotesk numerals, Inter text), the all-clear, needs-you and stale states, and the portrait variant. Delete the widget editor.
2. **Always-on:** night mode schedule, ambient-light dimming, pixel shift every few minutes, wake on critical, keep the foreground service resilient.
3. **Attention + Detail:** tabs, snooze, suggestions, actions (behind the setting).
4. **Cluster + Services refresh** using the existing endpoints.
5. **QR pairing and Settings.**
6. Device soak: 72 h on the target phone, covering reconnection, night transitions and burn-in shift.

## Decisions

- Target device: Redmi Note 7 (1080×2340, about 393×851 dp; MIUI battery limits apply). The landscape glance at 851×393 dp is the primary layout.
- Actions such as restart are wanted, behind the allowlist, confirmation and audit log.
- There are no backups in the cluster yet, so backup tiles and collectors are left out. Add a backup source when backups are planned.
