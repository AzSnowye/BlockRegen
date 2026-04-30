package me.allync.blockregen.util;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility untuk mengintegrasikan Model Engine dengan BlockRegen.
 *
 * Mode normal  : model muncul di atas blok aslinya (blok tetap ada).
 * Mode hide-block: blok asli disimpan lalu diganti AIR; model menjadi visual
 *                  satu-satunya.
 */
public class ModelEngineUtil {

    private static final String METADATA_KEY = "blockregen_model_entity";

    /** Lokasi blok (key) → UUID entity base. */
    private static final Map<String, UUID> activeModels = new HashMap<>();

    /**
     * Lokasi blok (key) → BlockState asli sebelum di-hide.
     * Hanya terisi jika hide-block=true.
     */
    private static final Map<String, BlockState> hiddenBlockStates = new HashMap<>();

    /** UUID entity base → lokasi blok (key). */
    private static final Map<UUID, String> entityToBlockKey = new HashMap<>();

    private static JavaPlugin pluginInstance;

    public static void init(JavaPlugin plugin) {
        pluginInstance = plugin;
    }

    // -------------------------------------------------------------------------
    // Spawn
    // -------------------------------------------------------------------------

    /**
     * Spawn model di lokasi blok.
     *
     * @param location     Lokasi blok (koordinat integer)
     * @param modelId      ID blueprint Model Engine
     * @param yawOffset    Rotasi horizontal (derajat)
     * @param heightOffset Offset Y di atas blok
     * @param hideBlock    Jika true, blok asli diganti AIR dan state-nya disimpan
     */
    public static void spawnModel(Location location, String modelId,
                                  float yawOffset, double heightOffset,
                                  boolean hideBlock) {
        if (pluginInstance == null || location == null || location.getWorld() == null
                || modelId == null || modelId.isEmpty()) {
            return;
        }

        String key = locationKey(location);

        // Hapus model lama di lokasi ini terlebih dahulu (WAJIB sebelum spawn baru)
        removeModelByKey(key, location.getWorld());

        try {
            // --- Hide block jika diminta ---
            if (hideBlock) {
                BlockState original = location.getBlock().getState();
                hiddenBlockStates.put(key, original);
                location.getBlock().setType(org.bukkit.Material.AIR, false);
            }

            // Posisi spawn entity: tengah blok + offset Y
            Location spawnLoc = location.clone().add(0.5, heightOffset, 0.5);
            spawnLoc.setYaw(yawOffset);

            // Spawn ArmorStand tersembunyi sebagai base entity
            ArmorStand base = (ArmorStand) location.getWorld()
                    .spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            base.setInvisible(true);
            base.setGravity(false);
            base.setInvulnerable(false);
            base.setSmall(false);
            base.setArms(false);
            base.setBasePlate(false);
            base.setSilent(true);
            base.setCollidable(false);
            base.setPersistent(false); // Tidak disimpan ke disk — mencegah duplikat saat reload
            base.addScoreboardTag("blockregen_model");
            base.setMetadata(METADATA_KEY, new FixedMetadataValue(pluginInstance, key));

            // Buat ModeledEntity & tambahkan model
            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(base);
            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) {
                pluginInstance.getLogger().warning(
                        "[BlockRegen-ModelEngine] Model '" + modelId
                                + "' tidak ditemukan! Pastikan blueprint sudah diinstall.");
                base.remove();
                if (hideBlock) {
                    BlockState saved = hiddenBlockStates.remove(key);
                    if (saved != null) saved.update(true, false);
                }
                return;
            }

            modeledEntity.addModel(activeModel, true);

            // Simpan mapping SETELAH semua berhasil
            activeModels.put(key, base.getUniqueId());
            entityToBlockKey.put(base.getUniqueId(), key);

        } catch (Throwable t) {
            pluginInstance.getLogger().warning(
                    "[BlockRegen-ModelEngine] Gagal spawn model '" + modelId + "': " + t.getMessage());
        }
    }

    /** Overload tanpa hideBlock (default: false). */
    public static void spawnModel(Location location, String modelId,
                                  float yawOffset, double heightOffset) {
        spawnModel(location, modelId, yawOffset, heightOffset, false);
    }

    /** Overload minimal. */
    public static void spawnModel(Location location, String modelId) {
        spawnModel(location, modelId, 0f, 0.0, false);
    }

    // -------------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------------

    /** Hapus model di lokasi tersebut. */
    public static void removeModel(Location location) {
        if (location == null || location.getWorld() == null) return;
        removeModelByKey(locationKey(location), location.getWorld());
    }

    /**
     * Implementasi internal penghapusan model.
     * Menggunakan World.getEntity(UUID) yang O(1) — jauh lebih cepat dan reliable
     * dibanding stream seluruh entity list.
     */
    private static void removeModelByKey(String key, World world) {
        UUID entityUuid = activeModels.remove(key);

        if (entityUuid != null) {
            entityToBlockKey.remove(entityUuid);
            // UUID diketahui — langsung hapus O(1), tidak perlu scan
            destroyEntityByUUID(world, entityUuid);
        } else if (world != null) {
            // Tidak ada UUID di map (ghost entity) — fallback scan metadata
            // Hanya terjadi jika entity di-spawn di luar sistem kita
            removeGhostAtKey(key, world);
        }
    }

    /** Hapus ghost ArmorStand berdasarkan metadata key (fallback, dipanggil hanya jika UUID tidak diketahui). */
    private static void removeGhostAtKey(String key, World world) {
        for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
            if (!entity.getScoreboardTags().contains("blockregen_model")) continue;
            if (!entity.hasMetadata(METADATA_KEY)) continue;
            String storedKey = entity.getMetadata(METADATA_KEY).get(0).asString();
            if (!key.equals(storedKey)) continue;
            try {
                ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
                if (me != null) me.destroy();
            } catch (Throwable ignored) {}
            entity.remove();
        }
    }

    /** Hapus entity berdasarkan UUID, termasuk Model Engine destroy. */
    private static void destroyEntityByUUID(World world, UUID uuid) {
        try {
            Entity entity = world.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                try {
                    ModeledEntity me = ModelEngineAPI.getModeledEntity(uuid);
                    if (me != null) me.destroy();
                } catch (Throwable ignored) {}
                entity.remove();
            }
        } catch (Throwable t) {
            if (pluginInstance != null) {
                pluginInstance.getLogger().warning(
                        "[BlockRegen-ModelEngine] Gagal hapus entity " + uuid + ": " + t.getMessage());
            }
        }
    }

    /**
     * Kembalikan blok asli yang disembunyikan.
     * Menghapus entry dari hiddenBlockStates.
     */
    public static void restoreHiddenBlock(Location location) {
        if (location == null) return;
        String key = locationKey(location);
        BlockState saved = hiddenBlockStates.remove(key);
        if (saved != null) {
            saved.update(true, false);
        }
    }

    /**
     * Hapus semua model aktif (plugin disable / cleanall command).
     * Kembalikan semua blok yang disembunyikan.
     */
    public static void removeAll() {
        if (pluginInstance == null) return;

        // Hapus semua model yang terdaftar
        new HashMap<>(activeModels).forEach((key, uuid) -> {
            try {
                Location loc = keyToLocation(key);
                if (loc != null && loc.getWorld() != null) {
                    destroyEntityByUUID(loc.getWorld(), uuid);
                }
            } catch (Throwable ignored) {}
        });

        // Kembalikan semua hidden blocks
        new HashMap<>(hiddenBlockStates).forEach((key, state) -> {
            try {
                state.update(true, false);
            } catch (Throwable ignored) {}
        });

        // Scan semua world untuk ghost model entities
        for (World world : Bukkit.getWorlds()) {
            try {
                for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                    if (!entity.getScoreboardTags().contains("blockregen_model")) continue;
                    try {
                        ModeledEntity me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
                        if (me != null) me.destroy();
                    } catch (Throwable ignored) {}
                    entity.remove();
                }
            } catch (Throwable ignored) {}
        }

        activeModels.clear();
        hiddenBlockStates.clear();
        entityToBlockKey.clear();
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    public static boolean hasModel(Location location) {
        return location != null && activeModels.containsKey(locationKey(location));
    }

    public static boolean isHiddenBlock(Location location) {
        return location != null && hiddenBlockStates.containsKey(locationKey(location));
    }

    public static BlockState getHiddenBlockState(Location location) {
        if (location == null) return null;
        return hiddenBlockStates.get(locationKey(location));
    }

    public static Location getBlockLocationFromEntity(UUID entityUuid) {
        if (entityUuid == null) return null;
        String key = entityToBlockKey.get(entityUuid);
        return (key != null) ? keyToLocation(key) : null;
    }

    public static int getActiveModelCount() {
        return activeModels.size();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String locationKey(Location loc) {
        return loc.getWorld().getName() + ":"
                + loc.getBlockX() + ":"
                + loc.getBlockY() + ":"
                + loc.getBlockZ();
    }

    private static Location keyToLocation(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 4) return null;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (Throwable ignored) {
            return null;
        }
    }
}
