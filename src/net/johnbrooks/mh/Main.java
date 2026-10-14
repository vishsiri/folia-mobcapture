package net.johnbrooks.mh;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.johnbrooks.mh.commands.CaptureItemTypeResolver;
import net.johnbrooks.mh.commands.CommandMobCapture;
import net.johnbrooks.mh.commands.MobCaptureCommandExceptionHandler;
import net.johnbrooks.mh.domain.item.CaptureItemType;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.listener.MobCaptureListener;
import net.johnbrooks.mh.persistence.NBTManager;
import net.johnbrooks.mh.service.PermissionService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

import java.util.logging.Logger;

public class Main extends JavaPlugin {
    public static Main plugin;
    public static Logger logger;

    public static MobCaptureListener mobCaptureListener = null;
    public static PermissionService permissionService = null;
    public static Economy economy = null;

    public static GriefPrevention griefPrevention;
    private Lamp<BukkitCommandActor> lamp;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        logger = getLogger();
        permissionService = new PermissionService();
        if (!NBTManager.preload()) {
            getLogger().warning("NBT-API failed to initialize. Disabling " + getPluginName() + ".");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Settings.load();
        lamp = BukkitLamp.builder(this)
                .parameterTypes(types -> types.addParameterType(CaptureItemType.class, new CaptureItemTypeResolver()))
                .exceptionHandler(new MobCaptureCommandExceptionHandler())
                .build();
        lamp.register(new CommandMobCapture());
        mobCaptureListener = new MobCaptureListener();
        mobCaptureListener.initialize();
        getLogger().info(getPluginName() + " is now enabled!");
    }

    @Override
    public void onDisable() {
        if (lamp != null) {
            lamp.unregisterAllCommands();
        }
        getLogger().info(getPluginName() + " is now disabled!");
    }

    public String getPluginVersion() {
        return getPluginMeta().getVersion();
    }

    private String getPluginName() {
        return getPluginMeta().getName();
    }
}
