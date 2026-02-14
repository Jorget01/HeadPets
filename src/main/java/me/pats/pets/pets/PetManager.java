package me.pats.pets.pets;

import me.pats.pets.config.PluginSettings;
import me.pats.pets.i18n.I18n;
import me.pats.pets.i18n.Language;
import me.pats.pets.text.Text;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final Map<UUID, CopyOnWriteArrayList<ActivePet>> activePets = new ConcurrentHashMap<>();
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
        for (CopyOnWriteArrayList<ActivePet> list : activePets.values()) {
            for (ActivePet active : list) {
                removeEntity(active.entityId());
            }
        }
        activePets.clear();
    }

    public String getActivePetId(UUID playerId) {
        CopyOnWriteArrayList<ActivePet> list = activePets.get(playerId);
        if (list == null || list.isEmpty()) return null;
        return list.get(0).petId();
    }

    public boolean isPetActive(UUID playerId, String petId) {
        CopyOnWriteArrayList<ActivePet> list = activePets.get(playerId);
        if (list == null) return false;
        for (ActivePet active : list) {
            if (active.petId().equalsIgnoreCase(petId)) return true;
        }
        return false;
    }

    public void togglePet(Player player, Pet pet) {
        UUID playerId = player.getUniqueId();
        CopyOnWriteArrayList<ActivePet> list = activePets.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>());
        ActivePet existing = findActive(list, pet.id());
        if (existing != null) {
            deactivate(playerId, existing);
            player.sendMessage(Text.parse("&7" + i18n.tr(player, "msg.pet_deactivated")));
            return;
        }

        // Safety: if the plugin reloaded/crashed earlier, old persistent ArmorStands may remain nearby.
        // Remove any stray entities for this player/pet before spawning a new one.
        removeOwnedPetEntitiesNearby(player, pet.id());

        int max = settings.pets().maxActivePerPlayer();
        if (list.size() >= max) {
            player.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.pet_limit", Map.of("max", String.valueOf(max)))));
            return;
        }

        ActivePet active = activate(player, pet);
        if (active != null) {
            list.add(active);
            dataStore.addActivePet(playerId, pet.id());
            dataStore.save();
        }
        Language lang = i18n.language(player);
        player.sendMessage(Text.parse("&a" + i18n.tr(player, "msg.pet_activated", Map.of("name", pet.displayName(lang)))));
    }

    private ActivePet activate(Player player, Pet pet) {
        CopyOnWriteArrayList<ActivePet> list = activePets.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>());
        int index = list.size();
        int total = Math.max(1, list.size() + 1);
        Location spawn = computeTarget(player.getLocation(), index, total);
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

        return new ActivePet(pet.id(), stand.getUniqueId());
    }

    private ActivePet activate(Player player, Pet pet, int index, int total) {
        Location spawn = computeTarget(player.getLocation(), index, total);
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
        return new ActivePet(pet.id(), stand.getUniqueId());
    }

    private void deactivate(UUID playerId, ActivePet active) {
        CopyOnWriteArrayList<ActivePet> list = activePets.get(playerId);
        if (list == null) return;
        boolean removed = list.remove(active);
        if (!removed) return;

        dataStore.removeActivePet(playerId, active.petId());
        dataStore.save();

        if (settings.effects().enabled() && settings.effects().removeOnDeactivate()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Pet pet = pets.byId(active.petId());
                if (pet != null) {
                    removePassiveEffects(player, pet, list);
                }
            }
        }

        removeEntity(active.entityId());

        if (list.isEmpty()) {
            activePets.remove(playerId, list);
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
            for (Map.Entry<UUID, CopyOnWriteArrayList<ActivePet>> entry : activePets.entrySet()) {
                UUID playerId = entry.getKey();
                CopyOnWriteArrayList<ActivePet> list = entry.getValue();
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    for (ActivePet active : list) {
                        removeEntity(active.entityId());
                    }
                    activePets.remove(playerId, list);
                    continue;
                }

                int total = Math.max(1, list.size());
                for (int idx = 0; idx < list.size(); idx++) {
                    ActivePet active = list.get(idx);
                    Entity entity = Bukkit.getEntity(active.entityId());
                    if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                        list.remove(active);
                        continue;
                    }

                    if (!player.getWorld().equals(living.getWorld())) {
                        living.teleport(player.getLocation());
                        continue;
                    }

                    Location playerLoc = player.getLocation();
                    Location petLoc = living.getLocation();
                    Location target = computeTarget(playerLoc, idx, total);
                    Vector deltaToTarget = target.toVector().subtract(petLoc.toVector());
                    double distTarget2 = deltaToTarget.lengthSquared();

                    double teleportDist = settings.follow().teleportDistance();
                    if (petLoc.distanceSquared(playerLoc) > (teleportDist * teleportDist)) {
                        living.teleport(target);
                        continue;
                    }

                    double closeDist = settings.follow().closeDistance();
                    if (total > 1) {
                        closeDist = Math.min(closeDist, 0.35);
                    }
                    if (distTarget2 < (closeDist * closeDist)) {
                        continue;
                    }

                    if (distTarget2 < 0.0004) continue;

                    double dist = Math.sqrt(distTarget2);
                    double maxStep = Math.max(0.05, settings.follow().stepSize());
                    double stepLen = Math.min(maxStep, Math.max(0.18, dist * 0.40));
                    Vector stepVec = deltaToTarget.multiply(stepLen / dist);

                    Location step = petLoc.clone().add(stepVec);
                    step.setYaw(playerLoc.getYaw());
                    step.setPitch(0f);
                    living.teleport(step);
                }

                if (list.isEmpty()) {
                    activePets.remove(playerId, list);
                }
            }
        }, settings.follow().periodTicks(), settings.follow().periodTicks());
    }

    private void startParticleTask() {
        PluginSettings.ParticleSettings p = settings.particles();
        List<PluginSettings.ParticleOption> options = p.options();
        String defId = options.isEmpty() ? "end_rod" : options.get(0).id();
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, CopyOnWriteArrayList<ActivePet>> entry : activePets.entrySet()) {
                UUID playerId = entry.getKey();
                CopyOnWriteArrayList<ActivePet> list = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (p.allowPlayerDisable() && !dataStore.getParticlesEnabled(playerId)) {
                    continue;
                }

                for (ActivePet active : list) {
                    Entity entity = Bukkit.getEntity(active.entityId());
                    if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                        continue;
                    }

                    Location loc = living.getLocation().clone().add(0, p.yOffset(), 0);
                    String chosenId = resolveChosenParticleId(p, playerId, active.petId(), defId);
                    if ("none".equalsIgnoreCase(chosenId)) {
                        continue;
                    }
                    PluginSettings.ParticleOption option = options.stream()
                            .filter(o -> o.id().equalsIgnoreCase(chosenId))
                            .findFirst()
                            .orElse(options.isEmpty() ? new PluginSettings.ParticleOption("end_rod", Particle.END_ROD, "Эндер-искра", "End Rod") : options.get(0));

                    if (option.particle() == null) {
                        continue;
                    }
                    if (p.visibility() == PluginSettings.ParticleVisibility.OWNER) {
                        player.spawnParticle(option.particle(), loc, p.count(), p.offsetX(), p.offsetY(), p.offsetZ(), p.extra());
                    } else {
                        living.getWorld().spawnParticle(option.particle(), loc, p.count(), p.offsetX(), p.offsetY(), p.offsetZ(), p.extra());
                    }
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
            for (Map.Entry<UUID, CopyOnWriteArrayList<ActivePet>> entry : activePets.entrySet()) {
                UUID playerId = entry.getKey();
                CopyOnWriteArrayList<ActivePet> list = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }

                for (ActivePet active : list) {
                    Pet pet = pets.byId(active.petId());
                    if (pet == null) continue;

                    for (Pet.PassiveEffect pe : pet.passiveEffects().effects()) {
                        applyPassiveEffect(player, pe, duration);
                    }
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

    private void removePassiveEffects(Player player, Pet removedPet, List<ActivePet> remainingActive) {
        Map<PotionEffectType, Integer> bestRemaining = new ConcurrentHashMap<>();
        for (ActivePet active : remainingActive) {
            Pet p = pets.byId(active.petId());
            if (p == null) continue;
            for (Pet.PassiveEffect eff : p.passiveEffects().effects()) {
                PotionEffectType type = eff.type();
                if (type == null) continue;
                bestRemaining.merge(type, eff.amplifier(), Math::max);
            }
        }

        int maxDuration = settings.effects().durationTicks() + 40;
        for (Pet.PassiveEffect passive : removedPet.passiveEffects().effects()) {
            PotionEffectType type = passive.type();
            if (type == null) continue;
            Integer remainingAmp = bestRemaining.get(type);
            if (remainingAmp != null && remainingAmp >= passive.amplifier()) {
                continue;
            }
            PotionEffect existing = player.getPotionEffect(type);
            if (existing == null) continue;

            if (existing.getAmplifier() != passive.amplifier()) continue;
            if (existing.getDuration() > maxDuration) continue;
            player.removePotionEffect(type);
        }
    }

    private Location computeTarget(Location playerLoc, int index, int total) {
        Vector back = playerLoc.getDirection().normalize().multiply(-settings.follow().backDistance());
        Location base = playerLoc.clone().add(back).add(0, settings.follow().height(), 0);

        if (total <= 1) {
            base.setYaw(playerLoc.getYaw());
            base.setPitch(0f);
            return base;
        }

        // Spread pets wider + stagger heights so they don't look like a single blob while moving.
        base.add(0, -0.25, 0);

        double spread = 1.05;
        double verticalStep = 0.35;
        double downPerRow = 0.15;
        double extraBack = 0.18;

        double center = (total - 1) / 2.0;
        double xIndex = index - center; // left negative, right positive

        double x = xIndex * spread;
        double y = ((index % 3) - 1) * verticalStep - (index / 3) * downPerRow;
        double z = Math.abs(xIndex) * extraBack;

        Vector right = playerLoc.getDirection().clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector offset = right.multiply(x).add(back.clone().normalize().multiply(z));

        Location target = base.add(offset).add(0, y, 0);
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

    private String getPetId(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        return living.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
    }

    private void removeOwnedPetEntitiesNearby(Player player, String petIdOrNull) {
        UUID ownerId = player.getUniqueId();
        double r = Math.max(48.0, settings.follow().teleportDistance() + 32.0);
        Collection<Entity> nearby = player.getNearbyEntities(r, r, r);
        for (Entity e : nearby) {
            if (!(e instanceof ArmorStand)) continue;
            if (!isOurPet(e)) continue;
            UUID owner = getOwnerId(e);
            if (owner == null || !owner.equals(ownerId)) continue;
            if (petIdOrNull != null) {
                String pid = getPetId(e);
                if (pid == null || !pid.equalsIgnoreCase(petIdOrNull)) continue;
            }
            e.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        CopyOnWriteArrayList<ActivePet> list = activePets.remove(playerId);
        if (list != null) {
            for (ActivePet active : list) {
                removeEntity(active.entityId());
            }
        }
        // Extra safety: remove any remaining persistent pet stands near the player.
        removeOwnedPetEntitiesNearby(event.getPlayer(), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        CopyOnWriteArrayList<ActivePet> list = activePets.remove(playerId);
        if (list != null) {
            for (ActivePet active : list) {
                removeEntity(active.entityId());
            }
        }
        removeOwnedPetEntitiesNearby(event.getEntity(), null);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        CopyOnWriteArrayList<ActivePet> list = activePets.get(playerId);
        if (list == null) return;
        for (ActivePet active : list) {
            Entity entity = Bukkit.getEntity(active.entityId());
            if (entity != null) {
                entity.teleport(event.getPlayer().getLocation());
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        CopyOnWriteArrayList<ActivePet> list = activePets.get(playerId);
        if (list == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ActivePet active : list) {
                Entity entity = Bukkit.getEntity(active.entityId());
                if (entity != null) {
                    entity.teleport(event.getPlayer().getLocation());
                }
            }
        });
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

    private ActivePet findActive(List<ActivePet> list, String petId) {
        for (ActivePet active : list) {
            if (active.petId().equalsIgnoreCase(petId)) return active;
        }
        return null;
    }

    private void restorePets(Player player) {
        UUID playerId = player.getUniqueId();
        List<String> stored = dataStore.getActivePets(playerId);

        // If the player relogs (or the plugin reloads) and old persistent stands are still around,
        // they can become orphaned and stop following. Remove them before restoring.
        removeOwnedPetEntitiesNearby(player, null);

        if (stored.isEmpty()) return;

        int max = settings.pets().maxActivePerPlayer();
        List<String> toSpawnIds = new ArrayList<>();
        for (String id : stored) {
            if (toSpawnIds.size() >= max) break;
            Pet pet = pets.byId(id);
            if (pet == null) continue;
            if (!player.hasPermission(pet.permission())) continue;
            if (toSpawnIds.stream().anyMatch(s -> s.equalsIgnoreCase(id))) continue;
            toSpawnIds.add(id);
        }

        if (toSpawnIds.isEmpty()) return;

        CopyOnWriteArrayList<ActivePet> list = activePets.computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>());
        for (ActivePet active : list) {
            removeEntity(active.entityId());
        }
        list.clear();

        int total = toSpawnIds.size();
        for (int i = 0; i < total; i++) {
            Pet pet = pets.byId(toSpawnIds.get(i));
            if (pet == null) continue;
            ActivePet active = activate(player, pet, i, total);
            if (active != null) list.add(active);
        }

        // keep data consistent (drop missing/locked/over-limit entries)
        if (!stored.equals(toSpawnIds)) {
            dataStore.setActivePets(playerId, toSpawnIds);
            dataStore.save();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> restorePets(player), 1L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> restorePets(player), 5L);
    }

    private record ActivePet(String petId, UUID entityId) {}
}
