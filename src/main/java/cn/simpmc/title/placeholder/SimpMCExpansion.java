package cn.simpmc.title.placeholder;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.util.TextUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class SimpMCExpansion extends PlaceholderExpansion {
    private final SimpMCTitlePlugin plugin;
    private final TitleManager titleManager;

    public SimpMCExpansion(SimpMCTitlePlugin plugin) {
        this.plugin = plugin;
        this.titleManager = plugin.getTitleManager();
    }

    @Override
    public String getIdentifier() {
        return "simpmctitle";
    }

    @Override
    public String getAuthor() {
        return "SimpMC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        String fallbackName = player.getName() == null ? "" : player.getName();
        return switch (params.toLowerCase()) {
            case "title" -> TextUtil.colorize(resolve(player,
                    titleManager.getActiveTitleDisplay(player.getUniqueId())));
            case "title_plain" -> TextUtil.strip(resolve(player,
                    titleManager.getActiveTitleDisplay(player.getUniqueId())));
            case "nickname" -> titleManager.getEffectiveName(player.getUniqueId(), fallbackName);
            case "display_name" -> titleManager.getDisplayName(player.getUniqueId(), fallbackName);
            case "name_color" -> titleManager.getNameColor(player.getUniqueId());
            default -> null;
        };
    }

    private String resolve(OfflinePlayer player, String value) {
        if (value == null || value.contains("%simpmctitle_")) {
            return value == null ? "" : value;
        }
        return PlaceholderAPI.setPlaceholders(player, value);
    }
}
