package me.pats.pets.pets;

import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PetRegistry {

    private final List<Pet> pets;
    private final Map<String, Pet> byId;

    private PetRegistry(List<Pet> pets) {
        this.pets = List.copyOf(pets);
        Map<String, Pet> map = new HashMap<>();
        for (Pet pet : pets) {
            map.put(pet.id().toLowerCase(Locale.ROOT), pet);
        }
        this.byId = Collections.unmodifiableMap(map);
    }

    public List<Pet> all() {
        return pets;
    }

    public Pet byId(String id) {
        if (id == null) return null;
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    public static PetRegistry load(Plugin plugin) {
        List<Map<?, ?>> list = plugin.getConfig().getMapList("pets");
        List<Pet> pets = new ArrayList<>();

        for (Map<?, ?> raw : list) {
            String id = Objects.toString(raw.get("id"), "").trim();
            if (id.isEmpty()) {
                plugin.getLogger().warning("Skipping pet with missing id in config.yml");
                continue;
            }
            String permission = Objects.toString(raw.get("permission"), "").trim();
            if (permission.isEmpty()) {
                permission = "pats.pets." + id;
            }

            Object displayObj = raw.containsKey("display") ? raw.get("display") : raw.get("name");
            Localized localized = readLocalizedName(displayObj, id);
            String nameRu = localized.ru();
            String nameEn = localized.en();

            Pet.HeadTexture head = readHead(plugin, id, raw.get("head"));
            if (head == null) {
                plugin.getLogger().warning("Skipping pet '" + id + "': missing head section");
                continue;
            }

            Pet.PassiveEffects effects = readEffects(plugin, id, raw.get("effects"));
            pets.add(new Pet(id, permission, new Pet.LocalizedName(nameRu, nameEn), head, effects));
        }

        if (pets.isEmpty()) {
            plugin.getLogger().warning("No pets found in config.yml, pets menu will be empty.");
        }

        // dedupe by id (keep first)
        Set<String> seen = new HashSet<>();
        pets.removeIf(p -> !seen.add(p.id().toLowerCase(Locale.ROOT)));
        return new PetRegistry(pets);
    }

    private static Pet.HeadTexture readHead(Plugin plugin, String petId, Object rawHead) {
        if (!(rawHead instanceof Map<?, ?> head)) return null;

        String textures = Objects.toString(head.get("textures"), "").trim();
        if (textures.isEmpty()) return null;

        String profileName = Objects.toString(head.get("profile-name"), "pets-" + petId);
        String profileIdRaw = Objects.toString(head.get("profile-id"), "").trim();
        UUID profileId;
        try {
            profileId = profileIdRaw.isEmpty()
                    ? UUID.nameUUIDFromBytes(("pets:" + petId).getBytes(StandardCharsets.UTF_8))
                    : UUID.fromString(profileIdRaw);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid profile-id for pet '" + petId + "': " + profileIdRaw);
            profileId = UUID.nameUUIDFromBytes(("pets:" + petId).getBytes(StandardCharsets.UTF_8));
        }

        return new Pet.HeadTexture(profileId, profileName, textures);
    }

    private record Localized(String ru, String en) {}

    private static Localized readLocalizedName(Object raw, String def) {
        if (raw == null) return new Localized(def, def);
        if (raw instanceof String s) {
            String name = s.trim();
            if (name.isEmpty()) return new Localized(def, def);
            return new Localized(name, name);
        }
        if (raw instanceof Map<?, ?> map) {
            Object ru = map.get("ru");
            Object en = map.get("en");
            String ruName = ru == null ? def : Objects.toString(ru, def);
            String enName = en == null ? def : Objects.toString(en, def);
            return new Localized(ruName, enName);
        }
        return new Localized(def, def);
    }

    private static int parseInt(Object raw, int def) {
        if (raw instanceof Number n) return n.intValue();
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static boolean parseBool(Object raw, boolean def) {
        if (raw instanceof Boolean b) return b;
        if (raw == null) return def;
        return Boolean.parseBoolean(raw.toString().trim());
    }

    private static Pet.PassiveEffects readEffects(Plugin plugin, String petId, Object rawEffects) {
        if (!(rawEffects instanceof List<?> list) || list.isEmpty()) {
            return Pet.PassiveEffects.empty();
        }

        List<Pet.PassiveEffect> out = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String s) {
                Pet.PassiveEffect pe = parseEffectString(plugin, petId, s);
                if (pe != null) out.add(pe);
                continue;
            }
            if (!(entry instanceof Map<?, ?> map)) continue;

            String typeRaw = Objects.toString(map.get("type"), "").trim();
            if (typeRaw.isEmpty()) continue;
            PotionEffectType type = PotionEffectType.getByName(typeRaw.toUpperCase(Locale.ROOT));
            if (type == null) {
                plugin.getLogger().warning("Unknown effect type for pet '" + petId + "': " + typeRaw);
                continue;
            }

            int amplifier = parseInt(map.get("amplifier"), 0);
            boolean ambient = parseBool(map.get("ambient"), false);
            boolean particles = parseBool(map.get("particles"), true);
            boolean icon = parseBool(map.get("icon"), true);

            out.add(new Pet.PassiveEffect(type, amplifier, ambient, particles, icon));
        }

        return out.isEmpty() ? Pet.PassiveEffects.empty() : new Pet.PassiveEffects(List.copyOf(out));
    }

    private static Pet.PassiveEffect parseEffectString(Plugin plugin, String petId, String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;
        String[] parts = s.split(":", 2);
        String typeRaw = parts[0].trim();
        int amplifier = 0;
        if (parts.length == 2) {
            try {
                amplifier = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                amplifier = 0;
            }
        }
        PotionEffectType type = PotionEffectType.getByName(typeRaw.toUpperCase(Locale.ROOT));
        if (type == null) {
            plugin.getLogger().warning("Unknown effect type for pet '" + petId + "': " + typeRaw);
            return null;
        }
        return new Pet.PassiveEffect(type, amplifier, false, true, true);
    }
}
