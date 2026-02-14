package me.pats.pets.menu;

import me.pats.pets.config.PluginSettings;
import me.pats.pets.i18n.I18n;
import me.pats.pets.i18n.Language;
import me.pats.pets.pets.Pet;
import me.pats.pets.pets.PetManager;
import me.pats.pets.pets.PetRegistry;
import me.pats.pets.storage.DataStore;
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

    private static final int MENU_SIZE = 54;
    private static final int PAGE_SIZE = 28;

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
        open(player, View.ALL, 0);
    }

    private void open(Player player, View view, int page) {
        player.openInventory(createInventory(player, view, page));
    }

    private Inventory createInventory(Player player, View view, int page) {
        Language lang = i18n.language(player);
        String tabTitle = i18n.tr(player, view == View.ALL ? "menu.tab.all" : "menu.tab.mine");
        String title = i18n.tr(player, "menu.title", Map.of("tab", tabTitle));

        var holder = new PetsMenuHolder(player.getUniqueId(), view, page);
        var inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text(title, NamedTextColor.GOLD));
        holder.setInventory(inventory);

        paintFrame(inventory);
        inventory.setItem(0, createTabItem(player, "menu.tab.all", view == View.ALL, "tab_all"));
        inventory.setItem(1, createTabItem(player, "menu.tab.mine", view == View.MINE, "tab_mine"));
        inventory.setItem(49, createParticleItem(player, lang));
        inventory.setItem(45, createNavItem(player, "menu.nav.prev", "page_prev", Material.ARROW));
        inventory.setItem(53, createNavItem(player, "menu.nav.next", "page_next", Material.ARROW));

        List<Pet> filtered = new ArrayList<>();
        for (Pet pet : pets.all()) {
            boolean hasAccess = player.hasPermission(pet.permission());
            if (view == View.MINE && !hasAccess) continue;
            if (view == View.ALL && !settings.menu().showLockedInAll() && !hasAccess) continue;

            filtered.add(pet);
        }

        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        holder.setPage(safePage);

        inventory.setItem(51, createPageItem(player, safePage + 1, pages));

        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filtered.size());

        int slot = 10;
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
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        for (int i = 0; i < MENU_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean border = row == 0 || row == 5 || col == 0 || col == 8;
            if (border) {
                inventory.setItem(i, pane);
            }
        }
    }

    private ItemStack createTabItem(Player player, String titleKey, boolean selected, String action) {
        String name = i18n.tr(player, titleKey);
        ItemStack item = new ItemStack(selected ? Material.LIME_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, selected ? NamedTextColor.GREEN : NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(i18n.tr(player, selected ? "menu.selected" : "menu.click_to_open"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavItem(Player player, String titleKey, String action, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(i18n.tr(player, titleKey), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageItem(Player player, int page, int pages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(i18n.tr(player, "menu.nav.page", Map.of("page", String.valueOf(page), "pages", String.valueOf(pages))), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createParticleItem(Player player, Language lang) {
        PluginSettings.ParticleSettings ps = settings.particles();
        if (!ps.enabled() || ps.options().isEmpty()) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(i18n.tr(player, "menu.particles.title"), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(i18n.tr(player, "menu.particles.disabled"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            return item;
        }

        String defId = ps.options().get(0).id();
        String chosenId = dataStore.getParticleId(player.getUniqueId(), defId);
        PluginSettings.ParticleOption option = ps.options().stream()
                .filter(o -> o.id().equalsIgnoreCase(chosenId))
                .findFirst()
                .orElse(ps.options().get(0));

        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(i18n.tr(player, "menu.particles.title"), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(i18n.tr(player, "menu.particles.current", Map.of("name", option.displayName(lang))), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(i18n.tr(player, "menu.particles.left"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(i18n.tr(player, "menu.particles.right"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "particle");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(Player player, String titleKey, String lineKey) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(i18n.tr(player, titleKey), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(i18n.tr(player, lineKey), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private int nextGridSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        col++;
        if (col >= 8) {
            row++;
            col = 1;
        }
        if (row >= 5) return -1;
        return row * 9 + col;
    }

    private ItemStack createPetItem(Player player, Pet pet, Language lang) {
        boolean hasAccess = player.hasPermission(pet.permission());
        boolean active = Objects.equals(petManager.getActivePetId(player.getUniqueId()), pet.id());

        ItemStack item = pet.iconItem();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(pet.displayName(lang), hasAccess ? NamedTextColor.AQUA : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (!hasAccess) {
            lore.add(Component.text(i18n.tr(player, "menu.pet.locked"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(pet.permission(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(i18n.tr(player, active ? "menu.pet.active" : "menu.pet.inactive"), active ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            if (settings.particles().enabled() && settings.particles().perPetSelection() && !settings.particles().options().isEmpty()) {
                PluginSettings.ParticleOption option = resolvePetParticleOption(player.getUniqueId(), pet.id());
                lore.add(Component.text(i18n.tr(player, "menu.pet.particle", Map.of("name", option.displayName(lang))), NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(i18n.tr(player, "menu.pet.right_particle_next"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(i18n.tr(player, "menu.pet.shift_right_particle_prev"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());
            lore.add(Component.text(i18n.tr(player, active ? "menu.pet.left_toggle_off" : "menu.pet.left_toggle_on"), NamedTextColor.YELLOW)
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
                case "tab_all" -> open(player, View.ALL, 0);
                case "tab_mine" -> open(player, View.MINE, 0);
                case "particle" -> {
                    cycleParticle(player, event.isRightClick() ? -1 : 1);
                    Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.page()));
                }
                case "page_prev" -> Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.page() - 1));
                case "page_next" -> Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.page() + 1));
            }
            return;
        }

        String petId = getPetId(clicked);
        if (petId == null) return;

        Pet pet = pets.byId(petId);
        if (pet == null) return;

        if (!player.hasPermission(pet.permission())) {
            player.sendMessage(Component.text(i18n.tr(player, "msg.no_permission_pet"), NamedTextColor.RED));
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

        Bukkit.getScheduler().runTask(plugin, () -> open(player, holder.view(), holder.page()));
    }

    private boolean isParticleClick(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }

    private void cycleParticle(Player player, int delta) {
        PluginSettings.ParticleSettings ps = settings.particles();
        if (!ps.enabled() || ps.options().isEmpty()) return;

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
        private int page;
        private Inventory inventory;

        private PetsMenuHolder(UUID playerId, View view, int page) {
            this.playerId = playerId;
            this.view = view;
            this.page = page;
        }

        UUID playerId() {
            return playerId;
        }

        View view() {
            return view;
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
}
