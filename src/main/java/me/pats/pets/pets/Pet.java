package me.pats.pets.pets;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.pats.pets.i18n.Language;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Objects;
import java.util.UUID;

public final class Pet {

    private final String id;
    private final String permission;
    private final LocalizedName name;
    private final HeadTexture head;
    private final PassiveEffects passiveEffects;
    private ItemStack cachedIcon;

    public Pet(String id, String permission, LocalizedName name, HeadTexture head, PassiveEffects passiveEffects) {
        this.id = Objects.requireNonNull(id, "id");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.name = Objects.requireNonNull(name, "name");
        this.head = Objects.requireNonNull(head, "head");
        this.passiveEffects = Objects.requireNonNull(passiveEffects, "passiveEffects");
    }

    public String id() {
        return id;
    }

    public String permission() {
        return permission;
    }

    public String displayName(Language lang) {
        return name.value(lang);
    }

    public ItemStack iconItem() {
        if (cachedIcon == null) {
            cachedIcon = createSkull();
        }
        return cachedIcon.clone();
    }

    public PassiveEffects passiveEffects() {
        return passiveEffects;
    }

    private ItemStack createSkull() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta meta) {
            PlayerProfile profile = Bukkit.createProfile(head.profileId(), head.profileName());
            profile.setProperty(new ProfileProperty("textures", head.textureValue()));
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
        }
        return item;
    }

    public record LocalizedName(String ru, String en) {
        public String value(Language lang) {
            return lang == Language.RU ? ru : en;
        }
    }

    public record HeadTexture(UUID profileId, String profileName, String textureValue) {}

    public record PassiveEffects(java.util.List<PassiveEffect> effects) {
        public static PassiveEffects empty() {
            return new PassiveEffects(java.util.List.of());
        }
    }

    public record PassiveEffect(
            org.bukkit.potion.PotionEffectType type,
            int amplifier,
            boolean ambient,
            boolean particles,
            boolean icon
    ) {}
}
