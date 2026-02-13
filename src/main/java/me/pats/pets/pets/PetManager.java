package me.pats.pets.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PetManager implements Listener {

    private static final String PET_TAG = "pats_pet";

    private final Plugin plugin;
    private final NamespacedKey petOwnerKey;
    private final NamespacedKey petIdKey;
    private final Map<UUID, ActivePet> activePets = new ConcurrentHashMap<>();
    private BukkitTask followTask;

    public PetManager(Plugin plugin) {
        this.plugin = plugin;
        this.petOwnerKey = new NamespacedKey(plugin, "pet_owner");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        startFollowTask();
    }

    public void disable() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
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

    public void togglePet(Player player, PetDefinition pet) {
        UUID playerId = player.getUniqueId();
        ActivePet current = activePets.get(playerId);
        if (current != null && Objects.equals(current.petId(), pet.id())) {
            deactivate(playerId);
            player.sendMessage(Component.text("Питомец деактивирован.", NamedTextColor.GRAY));
            return;
        }

        deactivate(playerId);
        activate(player, pet);
        player.sendMessage(Component.text("Питомец активирован: " + pet.displayName(), NamedTextColor.GREEN));
    }

    private void activate(Player player, PetDefinition pet) {
        Location spawn = player.getLocation().clone().add(0.6, 0.0, 0.6);
        Entity entity = player.getWorld().spawnEntity(spawn, pet.entityType());
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return;
        }

        living.setSilent(true);
        living.setInvulnerable(true);
        living.setRemoveWhenFarAway(false);
        living.setPersistent(true);
        living.setCustomNameVisible(false);
        living.addScoreboardTag(PET_TAG);
        living.getPersistentDataContainer().set(petOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        living.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.id());

        if (living instanceof Mob mob) {
            mob.setAI(false);
        }
        if (living instanceof Tameable tameable) {
            tameable.setOwner(player);
            tameable.setTamed(true);
        }

        activePets.put(player.getUniqueId(), new ActivePet(pet.id(), living.getUniqueId()));
    }

    private void deactivate(UUID playerId) {
        ActivePet active = activePets.remove(playerId);
        if (active != null) {
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

                if (dist2 > (12.0 * 12.0)) {
                    living.teleport(playerLoc);
                    continue;
                }

                Location target = computeTarget(playerLoc);
                Vector direction = target.toVector().subtract(petLoc.toVector());
                if (direction.lengthSquared() < 0.04) {
                    living.setVelocity(new Vector(0, living.getVelocity().getY(), 0));
                    continue;
                }

                Vector velocity = direction.normalize().multiply(0.35);
                velocity.setY(living.getVelocity().getY());
                living.setVelocity(velocity);
                living.setFallDistance(0f);
            }
        }, 10L, 10L);
    }

    private Location computeTarget(Location playerLoc) {
        Vector back = playerLoc.getDirection().normalize().multiply(-1.8);
        Location target = playerLoc.clone().add(back);
        target.setY(playerLoc.getY());
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
