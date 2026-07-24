# Contributing / developer notes

Developer-facing details for the Fate Locked Ironman plugin. End-user docs live
in [README.md](README.md).

## Building from source

```sh
gradle build      # needs JDK 11 + Gradle; produces build/libs/fatelocked-<version>.jar
```

### Sideloading a dev build

To run a local build without the Plugin Hub:

1. Copy `build/libs/fatelocked-<version>.jar` into `~/.runelite/sideloaded-plugins/`
   (`%USERPROFILE%\.runelite\sideloaded-plugins\` on Windows).
2. Launch the RuneLite client with `--developer-mode` — sideloaded plugins only
   load with that flag.
3. The plugin appears in the plugin list as **Fate Locked Ironman**.

## Plugin Hub release baseline

The Hub currently pins `fdca20aad7ffcf159b62210f7492f110c185afee`; the
next maintenance submission pins `f450bbd87cee74d26d24061d034368ad9f0c0c86`.
Keep that maintenance PR limited to the current-chunk overlay, locked-bank
warning, nearest bank/shop HUD lines, and aligned lock/free-area resolution. It
must not add gameplay automation, and Online Sync stays opt-in and off by
default.

## Bundle encodings

The plugin's import accepts two forms of the same v3 bundle:
- **Plain JSON** — the downloaded `fate-locked-bundle-*.json` file (readable,
  inspectable; what the file-watch path reads from ~/.runelite/fate-locked/).
- **`FLGZ:` + base64(gzip(json))** — the *clipboard* copy, compressed (~65%
  smaller) so the web app doesn't dump ~110 KB onto the user's clipboard. The
  plugin detects the `FLGZ:` prefix and inflates it.

## Bundle format (v3)

The web app exports a JSON bundle the plugin reads. v1/v2 bundles still load;
missing fields degrade gracefully.

```json
{
  "version": 3,
  "chunkOffset": { "cx": 0, "cy": 0 },
  "chunks":        { "Asgarnia": [{ "cx": 46, "cy": 52 }] },
  "subAreaChunks": { "Falador":  [{ "cx": 46, "cy": 52 }] },
  "regionGroups":  { "Asgarnia": ["Falador", "Port Sarim"] },
  "unlockedRegions": ["Falador"],
  "unlockedChunks": ["50,50", "50,51"],
  "state": {
    "keys": 3, "specialKeys": 0, "chaosKeys": 0,
    "fatePoints": 12, "activeBuff": "NONE", "pinnedGoals": [],
    "linkedAccount": "Zezima"
  }
}
```

- `chunks` — continent blocks; `subAreaChunks` — the named areas inside them.
- `regionGroups` — hierarchy so the plugin can resolve continent unlocks the
  same way the app does (Misthalin + its starter areas are always free).
- `unlockedRegions` — the sub-areas the player has unlocked.
- `unlockedChunks` — Chunked-mode runs only, a different unlock model (one map
  chunk at a time, not named areas). Its *presence* (not length) marks a
  bundle as Chunked — an empty array is a valid, meaningful state (a fresh
  Chunked run with nothing rolled yet, before the always-free start chunk).
  When present, `FateLockedBundle.lockStateAt`/`isUnlocked` resolve purely
  from raw chunk-coordinate membership instead of `unlockedRegions`.
- `state` — live run stats for the HUD and side panel.
- `state.linkedAccount` (v3) — the OSRS account the run is bound to, used for the
  HUD account line and the wrong-character warning. Omitted until the run is
  bound in the app's Auto-Roll tab.
- v1 bundles (no `subAreaChunks` / `regionGroups` / `state`) fall back to
  continent-level lock state. v2 bundles (no `linkedAccount`) load fine — the
  account features simply stay dormant.

The app's map is calibrated to canonical OSRS chunk coordinates, so
`chunkOffset` is `{0,0}` in current exports; the plugin still honors non-zero
offsets from old bundles.

## Implementation notes

- World map rendering uses the public `RenderOverview` API. If RuneLite changes
  that API, the overlay's pixel math may need a touch-up.
- By default everything is local — bundles load from the clipboard or from
  `.runelite/fate-locked/` only. The single exception is the opt-in Online Sync
  below, which makes outbound HTTPS calls.

## Online sync (relay) — the only network feature

Optional and **off by default**. Lets the run sync from the web app to the
plugin over the internet (handy across machines) without the plugin ever running
a server.

- **Explicit consent:** a dedicated boolean config **`onlineSync`** (default
  **false**) gates the whole feature. Its `@ConfigItem` carries the required
  warning *"This feature submits your IP address to a 3rd-party server not
  controlled or verified by Runelite developers"*, so enabling it shows a
  confirmation dialog. `pollRelay()` — the only method that contacts the relay —
  returns early on the very first line if `config.onlineSync()` is false, so no
  request is ever made without consent.
- **Pairing:** with online sync on, the user sets the *Online sync code* from the
  web app.
- **How:** the plugin polls a small Cloudflare Worker relay every ~4s using the
  **injected `OkHttpClient`** (never `new OkHttpClient()`), async via `enqueue`
  — never blocking the client thread. `GET /r/<code>` with `If-None-Match` so
  unchanged data returns `304`. On change it imports the bundle (same `FLGZ`
  payload as the clipboard).
- **Outbound-only:** no inbound socket/server (Hub rule). The relay default URL
  is `relayUrl` config (override to self-host).
- **Durable detected events (same consent gate):** supported detections are persisted atomically in `event-outbox.json`. `POST /r/<code>/events` appends v1 envelopes idempotently by stable `eventId`; `GET /r/<code>/acks` returns terminal app decisions so the plugin can remove completed, dismissed, or duplicate events. Retries use the same ID across disconnects and restarts.
- **Strict ownership:** detectors record facts only. The app checks run/account/revision/version gates, maps against canonical content and rates, reconciles factual progress without rolling, and exposes the only Roll button. Never call tracker roll logic from RuneLite.
- **Legacy migration:** `/suggest` remains Worker-compatible for one release, but current app code does not poll it. New detectors must use the v1 event outbox rather than timestamp-only suggestions.
- **Privacy:** bundle/state records expire after 24 hours. Event/ack records expire after seven days and contain the character name, run/revision, detector identity, event label/type, timestamps, confidence, and bounded evidence—no credentials, cookies, chat history, or arbitrary telemetry. Anyone with the random code can read its records; protected writes require the sub-resource token.- **Relay source + deploy docs:** `workers/fate-relay/` and `docs/online-relay.md`
  in the companion web-app repo
  (https://github.com/Nubles/OSRS-Fate-Locked).
## v1 event contract

`FateEvent` fields are: `protocolVersion`, `eventId`, `runId`, `account`, `runRevision`, `eventType`, `canonicalLabel`, `occurredAt`, `sessionSequence`, `bundleVersion`, `rulesVersion`, `contentVersion`, `detectorId`, `detectorVersion`, `confidence`, and bounded `evidence`.

Limits are 100 events per batch, 8 KiB per event, 32 evidence keys, and 256 characters per string. New detector versions require an app contract update; unknown IDs/versions are blocked rather than guessed.
