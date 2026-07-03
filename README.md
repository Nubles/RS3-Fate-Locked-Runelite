# Fate Locked Ironman

Companion plugin for the [Fate Locked Ironman tracker](https://github.com/Nubles/OSRS-Fate-Locked).
It draws the chunks you've authored in the web app's region map directly onto
RuneLite's world map and main game view, shows your live run state in-game, and
warns you — by chat, sound, screen flash, and right-click tags — before you
touch content you haven't unlocked yet.

## What it does

- **In-game HUD.** Always-visible overlay with your keys (standard · Omni ·
  Chaos), fate points, active ritual buff, first pinned goal, and the chunk
  you're standing in with its lock status.
- **Sub-area-aware lock state.** A Falador chunk reflects *Falador's* unlock —
  not all of Asgarnia's. Mirrors the web app's map exactly, including
  always-free Misthalin.
- **World map overlay.** Every authored chunk tinted green (unlocked) or red
  (locked), per chunk, on the full world map — hover any authored chunk to see
  its area name and lock status. Optional click-to-jump markers pin every area
  you haven't unlocked yet.
- **Scene + minimap overlays.** The 64×64 chunk you're in is outlined in the
  game view and tinted on the minimap with the same color coding.
- **Locked-border highlight.** The edges of your current chunk that border a
  locked chunk are traced in red, so you see exactly which way not to go.
- **Roll reminders.** A chat nudge on level-up, quest, achievement-diary,
  combat-achievement and collection-log completion — plus boss kills and raid
  chests (via loot events) — that it may be worth a roll in the tracker.
- **Roll suggestions in the web app.** With online sync on, quest, diary and
  combat-achievement completions are also pushed to the tracker, where they appear as a toast and a
  persistent list in the **Sync & Roll** tab with a *Take me there* jump to
  the right roll — the plugin never rolls on your behalf.
- **Over-tier gear warning.** Warns (chat + HUD) when you're wearing an item
  above your unlocked equipment tier for that slot.
- **Locked slayer-task warning.** Warns (chat + HUD) when your assigned slayer
  monster only lives in chunks you haven't unlocked.
- **Unlock progress.** A HUD line with how many areas you've unlocked and the
  percentage of authored chunks opened.
- **Locked right-click tags.** Menu entries for NPCs, objects, ground items and
  walks that target a locked chunk get a red **(LOCKED)** tag appended — the
  warning arrives *before* you click, where feasible.
- **Locked-entry alarm.** Crossing into locked territory plays an audio cue and
  pulses a red border around the viewport for ~1.6s.
- **Account binding.** A run is bound to one OSRS account in the web app
  (Auto-Roll). The HUD shows that account — green when it matches the logged-in
  character, red ⚠ when it doesn't — and a one-time chat warning fires per login
  if you're on the wrong character.
- **Side panel.** Run stats, current location, and an **Allowed / Forbidden /
  Unknown** breakdown of every unlocked area, every authored-but-locked area,
  and how many map chunks sit in unnamed terrain.
- **Chat on chunk entry.** One-liner per chunk boundary: coords, area
  ("Falador · Asgarnia"), and status.

## Getting started

1. Install **Fate Locked Ironman** from the RuneLite Plugin Hub (in the client:
   the wrench/configuration panel → **Plugin Hub** → search "Fate Locked").
2. Open the [web tracker](https://github.com/Nubles/OSRS-Fate-Locked), author
   your chunks, and export a bundle for the plugin.
3. Load the bundle into the plugin (see below). The overlays, HUD, and warnings
   light up immediately.

### Loading a bundle

Click **RL** in the web app — it copies the bundle to your clipboard *and*
downloads it — then get it into the plugin whichever way suits you:

- **Import from clipboard** *(easiest).* Open the plugin side panel and click
  **Import from clipboard** (or bind the re-import hotkey). No file at all.
- **Paste JSON.** Paste the export into the side-panel box and click *Import
  pasted JSON*.
- **Online sync** *(optional).* In the web app, enable **Online sync** to get a
  pairing code; paste it into the plugin's **Online sync code**. Your run then
  syncs over the internet (no clipboard/files) — handy when the game and the web
  app are on different machines. Outbound-only, ephemeral (24h); see
  [CONTRIBUTING.md](CONTRIBUTING.md).
- **Drop the file in the data folder.** Move the downloaded
  `fate-locked-bundle-*.json` into `~/.runelite/fate-locked/` (`%USERPROFILE%\.runelite\fate-locked\`
  on Windows); the plugin loads the newest one there and hot-reloads on change.
  (The plugin only reads from this folder — a RuneLite plugin can't read your
  Downloads or arbitrary paths.)

## Configuration

Every behavior has a toggle in the plugin config: the HUD, screen flash, menu
tags, chat messages, sound, each overlay (world map / scene / minimap), the
unlocked/locked/unauthored colors, the wrong-account warning, and auto-reload of
the bundle file.

## Notes

- The plugin can't physically stop you walking into a locked chunk — movement is
  server-authoritative. It marks, tags, and warns; the rest is on you.
- Right-click tagging covers NPC / object / ground-item / walk targets. Widget
  and spell menu entries have no world tile, so they can't be tagged.

---

Building from source, the bundle format, and other developer notes are in
[CONTRIBUTING.md](CONTRIBUTING.md).
