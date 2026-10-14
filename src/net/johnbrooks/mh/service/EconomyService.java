package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.i18n.Message;
import net.johnbrooks.mh.config.Settings;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.EconomyResponse;

public class EconomyService {

    public static boolean chargePlayer(Player player) {
        switch (Settings.costMode) {
            case ITEM:
                return chargeItem(player);
            case VAULT:
                return chargeVault(player);
            default:
                return false;
        }
    }

    private static boolean chargeVault(Player player) {
        if (Settings.costVault <= 0) {
            return true;
        }

        EconomyResponse response = Main.economy.withdrawPlayer(player, Settings.costVault);
        if (!response.transactionSuccess()) {
            return false;
        }

        Message.sendKey(player, "wallet-charged", "amount", Settings.costVault);
        return true;
    }

    private static boolean chargeItem(Player player) {
        if (Settings.costAmount <= 0) {
            return true;
        }

        ItemStack[] storage = player.getInventory().getStorageContents();
        int available = 0;
        for (ItemStack itemStack : storage) {
            if (itemStack != null && itemStack.getType() == Settings.costMaterial) {
                available += itemStack.getAmount();
            }
        }
        if (available < Settings.costAmount) {
            return false;
        }

        int remaining = Settings.costAmount;
        for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (itemStack == null || itemStack.getType() != Settings.costMaterial) {
                continue;
            }

            int consumed = Math.min(remaining, itemStack.getAmount());
            remaining -= consumed;
            if (consumed == itemStack.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                itemStack.setAmount(itemStack.getAmount() - consumed);
            }
        }

        String materialName = Settings.costMaterial.name();
        String capitalizedMaterial = materialName.substring(0, 1)
                + materialName.substring(1).toLowerCase().replace("_", " ");
        Message.sendKey(player, "item-charged", "item", capitalizedMaterial, "amount", Settings.costAmount);
        return true;
    }
}
