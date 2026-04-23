# 🪨 BlockRegen

**BlockRegen** is a highly optimized and configurable block regeneration plugin for Paper/Spigot servers (1.21+), built to support vanilla, custom, and third-party plugin blocks out of the box.

---

## ✨ Features

### 🔄 Block Regeneration
- Broken blocks automatically regenerate after a configurable delay (`regen-delay`).
- Choose the block that temporarily replaces the broken block (`replaced-block`).
- Support for **weighted random regen variants** — a block can regenerate into a different block type based on configurable chances (e.g., `agate_ore` → `agate_deepslate`).

---

### 🧱 Block Support
- ✅ **Vanilla blocks** — any standard Minecraft material (e.g., `COAL_ORE`, `DIAMOND_ORE`).
- ✅ **ItemsAdder blocks** — referenced via `itemsadder:namespace:id` format.
- ✅ **Nexo blocks** — referenced via `nexo:id` format.

---

### 💎 Drop System
- **Natural drops** — let Minecraft handle drops natively (respects Fortune, Silk Touch, etc.).
- **Custom drops** — define your own drops with configurable:
  - `chance` — drop probability (%).
  - `amount` — fixed or random range (e.g., `1-3`).
  - `material` — vanilla, ItemsAdder, MMOItems, or Nexo items.
  - `name` & `lore` — custom display name and lore.
- **Auto-inventory** — send drops directly to player inventory instead of dropping on the ground.
- **Fortune support** — configure per-fortune-level drop multipliers.

---

### 🗃️ Custom Item Integration (Drops)
BlockRegen supports dropping items from multiple custom item plugins:

| Plugin | Format | Example |
|---|---|---|
| **Vanilla** | `MATERIAL` | `IRON_INGOT` |
| **ItemsAdder** | `itemsadder:namespace:id` | `itemsadder:gold_coin` |
| **MMOItems** | `mmoitems:TYPE:ID` | `mmoitems:PICKAXE:LEGENDARY_PICKAXE` |
| **Nexo** | `nexo:id` | `nexo:agate_gem` |

---

### ⚒️ Tool Requirements
- Restrict which tools can break a block via `tools-required`.
- Supports **vanilla tools** (e.g., `DIAMOND_PICKAXE`) and **MMOItems tools** (e.g., `mmoitems:PICKAXE:RUSTED_PICKAXE`).
- Optionally require a tool with a **specific name and lore** for ultra-precise gating.
- Players attempting to break with wrong tools receive a configurable message and sound.

---

### ⏱️ Custom Break Duration
- Set a custom time (in seconds) required to break a block via `break-duration`.
- **Fixed duration mode** — ignores all speed bonuses including Efficiency, Haste, and MMOItems mining speed.
- Visual mining progress bar shown as a **floating hologram** above the block while mining.
- Anti-hold-mine detection: cancels mining progress if the player holds a click without moving the crosshair.
- **Multi-touch limit**: prevent players from making progress on too many blocks at once (configurable per-player via permission node `blockregen.multiplier.block.<number>`).
- **Resume mining**: players (or others) can resume paused mining progress within a configurable grace window.

---

### 🎵 Sounds & Particles
Per-block or global customization of:
- **Break sound** — played when a block is broken.
- **Regen sound** — played when a block regenerates.
- **Wrong-tool sound** — played when attempting to break with incorrect tool.
- **Break particles** — particle effect on break.
- **Regen particles** — particle effect on regen.

---

### 💬 Commands on Break
Run commands automatically when a block is broken. Supports three executors:
- `[CONSOLE]` — run as console.
- `[PLAYER]` — run as the player.
- `[OP]` — run temporarily as OP.

Placeholder `%player%` is available in all commands. Per-drop commands are also supported.

---

### ✨ EXP & Skill Integration
- Drop configurable experience points (`exp-drop-amount`, e.g., `1-5`).
- Choose between **auto-pickup EXP** (given directly) or **EXP orbs** dropped in world.
- **MMOCore** profession EXP support — award profession-specific XP (e.g., `mining: 15`).
- **AuraSkills** XP support — award AuraSkills skill XP on break.

---

### 🗺️ Region System (Built-in)
- Define custom **block regions** using a selection wand (default: Blaze Rod).
- Left-click to set Position 1, right-click to set Position 2.
- Save, list, and delete regions with `/blockregen` commands.
- Restrict specific block configs to only regenerate inside certain regions via `regions:` list in `blocks.yml`.
- Block broken outside its defined region will not regenerate (with optional admin notification).

