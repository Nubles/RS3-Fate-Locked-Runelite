# Fate Locked Ironman — RuneLite Plugin

Companion plugin for the [Fate Locked Ironman tracker](https://github.com/Nubles/OSRS-Fate-Locked). Renders the chunks
you've authored in the web app's region map directly onto RuneLite's world map
and main game view, shows your live run state in-game, and warns you — by
chat, sound, screen flash, and right-click tags — before you touch content you
haven't unlocked yet.

## What it does

- **In-game HUD.** Always-visible overlay with your keys (standard · Omni ·
  Chaos), fate points, active ritual buff, first pinned goal, and the chunk
  you're standing in with its lock status.
- **Sub-area-aware lock state.** v2 bundles carry the app's named sub-areas
  (Falador, Port Sarim, …) and region hierarchy, so a Falador chunk reflects
  *Falador's* unlock — not all of Asgarnia's. Mirrors the web app's map
  exactly, including always-free Misthalin.
- **World map overlay.** Every authored chunk tinted green (unlocked), red
  (locked), per chunk, on the full world map.
- **Scene + minimap overlays.** The 64×64 chunk you're in is outlined in the
  game view and tinted on the minimap with the same color coding.
- **Locked right-click tags.** Menu entries for NPCs, objects, ground items and
  walks that target a locked chunk get a red **(LOCKED)** tag appended — the
  warning arrives *before* you click, where feasible.
- **Locked-entry alarm.** Crossing into locked territory plays an audio cue
  and pulses a red border around the entire viewport for ~1.6s.
- **Account binding.** A run is bound to one OSRS account in the web app
  (Auto-Roll). The HUD shows that account — green when it matches the logged-in
  character, red ⚠ when it doesn't — and a one-time chat warning fires per login
  if you're on the wrong character. Toggle: *Warn on wrong account*.
- **Side panel.** Run stats (keys/fate/buff/goal), current location, and an
  **Allowed / Forbidden / Unknown** breakdown: every unlocked area, every
  authored-but-locked area, and how many map chunks sit in unnamed terrain.
- **Chat on chunk entry.** One-liner per chunk boundary: coords, area
  ("Falador · Asgarnia"), and status.
- **Hot reload.** The bundle file is watched and re-read on change.

Every behavior has a config toggle (HUD, flash, menu tags, chat, sound, each
overlay, all three colors).

## Two ways to load a bundle

1. **Clipboard → side panel** — the web app's **RL** button now copies the
   bundle to your clipboard *and* downloads it. Paste into the panel's box and
   click *Import pasted JSON*. Fastest loop.
2. **Config file path** — point **Bundle file path** at the downloaded
   `fate-locked-bundle-YYYY-MM-DD.json`; the file is watched and hot-reloaded,
   so re-exporting over it updates the plugin automatically.

## Install

### Easiest — download the prebuilt jar (no build tools)

1. Grab `fatelocked-0.1.0-all.jar` from the repo's
   [**latest plugin release**](https://github.com/Nubles/OSRS-Fate-Locked/releases/tag/runelite-plugin-latest)
   (auto-built by CI on every change).
2. Drop it into `~/.runelite/sideloaded-plugins/` (create the folder if needed).
   On Windows that's `%USERPROFILE%\.runelite\sideloaded-plugins\`.
3. Launch RuneLite **with developer mode** — sideloaded plugins only load with
   the `--developer-mode` flag (append it to your RuneLite shortcut's target).
4. The plugin appears in the plugin panel as **Fate Locked Ironman**.

> For a zero-friction, no-developer-mode install, the plugin needs to be on the
> RuneLite Plugin Hub — see [HUB-SUBMISSION.md](HUB-SUBMISSION.md).

### Build it yourself (development)

1. `cd runelite-plugin`
2. `gradle shadowJar` (needs JDK 11 + Gradle)
3. Copy `build/libs/fatelocked-0.1.0-all.jar` into `~/.runelite/sideloaded-plugins/`.
4. Launch RuneLite with `--developer-mode`.

### Cutting a versioned release

Push a `vX.Y.Z` git tag — CI builds the jar (named to match the tag) and
publishes a permanent GitHub release `vX.Y.Z`, alongside the rolling
`runelite-plugin-latest`:

```sh
git tag v0.1.0 && git push origin v0.1.0
```

## Bundle format (v3)

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

- `state.linkedAccount` (v3) — the OSRS account the run is bound to, used for the
  HUD account line and the wrong-character warning. Omitted until the run is
  bound in the app's Auto-Roll tab.

- `chunks` — continent blocks; `subAreaChunks` — the named areas inside them.
- `regionGroups` — hierarchy so the plugin can resolve continent unlocks the
  same way the app does (Misthalin + its starter areas are always free).
- `state` — live run stats for the HUD and side panel.
- v1 bundles (no `subAreaChunks` / `regionGroups` / `state`) still load; lock
  state then falls back to continent-level. v2 bundles (no `linkedAccount`) load
  fine too — the account features simply stay dormant.

The app's map is calibrated to canonical OSRS chunk coordinates, so
`chunkOffset` is `{0,0}` in current exports; the plugin still honors non-zero
offsets from old bundles.

## Limits

- The plugin cannot actually stop you walking into a locked chunk. Movement is
  server-authoritative; all we can do is mark, tag, and warn.
- Right-click tagging resolves NPC/object/ground-item/walk targets; widget and
  spell menu entries have no world tile, so they can't be tagged.
- Rendering on the world map uses the currently-public `RenderOverview` API.
  If RuneLite changes that API, the overlay's pixel math may need a touch-up.
- Plugin-hub submission requires removing any external network calls. This
  plugin deliberately has none — everything is local JSON.

## Future work

- Auto-log rolls from in-game item drops into the tracker's history.
- Per-chunk "what's here" tooltip on the world map, fed by the app's
  chunk-content dataset.
- Emit a hash-chained audit log of chunk transitions + events so the web app's
  integrity layer can be verified against actual gameplay.
