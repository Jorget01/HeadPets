package me.pats.pets;

import me.pats.pets.config.PluginSettings;
import me.pats.pets.i18n.I18n;
import me.pats.pets.menu.PetsMenu;
import me.pats.pets.pets.PetManager;
import me.pats.pets.pets.PetRegistry;
import me.pats.pets.storage.DataStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PatsPetsPlugin extends JavaPlugin implements CommandExecutor {

    private PetManager petManager;
    private PetsMenu petsMenu;
    private DataStore dataStore;
    private I18n i18n;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.dataStore = new DataStore(this);
        this.dataStore.load();

        PluginSettings settings = PluginSettings.load(this);
        this.i18n = new I18n(this, dataStore, settings.language().mode(), settings.language().fixed(), settings.language().allowPlayerOverride());
        this.i18n.load();

        PetRegistry pets = PetRegistry.load(this);
        this.petManager = new PetManager(this, dataStore, settings, i18n, pets);
        this.petsMenu = new PetsMenu(this, petManager, dataStore, settings, i18n, pets);

        var petsCommand = getCommand("pets");
        if (petsCommand != null) {
            petsCommand.setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(petManager, this);
        getServer().getPluginManager().registerEvents(petsMenu, this);
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.disable();
        }
        if (dataStore != null) {
            dataStore.save();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(i18n == null ? "This command can only be used by a player." : i18n.tr(null, "msg.player_only"));
            return true;
        }

        petsMenu.open(player);
        return true;
    }
}