---

### 🌍 WorldGuard Integration *(Optional)*
- Respects WorldGuard `block-break` flags.
- `disable-other-break` — prevent breaking non-regen blocks inside protected regions.
- `break-regen-in-deny-regions` — allow mining regen blocks even in DENY regions (useful for protected farming zones).

---

### 📈 Drop Multiplier System
- Players can upgrade their **drop multiplier** through a configurable GUI (`/regenmultiplier` or `/rm`).
- Multiplier profiles — define separate upgrade trees per world (e.g., `default`, `prison`).
- **Multiple currency support** — Vault (money) or CoinsEngine (custom coin types).
- Configurable upgrade costs per level.
- Configurable multiplier values per level (up to any max level).
- Admin command to set a player's multiplier level manually: `/rm set <player> <profile> <level>`.

---

### 🪨 Random Ore Spawn System *(Anti-AFK)*
- Dynamically spawn blocks at pre-defined points on a cycle (`interval-seconds`).
- Configurable **max active** blocks per region.
- **Strict relocate mode** — recently-broken points cannot immediately respawn, forcing players to move around.
- Register spawn points with `/regen block set <region> <block_id>`.
- Force-refresh or manually spawn blocks with admin commands.
- Debug mode to preview which points were selected in the last cycle.

---

### 🔔 Update Notifications
- Checks for new versions on startup.
- Configurable notification message with placeholders: `%latest_version%`, `%current_version%`, `%download_link%`.
- Fully toggleable via `check-for-updates: false`.

---

## 🔌 Soft Dependencies

| Plugin | Purpose |
|---|---|
| **WorldGuard** | Region-based block-break rules |
| **ItemsAdder** | Custom block & drop support |
| **Nexo** | Custom block & drop support |
| **MMOItems** | Custom tool requirements & drops |
| **MMOCore** | Profession EXP rewards |
| **AuraSkills** | Skill XP rewards |
| **Vault** | Economy support for multiplier upgrades |
| **CoinsEngine** | Alternative currency for multiplier upgrades |
| **FancyHolograms** | Enhanced hologram display compatibility |

> All integrations are optional — BlockRegen works fully without any of them.

---

## 📋 Commands

| Command | Description | Permission |
|---|---|---|
| `/blockregen reload` | Reload all configuration files | `blockregen.admin` |
| `/blockregen wand` | Receive the region selection wand | `blockregen.admin` |
| `/blockregen save <name>` | Save the selected region | `blockregen.admin` |
| `/blockregen remove <name>` | Remove a saved region | `blockregen.admin` |
| `/regen block set <region> <block_id>` | Add a random ore spawn point | `blockregen.admin` |
| `/regen block remove <region> <block_id>` | Remove a random ore spawn point | `blockregen.admin` |
| `/regen block refresh [region\|all]` | Immediately refresh random ore positions | `blockregen.admin` |
| `/regen block spawn <region> <block_id> <count>` | Force spawn random blocks | `blockregen.admin` |
| `/regen block debug <region>` | View last cycle's selected points | `blockregen.admin` |
| `/regenmultiplier` or `/rm` | Open the multiplier upgrade GUI | `blockregen.multiplier.use` |
| `/rm set <player> <profile> <level>` | Manually set a player's multiplier | `blockregen.multiplier.admin` |

**Aliases:** `/blockregen` → `/br`, `/regen` | `/regenmultiplier` → `/rm`

---

## 🔑 Permissions

| Permission | Description | Default |
|---|---|---|
| `blockregen.admin` | Access to all admin commands | OP |
| `blockregen.multiplier.use` | Open the multiplier GUI | `true` |
| `blockregen.multiplier.admin` | Use `/rm set` admin command | OP |
| `blockregen.multiplier.block.<n>` | Increases block touch limit by `n` | `false` |

---

## ⚙️ Configuration Files

| File | Description |
|---|---|
| `config.yml` | Global settings: sounds, particles, worlds, WorldGuard, messages, mining options |
| `blocks.yml` | Per-block regeneration rules, drops, tools, sounds, particles, and commands |
| `multiplier.yml` | Multiplier GUI layout, upgrade costs, and per-world profiles |
| `random-ores.yml` | Random ore spawn system settings and registered region points |
| `regions.yml` | Saved region coordinates |

---

## 🧩 Requirements

- **Server:** Paper (or Spigot) 1.21+
- **Java:** 21+
