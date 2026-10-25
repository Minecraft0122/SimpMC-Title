package cn.simpmc.title.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds presets without rewriting existing title definitions, comments or unrelated settings. */
public final class PresetConfigUpdater {
    public static final String DEFAULT_TITLE_ID = "default_player";
    public static final String NEW_PLAYER_TITLE_ID = "new_player_7d";

    private PresetConfigUpdater() { }

    public static void apply(JavaPlugin plugin) {
        Path configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            String original = Files.readString(configFile, StandardCharsets.UTF_8);
            String updated = upgrade(original);
            if (updated.equals(original)) {
                return;
            }
            Path temporaryFile = Files.createTempFile(configFile.getParent(), "config-", ".yml.tmp");
            try {
                Files.writeString(temporaryFile, updated, StandardCharsets.UTF_8);
                try {
                    Files.move(temporaryFile, configFile, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    Files.move(temporaryFile, configFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
            plugin.getLogger().info("已补充预设称号配置，保留原有称号和其他设置。");
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().severe("更新预设称号配置失败，原文件未修改: " + exception.getMessage());
        }
    }

    static String upgrade(String original) throws InvalidConfigurationException {
        YamlConfiguration config = YamlConfigEditor.parse(original);
        String updated = original;
        // Empty was the 1.3 default. Migrate it once; later explicit disabling remains possible.
        if (!config.getBoolean("presets.initialized", false)) {
            if (config.getString("display.default-title", "").isBlank()) {
                updated = YamlConfigEditor.put(updated, "display.default-title", DEFAULT_TITLE_ID);
            }
            updated = YamlConfigEditor.put(updated, "presets.initialized", true);
        }
        if (!config.contains("titles." + DEFAULT_TITLE_ID)) {
            updated = YamlConfigEditor.put(updated, "titles." + DEFAULT_TITLE_ID,
                    title("&7[玩家]", "所有玩家均可使用；聊天默认保底称号，Tab 默认隐藏", "PLAYER_HEAD", 0));
        }
        if (!config.contains("titles." + NEW_PLAYER_TITLE_ID)) {
            updated = YamlConfigEditor.put(updated, "titles." + NEW_PLAYER_TITLE_ID,
                    title("&7[&f初来乍到&7]", "新人首次进服默认佩戴，7天后自动回收；可手动取消或更换", "NAME_TAG", 7));
        }
        if (!config.contains("display.tab.mode")) {
            updated = YamlConfigEditor.put(updated, "display.tab.mode", "auto");
        }
        if (!config.contains("display.tab.hide-player-title")) {
            updated = YamlConfigEditor.put(updated, "display.tab.hide-player-title", true);
        }
        if (!config.contains("messages.title-cleared-chat-required")) {
            updated = YamlConfigEditor.put(updated, "messages.title-cleared-chat-required",
                    "&a已取消个人称号；聊天仍显示玩家称号，称号组仍优先生效。");
        }
        return updated;
    }

    public static String appendCustomTitle(String original, String id, String display)
            throws InvalidConfigurationException {
        Map<String, Object> title = title(display, "", "NAME_TAG", 0);
        title.put("purchase", Map.of("enabled", true, "days", 0, "gold", -1, "internal", -1));
        return YamlConfigEditor.put(original, "titles." + id, title);
    }

    private static Map<String, Object> title(String display, String description, String icon, int days) {
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("display", display);
        title.put("description", description);
        title.put("icon", icon);
        if (days > 0) {
            title.put("new-player-days", days);
        }
        title.put("purchase", Map.of("enabled", false, "days", 0, "gold", -1, "internal", -1));
        return title;
    }
}
