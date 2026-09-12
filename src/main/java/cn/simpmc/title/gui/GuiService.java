package cn.simpmc.title.gui;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.model.PurchaseMethod;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiService {
    private final SimpMCTitlePlugin plugin;
    private final TitleManager titleManager;

    public GuiService(SimpMCTitlePlugin plugin, TitleManager titleManager) {
        this.plugin = plugin;
        this.titleManager = titleManager;
    }

    public void openTitles(Player player) {
        TitleMenuHolder holder = new TitleMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, TextUtil.colorize("&0SimpMC-Title | 称号"));
        holder.setInventory(inventory);
        int slot = 0;
        for (TitleDefinition title : titleManager.getTitleList()) {
            if (slot >= 45) {
                break;
            }
            ItemStack item = new ItemStack(title.getIcon() == null ? Material.NAME_TAG : title.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.colorize(title.getDisplay()));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7" + title.getDescription()));
            lore.add("");
            if (titleManager.canUse(player, title)) {
                lore.add(TextUtil.colorize("&a点击装备"));
            } else if (title.isPurchaseEnabled()) {
                lore.add(TextUtil.colorize("&e请前往称号商城购买"));
            } else if (!title.getPermission().isBlank()) {
                lore.add(TextUtil.colorize("&c需要权限: &f" + title.getPermission()));
            } else {
                lore.add(TextUtil.colorize("&c尚未解锁"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.put(slot, title.getId());
            slot++;
        }
        inventory.setItem(49, button(Material.BARRIER, "&c取消装备称号"));
        inventory.setItem(53, button(Material.ARROW, "&7关闭"));
        player.openInventory(inventory);
    }

    public void openShop(Player player) {
        ShopMenuHolder holder = new ShopMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, TextUtil.colorize("&0SimpMC-Title | 称号商城"));
        holder.setInventory(inventory);
        int slot = 0;
        for (TitleDefinition title : titleManager.getTitleList()) {
            if (slot >= 45) {
                break;
            }
            ItemStack item = new ItemStack(title.getIcon() == null ? Material.NAME_TAG : title.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.colorize(title.getDisplay()));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7" + title.getDescription()));
            lore.add("");
            if (titleManager.canUse(player, title)) {
                lore.add(TextUtil.colorize("&a你已拥有或有权限使用"));
            } else if (!title.isPurchaseEnabled()) {
                lore.add(TextUtil.colorize("&c当前不可购买"));
            } else {
                if (title.isGoldPurchasable()) {
                    String state = titleManager.isGoldPurchaseEnabled() ? "&6可用" : "&8已关闭";
                    lore.add(TextUtil.colorize("&6金币: &f" + TextUtil.formatMoney(title.getGoldPrice()) + " &8(" + state + "&8)"));
                }
                if (title.isInternalPurchasable()) {
                    String state = titleManager.isInternalPurchaseEnabled() ? "&a可用" : "&8已关闭";
                    lore.add(TextUtil.colorize("&a" + titleManager.getInternalCurrencyName() + ": &f"
                            + title.getInternalPrice() + " &8(" + state + "&8)"));
                }
                lore.add(TextUtil.colorize("&e点击选择购买方式"));
            }
            if (title.getPurchaseDays() > 0) {
                lore.add(TextUtil.colorize("&7有效期: &f" + title.getPurchaseDays() + " 天"));
            } else if (title.isPurchaseEnabled()) {
                lore.add(TextUtil.colorize("&7有效期: &f永久"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.put(slot, title.getId());
            slot++;
        }
        inventory.setItem(49, button(Material.BOOK, "&b余额与兑换",
                List.of("&7使用命令查看余额: &f/smtitle balance", "&7金币兑换: &f/smtitle exchange <数量>")));
        inventory.setItem(53, button(Material.ARROW, "&7关闭"));
        player.openInventory(inventory);
    }

    public void openPurchase(Player player, String titleId) {
        TitleDefinition title = titleManager.getTitle(titleId);
        if (title == null) {
            plugin.send(player, "title-not-found", java.util.Map.of("%id%", titleId));
            return;
        }
        if (!title.isPurchaseEnabled()) {
            plugin.send(player, "purchase-not-for-sale");
            return;
        }
        PurchaseMenuHolder holder = new PurchaseMenuHolder(title.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                TextUtil.colorize("&0购买 | " + title.getDisplay()));
        holder.setInventory(inventory);

        ItemStack info = new ItemStack(title.getIcon() == null ? Material.NAME_TAG : title.getIcon());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(TextUtil.colorize(title.getDisplay()));
        infoMeta.setLore(List.of(
                TextUtil.colorize("&7" + title.getDescription()),
                TextUtil.colorize("&7有效期: &f" + (title.getPurchaseDays() <= 0 ? "永久" : title.getPurchaseDays() + " 天")),
                TextUtil.colorize("&8选择一种支付方式")));
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        if (title.isGoldPurchasable()) {
            boolean enabled = titleManager.isGoldPurchaseEnabled();
            ItemStack gold = button(Material.GOLD_INGOT, enabled ? "&6金币购买" : "&8金币购买（已关闭）",
                    List.of(TextUtil.colorize("&7价格: &f" + TextUtil.formatMoney(title.getGoldPrice())),
                            TextUtil.colorize(enabled ? "&e点击购买" : "&c管理员已关闭金币购买")));
            inventory.setItem(11, gold);
            holder.put(11, PurchaseMethod.GOLD);
        }
        if (title.isInternalPurchasable()) {
            boolean enabled = titleManager.isInternalPurchaseEnabled();
            ItemStack internal = button(Material.EMERALD, enabled ? "&a内部货币购买" : "&8内部货币购买（已关闭）",
                    List.of(TextUtil.colorize("&7价格: &f" + title.getInternalPrice() + " "
                            + titleManager.getInternalCurrencyName()),
                            TextUtil.colorize(enabled ? "&e点击购买" : "&c管理员已关闭内部货币购买")));
            inventory.setItem(15, internal);
            holder.put(15, PurchaseMethod.INTERNAL);
        }
        inventory.setItem(22, button(Material.ARROW, "&7返回商城"));
        inventory.setItem(26, button(Material.BARRIER, "&c关闭"));
        player.openInventory(inventory);
    }

    public void openColors(Player player) {
        ColorMenuHolder holder = new ColorMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, TextUtil.colorize("&0SimpMC-Title | 名字颜色"));
        holder.setInventory(inventory);
        List<String> colors = plugin.getConfig().getStringList("colors");
        int slot = 0;
        for (String color : colors) {
            if (slot >= 18) {
                break;
            }
            ItemStack item = new ItemStack(Material.INK_SAC);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.renderName(color, "SimpMC"));
            meta.setLore(List.of(TextUtil.colorize("&7点击使用这个颜色")));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.put(slot, color);
            slot++;
        }
        inventory.setItem(22, button(Material.BARRIER, "&c恢复白色"));
        inventory.setItem(26, button(Material.ARROW, "&7关闭"));
        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String name) {
        return button(material, name, List.of());
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TextUtil.colorize(name));
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }
}
