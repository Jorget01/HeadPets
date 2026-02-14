package me.pats.pets.config;

import me.pats.pets.i18n.I18n;
import me.pats.pets.i18n.Language;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class PluginSettings {

    private final LanguageSettings language;
    private final MenuSettings menu;
    private final FollowSettings follow;
    private final ParticleSettings particles;
    private final EffectSettings effects;
    private final PetsSettings pets;

    public PluginSettings(LanguageSettings language, MenuSettings menu, FollowSettings follow, ParticleSettings particles, EffectSettings effects, PetsSettings pets) {
        this.language = language;
        this.menu = menu;
        this.follow = follow;
        this.particles = particles;
        this.effects = effects;
        this.pets = pets;
    }

    public LanguageSettings language() {
        return language;
    }

    public MenuSettings menu() {
        return menu;
    }

    public FollowSettings follow() {
        return follow;
    }

    public ParticleSettings particles() {
        return particles;
    }

    public EffectSettings effects() {
        return effects;
    }

    public PetsSettings pets() {
        return pets;
    }

    public static PluginSettings load(Plugin plugin) {
        ConfigurationSection root = plugin.getConfig();

        ConfigurationSection langSec = root.getConfigurationSection("settings.language");
        String modeRaw = langSec == null ? "auto" : langSec.getString("mode", "auto");
        I18n.LanguageMode mode = "fixed".equalsIgnoreCase(modeRaw) ? I18n.LanguageMode.FIXED : I18n.LanguageMode.AUTO;
        Language fixed = Language.fromId(langSec == null ? null : langSec.getString("fixed", "ru"));
        if (fixed == null) fixed = Language.RU;
        boolean allowOverride = langSec == null || langSec.getBoolean("allow-player-override", true);
        LanguageSettings language = new LanguageSettings(mode, fixed, allowOverride);

        ConfigurationSection menuSec = root.getConfigurationSection("menu");
        boolean showLocked = menuSec == null || menuSec.getBoolean("show-locked-in-all", true);
        GuiSettings gui = loadGuiSettings(plugin, menuSec == null ? null : menuSec.getConfigurationSection("gui"));
        MenuSettings menu = new MenuSettings(showLocked, gui);

        ConfigurationSection followSec = root.getConfigurationSection("follow");
        FollowSettings follow = new FollowSettings(
                followSec == null ? 2L : followSec.getLong("period-ticks", 2L),
                followSec == null ? 1.15 : followSec.getDouble("back-distance", 1.15),
                followSec == null ? 1.2 : followSec.getDouble("height", 1.2),
                followSec == null ? 2.1 : followSec.getDouble("close-distance", 2.1),
                followSec == null ? 0.6 : followSec.getDouble("step-size", 0.6),
                followSec == null ? 24.0 : followSec.getDouble("teleport-distance", 24.0)
        );

        ConfigurationSection particlesSec = root.getConfigurationSection("particles");
        boolean enabled = particlesSec == null || particlesSec.getBoolean("enabled", true);
        String visibility = particlesSec == null ? "all" : particlesSec.getString("visibility", "all");
        ParticleVisibility vis = "owner".equalsIgnoreCase(visibility) ? ParticleVisibility.OWNER : ParticleVisibility.ALL;

        double yOffset = particlesSec == null ? 1.0 : particlesSec.getDouble("y-offset", 1.0);
        boolean perPetSelection = particlesSec == null || particlesSec.getBoolean("per-pet-selection", true);
        boolean allowPlayerDisable = particlesSec == null || particlesSec.getBoolean("allow-player-disable", true);

        long delayTicks = particlesSec == null ? 10L : particlesSec.getLong("delay-ticks", 10L);
        long periodTicks = particlesSec == null ? 6L : particlesSec.getLong("period-ticks", 6L);
        int count = particlesSec == null ? 2 : particlesSec.getInt("count", 2);
        double offX = particlesSec == null ? 0.25 : particlesSec.getDouble("offset-x", 0.25);
        double offY = particlesSec == null ? 0.25 : particlesSec.getDouble("offset-y", 0.25);
        double offZ = particlesSec == null ? 0.25 : particlesSec.getDouble("offset-z", 0.25);
        double extra = particlesSec == null ? 0.0 : particlesSec.getDouble("extra", 0.0);

        List<ParticleOption> options = new ArrayList<>();
        List<Map<?, ?>> list = particlesSec == null ? List.of() : particlesSec.getMapList("options");
        for (Map<?, ?> raw : list) {
            String id = Objects.toString(raw.get("id"), "").trim();
            String particleName = Objects.toString(raw.get("particle"), "").trim();
            if (id.isEmpty() || particleName.isEmpty()) continue;
            Particle particle;
            try {
                particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown particle in config: " + particleName + " (id=" + id + ")");
                continue;
            }

            String nameRu = null;
            String nameEn = null;
            Object displayObj = raw.get("display");
            if (displayObj instanceof Map<?, ?> display) {
                nameRu = Objects.toString(display.get("ru"), null);
                nameEn = Objects.toString(display.get("en"), null);
            }
            if (nameRu == null || nameRu.isBlank()) nameRu = id;
            if (nameEn == null || nameEn.isBlank()) nameEn = id;

            options.add(new ParticleOption(id, particle, nameRu, nameEn));
        }
        if (options.isEmpty()) {
            options.add(new ParticleOption("end_rod", Particle.END_ROD, "Эндер-искра", "End Rod"));
        }

        ParticleSettings particles = new ParticleSettings(enabled, vis, yOffset, perPetSelection, allowPlayerDisable, delayTicks, periodTicks, count, offX, offY, offZ, extra, options);

        ConfigurationSection effectsSec = root.getConfigurationSection("effects");
        EffectSettings effects = new EffectSettings(
                effectsSec == null || effectsSec.getBoolean("enabled", true),
                effectsSec == null ? 40L : effectsSec.getLong("period-ticks", 40L),
                effectsSec == null ? 80 : effectsSec.getInt("duration-ticks", 80),
                effectsSec == null || effectsSec.getBoolean("remove-on-deactivate", true)
        );

        ConfigurationSection petsSec = root.getConfigurationSection("pets-settings");
        int maxActive = petsSec == null ? 1 : petsSec.getInt("max-active-per-player", 1);
        if (maxActive < 1) maxActive = 1;
        PetsSettings pets = new PetsSettings(maxActive);

        return new PluginSettings(language, menu, follow, particles, effects, pets);
    }

    public record LanguageSettings(I18n.LanguageMode mode, Language fixed, boolean allowPlayerOverride) {}

    public record MenuSettings(boolean showLockedInAll, GuiSettings gui) {}

    public record GuiSettings(
            int size,
            boolean frameEnabled,
            Material frameMaterial,
            GuiSlots slots,
            GuiItems items,
            GuiGrid grid
    ) {}

    public record GuiSlots(int tabAll, int tabMine, int particles, int pagePrev, int pageInfo, int pageNext) {}

    public record GuiItems(
            Material tabSelected,
            Material tabUnselected,
            Material particles,
            Material nav,
            Material pageInfo,
            Material empty
    ) {}

    public record GuiGrid(int startRow, int startCol, int rows, int cols) {
        public int pageSize() {
            return Math.max(0, rows) * Math.max(0, cols);
        }
    }

    public record FollowSettings(long periodTicks, double backDistance, double height, double closeDistance, double stepSize, double teleportDistance) {}

    public record ParticleSettings(
            boolean enabled,
            ParticleVisibility visibility,
            double yOffset,
            boolean perPetSelection,
            boolean allowPlayerDisable,
            long delayTicks,
            long periodTicks,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra,
            List<ParticleOption> options
    ) {}

    public record EffectSettings(boolean enabled, long periodTicks, int durationTicks, boolean removeOnDeactivate) {}

    public record PetsSettings(int maxActivePerPlayer) {}

    public enum ParticleVisibility {
        ALL,
        OWNER
    }

    public record ParticleOption(String id, Particle particle, String nameRu, String nameEn) {
        public String displayName(Language lang) {
            return lang == Language.RU ? nameRu : nameEn;
        }
    }

    private static GuiSettings loadGuiSettings(Plugin plugin, ConfigurationSection guiSec) {
        int size = guiSec == null ? 54 : guiSec.getInt("size", 54);
        if (size % 9 != 0) size = 54;
        size = Math.min(54, Math.max(9, size));

        ConfigurationSection frame = guiSec == null ? null : guiSec.getConfigurationSection("frame");
        boolean frameEnabled = frame == null || frame.getBoolean("enabled", true);
        Material frameMaterial = material(plugin, frame == null ? null : frame.getString("material"), Material.GRAY_STAINED_GLASS_PANE);

        ConfigurationSection slots = guiSec == null ? null : guiSec.getConfigurationSection("slots");
        GuiSlots guiSlots = new GuiSlots(
                clampSlot(slots == null ? 0 : slots.getInt("tab-all", 0), size),
                clampSlot(slots == null ? 1 : slots.getInt("tab-mine", 1), size),
                clampSlot(slots == null ? 49 : slots.getInt("particles", 49), size),
                clampSlot(slots == null ? 45 : slots.getInt("page-prev", 45), size),
                clampSlot(slots == null ? 51 : slots.getInt("page-info", 51), size),
                clampSlot(slots == null ? 53 : slots.getInt("page-next", 53), size)
        );

        ConfigurationSection items = guiSec == null ? null : guiSec.getConfigurationSection("items");
        GuiItems guiItems = new GuiItems(
                material(plugin, items == null ? null : items.getString("tab-selected"), Material.LIME_STAINED_GLASS_PANE),
                material(plugin, items == null ? null : items.getString("tab-unselected"), Material.BLUE_STAINED_GLASS_PANE),
                material(plugin, items == null ? null : items.getString("particles"), Material.BLAZE_POWDER),
                material(plugin, items == null ? null : items.getString("nav"), Material.ARROW),
                material(plugin, items == null ? null : items.getString("page-info"), Material.PAPER),
                material(plugin, items == null ? null : items.getString("empty"), Material.PAPER)
        );

        ConfigurationSection grid = guiSec == null ? null : guiSec.getConfigurationSection("grid");
        int startRow = grid == null ? 2 : grid.getInt("start-row", 2);
        int startCol = grid == null ? 2 : grid.getInt("start-col", 2);
        int rows = grid == null ? 4 : grid.getInt("rows", 4);
        int cols = grid == null ? 7 : grid.getInt("cols", 7);
        startRow = clamp(startRow, 1, size / 9);
        startCol = clamp(startCol, 1, 9);
        rows = clamp(rows, 1, (size / 9) - (startRow - 1));
        cols = clamp(cols, 1, 9 - (startCol - 1));
        GuiGrid guiGrid = new GuiGrid(startRow, startCol, rows, cols);

        return new GuiSettings(size, frameEnabled, frameMaterial, guiSlots, guiItems, guiGrid);
    }

    private static Material material(Plugin plugin, String raw, Material def) {
        if (raw == null || raw.isBlank()) return def;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown material in config: " + raw + " (using " + def + ")");
            return def;
        }
    }

    private static int clampSlot(int slot, int size) {
        return clamp(slot, 0, size - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
