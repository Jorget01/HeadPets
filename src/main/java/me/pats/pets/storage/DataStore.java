package me.pats.pets.storage;

import me.pats.pets.i18n.Language;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DataStore {

    private final Plugin plugin;
    private final File file;
    private YamlConfiguration config;

    public DataStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (config == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }

    public String getParticleId(UUID playerId, String def) {
        return config.getString("players." + playerId + ".particle", def);
    }

    public void setParticleId(UUID playerId, String particleId) {
        config.set("players." + playerId + ".particle", particleId);
    }

    public boolean getParticlesEnabled(UUID playerId) {
        return config.getBoolean("players." + playerId + ".particles_enabled", true);
    }

    public void setParticlesEnabled(UUID playerId, boolean enabled) {
        config.set("players." + playerId + ".particles_enabled", enabled);
    }

    public String getPetParticleId(UUID playerId, String petId) {
        return config.getString("players." + playerId + ".pet_particles." + petId, null);
    }

    public void setPetParticleId(UUID playerId, String petId, String particleId) {
        String path = "players." + playerId + ".pet_particles." + petId;
        if (particleId == null) {
            config.set(path, null);
        } else {
            config.set(path, particleId);
        }
    }

    public List<String> getActivePets(UUID playerId) {
        List<String> list = config.getStringList("players." + playerId + ".active_pets");
        if (list == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : list) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    public void setActivePets(UUID playerId, List<String> petIds) {
        config.set("players." + playerId + ".active_pets", petIds == null ? List.of() : petIds);
    }

    public void addActivePet(UUID playerId, String petId) {
        List<String> list = new ArrayList<>(getActivePets(playerId));
        for (String existing : list) {
            if (existing.equalsIgnoreCase(petId)) return;
        }
        list.add(petId);
        setActivePets(playerId, list);
    }

    public void removeActivePet(UUID playerId, String petId) {
        List<String> list = new ArrayList<>(getActivePets(playerId));
        boolean changed = list.removeIf(s -> s.equalsIgnoreCase(petId));
        if (changed) {
            setActivePets(playerId, list);
        }
    }

    public Language getLanguageOverride(UUID playerId) {
        String raw = config.getString("players." + playerId + ".lang", null);
        return Language.fromId(raw);
    }

    public void setLanguageOverride(UUID playerId, Language language) {
        config.set("players." + playerId + ".lang", language == null ? null : language.id());
    }
}
