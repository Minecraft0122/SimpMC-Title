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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuiService {
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PAGE_SIZE = CONTENT_SLOTS.length;
    private final SimpMCTitlePlugin plugin;
    private final TitleManager titleManager;

    public GuiService(SimpMCTitlePlugin plugin, TitleManager titleManager) {
        this.plugin = plugin;
        this.titleManager = titleManager;
    }

    public void openTitles(Player player) {
        openTitles(player, 0);
    }

    public void openTitles(Player player, int requestedPage) {
        List<TitleDefinition> titles = titleManager.getTitleList();
        int page = clampPage(requestedPage, titles.size());
        int pages = pageCount(titles.size());
        TitleMenuHolder holder = new TitleMenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                TextUtil.colorize("&0称号仓库 &8| &7" + (page + 1) + "/" + pages));
        holder.setInventory(inventory);
        decorate(inventory, Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inventory.setItem(4, profileItem(player, "&b我的称号仓库", List.of(
                "&7当前称号: " + activeTitleDisplay(player),
                "&7已解锁: &f" + unlockedCount(player) + "&7/&f" + titles.size(),
                "",
                "&8选择下方称号即可装备")));

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, titles.size()); index++) {
            TitleDefinition title = titles.get(index);
            int slot = CONTENT_SLOTS[index - start];
            ItemStack item = new ItemStack(title.getIcon() == null ? Material.NAME_TAG : title.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.colorize(title.getDisplay()));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&8[ &c称号说明 &8]"));
            lore.add(TextUtil.colorize("&7" + title.getDescription()));
            lore.add("");
            boolean active = title.getId().equalsIgnoreCase(titleManager.getActiveTitleId(player.getUniqueId()));
            if (active) {
                lore.add(TextUtil.colorize("&8[ &a当前状态 &8]"));
                lore.add(TextUtil.colorize("&a✔ 正在使用"));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else if (titleManager.canUse(player, title)) {
                lore.add(TextUtil.colorize("&8[ &a当前状态 &8]"));
                lore.add(TextUtil.colorize("&e➤ 点击使用"));
            } else if (title.isPurchaseEnabled()) {
                lore.add(TextUtil.colorize("&8[ &c当前状态 &8]"));
                lore.add(TextUtil.colorize("&c✘ 尚未拥有"));
                lore.add(TextUtil.colorize("&7可前往称号商城购买"));
            } else if (!title.getPermission().isBlank()) {
                lore.add(TextUtil.colorize("&8[ &c解锁条件 &8]"));
                lore.add(TextUtil.colorize("&c需要权限: &f" + title.getPermission()));
            } else {
                lore.add(TextUtil.colorize("&c尚未解锁"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.put(slot, title.getId());
        }
        navigation(inventory, page, pages);
        inventory.setItem(47, button(Material.CHEST, "&6称号商城", List.of("&7购买新的称号", "&e点击进入")));
        inventory.setItem(48, button(Material.INK_SAC, "&d名字颜色", List.of("&7设置玩家名字颜色", "&e点击进入")));
        inventory.setItem(49, button(Material.BARRIER, "&c取消当前称号", List.of("&7恢复为无称号状态")));
        inventory.setItem(51, button(Material.OAK_DOOR, "&c关闭菜单"));
        player.openInventory(inventory);
    }

    public void openShop(Player player) {
        openShop(player, 0);
    }

    public void openShop(Player player, int requestedPage) {
        List<TitleDefinition> titles = titleManager.getTitleList();
        int page = clampPage(requestedPage, titles.size());
        int pages = pageCount(titles.size());
        ShopMenuHolder holder = new ShopMenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                TextUtil.colorize("&0称号商城 &8| &7" + (page + 1) + "/" + pages));
        holder.setInventory(inventory);
        decorate(inventory, Material.GRAY_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE);
        inventory.setItem(4, profileItem(player, "&e称号商城", List.of(
                "&6金币: &f" + TextUtil.formatMoney(titleManager.getGoldBalance(player)),
                "&a" + titleManager.getInternalCurrencyName() + ": &f" + titleManager.getInternalBalance(player),
                "",
                "&8点击商品选择支付方式")));

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, titles.size()); index++) {
            TitleDefinition title = titles.get(index);
            int slot = CONTENT_SLOTS[index - start];
            ItemStack item = new ItemStack(title.getIcon() == null ? Material.NAME_TAG : title.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.colorize(title.getDisplay()));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&8[ &c称号说明 &8]"));
            lore.add(TextUtil.colorize("&7" + title.getDescription()));
            lore.add("");
            if (titleManager.canUse(player, title)) {
                lore.add(TextUtil.colorize("&8[ &a当前状态 &8]"));
                lore.add(TextUtil.colorize("&a✔ 已拥有"));
            } else if (!title.isPurchaseEnabled()) {
                lore.add(TextUtil.colorize("&8[ &c当前状态 &8]"));
                lore.add(TextUtil.colorize("&c✘ 暂不出售"));
            } else {
                lore.add(TextUtil.colorize("&8[ &c称号价格 &8]"));
                if (title.isGoldPurchasable()) {
                    String state = titleManager.isGoldPurchaseEnabled() ? "&a可用" : "&c关闭";
                    lore.add(TextUtil.colorize("&6▪ 金币: &f" + TextUtil.formatMoney(title.getGoldPrice()) + " &8[" + state + "&8]"));
                }
                if (title.isInternalPurchasable()) {
                    String state = titleManager.isInternalPurchaseEnabled() ? "&a可用" : "&c关闭";
                    lore.add(TextUtil.colorize("&a▪ " + titleManager.getInternalCurrencyName() + ": &f"
                            + title.getInternalPrice() + " &8[" + state + "&8]"));
                }
            }
            if (title.getPurchaseDays() > 0) {
                lore.add(TextUtil.colorize("&7▪ 有效期: &f" + title.getPurchaseDays() + " 天"));
            } else if (title.isPurchaseEnabled()) {
                lore.add(TextUtil.colorize("&7▪ 有效期: &f永久"));
            }
            if (!titleManager.canUse(player, title) && title.isPurchaseEnabled()) {
                lore.add("");
                lore.add(TextUtil.colorize("&e➤ 点击选择支付方式"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.put(slot, title.getId());
        }
        navigation(inventory, page, pages);
        inventory.setItem(47, button(Material.ENDER_CHEST, "&b我的称号", List.of("&7查看并装备已解锁称号", "&e点击进入")));
        inventory.setItem(49, button(Material.GOLD_INGOT, "&6余额与兑换", List.of(
                "&6金币: &f" + TextUtil.formatMoney(titleManager.getGoldBalance(player)),
                "&a" + titleManager.getInternalCurrencyName() + ": &f" + titleManager.getInternalBalance(player),
                "",
                "&7兑换命令: &f/smtitle exchange <数量|all>",
                "&e点击在聊天栏查看余额")));
        inventory.setItem(51, button(Material.OAK_DOOR, "&c关闭菜单"));
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
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        ItemStack info = new ItemStack(title.getIcon() == null ? Material.NAME_TAG : title.getIcon());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(TextUtil.colorize(title.getDisplay()));
        infoMeta.setLore(List.of(
                TextUtil.colorize("&8[ &c称号说明 &8]"),
                TextUtil.colorize("&7" + title.getDescription()),
                "",
                TextUtil.colorize("&7有效期: &f" + (title.getPurchaseDays() <= 0 ? "永久" : title.getPurchaseDays() + " 天")),
                TextUtil.colorize("&e请选择一种支付方式")));
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        if (title.isGoldPurchasable()) {
            boolean enabled = titleManager.isGoldPurchaseEnabled();
            ItemStack gold = button(Material.GOLD_INGOT, enabled ? "&6金币购买" : "&8金币购买（已关闭）",
                    List.of(TextUtil.colorize("&7价格: &f" + TextUtil.formatMoney(title.getGoldPrice())),
                            TextUtil.colorize("&7余额: &f" + TextUtil.formatMoney(titleManager.getGoldBalance(player))),
                            "",
                            TextUtil.colorize(enabled ? "&e点击购买" : "&c管理员已关闭金币购买")));
            inventory.setItem(11, gold);
            holder.put(11, PurchaseMethod.GOLD);
        }
        if (title.isInternalPurchasable()) {
            boolean enabled = titleManager.isInternalPurchaseEnabled();
            ItemStack internal = button(Material.EMERALD, enabled ? "&a内部货币购买" : "&8内部货币购买（已关闭）",
                    List.of(TextUtil.colorize("&7价格: &f" + title.getInternalPrice() + " "
                            + titleManager.getInternalCurrencyName()),
                            TextUtil.colorize("&7余额: &f" + titleManager.getInternalBalance(player) + " "
                                    + titleManager.getInternalCurrencyName()),
                            "",
                            TextUtil.colorize(enabled ? "&e点击购买" : "&c管理员已关闭内部货币购买")));
            inventory.setItem(15, internal);
            holder.put(15, PurchaseMethod.INTERNAL);
        }
        inventory.setItem(22, button(Material.ARROW, "&7返回商城"));
        inventory.setItem(26, button(Material.BARRIER, "&c关闭"));
        player.openInventory(inventory);
    }

    public void openColors(Player player) {
        openColors(player, 0);
    }

    public void openColors(Player player, int requestedPage) {
        List<String> colors = plugin.getConfig().getStringList("colors");
        int page = clampPage(requestedPage, colors.size());
        int pages = pageCount(colors.size());
        ColorMenuHolder holder = new ColorMenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                TextUtil.colorize("&0名字颜色 &8| &7" + (page + 1) + "/" + pages));
        holder.setInventory(inventory);
        decorate(inventory, Material.GRAY_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE);
        inventory.setItem(4, profileItem(player, "&d名字颜色", List.of(
                "&7当前效果: " + titleManager.getDisplayName(player),
                "",
                "&8选择下方颜色即可立即应用")));
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, colors.size()); index++) {
            String color = colors.get(index);
            int slot = CONTENT_SLOTS[index - start];
            ItemStack item = new ItemStack(colorMaterial(color));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.renderName(color, "SimpMC"));
            meta.setLore(List.of(
                    TextUtil.colorize("&7预览: ") + TextUtil.renderName(color, player.getName()),
                    "",
                    TextUtil.colorize("&e➤ 点击使用这个颜色")));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.put(slot, color);
        }
        navigation(inventory, page, pages);
        inventory.setItem(47, button(Material.ENDER_CHEST, "&b我的称号", List.of("&7返回称号仓库", "&e点击进入")));
        inventory.setItem(49, button(Material.MILK_BUCKET, "&f恢复默认颜色", List.of("&7将名字颜色恢复为白色")));
        inventory.setItem(51, button(Material.OAK_DOOR, "&c关闭菜单"));
        player.openInventory(inventory);
    }

    private void decorate(Inventory inventory, Material frame, Material accent) {
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, pane(slot == 4 ? accent : frame));
        }
        for (int slot : new int[]{9, 17, 18, 26, 27, 35, 36, 44}) {
            inventory.setItem(slot, pane(frame));
        }
    }

    private void navigation(Inventory inventory, int page, int pages) {
        if (page > 0) {
            inventory.setItem(45, button(Material.PAPER, "&e上一页", List.of("&7前往第 " + page + " 页")));
        }
        if (page + 1 < pages) {
            inventory.setItem(53, button(Material.PAPER, "&e下一页", List.of("&7前往第 " + (page + 2) + " 页")));
        }
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack pane = pane(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane);
        }
    }

    private ItemStack pane(Material material) {
        return button(material, " ");
    }

    private ItemStack profileItem(Player player, String name, List<String> lore) {
        return button(Material.PLAYER_HEAD, name, lore);
    }

    private String activeTitleDisplay(Player player) {
        String display = titleManager.getActiveTitleDisplay(player);
        return display.isEmpty() ? TextUtil.colorize("&c未装备") : display;
    }

    private int unlockedCount(Player player) {
        int count = 0;
        for (TitleDefinition title : titleManager.getTitleList()) {
            if (titleManager.canUse(player, title)) {
                count++;
            }
        }
        return count;
    }

    private int pageCount(int itemCount) {
        return Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int clampPage(int requestedPage, int itemCount) {
        return Math.max(0, Math.min(requestedPage, pageCount(itemCount) - 1));
    }

    private Material colorMaterial(String color) {
        String lower = color.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("red")) return Material.RED_DYE;
        if (lower.contains("gold") || lower.contains("orange")) return Material.ORANGE_DYE;
        if (lower.contains("yellow")) return Material.YELLOW_DYE;
        if (lower.contains("green")) return Material.LIME_DYE;
        if (lower.contains("aqua") || lower.contains("55ccff")) return Material.LIGHT_BLUE_DYE;
        if (lower.contains("blue")) return Material.BLUE_DYE;
        if (lower.contains("purple") || lower.contains("b36bff")) return Material.PURPLE_DYE;
        if (lower.contains("gray") || lower.contains("grey")) return Material.GRAY_DYE;
        if (lower.contains("black")) return Material.BLACK_DYE;
        return Material.WHITE_DYE;
    }

    private ItemStack button(Material material, String name) {
        return button(material, name, List.of());
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TextUtil.colorize(name));
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream().map(TextUtil::colorize).toList());
        }
        item.setItemMeta(meta);
        return item;
    }
}
