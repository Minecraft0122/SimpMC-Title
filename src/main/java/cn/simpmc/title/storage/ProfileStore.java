package cn.simpmc.title.storage;

import cn.simpmc.title.model.PlayerProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public final class ProfileStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public ProfileStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录。");
        }
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("无法创建 players.yml。");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("创建 players.yml 失败: " + exception.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized PlayerProfile load(UUID uuid) {
        PlayerProfile profile = new PlayerProfile(uuid);
        String root = "players." + uuid;
        profile.setNickname(data.getString(root + ".nickname", ""));
        profile.setNameColor(data.getString(root + ".name-color", "<white>"));
        profile.setActiveTitle(data.getString(root + ".active-title", ""));
        profile.setCustomTitle(data.getString(root + ".custom-title", ""));
        profile.setActiveTitleExpiresAt(data.getLong(root + ".active-title-expires-at", 0L));
        long initialBalance = plugin.getConfig().getLong("economy.internal.initial-balance", 0L);
        profile.setInternalBalance(data.contains(root + ".internal-balance")
                ? data.getLong(root + ".internal-balance") : Math.max(0L, initialBalance));

        ConfigurationSection titles = data.getConfigurationSection(root + ".titles");
        if (titles != null) {
            for (String titleId : titles.getKeys(false)) {
                profile.grantTitle(titleId, titles.getLong(titleId));
            }
        }
        profile.purgeExpired();
        return profile;
    }

    public synchronized void save(PlayerProfile profile) {
        String root = "players." + profile.getUuid();
        data.set(root, null);
        data.set(root + ".nickname", profile.getNickname());
        data.set(root + ".name-color", profile.getNameColor());
        data.set(root + ".active-title", profile.getActiveTitle());
        data.set(root + ".custom-title", profile.getCustomTitle());
        data.set(root + ".active-title-expires-at", profile.getActiveTitleExpiresAt());
        data.set(root + ".internal-balance", profile.getInternalBalance());
        for (Map.Entry<String, Long> entry : profile.getOwnedTitles().entrySet()) {
            data.set(root + ".titles." + entry.getKey(), entry.getValue());
        }
        saveFile();
    }

    public synchronized void saveFile() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("保存 players.yml 失败: " + exception.getMessage());
        }
    }
}
