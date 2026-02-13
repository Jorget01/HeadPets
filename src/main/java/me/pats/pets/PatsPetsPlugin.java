package me.pats.pets;

import me.pats.pets.menu.PetsMenu;
import me.pats.pets.pets.PetManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PatsPetsPlugin extends JavaPlugin implements CommandExecutor {

    private PetManager petManager;
    private PetsMenu petsMenu;

    @Override
    public void onEnable() {
        this.petManager = new PetManager(this);
        this.petsMenu = new PetsMenu(this, petManager);

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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Эта команда доступна только игроку.");
            return true;
        }

        petsMenu.open(player);
        return true;
    }
}

