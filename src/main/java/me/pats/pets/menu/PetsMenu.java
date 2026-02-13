package me.pats.pets.menu;

import me.pats.pets.pets.PetDefinition;
import me.pats.pets.pets.PetManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PetsMenu implements Listener {

    private static final int MENU_SIZE = 27;

    private final Plugin plugin;
    private final PetManager petManager;
    private final NamespacedKey petIdKey;

    public PetsMenu(Plugin plugin, PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
    }

    public void open(Player player) {
        player.openInventory(createInventory(player));
    }

    private Inventory createInventory(Player player) {
        var holder = new PetsMenuHolder(player.getUniqueId());
        var inventory = Bukkit.createInventory(holder, MENU_SIZE, Component.text("Питомцы", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        int slot = 10;
        for (PetDefinition pet : PetDefinition.values()) {
            inventory.setItem(slot, createPetItem(player, pet));
            slot++;
            if (slot == 17) slot = 19;
        }

        return inventory;
    }

    private ItemStack createPetItem(Player player, PetDefinition pet) {
        boolean hasAccess = player.hasPermission(pet.permission());
        boolean active = Objects.equals(petManager.getActivePetId(player.getUniqueId()), pet.id());

        ItemStack item = new ItemStack(hasAccess ? pet.icon() : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(pet.displayName(), hasAccess ? NamedTextColor.AQUA : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (!hasAccess) {
            lore.add(Component.text("Нет доступа (perms).", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(pet.permission(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(active ? "Сейчас активен" : "Сейчас не активен", active ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("ЛКМ: " + (active ? "деактивировать" : "активировать"), NamedTextColor.YELLOW)
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

        String petId = getPetId(clicked);
        if (petId == null) return;

        PetDefinition pet = PetDefinition.byId(petId);
        if (pet == null) return;

        if (!player.hasPermission(pet.permission())) {
            player.sendMessage(Component.text("У тебя нет доступа к этому питомцу.", NamedTextColor.RED));
            return;
        }

        petManager.togglePet(player, pet);
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    private String getPetId(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
    }

    private static final class PetsMenuHolder implements InventoryHolder {
        private final UUID playerId;
        private Inventory inventory;

        private PetsMenuHolder(UUID playerId) {
            this.playerId = playerId;
        }

        UUID playerId() {
            return playerId;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

