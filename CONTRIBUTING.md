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
- **Roll suggestions (same consent gate):** when the plugin detects a
  completion that may be worth a roll (quest, diary-tier and
  combat-achievement completions), it also
  writes a tiny `{source, label, ts}` suggestion to `POST /r/<code>/suggest` —
  a sub-resource of the same relay session, shown by the web app as a
  dismissible reminder. Both `pushSuggestion()` calls sit behind the same
  `config.onlineSync()` early-return as `pollRelay()`; no suggestion request is
  ever made without consent. The sub-resource's write-token is persisted in
  plugin config (keyed per sync code) so a client restart doesn't orphan the
  suggestion array until its TTL expires.
- **Privacy:** the payload is chunk-unlock + run state — **no account
  credentials**. The relay stores it ephemerally (24h TTL), keyed by the random
  pairing code; only that code can read it, and a private write-token (held in
  the web app) is required to write.
- **Relay source + deploy docs:** `workers/fate-relay/` and `docs/online-relay.md`
  in the companion web-app repo
  (https://github.com/Nubles/OSRS-Fate-Locked).

## Ideas / future work

- Auto-log rolls from in-game item drops into the tracker's history.
- Emit a hash-chained audit log of chunk transitions + events so the web app's
  integrity layer can be verified against actual gameplay.
