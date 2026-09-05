package cn.simpmc.title;

import cn.simpmc.title.model.PlayerProfile;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.storage.ProfileStore;
import cn.simpmc.title.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class TitleManager {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final String NO_TITLE = "__none__";

    private final SimpMCTitlePlugin plugin;
    private final ProfileStore store;
    private final Map<UUID, PlayerProfile> onlineProfiles = new ConcurrentHashMap<>();
    private final Map<String, TitleDefinition> titles = new LinkedHashMap<>();

    public TitleManager(SimpMCTitlePlugin plugin, ProfileStore store) {
        this.plugin = plugin;
        this.store = store;
        reloadTitles();
    }

    public void reload() {
        plugin.reloadConfig();
        store.reload();
        reloadTitles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyDisplay(player);
        }
    }

    private void reloadTitles() {
        titles.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("titles");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = "titles." + id;
            String display = plugin.getConfig().getString(path + ".display", id);
            String description = plugin.getConfig().getString(path + ".description", "");
            String permission = plugin.getConfig().getString(path + ".permission", "");
            Material icon = Material.matchMaterial(plugin.getConfig().getString(path + ".icon", "NAME_TAG"));
            if (icon == null) {
                icon = Material.NAME_TAG;
            }
            titles.put(id.toLowerCase(), new TitleDefinition(id.toLowerCase(), display, description, permission, icon));
        }
    }

    public void handleJoin(Player player) {
        PlayerProfile profile = store.load(player.getUniqueId());
        onlineProfiles.put(player.getUniqueId(), profile);
        applyDisplay(player);
    }

    public void handleQuit(Player player) {
        PlayerProfile profile = onlineProfiles.remove(player.getUniqueId());
        if (profile != null) {
            store.save(profile);
        }
    }

    public PlayerProfile getProfile(Player player) {
        return onlineProfiles.computeIfAbsent(player.getUniqueId(), key -> store.load(key));
    }

    public PlayerProfile getStoredProfile(UUID uuid) {
        PlayerProfile online = onlineProfiles.get(uuid);
        return online == null ? store.load(uuid) : online;
    }

    public Map<String, TitleDefinition> getTitles() {
        return Collections.unmodifiableMap(titles);
    }

    public List<TitleDefinition> getTitleList() {
        return Collections.unmodifiableList(new ArrayList<>(titles.values()));
    }

    public TitleDefinition getTitle(String id) {
        return id == null ? null : titles.get(id.toLowerCase());
    }

    public boolean canUse(Player player, TitleDefinition title) {
        if (title == null) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        return player.hasPermission("simpmctitle.title.*")
                || player.hasPermission("simpmctitle.admin")
                || title.getPermission().isBlank()
                || player.hasPermission(title.getPermission())
                || profile.ownsTitle(title.getId());
    }

    public boolean equip(Player player, String id) {
        TitleDefinition title = getTitle(id);
        if (!canUse(player, title)) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setActiveTitle(title.getId());
        store.save(profile);
        applyDisplay(player);
        return true;
    }

    public boolean forceEquip(Player player, String id) {
        TitleDefinition title = getTitle(id);
        if (title == null) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setActiveTitle(title.getId());
        store.save(profile);
        applyDisplay(player);
        return true;
    }

    public void clearTitle(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.setActiveTitle(NO_TITLE);
        store.save(profile);
        applyDisplay(player);
    }

    public boolean grant(Player player, String id, int days) {
        TitleDefinition title = getTitle(id);
        if (title == null) {
            return false;
        }
        long expiresAt = days <= 0 ? 0L : System.currentTimeMillis() + days * DAY_MILLIS;
        PlayerProfile profile = getProfile(player);
        profile.grantTitle(title.getId(), expiresAt);
        store.save(profile);
        return true;
    }

    public boolean take(Player player, String id) {
        TitleDefinition title = getTitle(id);
        if (title == null) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        boolean existed = profile.getOwnedTitles().remove(title.getId()) != null;
        if (title.getId().equals(profile.getActiveTitle())) {
            profile.setActiveTitle("");
        }
        store.save(profile);
        applyDisplay(player);
        return existed;
    }

    public boolean updateNickname(Player player, String rawNickname) {
        String nickname = TextUtil.cleanNickname(rawNickname);
        int maxLength = plugin.getConfig().getInt("nickname.max-length", 16);
        String pattern = plugin.getConfig().getString("nickname.pattern", "^[\\p{L}\\p{N}_\\- ]+$");
        if (nickname.isEmpty() || nickname.length() > maxLength || !Pattern.matches(pattern, nickname)) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setNickname(nickname);
        store.save(profile);
        applyDisplay(player);
        return true;
    }

    public void resetNickname(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.setNickname("");
        profile.setNameColor(plugin.getConfig().getString("nickname.default-color", "<white>"));
        store.save(profile);
        applyDisplay(player);
    }

    public boolean updateColor(Player player, String rawColor) {
        String color = TextUtil.normalizeColor(rawColor);
        if (color == null) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setNameColor(color);
        store.save(profile);
        applyDisplay(player);
        return true;
    }

    public String getActiveTitleDisplay(Player player) {
        PlayerProfile profile = getProfile(player);
        if (NO_TITLE.equals(profile.getActiveTitle())) {
            return "";
        }
        TitleDefinition active = getTitle(profile.getActiveTitle());
        if (active != null && canUse(player, active)) {
            return active.getDisplay();
        }
        String defaultId = plugin.getConfig().getString("display.default-title", "");
        TitleDefinition defaultTitle = getTitle(defaultId);
        return defaultTitle == null ? "" : defaultTitle.getDisplay();
    }

    public String getActiveTitleDisplay(UUID uuid) {
        PlayerProfile profile = getStoredProfile(uuid);
        if (NO_TITLE.equals(profile.getActiveTitle())) {
            return "";
        }
        TitleDefinition active = getTitle(profile.getActiveTitle());
        if (active != null) {
            return active.getDisplay();
        }
        String defaultId = plugin.getConfig().getString("display.default-title", "");
        TitleDefinition defaultTitle = getTitle(defaultId);
        return defaultTitle == null ? "" : defaultTitle.getDisplay();
    }

    public String getEffectiveName(Player player) {
        PlayerProfile profile = getProfile(player);
        return profile.getNickname().isBlank() ? player.getName() : profile.getNickname();
    }

    public String getEffectiveName(UUID uuid, String fallbackName) {
        PlayerProfile profile = getStoredProfile(uuid);
        return profile.getNickname().isBlank() ? fallbackName : profile.getNickname();
    }

    public String getDisplayName(Player player) {
        PlayerProfile profile = getProfile(player);
        return TextUtil.renderName(profile.getNameColor(), getEffectiveName(player));
    }

    public String getDisplayName(UUID uuid, String fallbackName) {
        PlayerProfile profile = getStoredProfile(uuid);
        return TextUtil.renderName(profile.getNameColor(), getEffectiveName(uuid, fallbackName));
    }

    public String getNameColor(UUID uuid) {
        return getStoredProfile(uuid).getNameColor();
    }

    public String getFullDisplay(Player player) {
        String title = TextUtil.colorize(getActiveTitleDisplay(player));
        String name = getDisplayName(player);
        String separator = TextUtil.colorize(plugin.getConfig().getString("display.title-separator", " "));
        return title + separator + name;
    }

    public String getFullDisplay(UUID uuid, String fallbackName) {
        String title = TextUtil.colorize(getActiveTitleDisplay(uuid));
        String name = getDisplayName(uuid, fallbackName);
        String separator = TextUtil.colorize(plugin.getConfig().getString("display.title-separator", " "));
        return title + separator + name;
    }

    public void applyDisplay(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.purgeExpired();
        String name = getDisplayName(player);
        String fullDisplay = getFullDisplay(player);
        player.setDisplayName(name);

        if (plugin.getConfig().getBoolean("display.tab.enabled", true)) {
            String format = plugin.getConfig().getString("display.tab.format", "%title%%name%");
            player.setPlayerListName(TextUtil.colorize(format
                    .replace("%title%", TextUtil.colorize(getActiveTitleDisplay(player)))
                    .replace("%name%", name)));
        } else {
            player.setPlayerListName(name);
        }

        if (plugin.getConfig().getBoolean("display.head.enabled", true)) {
            String format = plugin.getConfig().getString("display.head.format", "%title%%name%");
            String head = format.replace("%title%", TextUtil.colorize(getActiveTitleDisplay(player)))
                    .replace("%name%", name);
            player.setCustomName(TextUtil.colorize(head));
            player.setCustomNameVisible(true);
        } else {
            player.setCustomName(null);
            player.setCustomNameVisible(false);
        }
    }

    public void formatChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("display.chat.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        String format = plugin.getConfig().getString("display.chat.format", "%title%%name% &8» &r%2$s");
        format = format.replace("%title%", TextUtil.colorize(getActiveTitleDisplay(player)))
                .replace("%name%", getDisplayName(player));
        event.setFormat(TextUtil.escapeFormat(TextUtil.colorize(format)));
    }
}
