package me.pats.pets;

import me.pats.pets.config.PluginSettings;
import me.pats.pets.i18n.I18n;
import me.pats.pets.menu.PetsMenu;
import me.pats.pets.pets.PetManager;
import me.pats.pets.pets.PetRegistry;
import me.pats.pets.storage.DataStore;
import me.pats.pets.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PatsPetsPlugin extends JavaPlugin implements CommandExecutor {

    private PetManager petManager;
    private PetsMenu petsMenu;
    private DataStore dataStore;
    private I18n i18n;
    private PluginSettings settings;
    private PetRegistry petRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dataStore = new DataStore(this);
        this.dataStore.load();

        reloadPluginState();

        registerCommands();
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.disable();
        }
        if (petManager != null) {
            HandlerList.unregisterAll(petManager);
        }
        if (petsMenu != null) {
            HandlerList.unregisterAll(petsMenu);
        }
        if (dataStore != null) {
            dataStore.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "pets" -> handlePets(sender, args);
            case "petsreload" -> handleReload(sender);
            case "petadd" -> handlePetAdd(sender, args);
            case "petremove" -> handlePetRemove(sender, args);
            default -> true;
        };
    }

    private boolean handlePets(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(i18n == null ? "This command can only be used by a player." : i18n.tr(null, "msg.player_only"));
                return true;
            }
            petsMenu.open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "add" -> handlePetAdd(sender, Arrays.copyOfRange(args, 1, args.length));
            case "remove", "delete", "del" -> handlePetRemove(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                sender.sendMessage(Text.parse("&c/pets [add|remove|reload]"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("pats.pets.reload")) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(sender instanceof Player p ? p : null, "msg.no_permission")));
            return true;
        }
        reloadPluginState();
        sender.sendMessage(Text.parse("&a" + i18n.tr(sender instanceof Player p ? p : null, "msg.reloaded")));
        return true;
    }

    private boolean handlePetAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pats.pets.admin")) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(sender instanceof Player p ? p : null, "msg.no_permission")));
            return true;
        }
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 2) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.usage_pets_add")));
            return true;
        }

        String id = args[0].trim();
        String textures = args[1].trim();
        if (id.isEmpty() || textures.isEmpty()) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.usage_pets_add")));
            return true;
        }

        String ruName = args.length >= 3 ? args[2].replace('_', ' ') : id;
        String enName;
        if (args.length >= 4) {
            enName = args[3].replace('_', ' ');
        } else if (args.length >= 3) {
            enName = ruName;
        } else {
            enName = id;
        }

        if (petRegistry.byId(id) != null) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.pet_exists", Map.of("id", id))));
            return true;
        }

        Map<String, Object> newPet = new LinkedHashMap<>();
        newPet.put("id", id);
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("ru", ruName);
        display.put("en", enName);
        newPet.put("display", display);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("profile-name", "pets-" + id);
        head.put("textures", textures);
        newPet.put("head", head);

        List<Map<?, ?>> list = getConfig().getMapList("pets");
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Map<?, ?> entry : list) {
            updated.add(copyStringObjectMap(entry));
        }
        updated.add(newPet);
        getConfig().set("pets", updated);
        saveConfig();

        reloadPluginState();
        sender.sendMessage(Text.parse("&a" + i18n.tr(player, "msg.pet_added", Map.of("id", id))));
        return true;
    }

    private boolean handlePetRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pats.pets.admin")) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(sender instanceof Player p ? p : null, "msg.no_permission")));
            return true;
        }
        Player player = sender instanceof Player p ? p : null;
        if (args.length < 1) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.usage_pets_remove")));
            return true;
        }

        String id = args[0].trim();
        if (id.isEmpty()) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.usage_pets_remove")));
            return true;
        }

        List<Map<?, ?>> list = getConfig().getMapList("pets");
        List<Map<String, Object>> updated = new ArrayList<>();
        boolean removed = false;
        for (Map<?, ?> entry : list) {
            String entryId = Objects.toString(entry.get("id"), "");
            if (!removed && entryId.equalsIgnoreCase(id)) {
                removed = true;
                continue;
            }
            updated.add(copyStringObjectMap(entry));
        }

        if (!removed) {
            sender.sendMessage(Text.parse("&c" + i18n.tr(player, "msg.pet_not_found", Map.of("id", id))));
            return true;
        }

        getConfig().set("pets", updated);
        saveConfig();
        reloadPluginState();

        sender.sendMessage(Text.parse("&a" + i18n.tr(player, "msg.pet_removed", Map.of("id", id))));
        return true;
    }

    private void registerCommands() {
        String[] names = {"pets", "petsreload", "petadd", "petremove"};
        for (String name : names) {
            var cmd = getCommand(name);
            if (cmd != null) cmd.setExecutor(this);
        }
    }

    private void reloadPluginState() {
        reloadConfig();

        this.settings = PluginSettings.load(this);
        this.i18n = new I18n(this, dataStore, settings.language().mode(), settings.language().fixed(), settings.language().allowPlayerOverride());
        this.i18n.load();
        this.petRegistry = PetRegistry.load(this);

        if (petManager != null) {
            HandlerList.unregisterAll(petManager);
            petManager.disable();
        }
        if (petsMenu != null) {
            HandlerList.unregisterAll(petsMenu);
        }

        this.petManager = new PetManager(this, dataStore, settings, i18n, petRegistry);
        this.petsMenu = new PetsMenu(this, petManager, dataStore, settings, i18n, petRegistry);

        getServer().getPluginManager().registerEvents(petManager, this);
        getServer().getPluginManager().registerEvents(petsMenu, this);
    }

    private Map<String, Object> copyStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(Objects.toString(e.getKey(), ""), e.getValue());
        }
        return out;
    }
}
