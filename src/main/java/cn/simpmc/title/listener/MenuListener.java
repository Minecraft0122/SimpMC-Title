package cn.simpmc.title.listener;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.gui.ColorMenuHolder;
import cn.simpmc.title.gui.TitleMenuHolder;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {
    private final SimpMCTitlePlugin plugin;
    private final TitleManager titleManager;

    public MenuListener(SimpMCTitlePlugin plugin, TitleManager titleManager) {
        this.plugin = plugin;
        this.titleManager = titleManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof TitleMenuHolder holder) {
            event.setCancelled(true);
            String titleId = holder.getTitle(event.getRawSlot());
            if (titleId != null) {
                TitleDefinition definition = titleManager.getTitle(titleId);
                if (titleManager.equip(player, titleId)) {
                    player.sendMessage(TextUtil.colorize(plugin.message("prefix")
                            + plugin.message("title-equipped").replace("%title%",
                            definition == null ? titleId : definition.getDisplay())));
                    player.closeInventory();
                } else {
                    player.sendMessage(TextUtil.colorize(plugin.message("prefix") + plugin.message("title-no-access")));
                }
                return;
            }
            if (event.getRawSlot() == 49) {
                titleManager.clearTitle(player);
                player.sendMessage(TextUtil.colorize(plugin.message("prefix") + plugin.message("title-cleared")));
                player.closeInventory();
            } else if (event.getRawSlot() == 53) {
                player.closeInventory();
            }
        } else if (event.getInventory().getHolder() instanceof ColorMenuHolder holder) {
            event.setCancelled(true);
            String color = holder.getColor(event.getRawSlot());
            if (color != null) {
                titleManager.updateColor(player, color);
                player.sendMessage(TextUtil.colorize(plugin.message("prefix")
                        + plugin.message("color-updated").replace("%name%", titleManager.getDisplayName(player))));
                player.closeInventory();
            } else if (event.getRawSlot() == 22) {
                titleManager.updateColor(player, "<white>");
                player.sendMessage(TextUtil.colorize(plugin.message("prefix") + plugin.message("color-updated")
                        .replace("%name%", titleManager.getDisplayName(player))));
                player.closeInventory();
            } else if (event.getRawSlot() == 26) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TitleMenuHolder
                || event.getInventory().getHolder() instanceof ColorMenuHolder) {
            event.setCancelled(true);
        }
    }
}
