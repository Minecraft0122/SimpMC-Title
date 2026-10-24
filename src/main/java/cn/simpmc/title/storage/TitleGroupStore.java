package cn.simpmc.title.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class TitleGroupStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public TitleGroupStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "groups.yml");
        reload();
    }

    public synchronized void reload() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录。");
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized boolean create(String group) {
        String path = path(group);
        if (data.contains(path)) {
            return false;
        }
        data.set(path + ".title", "");
        data.set(path + ".members", Set.of());
        save();
        return true;
    }

    public synchronized boolean delete(String group) {
        String path = path(group);
        if (!data.contains(path)) {
            return false;
        }
        data.set(path, null);
        save();
        return true;
    }

    public synchronized boolean setTitle(String group, String title) {
        String path = path(group);
        if (!data.contains(path)) {
            return false;
        }
        data.set(path + ".title", title);
        save();
        return true;
    }

    public synchronized boolean addMember(String group, UUID uuid) {
        String path = path(group);
        if (!data.contains(path)) {
            return false;
        }
        ConfigurationSection groups = data.getConfigurationSection("groups");
        if (groups != null) {
            for (String existing : groups.getKeys(false)) {
                Set<String> existingMembers = new LinkedHashSet<>(
                        data.getStringList("groups." + existing + ".members"));
                if (!existing.equalsIgnoreCase(group) && existingMembers.remove(uuid.toString())) {
                    data.set("groups." + existing + ".members", existingMembers.stream().toList());
                }
            }
        }
        Set<String> members = new LinkedHashSet<>(data.getStringList(path + ".members"));
        boolean changed = members.add(uuid.toString());
        if (changed) {
            data.set(path + ".members", members.stream().toList());
            save();
        }
        return changed;
    }

    public synchronized boolean removeMember(String group, UUID uuid) {
        String path = path(group);
        if (!data.contains(path)) {
            return false;
        }
        Set<String> members = new LinkedHashSet<>(data.getStringList(path + ".members"));
        boolean changed = members.remove(uuid.toString());
        if (changed) {
            data.set(path + ".members", members.stream().toList());
            save();
        }
        return changed;
    }

    public synchronized String getTitle(UUID uuid) {
        String group = getGroup(uuid);
        return group == null ? "" : data.getString("groups." + group + ".title", "");
    }

    public synchronized String getGroup(UUID uuid) {
        ConfigurationSection groups = data.getConfigurationSection("groups");
        if (groups == null) {
            return null;
        }
        String member = uuid.toString();
        for (String group : groups.getKeys(false)) {
            if (data.getStringList("groups." + group + ".members").contains(member)) {
                return group;
            }
        }
        return null;
    }

    public synchronized Set<String> getGroups() {
        ConfigurationSection groups = data.getConfigurationSection("groups");
        return groups == null ? Set.of() : new LinkedHashSet<>(groups.getKeys(false));
    }

    private String path(String group) {
        return "groups." + group.toLowerCase(java.util.Locale.ROOT);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("保存 groups.yml 失败: " + exception.getMessage());
        }
    }
}
