package cn.simpmc.title.gui;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
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
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TextUtil.colorize(name));
        item.setItemMeta(meta);
        return item;
    }
}
