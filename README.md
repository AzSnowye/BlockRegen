# BlockRegen

BlockRegen is a highly optimized and configurable Minecraft block regeneration plugin built for modern Paper/Spigot servers. It lets you turn selected blocks into regenerating resource nodes with custom drops, custom mining behavior, configurable regions, and optional economy-based multiplier upgrades.

## Features

- Regenerating blocks inside configured regions
- Per-block regeneration settings in `blocks.yml`
- Support for vanilla drops, custom vanilla items, ItemsAdder items, and MMOItems drops
- Optional tool requirements for breaking specific blocks
- Optional custom break duration with fixed mining time
- Auto inventory support for drops
- Configurable EXP rewards and auto-pickup EXP
- Optional MMOCore profession EXP rewards
- Configurable sounds, particles, and command execution on break
- WorldGuard integration for protected mining areas
- Per-world multiplier profiles with upgrade GUI
- Vault and CoinsEngine support for multiplier upgrade costs
- Debug mode and bypass mode for administrators

## Supported Integrations

BlockRegen includes optional support for:

- WorldGuard
- ItemsAdder
- MMOItems
- MMOCore
- Vault
- CoinsEngine
- HarvestFlow compatibility mode

## Commands

### Main Commands

- `/blockregen help`
- `/blockregen reload`
- `/blockregen wand`
- `/blockregen save <region>`
- `/blockregen remove <region>`
- `/blockregen debug`
- `/blockregen bypass`

Aliases:

- `/br`
- `/regen`

### Multiplier Commands

- `/regenmultiplier`
- `/regenmultiplier set <player> <profile> <level>`

Aliases:

- `/rm`

## Permissions

- `blockregen.admin` - Access to all main admin commands
- `blockregen.multiplier.use` - Open the multiplier GUI
- `blockregen.multiplier.admin` - Manage player multiplier levels with admin commands

## Configuration Files

### `config.yml`
Main plugin settings:

- wand material and display name
- default sounds
- enabled worlds
- per-world multiplier profile assignment
- update checker
- inventory-full prevention
- WorldGuard options
- MMOCore options
- particle settings
- all configurable messages

### `blocks.yml`
Defines each regenerating block, including:

- replacement block while regenerating
- regeneration delay
- required tools
- custom break duration
- natural drops
- custom drops
- fortune multipliers
- auto inventory
- vanilla EXP and MMOCore EXP
- sounds and particles
- commands executed on break

### `regions.yml`
Stores all saved regeneration regions.

### `multiplier.yml`
Controls the regen multiplier system:

- GUI layout and item settings
- success and error messages
- multiplier profiles
- upgrade costs
- drop multiplier values
- economy type per profile using Vault or CoinsEngine

### `playerdata/`
Stores player multiplier progress by UUID.

## How It Works

1. Define allowed worlds and options in `config.yml`
2. Configure regenerating blocks in `blocks.yml`
3. Create mining regions in-game with the wand
4. Save the region with `/br save <name>`
5. Players mine configured blocks
6. BlockRegen handles drops, effects, commands, and regeneration automatically
7. Optional multiplier upgrades can increase drop output per world profile

## Region Setup

1. Run `/br wand`
2. Left-click a block to set position 1
3. Right-click a block to set position 2
4. Run `/br save <region>`

To remove a region:

```text
/br remove <region>
```

## Example Block Configuration

```yml
DIAMOND_ORE:
  replaced-block: BEDROCK
  regen-delay: 15
  break-duration: 3.0
  fixed-duration: true
  exp-drop-amount: '3-7'
  drops:
    auto-inventory: false
    natural-drop: false
  custom-drops:
    legendary_pickaxe:
      chance: 10.0
      amount: '1'
      material: 'mmoitems:PICKAXE:LEGENDARY_PICKAXE'
  mmocore-exp:
    mining: 15
```

## Multiplier System

The multiplier system is profile-based and can be assigned per world in `config.yml`.

Example use cases:

- a default economy profile using Vault
- a prison profile using CoinsEngine tokens
- different upgrade caps and multiplier values for different worlds

Players can open the multiplier GUI with:

```text
/rm
```

Admins can directly set levels with:

```text
/rm set <player> <profile> <level>
```

## Building

This project uses Maven.

```bash
mvn clean package
```

## Notes

- If no BlockRegen regions are defined, regeneration will not be allowed anywhere.
- `blocks.yml` supports both vanilla material names and namespaced custom IDs.
- Custom break duration is handled through a dedicated mining listener for fixed-duration blocks.
- Multiplier data is saved per player.

## License

No license file is currently provided in this repository. Add one if you want to define usage permissions clearly.
