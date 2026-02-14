package me.pats.pets.menu;

import me.pats.pets.config.PluginSettings;
import me.pats.pets.i18n.I18n;
import me.pats.pets.i18n.Language;
import me.pats.pets.pets.Pet;
import me.pats.pets.pets.PetManager;
import me.pats.pets.pets.PetRegistry;
import me.pats.pets.storage.DataStore;
import me.pats.pets.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class PetsMenu implements Listener {

    private final Plugin plugin;
    private final PetManager petManager;
    private final DataStore dataStore;
    private final PluginSettings settings;
    private final I18n i18n;
    private final PetRegistry pets;
    private final NamespacedKey petIdKey;
    private final NamespacedKey actionKey;

    public PetsMenu(Plugin plugin, PetManager petManager, DataStore dataStore, PluginSettings settings, I18n i18n, PetRegistry pets) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.dataStore = dataStore;
        this.settings = settings;
        this.i18n = i18n;
        this.pets = pets;
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.actionKey = new NamespacedKey(plugin, "menu_action");
    }

    public void open(Player player) {
        open(player, View.ALL, EffectFilter.ALL, 0);
    }

    private void open(Player player, View view, EffectFilter filter, int page) {
        player.openInventory(createInventory(player, view, filter, page));
    }

    private Inventory createInventory(Player player, View view, EffectFilter filter, int page) {
        Language lang = i18n.language(player);
        String tabTitle = i18n.tr(player, view == View.ALL ? "menu.tab.all" : "menu.tab.mine");
        String title = i18n.tr(player, "menu.title", Map.of("tab", tabTitle));

        var holder = new PetsMenuHolder(player.getUniqueId(), view, filter, page);
        int size = settings.menu().gui().size();
        var inventory = Bukkit.createInventory(holder, size, Text.parse(title).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inventory);

        paintFrame(inventory);
        var slots = settings.menu().gui().slots();
        inventory.setItem(slots.tabAll(), createTabItem(player, "menu.tab.all", view == View.ALL, "tab_all"));
        inventory.setItem(slots.tabMine(), createTabItem(player, "menu.tab.mine", view == View.MINE, "tab_mine"));
        inventory.setItem(slots.filterAll(), createFilterItem(player, lang, filter == EffectFilter.ALL, "menu.filter.all", "filter_all"));
        inventory.setItem(slots.filterWithEffects(), createFilterItem(player, lang, filter == EffectFilter.WITH_EFFECTS, "menu.filter.with_effects", "filter_with"));
        inventory.setItem(slots.filterWithoutEffects(), createFilterItem(player, lang, filter == EffectFilter.WITHOUT_EFFECTS, "menu.filter.without_effects", "filter_without"));
        inventory.setItem(slots.particles(), createParticleItem(player, lang));
        inventory.setItem(slots.pagePrev(), createNavItem(player, "menu.nav.prev", "page_prev"));
        inventory.setItem(slots.pageNext(), createNavItem(player, "menu.nav.next", "page_next"));

        List<Pet> filtered = new ArrayList<>();
        for (Pet pet : pets.all()) {
            boolean hasAccess = player.hasPermission(pet.permission());
            if (view == View.MINE && !hasAccess) continue;
            if (view == View.ALL && !settings.menu().showLockedInAll() && !hasAccess) continue;

            boolean hasEffects = !pet.passiveEffects().effects().isEmpty();
            if (filter == EffectFilter.WITH_EFFECTS && !hasEffects) continue;
            if (filter == EffectFilter.WITHOUT_EFFECTS && hasEffects) continue;

            filtered.add(pet);
        }

        int pageSize = Math.max(1, settings.menu().gui().grid().pageSize());
        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        holder.setPage(safePage);

        inventory.setItem(slots.pageInfo(), createPageItem(player, safePage + 1, pages));

        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, filtered.size());

        int slot = firstGridSlot();
        boolean any = false;
        for (int i = from; i < to; i++) {
            Pet pet = filtered.get(i);
            any = true;
            inventory.setItem(slot, createPetItem(player, pet, lang));
            slot = nextGridSlot(slot);
            if (slot == -1) break;
        }
        if (!any) {
            inventory.setItem(22, createInfoItem(player, "menu.empty.title", "menu.empty.hint"));
        }

        return inventory;
    }

    private void paintFrame(Inventory inventory) {
        var gui = settings.menu().gui();
        if (!gui.frameEnabled()) return;

        ItemStack pane = new ItemStack(gui.frameMaterial());
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            int lastRow = (inventory.getSize() / 9) - 1;
            boolean border = row == 0 || row == lastRow || col == 0 || col == 8;
            if (border) {
                inventory.setItem(i, pane);
            }
        }
    }

    private ItemStack createTabItem(Player player, String titleKey, boolean selected, String action) {
        String name = i18n.tr(player, titleKey);
        Material material = selected ? settings.menu().gui().items().tabSelected() : settings.menu().gui().items().tabUnselected();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String prefix = selected ? "&#7CFF6B" : "&#7CD9FF";
        meta.displayName(Text.parse(prefix + name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(i18n.tr(player, selected ? "menu.selected" : "menu.click_to_open"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFilterItem(Player player, Language lang, boolean selected, String titleKey, String action) {
        String name = i18n.tr(player, titleKey);
        Material material = selected ? settings.menu().gui().items().tabSelected() : settings.menu().gui().items().tabUnselected();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String prefix = selected ? "&#FFD15C" : "&#B0B0B0";
        meta.displayName(Text.parse(prefix + name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Text.parse("&8" + i18n.tr(player, "menu.click_to_open")).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(Player player, String titleKey, String action) {
        ItemStack item = new ItemStack(settings.menu().gui().items().nav());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse("&#FFFFFF" + i18n.tr(player, titleKey)).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageItem(Player player, int page, int pages) {
        ItemStack item = new ItemStack(settings.menu().gui().items().pageInfo());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse("&#C8C8C8" + i18n.tr(player, "menu.nav.page", Map.of("page", String.valueOf(page), "pages", String.valueOf(pages))))
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createParticleItem(Player player, Language lang) {
        PluginSettings.ParticleSettings ps = settings.particles();
        if (!ps.enabled() || ps.options().isEmpty()) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.parse("&#FF7CF2" + i18n.tr(player, "menu.particles.title")).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Text.parse("&7" + i18n.tr(player, "menu.particles.disabled")).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            return item;
        }

        String defId = ps.options().get(0).id();
        boolean enabled = !ps.allowPlayerDisable() || dataStore.getParticlesEnabled(player.getUniqueId());
        String chosenId = enabled ? dataStore.getParticleId(player.getUniqueId(), defId) : "off";
        PluginSettings.ParticleOption option = ps.options().stream()
                .filter(o -> o.id().equalsIgnoreCase(chosenId))
                .findFirst()
                .orElse(ps.options().get(0));

        ItemStack item = new ItemStack(settings.menu().gui().items().particles());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse("&#FF7CF2" + i18n.tr(player, "menu.particles.title")).decoration(TextDecoration.ITALIC, false));
        String currentName = enabled ? option.displayName(lang) : i18n.tr(player, "menu.particles.off");
        List<Component> lore = new ArrayList<>();
        lore.add(Text.parse("&b" + i18n.tr(player, "menu.particles.current", Map.of("name", currentName))).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Text.parse("&e" + i18n.tr(player, "menu.particles.left")).decoration(TextDecoration.ITALIC, false));
        lore.add(Text.parse("&e" + i18n.tr(player, "menu.particles.right")).decoration(TextDecoration.ITALIC, false));
        if (ps.allowPlayerDisable()) {
            lore.add(Text.parse("&e" + i18n.tr(player, "menu.particles.toggle")).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "particle");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(Player player, String titleKey, String lineKey) {
        ItemStack item = new ItemStack(settings.menu().gui().items().empty());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.parse("&7" + i18n.tr(player, titleKey)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Text.parse("&8" + i18n.tr(player, lineKey)).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private int firstGridSlot() {
        var g = settings.menu().gui().grid();
        return ((g.startRow() - 1) * 9) + (g.startCol() - 1);
    }

    private int nextGridSlot(int slot) {
        var g = settings.menu().gui().grid();
        int startRow0 = g.startRow() - 1;
        int startCol0 = g.startCol() - 1;

        int row = slot / 9;
        int col = slot % 9;

        int endCol = startCol0 + g.cols() - 1;
        int endRow = startRow0 + g.rows() - 1;

        col++;
        if (col > endCol) {
            row++;
            col = startCol0;
        }
        if (row > endRow) return -1;
        return row * 9 + col;
    }

    private ItemStack createPetItem(Player player, Pet pet, Language lang) {
        boolean hasAccess = player.hasPermission(pet.permission());
        boolean active = petManager.isPetActive(player.getUniqueId(), pet.id());

        ItemStack item = pet.iconItem();
        ItemMeta meta = item.getItemMeta();
        String namePrefix = hasAccess ? "&b" : "&c";
        meta.displayName(Text.parse(namePrefix + pet.displayName(lang)).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (!hasAccess) {
            lore.add(Text.parse("&7" + i18n.tr(player, "menu.pet.locked")).decoration(TextDecoration.ITALIC, false));
            lore.add(Text.parse("&8" + pet.permission()).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Text.parse((active ? "&a" : "&7") + i18n.tr(player, active ? "menu.pet.active" : "menu.pet.inactive"))
                    .decoration(TextDecoration.ITALIC, false));

            if (!pet.passiveEffects().effects().isEmpty()) {
                lore.add(Component.empty());
                lore.add(Text.parse("&d" + i18n.tr(player, "menu.pet.effects_title")).decoration(TextDecoration.ITALIC, false));
                for (var eff : pet.passiveEffects().effects()) {
                    int level = Math.max(1, eff.amplifier() + 1);
                    lore.add(
                            Component.text(" - ", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false)
                                    .append(Component.translatable(eff.type()).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
                                    .append(Component.text(" " + toRoman(level), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
                    );
                }
            }

            if (settings.particles().enabled() && settings.particles().perPetSelection() && !settings.particles().options().isEmpty()) {
                PluginSettings.ParticleOption option = resolvePetParticleOption(player.getUniqueId(), pet.id());
                lore.add(Text.parse("&b" + i18n.tr(player, "menu.pet.particle", Map.of("name", option.displayName(lang))))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Text.parse("&e" + i18n.tr(player, "menu.pet.right_particle_next")).decoration(TextDecoration.ITALIC, false));
                lore.add(Text.parse("&e" + i18n.tr(player, "menu.pet.shift_right_particle_prev")).decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());
            lore.add(Text.parse("&e" + i18n.tr(player, active ? "menu.pet.left_toggle_off" : "menu.pet.left_toggle_on"))
                    .decoration(TextDecoration.ITALIC, false));
            if (active) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    private static String toRoman(int number) {
        if (number <= 0) return String.valueOf(number);
        if (number > 20) return String.valueOf(number);
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            case 13 -> "XIII";
            case 14 -> "XIV";
            case 15 -> "XV";
            case 16 -> "XVI";
            case 17 -> "XVII";
            case 18 -> "XVIII";
            case 19 -> "XIX";
            case 20 -> "XX";
            default -> String.valueOf(number);
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PetsMenuHolder holder)) return;

        event.setCancelled(true);

        if (!holder.playerId().equals(player.getUniqueId())) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(top)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String action = getAction(clicked);
        if (action != null) {
            switch (action) {
                case "tab_all" -> open(player, View.ALL, holder.filter(), 0);
                case "tab_mine" -> open(player, View.MINE, holder.filter(), 0);
                case "filter_all" -> open(player, holder.view(), EffectFilter.ALL, 0);
                case "filter_with" -> open(player, holder.view(), EffectFilter.WITH_EFFECTS, 0);
                case "filter_without" -> open(player, holder.view(), EffectFilter.WITHOUT_EFFECTS, 0);
                case "particle" -> {
                    if (event.getClick() == ClickType.SHIFT_LEFT) {
                        toggleParticles(player);
                    } else {
                        cycleParticle(player, event.isRightClick() ? -1 : 1);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.filter(), holder.page()));
                }
                case "page_prev" -> Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.filter(), holder.page() - 1));
                case "page_next" -> Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.filter(), holder.page() + 1));
            }
            return;
        }

        String petId = getPetId(clicked);
        if (petId == null) return;

        Pet pet = pets.byId(petId);
        if (pet == null) return;

        if (!player.hasPermission(pet.permission())) {
            player.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.no_permission_pet")));
            return;
        }

        if (isParticleClick(event.getClick())) {
            if (settings.particles().enabled() && settings.particles().perPetSelection() && !settings.particles().options().isEmpty()) {
                int delta = event.getClick() == ClickType.SHIFT_RIGHT ? -1 : 1;
                cyclePetParticle(player, pet.id(), delta);
            }
        } else if (event.isLeftClick()) {
            petManager.togglePet(player, pet);
        }

        Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.filter(), holder.page()));
    }

    private boolean isParticleClick(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }

    private void cycleParticle(Player player, int delta) {
        PluginSettings.ParticleSettings ps = settings.particles();
        if (!ps.enabled() || ps.options().isEmpty()) return;
        if (ps.allowPlayerDisable() && !dataStore.getParticlesEnabled(player.getUniqueId())) {
            return;
        }

        String defId = ps.options().get(0).id();
        String chosenId = dataStore.getParticleId(player.getUniqueId(), defId);

        int idx = 0;
        for (int i = 0; i < ps.options().size(); i++) {
            if (ps.options().get(i).id().equalsIgnoreCase(chosenId)) {
                idx = i;
                break;
            }
        }

        int next = (idx + delta) % ps.options().size();
        if (next < 0) next += ps.options().size();
        dataStore.setParticleId(player.getUniqueId(), ps.options().get(next).id());
        dataStore.save();
    }

    private void toggleParticles(Player player) {
        PluginSettings.ParticleSettings ps = settings.particles();
        if (!ps.enabled() || !ps.allowPlayerDisable()) return;
        boolean current = dataStore.getParticlesEnabled(player.getUniqueId());
        dataStore.setParticlesEnabled(player.getUniqueId(), !current);
        dataStore.save();
    }

    private void cyclePetParticle(Player player, String petId, int delta) {
        PluginSettings.ParticleSettings ps = settings.particles();
        if (!ps.enabled() || ps.options().isEmpty()) return;

        PluginSettings.ParticleOption current = resolvePetParticleOption(player.getUniqueId(), petId);
        int idx = ps.options().indexOf(current);
        if (idx < 0) idx = 0;

        int next = (idx + delta) % ps.options().size();
        if (next < 0) next += ps.options().size();
        dataStore.setPetParticleId(player.getUniqueId(), petId, ps.options().get(next).id());
        dataStore.save();
    }

    private PluginSettings.ParticleOption resolvePetParticleOption(UUID playerId, String petId) {
        PluginSettings.ParticleSettings ps = settings.particles();
        String defId = ps.options().get(0).id();

        String petChoice = dataStore.getPetParticleId(playerId, petId);
        String playerChoice = dataStore.getParticleId(playerId, defId);
        String chosen = petChoice != null ? petChoice : playerChoice;

        return ps.options().stream()
                .filter(o -> o.id().equalsIgnoreCase(chosen))
                .findFirst()
                .orElse(ps.options().get(0));
    }

    private String getPetId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
    }

    private String getAction(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private static final class PetsMenuHolder implements InventoryHolder {
        private final UUID playerId;
        private final View view;
        private final EffectFilter filter;
        private int page;
        private Inventory inventory;

        private PetsMenuHolder(UUID playerId, View view, EffectFilter filter, int page) {
            this.playerId = playerId;
            this.view = view;
            this.filter = filter;
            this.page = page;
        }

        UUID playerId() {
            return playerId;
        }

        View view() {
            return view;
        }

        EffectFilter filter() {
            return filter;
        }

        int page() {
            return page;
        }

        void setPage(int page) {
            this.page = page;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum View {
        ALL,
        MINE
    }

    private enum EffectFilter {
        ALL,
        WITH_EFFECTS,
        WITHOUT_EFFECTS
    }
}
