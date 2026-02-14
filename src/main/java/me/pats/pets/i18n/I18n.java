package me.pats.pets.i18n;

import me.pats.pets.storage.DataStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class I18n {

    private final Plugin plugin;
    private final DataStore dataStore;
    private final LanguageMode mode;
    private final Language fixed;
    private final boolean allowPlayerOverride;

    private final Map<Language, YamlConfiguration> bundles = new HashMap<>();

    public I18n(Plugin plugin, DataStore dataStore, LanguageMode mode, Language fixed, boolean allowPlayerOverride) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.mode = mode;
        this.fixed = fixed;
        this.allowPlayerOverride = allowPlayerOverride;
    }

    public void load() {
        ensureLangFile("lang/ru.yml");
        ensureLangFile("lang/en.yml");
        bundles.put(Language.RU, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/ru.yml")));
        bundles.put(Language.EN, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/en.yml")));
    }

    public Language language(Player player) {
        if (allowPlayerOverride && player != null) {
            Language override = dataStore.getLanguageOverride(player.getUniqueId());
            if (override != null) return override;
        }
        return mode == LanguageMode.FIXED ? fixed : Language.fromPlayerLocale(player);
    }

    public String tr(Player player, String key) {
        return tr(player, key, Collections.emptyMap());
    }

    public String tr(Player player, String key, Map<String, String> placeholders) {
        Language lang = language(player);
        YamlConfiguration cfg = bundles.getOrDefault(lang, bundles.get(Language.EN));
        String raw = cfg == null ? null : cfg.getString(key);
        if (raw == null) raw = key;
        if (placeholders.isEmpty()) return raw;
        String out = raw;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", Objects.toString(e.getValue(), ""));
        }
        return out;
    }

    private void ensureLangFile(String resourcePath) {
        File out = new File(plugin.getDataFolder(), resourcePath);
        if (out.exists()) return;
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        plugin.saveResource(resourcePath, false);
    }

    public enum LanguageMode {
        AUTO,
        FIXED
    }
}

