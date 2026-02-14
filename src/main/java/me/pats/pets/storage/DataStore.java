package me.pats.pets.storage;

import me.pats.pets.i18n.Language;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
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

    public Language getLanguageOverride(UUID playerId) {
        String raw = config.getString("players." + playerId + ".lang", null);
        return Language.fromId(raw);
    }

    public void setLanguageOverride(UUID playerId, Language language) {
        config.set("players." + playerId + ".lang", language == null ? null : language.id());
    }
}
