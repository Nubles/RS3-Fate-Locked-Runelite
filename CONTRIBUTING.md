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
  inspectable; what the file-watch / Downloads auto-detect path reads).
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
- Everything is local: the plugin makes no network calls, reading only the
  bundle JSON from the clipboard or a local file.

## Ideas / future work

- Auto-log rolls from in-game item drops into the tracker's history.
- Per-chunk "what's here" tooltip on the world map, fed by the app's
  chunk-content dataset.
- Emit a hash-chained audit log of chunk transitions + events so the web app's
  integrity layer can be verified against actual gameplay.
