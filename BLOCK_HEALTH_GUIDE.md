# 🏥 Block Health Mode - Configuration Guide

## 📌 Apa itu Block Health Mode?

**Block Health Mode** adalah mekanik mining berbasis HP di mana:
- Setiap **klik kiri** mengurangi HP block sebesar **pickaxe power** pemain
- Hologram health bar ditampilkan di atas block saat mining
- Dapat di-toggle on/off via `config.yml`

### Contoh Kalkulasi:
```
Block Health: 100 HP
Player Pickaxe Power: 5

Klik yang dibutuhkan: 100 ÷ 5 = 20 klik
```

---

## ⚙️ Konfigurasi Dasar

### 1️⃣ Enable/Disable Hologram Health

Di `config.yml`:
```yaml
block-health-hologram:
  enabled: true  # Set ke false untuk menyembunyikan hologram
```

### 2️⃣ Setup Block dengan Health Mode

Di `blocks.yml`, tambahkan `block-health: <nilai>`:

```yaml
EMERALD_ORE:
  replaced-block: STONE
  regen-delay: 20
  block-health: 80              # ← HP maksimal block
  require-pickaxe-power: 10     # ← Minimum power untuk damage
  drops:
    auto-inventory: false
    natural-drop: true
  sounds:
    break-sound: ENTITY_ITEM_PICKUP
    regen-sound: BLOCK_NOTE_BLOCK_CHIME
```

---

## 📚 Configuration Options

### Core Options

| Konfigurasi | Tipe | Penjelasan |
|---|---|---|
| `block-health` | Number | HP maksimal block. Diperlukan untuk enable health mode |
| `require-pickaxe-power` | Number | Minimum pickaxe power untuk bisa damage block (optional) |
| `replaced-block` | Material | Block sementara saat lagi regenerate |
| `regen-delay` | Number | Delay regenerate dalam detik |

### Additional Features

| Konfigurasi | Deskripsi |
|---|---|
| `tools-required` | Hanya tool spesifik yang bisa damage (MMOItems/Vanilla) |
| `regions` | Block hanya regen di region tertentu |
| `exp-drop-amount` | EXP yang di-drop (e.g., `'5-10'`) |
| `drops: auto-inventory` | Drop langsung ke inventory atau drop di ground |
| `sounds` | Custom sound saat break/regen |
| `particles` | Custom particle effect |
| `mmocore-exp` | MMOCore profession XP reward |
| `auraskills-xp` | AuraSkills XP reward |

---

## 🎯 Real-World Examples

### ✅ EXAMPLE 1: Basic Health Block

```yaml
QUARTZ_ORE:
  replaced-block: STONE
  regen-delay: 15
  block-health: 50          # 50 HP
  drops:
    auto-inventory: false
    natural-drop: true
  exp-drop-amount: '2-4'
```

**Balancing:**
- Pickaxe Power 5 → butuh 10 klik
- Pickaxe Power 10 → butuh 5 klik

---

### ✅ EXAMPLE 2: Hard Health Block

```yaml
LAPIS_ORE:
  replaced-block: STONE
  regen-delay: 20
  block-health: 100         # 100 HP (lebih tangguh)
  require-pickaxe-power: 15 # Harus power ≥ 15
  tools-required:
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE
  drops:
    auto-inventory: true
    natural-drop: false
  custom-drops:
    lapis_dust:
      chance: 100.0
      amount: '2-5'
      material: LAPIS_LAZULI
```

---

### ✅ EXAMPLE 3: Premium Health Block (Nexo)

```yaml
'nexo:celestial_ore':
  replaced-block: STONE
  regen-delay: 60
  block-health: 250         # Sangat tangguh!
  require-pickaxe-power: 50 # Hanya pickaxe kuat
  regions:
    - premium_mining_zone
  exp-drop-amount: '15-25'
  drops:
    auto-inventory: true
    natural-drop: false
  custom-drops:
    celestial_gem:
      chance: 100.0
      amount: '1'
      material: 'nexo:celestial_gem'
  mmocore-exp:
    mining: 40
  auraskills-xp:
    mining: 35
  sounds:
    break-sound: BLOCK_AMETHYST_CLUSTER_BREAK
    regen-sound: ENTITY_LIGHTNING_BOLT_THUNDER
```

---

## 🧪 Testing Block Health

1. **Enable health mode:**
   ```yaml
   block-health-hologram:
     enabled: true
   ```

2. **Set block di blocks.yml:**
   ```yaml
   DIAMOND_ORE:
     block-health: 100
     require-pickaxe-power: 20
   ```

3. **Reload config:**
   ```
   /blockregen reload
   ```

4. **Test mining:**
   - Klik kiri block dengan pickaxe
   - Hologram health bar harusnya muncul
   - Lihat HP berkurang setiap klik

---

## ⚠️ Tips & Troubleshooting

### ❌ Hologram tidak muncul?
- Pastikan `block-health-hologram.enabled: true` di config.yml
- Pastikan block punya `block-health: <nilai>` di blocks.yml
- Coba `/blockregen reload`

### ❌ Block terlalu mudah/sulit?
Adjust `block-health` value:
- **Mudah:** 30-50 HP
- **Medium:** 60-100 HP
- **Hard:** 150-250 HP

### ❌ Player power terlalu rendah?
Set `require-pickaxe-power: 0` atau remove option, agar semua orang bisa damage.

---

## 📊 Performance Notes

- **Block Health Hologram** menggunakan ArmorStand (performa bagus)
- Hologram auto-cleaned setelah 3 detik tanpa hit
- Multi-player mining supported via touch-limit system

---

## 🔗 Related Settings

**Di `config.yml`:**
```yaml
options:
  block-health:
    reset-timeout-ms: 5000     # Timeout HP reset (5 detik)
    default-damage: 1.0        # Default damage per hit jika no pickaxe power

block-health-hologram:
  enabled: true                # Toggle hologram display
```

---

Generated: 2026-05-08
Plugin: BlockRegen v1.0.0+

