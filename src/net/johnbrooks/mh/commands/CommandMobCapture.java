package net.johnbrooks.mh.commands;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.domain.item.CaptureItemType;
import net.johnbrooks.mh.domain.item.UniqueProjectileData;
import net.johnbrooks.mh.i18n.Message;
import net.johnbrooks.mh.service.UpdateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Default;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.io.File;
import java.util.Map;

@CommandPermission("mobcapture.admin")
@Command("mobcapture")
public final class CommandMobCapture {


    @Description("Shows MobCapture information.")
    @Subcommand("info")
    public void info(CommandSender sender) {
        Message.sendKey(sender, "info-author");
        Message.sendKey(sender, "info-profile");
    }
    @Subcommand("version")
    @Description("Shows the current plugin version.")
    public void version(CommandSender sender) {
        Message.sendKey(sender, "version", "plugin", Main.plugin.getName(), "version", Main.plugin.getPluginVersion());
        Message.sendKey(sender, "update-status", "status", Message.get(UpdateService.isUpdateAvailable() ? "update-status-available" : "update-status-current"));
    }

    @Subcommand("update")
    @Description("Checks whether a plugin update is available.")
    public void update(CommandSender sender) {
        if (UpdateService.isUpdateAvailable()) {
            Message.sendKey(sender, "update-available", "plugin", Main.plugin.getName());
            return;
        }

        Message.sendKey(sender, "up-to-date");
    }

    @Subcommand("reload")
    @Description("Reloads the plugin configuration.")
    public void reload(CommandSender sender) {
        Settings.load();
        Message.sendKey(sender, "config-reloaded");
    }

    @Subcommand("regenerateconfig")
    @Description("Regenerates the plugin configuration.")
    public void regenerateConfig(CommandSender sender) {
        File file = new File("plugins" + File.separator + Main.plugin.getName() + File.separator + "config.yml");
        file.delete();
        Main.plugin.saveDefaultConfig();
        Settings.load();
        Message.sendKey(sender, "config-regenerated");
    }

    @Subcommand("spawn")
    @Description("Spawns a configured capture item.")
    public void spawn(Player player) {
        spawn(player, UniqueProjectileData.defaultCaptureType(), 1);
    }

    @Subcommand("spawn")
    @Description("Spawns a configured capture item.")
    public void spawn(Player player, CaptureItemType type, @Default("1") int amount) {
        if (!UniqueProjectileData.isEnabled()) {
            Message.sendKey(player, "capture-items-disabled");
            return;
        }

        int safeAmount = Math.max(1, amount);
        giveCaptureItems(player, type, safeAmount);
        Message.sendKey(player, "capture-item-spawned", "amount", safeAmount, "type", type.getId());
    }

    @Subcommand("give")
    @Description("Gives configured capture items to a player.")
    public void give(CommandSender sender, @SuggestWith(PlayerSuggestionProvider.class) Player target, CaptureItemType type, @Default("1") int amount) {
        if (!UniqueProjectileData.isEnabled()) {
            Message.sendKey(sender, "capture-items-disabled");
            return;
        }

        int safeAmount = Math.max(1, amount);
        giveCaptureItems(target, type, safeAmount);
        Message.sendKey(sender, "capture-item-given", "amount", safeAmount, "type", type.getId(), "player", target.getName());
        if (!sender.equals(target)) {
            Message.sendKey(target, "capture-item-received", "amount", safeAmount, "type", type.getId(), "player", sender.getName());
        }
    }

    private void giveCaptureItems(Player player, CaptureItemType type, int amount) {
        int remaining = amount;
        int maxStackSize = Math.max(1, type.getMaterial().getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(UniqueProjectileData.spawn(type.getId(), stackAmount));
            overflow.values().forEach(itemStack -> player.getWorld().dropItemNaturally(player.getLocation(), itemStack));
            remaining -= stackAmount;
        }
    }
}
