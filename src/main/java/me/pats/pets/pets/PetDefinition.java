package me.pats.pets.pets;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum PetDefinition {
    WOLF("wolf", "Волк", EntityType.WOLF, Material.WOLF_SPAWN_EGG, "pats.pets.wolf"),
    CAT("cat", "Кот", EntityType.CAT, Material.CAT_SPAWN_EGG, "pats.pets.cat"),
    FOX("fox", "Лиса", EntityType.FOX, Material.FOX_SPAWN_EGG, "pats.pets.fox"),
    PARROT("parrot", "Попугай", EntityType.PARROT, Material.PARROT_SPAWN_EGG, "pats.pets.parrot"),
    ALLAY("allay", "Аллай", EntityType.ALLAY, Material.ALLAY_SPAWN_EGG, "pats.pets.allay");

    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final Material icon;
    private final String permission;

    PetDefinition(String id, String displayName, EntityType entityType, Material icon, String permission) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.icon = icon;
        this.permission = permission;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType entityType() {
        return entityType;
    }

    public Material icon() {
        return icon;
    }

    public String permission() {
        return permission;
    }

    public static PetDefinition byId(String id) {
        for (PetDefinition pet : values()) {
            if (pet.id.equalsIgnoreCase(id)) return pet;
        }
        return null;
    }
}

