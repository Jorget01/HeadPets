package me.pats.pets.pets;

import me.pats.pets.config.PluginSettings;
import me.pats.pets.i18n.I18n;
import me.pats.pets.i18n.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import me.pats.pets.storage.DataStore;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PetManager implements Listener {

    private static final String PET_TAG = "pats_pet";

    private final Plugin plugin;
    private final DataStore dataStore;
    private final PluginSettings settings;
    private final I18n i18n;
    private final PetRegistry pets;
    private final NamespacedKey petOwnerKey;
    private final NamespacedKey petIdKey;
    private final Map<UUID, ActivePet> activePets = new ConcurrentHashMap<>();
    private BukkitTask followTask;
    private BukkitTask particleTask;
    private BukkitTask effectTask;

    public PetManager(Plugin plugin, DataStore dataStore, PluginSettings settings, I18n i18n, PetRegistry pets) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.settings = settings;
        this.i18n = i18n;
        this.pets = pets;
        this.petOwnerKey = new NamespacedKey(plugin, "pet_owner");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        startFollowTask();
        if (settings.particles().enabled()) {
            startParticleTask();
        }
        if (settings.effects().enabled()) {
            startEffectTask();
        }
    }

    public void disable() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
        for (ActivePet active : activePets.values()) {
            removeEntity(active.entityId());
        }
        activePets.clear();
    }

    public String getActivePetId(UUID playerId) {
        ActivePet activePet = activePets.get(playerId);
        return activePet == null ? null : activePet.petId();
    }

    public void togglePet(Player player, Pet pet) {
        UUID playerId = player.getUniqueId();
        ActivePet current = activePets.get(playerId);
        if (current != null && Objects.equals(current.petId(), pet.id())) {
            deactivate(playerId);
            player.sendMessage(Component.text(i18n.tr(player, "msg.pet_deactivated"), NamedTextColor.GRAY));
            return;
        }

        deactivate(playerId);
        activate(player, pet);
        Language lang = i18n.language(player);
        player.sendMessage(Component.text(i18n.tr(player, "msg.pet_activated", Map.of("name", pet.displayName(lang))), NamedTextColor.GREEN));
    }

    private void activate(Player player, Pet pet) {
        Location spawn = computeTarget(player.getLocation());
        ArmorStand stand = player.getWorld().spawn(spawn, ArmorStand.class, as -> {
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setRemoveWhenFarAway(false);
            as.setPersistent(true);
            as.setCustomNameVisible(false);
            as.setCollidable(false);
            as.setGravity(false);
            as.setVisible(false);
            as.setSmall(true);
            as.setMarker(true);
            as.setBasePlate(false);
            as.addScoreboardTag(PET_TAG);
            as.getPersistentDataContainer().set(petOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            as.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.id());
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(pet.iconItem());
            }
        });

        activePets.put(player.getUniqueId(), new ActivePet(pet.id(), stand.getUniqueId()));
    }

    private void deactivate(UUID playerId) {
        ActivePet active = activePets.remove(playerId);
        if (active != null) {
            if (settings.effects().enabled() && settings.effects().removeOnDeactivate()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    Pet pet = pets.byId(active.petId());
                    if (pet != null) {
                        removePassiveEffects(player, pet);
                    }
                }
            }
            removeEntity(active.entityId());
        }
    }

    private void removeEntity(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private void startFollowTask() {
        this.followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, ActivePet> entry : activePets.entrySet()) {
                UUID playerId = entry.getKey();
                ActivePet active = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    removeEntity(active.entityId());
                    activePets.remove(playerId, active);
                    continue;
                }

                Entity entity = Bukkit.getEntity(active.entityId());
                if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                    activePets.remove(playerId, active);
                    continue;
                }

                if (!player.getWorld().equals(living.getWorld())) {
                    living.teleport(player.getLocation());
                    continue;
                }

                Location playerLoc = player.getLocation();
                Location petLoc = living.getLocation();
                double dist2 = petLoc.distanceSquared(playerLoc);

                double teleportDist = settings.follow().teleportDistance();
                if (dist2 > (teleportDist * teleportDist)) {
                    living.teleport(computeTarget(playerLoc));
                    continue;
                }

                Location target = computeTarget(playerLoc);
                double closeDist = settings.follow().closeDistance();
                if (dist2 < (closeDist * closeDist)) {
                    continue;
                }

                Vector delta = target.toVector().subtract(petLoc.toVector());
                if (delta.lengthSquared() < 0.01) continue;

                Location step = petLoc.clone().add(delta.normalize().multiply(settings.follow().stepSize()));
                step.setYaw(playerLoc.getYaw());
                step.setPitch(0f);
                living.teleport(step);
            }
        }, settings.follow().periodTicks(), settings.follow().periodTicks());
    }

    private void startParticleTask() {
        PluginSettings.ParticleSettings p = settings.particles();
        List<PluginSettings.ParticleOption> options = p.options();
        String defId = options.isEmpty() ? "end_rod" : options.get(0).id();
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, ActivePet> entry : activePets.entrySet()) {
                UUID playerId = entry.getKey();
                ActivePet active = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                Entity entity = Bukkit.getEntity(active.entityId());
                if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                    continue;
                }

                Location loc = living.getLocation().clone().add(0, p.yOffset(), 0);
                String chosenId = resolveChosenParticleId(p, playerId, active.petId(), defId);
                PluginSettings.ParticleOption option = options.stream()
                        .filter(o -> o.id().equalsIgnoreCase(chosenId))
                        .findFirst()
                        .orElse(options.isEmpty() ? new PluginSettings.ParticleOption("end_rod", Particle.END_ROD, "Эндер-искра", "End Rod") : options.get(0));

                if (p.visibility() == PluginSettings.ParticleVisibility.OWNER && player != null) {
                    player.spawnParticle(option.particle(), loc, p.count(), p.offsetX(), p.offsetY(), p.offsetZ(), p.extra());
                } else {
                    living.getWorld().spawnParticle(option.particle(), loc, p.count(), p.offsetX(), p.offsetY(), p.offsetZ(), p.extra());
                }
            }
        }, p.delayTicks(), p.periodTicks());
    }

    private String resolveChosenParticleId(PluginSettings.ParticleSettings p, UUID playerId, String petId, String defId) {
        if (p.perPetSelection()) {
            String petChoice = dataStore.getPetParticleId(playerId, petId);
            if (petChoice != null) return petChoice;
        }
        return dataStore.getParticleId(playerId, defId);
    }

    private void startEffectTask() {
        PluginSettings.EffectSettings eff = settings.effects();
        int duration = Math.max(20, eff.durationTicks());
        long period = Math.max(1L, eff.periodTicks());
        this.effectTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, ActivePet> entry : activePets.entrySet()) {
                UUID playerId = entry.getKey();
                ActivePet active = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                Pet pet = pets.byId(active.petId());
                if (pet == null) continue;

                for (Pet.PassiveEffect pe : pet.passiveEffects().effects()) {
                    applyPassiveEffect(player, pe, duration);
                }
            }
        }, period, period);
    }

    private void applyPassiveEffect(Player player, Pet.PassiveEffect passive, int durationTicks) {
        PotionEffectType type = passive.type();
        if (type == null) return;

        PotionEffect existing = player.getPotionEffect(type);
        if (existing != null) {
            if (existing.getAmplifier() > passive.amplifier()) return;
            if (existing.getAmplifier() == passive.amplifier() && existing.getDuration() > durationTicks) return;
        }

        PotionEffect effect = new PotionEffect(type, durationTicks, passive.amplifier(), passive.ambient(), passive.particles(), passive.icon());
        player.addPotionEffect(effect, false);
    }

    private void removePassiveEffects(Player player, Pet pet) {
        int maxDuration = settings.effects().durationTicks() + 40;
        for (Pet.PassiveEffect passive : pet.passiveEffects().effects()) {
            PotionEffectType type = passive.type();
            if (type == null) continue;
            PotionEffect existing = player.getPotionEffect(type);
            if (existing == null) continue;

            if (existing.getAmplifier() != passive.amplifier()) continue;
            if (existing.getDuration() > maxDuration) continue;
            player.removePotionEffect(type);
        }
    }

    private Location computeTarget(Location playerLoc) {
        Vector back = playerLoc.getDirection().normalize().multiply(-settings.follow().backDistance());
        Location target = playerLoc.clone().add(back).add(0, settings.follow().height(), 0);
        target.setYaw(playerLoc.getYaw());
        target.setPitch(0f);
        return target;
    }

    private boolean isOurPet(Entity entity) {
        if (entity == null) return false;
        if (!entity.getScoreboardTags().contains(PET_TAG)) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        return living.getPersistentDataContainer().has(petOwnerKey, PersistentDataType.STRING);
    }

    private UUID getOwnerId(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        String raw = living.getPersistentDataContainer().get(petOwnerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        deactivate(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        deactivate(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        var active = activePets.get(event.getPlayer().getUniqueId());
        if (active == null) return;
        Entity entity = Bukkit.getEntity(active.entityId());
        if (entity != null) {
            entity.teleport(event.getPlayer().getLocation());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        var active = activePets.get(event.getPlayer().getUniqueId());
        if (active == null) return;
        Entity entity = Bukkit.getEntity(active.entityId());
        if (entity != null) {
            Bukkit.getScheduler().runTask(plugin, () -> entity.teleport(event.getPlayer().getLocation()));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!isOurPet(event.getEntity())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!isOurPet(event.getRightClicked())) return;
        UUID ownerId = getOwnerId(event.getRightClicked());
        if (ownerId != null && ownerId.equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
    }

    private record ActivePet(String petId, UUID entityId) {}
}
