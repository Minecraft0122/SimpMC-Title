package cn.simpmc.title.listener;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.gui.ColorMenuHolder;
import cn.simpmc.title.gui.PurchaseMenuHolder;
import cn.simpmc.title.gui.ShopMenuHolder;
import cn.simpmc.title.gui.TitleMenuHolder;
import cn.simpmc.title.model.PurchaseMethod;
import cn.simpmc.title.model.PurchaseResult;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.LinkedHashMap;
import java.util.Map;

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
                plugin.send(player, player.hasPermission(TitleManager.HIDE_CHAT_TITLE_PERMISSION)
                        ? "title-cleared" : "title-cleared-chat-required");
                player.closeInventory();
            } else if (event.getRawSlot() == 45 && holder.getPage() > 0) {
                plugin.getGuiService().openTitles(player, holder.getPage() - 1);
            } else if (event.getRawSlot() == 53 && isPageButton(event)) {
                plugin.getGuiService().openTitles(player, holder.getPage() + 1);
            } else if (event.getRawSlot() == 47) {
                plugin.getGuiService().openShop(player);
            } else if (event.getRawSlot() == 48) {
                plugin.getGuiService().openColors(player);
            } else if (event.getRawSlot() == 51) {
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
            } else if (event.getRawSlot() == 49) {
                titleManager.updateColor(player, "<white>");
                player.sendMessage(TextUtil.colorize(plugin.message("prefix") + plugin.message("color-updated")
                        .replace("%name%", titleManager.getDisplayName(player))));
                player.closeInventory();
            } else if (event.getRawSlot() == 45 && holder.getPage() > 0) {
                plugin.getGuiService().openColors(player, holder.getPage() - 1);
            } else if (event.getRawSlot() == 53 && isPageButton(event)) {
                plugin.getGuiService().openColors(player, holder.getPage() + 1);
            } else if (event.getRawSlot() == 47) {
                plugin.getGuiService().openTitles(player);
            } else if (event.getRawSlot() == 51) {
                player.closeInventory();
            }
        } else if (event.getInventory().getHolder() instanceof ShopMenuHolder holder) {
            event.setCancelled(true);
            String titleId = holder.getTitle(event.getRawSlot());
            if (titleId != null) {
                plugin.getGuiService().openPurchase(player, titleId);
            } else if (event.getRawSlot() == 45 && holder.getPage() > 0) {
                plugin.getGuiService().openShop(player, holder.getPage() - 1);
            } else if (event.getRawSlot() == 53 && isPageButton(event)) {
                plugin.getGuiService().openShop(player, holder.getPage() + 1);
            } else if (event.getRawSlot() == 47) {
                plugin.getGuiService().openTitles(player);
            } else if (event.getRawSlot() == 49) {
                Map<String, String> replacements = new LinkedHashMap<>();
                replacements.put("%gold%", TextUtil.formatMoney(titleManager.getGoldBalance(player)));
                replacements.put("%internal%", String.valueOf(titleManager.getInternalBalance(player)));
                replacements.put("%currency%", titleManager.getInternalCurrencyName());
                replacements.put("%vault%", titleManager.isEconomyAvailable() ? "已连接" : "未连接");
                plugin.send(player, "balance", replacements);
            } else if (event.getRawSlot() == 51) {
                player.closeInventory();
            }
        } else if (event.getInventory().getHolder() instanceof PurchaseMenuHolder holder) {
            event.setCancelled(true);
            PurchaseMethod method = holder.getMethod(event.getRawSlot());
            if (method != null) {
                PurchaseResult result = titleManager.purchase(player, holder.getTitleId(), method);
                TitleDefinition title = titleManager.getTitle(holder.getTitleId());
                plugin.sendPurchaseResult(player, result, title, method);
                if (result == PurchaseResult.SUCCESS) {
                    player.closeInventory();
                }
            } else if (event.getRawSlot() == 22) {
                plugin.getGuiService().openShop(player);
            } else if (event.getRawSlot() == 26) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TitleMenuHolder
                || event.getInventory().getHolder() instanceof ColorMenuHolder
                || event.getInventory().getHolder() instanceof ShopMenuHolder
                || event.getInventory().getHolder() instanceof PurchaseMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean isPageButton(InventoryClickEvent event) {
        return event.getCurrentItem() != null
                && event.getCurrentItem().getType() == org.bukkit.Material.PAPER;
    }
}
