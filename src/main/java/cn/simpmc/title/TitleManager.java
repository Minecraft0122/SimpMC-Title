package cn.simpmc.title;

import cn.simpmc.title.economy.EconomyService;
import cn.simpmc.title.logging.AuditLogger;
import cn.simpmc.title.model.ExchangeOutcome;
import cn.simpmc.title.model.ExchangeResult;
import cn.simpmc.title.model.PlayerProfile;
import cn.simpmc.title.model.PurchaseMethod;
import cn.simpmc.title.model.PurchaseResult;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.storage.ProfileStore;
import cn.simpmc.title.util.TextUtil;
import cn.simpmc.title.util.TitleContentPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class TitleManager {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final String NO_TITLE = "__none__";

    private final SimpMCTitlePlugin plugin;
    private final ProfileStore store;
    private final EconomyService economyService;
    private final AuditLogger auditLogger;
    private final TitleContentPolicy contentPolicy;
    private final Map<UUID, PlayerProfile> onlineProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, DisplaySnapshot> displaySnapshots = new ConcurrentHashMap<>();
    private volatile Map<String, TitleDefinition> titles = Map.of();
    private volatile boolean chatEnabled;
    private volatile String chatFormat;

    public TitleManager(SimpMCTitlePlugin plugin, ProfileStore store, EconomyService economyService,
                        AuditLogger auditLogger) {
        this.plugin = plugin;
        this.store = store;
        this.economyService = economyService;
        this.auditLogger = auditLogger;
        this.contentPolicy = new TitleContentPolicy(plugin);
        reloadTitles();
    }

    public void reload() {
        plugin.reloadConfig();
        contentPolicy.reload();
        economyService.refresh();
        auditLogger.reload();
        store.reload();
        reloadTitles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleDisplayUpdate(player);
        }
    }

    private void reloadTitles() {
        chatEnabled = plugin.getConfig().getBoolean("display.chat.enabled", true);
        chatFormat = plugin.getConfig().getString("display.chat.format", "%title%%name% &8» &r%2$s");
        Map<String, TitleDefinition> loadedTitles = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("titles");
        if (section == null) {
            titles = Map.of();
            return;
        }
        for (String configuredId : section.getKeys(false)) {
            String id = configuredId.toLowerCase(Locale.ROOT);
            String path = "titles." + configuredId;
            String display = plugin.getConfig().getString(path + ".display", id);
            TitleContentPolicy.Validation validation = contentPolicy.validate(display);
            if (!validation.valid()) {
                plugin.getLogger().warning("跳过称号 " + configuredId + "：" + validation.reason());
                continue;
            }
            String description = plugin.getConfig().getString(path + ".description", "");
            String permission = plugin.getConfig().getString(path + ".permission", "");
            Material icon = Material.matchMaterial(plugin.getConfig().getString(path + ".icon", "NAME_TAG"));
            if (icon == null) {
                icon = Material.NAME_TAG;
            }
            String purchasePath = path + ".purchase";
            boolean purchaseEnabled = plugin.getConfig().getBoolean(purchasePath + ".enabled", false);
            int purchaseDays = Math.max(0, plugin.getConfig().getInt(purchasePath + ".days", 0));
            double goldPrice = plugin.getConfig().getDouble(purchasePath + ".gold", -1D);
            if (!Double.isFinite(goldPrice) || goldPrice < 0D) {
                goldPrice = -1D;
            }
            long internalPrice = plugin.getConfig().getLong(purchasePath + ".internal", -1L);
            if (internalPrice < 0L) {
                internalPrice = -1L;
            }
            loadedTitles.put(id, new TitleDefinition(id, display, description, permission, icon,
                    purchaseEnabled, purchaseDays, goldPrice, internalPrice));
        }
        titles = Collections.unmodifiableMap(loadedTitles);
    }

    public void handleJoin(Player player) {
        PlayerProfile profile = store.load(player.getUniqueId());
        onlineProfiles.put(player.getUniqueId(), profile);
        if (profile.purgeExpired()) {
            store.save(profile);
        }
        scheduleDisplayUpdate(player);
    }

    public void handleQuit(Player player) {
        PlayerProfile profile = onlineProfiles.remove(player.getUniqueId());
        displaySnapshots.remove(player.getUniqueId());
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
        return titles;
    }

    public List<TitleDefinition> getTitleList() {
        return Collections.unmodifiableList(new ArrayList<>(titles.values()));
    }

    public TitleDefinition getTitle(String id) {
        return id == null ? null : titles.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean canUse(Player player, TitleDefinition title) {
        if (title == null) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        if (player.hasPermission("simpmctitle.title.*") || player.hasPermission("simpmctitle.admin")) {
            return true;
        }
        if (!title.getPermission().isBlank() && player.hasPermission(title.getPermission())) {
            return true;
        }
        if (profile.ownsTitle(title.getId())) {
            return true;
        }
        return !title.isPurchaseEnabled() && title.getPermission().isBlank();
    }

    public boolean equip(Player player, String id) {
        TitleDefinition title = getTitle(id);
        if (!canUse(player, title)) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setActiveTitle(title.getId());
        profile.setActiveTitleExpiresAt(0L);
        store.save(profile);
        applyDisplay(player);
        auditLogger.logPlayer("title.equip", player, null, Map.of(
                "title_id", title.getId(),
                "title", TextUtil.strip(title.getDisplay()),
                "result", "success"));
        return true;
    }

    public boolean forceEquip(Player player, String id) {
        TitleDefinition title = getTitle(id);
        if (title == null) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setCustomTitle("");
        profile.setActiveTitle(title.getId());
        profile.setActiveTitleExpiresAt(0L);
        store.save(profile);
        applyDisplay(player);
        return true;
    }

    public String registerCustomTitle(String display) {
        TitleContentPolicy.Validation validation = contentPolicy.validate(display);
        if (!validation.valid()) {
            return null;
        }
        for (TitleDefinition title : titles.values()) {
            if (title.getDisplay().equals(display)) {
                return title.getId();
            }
        }

        String baseId = "custom-" + shortHash(display);
        String id = baseId;
        int suffix = 2;
        while (getTitle(id) != null && !getTitle(id).getDisplay().equals(display)) {
            id = baseId + "-" + suffix++;
        }
        if (getTitle(id) != null) {
            return id;
        }

        Path configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            String contents = Files.readString(configFile, StandardCharsets.UTF_8);
            String updated = insertTitleEntry(contents, id, display);
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
            plugin.reloadConfig();
            contentPolicy.reload();
            reloadTitles();
            return getTitle(id) == null ? null : id;
        } catch (IOException exception) {
            plugin.getLogger().severe("无法将称号写入 config.yml: " + exception.getMessage());
            return null;
        }
    }

    public boolean forceEquip(Player player, String id, int days) {
        TitleDefinition title = getTitle(id);
        if (title == null || days < 0) {
            return false;
        }
        long expiresAt = days == 0 ? 0L : safeFutureTime(days);
        PlayerProfile profile = getProfile(player);
        profile.grantTitle(title.getId(), expiresAt);
        profile.setCustomTitle("");
        profile.setActiveTitle(title.getId());
        profile.setActiveTitleExpiresAt(expiresAt);
        store.save(profile);
        applyDisplay(player);
        if (expiresAt > 0L) {
            long delayTicks = Math.max(1L, (expiresAt - System.currentTimeMillis()) / 50L);
            player.getScheduler().runDelayed(plugin, task -> {
                PlayerProfile current = getProfile(player);
                if (title.getId().equals(current.getActiveTitle())
                        && current.getActiveTitleExpiresAt() == expiresAt) {
                    current.setActiveTitle("");
                    current.setActiveTitleExpiresAt(0L);
                    current.getOwnedTitles().remove(title.getId());
                    store.save(current);
                    applyDisplay(player);
                }
            }, null, delayTicks);
        }
        return true;
    }

    public boolean forceSetCustomTitle(Player player, String display) {
        TitleContentPolicy.Validation validation = contentPolicy.validate(display);
        if (!validation.valid()) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setActiveTitle("__custom__");
        profile.setCustomTitle(display);
        profile.setActiveTitleExpiresAt(0L);
        store.save(profile);
        applyDisplay(player);
        return true;
    }

    public void clearTitle(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.setActiveTitle(NO_TITLE);
        profile.setCustomTitle("");
        profile.setActiveTitleExpiresAt(0L);
        store.save(profile);
        applyDisplay(player);
        auditLogger.logPlayer("title.clear", player, null, Map.of("result", "success"));
    }

    public boolean grant(Player player, String id, int days) {
        TitleDefinition title = getTitle(id);
        if (title == null) {
            return false;
        }
        long expiresAt = days <= 0 ? 0L : safeFutureTime(days);
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

    public PurchaseResult purchase(Player player, String id, PurchaseMethod method) {
        TitleDefinition title = getTitle(id);
        if (title == null) {
            return logPurchase(player, id, method, PurchaseResult.TITLE_NOT_FOUND, null,
                    Double.NaN, 0L, Double.NaN, 0L, 0L);
        }
        PlayerProfile profile = getProfile(player);
        long internalBefore = profile.getInternalBalance();
        double goldBefore = economyService.getBalance(player);
        if (!title.isPurchaseEnabled()) {
            return logPurchase(player, id, method, PurchaseResult.NOT_FOR_SALE, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }
        if (method == null) {
            return logPurchase(player, id, method, PurchaseResult.NOT_FOR_SALE, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }
        Long existingExpiry = profile.getOwnedTitles().get(title.getId());
        boolean alreadyOwns = profile.ownsTitle(title.getId());
        boolean renewalAllowed = plugin.getConfig().getBoolean("economy.purchase.allow-renewal", true);
        if (hasDirectAccess(player, title)
                || (alreadyOwns && (existingExpiry == null || existingExpiry <= 0L || !renewalAllowed))) {
            return logPurchase(player, id, method, PurchaseResult.ALREADY_OWNED, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }

        if (method == PurchaseMethod.GOLD) {
            if (!isGoldPurchaseEnabled()) {
                return logPurchase(player, id, method, PurchaseResult.GOLD_DISABLED, title,
                        goldBefore, internalBefore, goldBefore, internalBefore, 0L);
            }
            if (!title.isGoldPurchasable()) {
                return logPurchase(player, id, method, PurchaseResult.INVALID_PRICE, title,
                        goldBefore, internalBefore, goldBefore, internalBefore, 0L);
            }
            if (!economyService.isAvailable()) {
                return logPurchase(player, id, method, PurchaseResult.ECONOMY_UNAVAILABLE, title,
                        goldBefore, internalBefore, goldBefore, internalBefore, 0L);
            }
            if (!economyService.has(player, title.getGoldPrice())) {
                return logPurchase(player, id, method, PurchaseResult.INSUFFICIENT_GOLD, title,
                        goldBefore, internalBefore, goldBefore, internalBefore, 0L);
            }
            EconomyService.TransactionResult transaction = economyService.withdraw(player, title.getGoldPrice());
            if (!transaction.success()) {
                PurchaseResult result = Double.isFinite(goldBefore)
                        && goldBefore + 0.000001D < title.getGoldPrice()
                        ? PurchaseResult.INSUFFICIENT_GOLD : PurchaseResult.TRANSACTION_FAILED;
                return logPurchase(player, id, method, result, title, goldBefore, internalBefore,
                        transaction.balance(), internalBefore, 0L);
            }
            long expiresAt = grantPurchasedTitle(profile, title);
            store.save(profile);
            applyDisplay(player);
            return logPurchase(player, id, method, PurchaseResult.SUCCESS, title, goldBefore,
                    internalBefore, transaction.balance(), internalBefore, expiresAt);
        }

        if (!isInternalPurchaseEnabled()) {
            return logPurchase(player, id, method, PurchaseResult.INTERNAL_DISABLED, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }
        if (!title.isInternalPurchasable()) {
            return logPurchase(player, id, method, PurchaseResult.INVALID_PRICE, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }
        if (internalBefore < title.getInternalPrice()) {
            return logPurchase(player, id, method, PurchaseResult.INSUFFICIENT_INTERNAL, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }
        if (!profile.withdrawInternal(title.getInternalPrice())) {
            return logPurchase(player, id, method, PurchaseResult.INSUFFICIENT_INTERNAL, title,
                    goldBefore, internalBefore, goldBefore, internalBefore, 0L);
        }
        long expiresAt = grantPurchasedTitle(profile, title);
        store.save(profile);
        applyDisplay(player);
        return logPurchase(player, id, method, PurchaseResult.SUCCESS, title, goldBefore,
                internalBefore, goldBefore, profile.getInternalBalance(), expiresAt);
    }

    public ExchangeOutcome exchange(Player player, String rawAmount) {
        PlayerProfile profile = getProfile(player);
        long internalBefore = profile.getInternalBalance();
        double goldBefore = economyService.getBalance(player);
        if (!isExchangeEnabled()) {
            return logExchange(player, ExchangeResult.DISABLED, 0D, 0L, goldBefore, internalBefore,
                    goldBefore, internalBefore);
        }
        if (!economyService.isAvailable()) {
            return logExchange(player, ExchangeResult.ECONOMY_UNAVAILABLE, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }
        if (!Double.isFinite(goldBefore) || goldBefore <= 0D) {
            return logExchange(player, ExchangeResult.INVALID_AMOUNT, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }

        BigDecimal requested;
        if (rawAmount != null && rawAmount.trim().equalsIgnoreCase("all")) {
            requested = BigDecimal.valueOf(goldBefore);
        } else {
            try {
                requested = new BigDecimal(rawAmount == null ? "" : rawAmount.trim());
            } catch (NumberFormatException exception) {
                return logExchange(player, ExchangeResult.INVALID_AMOUNT, 0D, 0L, goldBefore,
                        internalBefore, goldBefore, internalBefore);
            }
        }
        double configuredRate = plugin.getConfig().getDouble("economy.exchange.gold-per-internal", 100D);
        double configuredMinimum = plugin.getConfig().getDouble("economy.exchange.minimum-gold", configuredRate);
        if (!Double.isFinite(configuredRate) || !Double.isFinite(configuredMinimum)) {
            return logExchange(player, ExchangeResult.INVALID_AMOUNT, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }
        BigDecimal rate = BigDecimal.valueOf(configuredRate);
        BigDecimal minimum = BigDecimal.valueOf(configuredMinimum);
        if (requested.signum() <= 0 || rate.signum() <= 0 || minimum.signum() <= 0) {
            return logExchange(player, ExchangeResult.INVALID_AMOUNT, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }
        if (requested.compareTo(minimum) < 0) {
            return logExchange(player, ExchangeResult.TOO_SMALL, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }

        BigDecimal unitsDecimal = requested.divide(rate, 0, RoundingMode.DOWN);
        long units;
        try {
            units = unitsDecimal.longValueExact();
        } catch (ArithmeticException exception) {
            return logExchange(player, ExchangeResult.INVALID_AMOUNT, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }
        if (units <= 0L || Long.MAX_VALUE - internalBefore < units) {
            return logExchange(player, ExchangeResult.TOO_SMALL, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }
        BigDecimal spentDecimal = rate.multiply(BigDecimal.valueOf(units));
        double spent = spentDecimal.doubleValue();
        if (!Double.isFinite(spent) || spent <= 0D || !economyService.has(player, spent)) {
            return logExchange(player, ExchangeResult.INSUFFICIENT_GOLD, 0D, 0L, goldBefore,
                    internalBefore, goldBefore, internalBefore);
        }
        EconomyService.TransactionResult transaction = economyService.withdraw(player, spent);
        if (!transaction.success()) {
            return logExchange(player, ExchangeResult.TRANSACTION_FAILED, 0D, 0L, goldBefore,
                    internalBefore, transaction.balance(), internalBefore);
        }
        if (!profile.depositInternal(units)) {
            economyService.deposit(player, spent);
            return logExchange(player, ExchangeResult.TRANSACTION_FAILED, spent, 0L, goldBefore,
                    internalBefore, economyService.getBalance(player), internalBefore);
        }
        store.save(profile);
        double goldAfter = economyService.getBalance(player);
        return logExchange(player, ExchangeResult.SUCCESS, spent, units, goldBefore, internalBefore,
                goldAfter, profile.getInternalBalance());
    }

    public long getInternalBalance(Player player) {
        return getProfile(player).getInternalBalance();
    }

    public long getInternalBalance(UUID uuid) {
        return getStoredProfile(uuid).getInternalBalance();
    }

    public double getGoldBalance(OfflinePlayer player) {
        return economyService.getBalance(player);
    }

    public boolean isEconomyAvailable() {
        return economyService.isAvailable();
    }

    public String getInternalCurrencyName() {
        return plugin.getConfig().getString("economy.internal.name", "称号币");
    }

    public int getOwnedTitleCount(UUID uuid) {
        PlayerProfile profile = getStoredProfile(uuid);
        if (profile.purgeExpired()) {
            store.save(profile);
        }
        return profile.getOwnedTitles().size();
    }

    public boolean setInternalBalance(Player player, long amount) {
        if (amount < 0L) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setInternalBalance(amount);
        store.save(profile);
        return true;
    }

    public boolean giveInternalBalance(Player player, long amount) {
        if (amount < 0L) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        if (!profile.depositInternal(amount)) {
            return false;
        }
        store.save(profile);
        return true;
    }

    public boolean takeInternalBalance(Player player, long amount) {
        if (amount < 0L) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        if (!profile.withdrawInternal(amount)) {
            return false;
        }
        store.save(profile);
        return true;
    }

    public boolean updateNickname(Player player, String rawNickname) {
        String nickname = TextUtil.cleanNickname(rawNickname);
        int maxLength = plugin.getConfig().getInt("nickname.max-length", 16);
        String pattern = plugin.getConfig().getString("nickname.pattern", "^[\\p{L}\\p{N}_\\- ]+$");
        if (nickname.isEmpty() || nickname.codePointCount(0, nickname.length()) > maxLength
                || !Pattern.matches(pattern, nickname)) {
            return false;
        }
        PlayerProfile profile = getProfile(player);
        profile.setNickname(nickname);
        store.save(profile);
        applyDisplay(player);
        auditLogger.logPlayer("profile.nickname", player, null, Map.of(
                "nickname", nickname,
                "result", "success"));
        return true;
    }

    public void resetNickname(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.setNickname("");
        profile.setNameColor(plugin.getConfig().getString("nickname.default-color", "<white>"));
        store.save(profile);
        applyDisplay(player);
        auditLogger.logPlayer("profile.reset-name", player, null, Map.of("result", "success"));
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
        auditLogger.logPlayer("profile.color", player, null, Map.of(
                "color", color,
                "result", "success"));
        return true;
    }

    public String getActiveTitleDisplay(Player player) {
        PlayerProfile profile = getProfile(player);
        if (expireActiveTitle(profile)) {
            store.save(profile);
        }
        if (NO_TITLE.equals(profile.getActiveTitle())) {
            return "";
        }
        if ("__custom__".equals(profile.getActiveTitle())) {
            return profile.getCustomTitle();
        }
        TitleDefinition active = getTitle(profile.getActiveTitle());
        if (active != null && canUse(player, active)) {
            return active.getDisplay();
        }
        return getDefaultTitleDisplay();
    }

    public String getActiveTitleDisplay(UUID uuid) {
        PlayerProfile profile = getStoredProfile(uuid);
        if (expireActiveTitle(profile)) {
            store.save(profile);
        }
        if (NO_TITLE.equals(profile.getActiveTitle())) {
            return "";
        }
        if ("__custom__".equals(profile.getActiveTitle())) {
            return profile.getCustomTitle();
        }
        TitleDefinition active = getTitle(profile.getActiveTitle());
        if (active != null && profileCanUseOffline(profile, active)) {
            return active.getDisplay();
        }
        return getDefaultTitleDisplay();
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
        if (profile.purgeExpired()) {
            store.save(profile);
        }
        String name = getDisplayName(player);
        String title = TextUtil.colorize(getActiveTitleDisplay(player));
        String formattedTitle = formatTitleSpacing(title);
        displaySnapshots.put(player.getUniqueId(), new DisplaySnapshot(title, name));
        player.setDisplayName(name);

        if (plugin.getConfig().getBoolean("display.tab.enabled", true)) {
            String format = plugin.getConfig().getString("display.tab.format", "%title%%name%");
            player.setPlayerListName(TextUtil.colorize(format
                    .replace("%title%", formattedTitle)
                    .replace("%name%", name)));
        } else {
            player.setPlayerListName(name);
        }

        if (plugin.getConfig().getBoolean("display.head.enabled", true)) {
            String format = plugin.getConfig().getString("display.head.format", "%title%%name%");
            String head = format.replace("%title%", formattedTitle)
                    .replace("%name%", name);
            player.setCustomName(TextUtil.colorize(head));
            player.setCustomNameVisible(true);
        } else {
            player.setCustomName(null);
            player.setCustomNameVisible(false);
        }
    }

    public void formatChat(AsyncPlayerChatEvent event) {
        if (!chatEnabled) {
            return;
        }
        Player player = event.getPlayer();
        DisplaySnapshot snapshot = displaySnapshots.get(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        String format = chatFormat;
        format = format.replace("%title%", formatTitleSpacing(snapshot.title()))
                .replace("%name%", snapshot.name());
        event.setFormat(TextUtil.escapeFormat(TextUtil.colorize(format)));
    }

    public void scheduleDisplayUpdate(Player player) {
        player.getScheduler().run(plugin, task -> applyDisplay(player),
                () -> displaySnapshots.remove(player.getUniqueId()));
    }

    private String formatTitleSpacing(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        return title + TextUtil.colorize(plugin.getConfig().getString("display.title-separator", " "));
    }

    private boolean expireActiveTitle(PlayerProfile profile) {
        long expiresAt = profile.getActiveTitleExpiresAt();
        if (expiresAt == 0L || expiresAt > System.currentTimeMillis()) {
            return false;
        }
        profile.getOwnedTitles().remove(profile.getActiveTitle());
        profile.setActiveTitle("");
        profile.setActiveTitleExpiresAt(0L);
        profile.setCustomTitle("");
        return true;
    }

    private String insertTitleEntry(String contents, String id, String display) throws IOException {
        String[] lines = contents.split("\\R", -1);
        int titlesLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("titles:")) {
                titlesLine = i;
                break;
            }
        }
        if (titlesLine < 0) {
            throw new IOException("找不到顶层 titles 区块");
        }
        int insertLine = lines.length;
        for (int i = titlesLine + 1; i < lines.length; i++) {
            if (!lines[i].isBlank() && !lines[i].startsWith(" ") && !lines[i].startsWith("\t")
                    && !lines[i].startsWith("#")) {
                insertLine = i;
                break;
            }
        }
        int offset = 0;
        for (int i = 0; i < insertLine; i++) {
            offset += lines[i].length();
            if (offset < contents.length()) {
                if (contents.charAt(offset) == '\r') {
                    offset++;
                }
                if (offset < contents.length() && contents.charAt(offset) == '\n') {
                    offset++;
                }
            }
        }
        String newline = contents.contains("\r\n") ? "\r\n" : "\n";
        String entry = "  " + id + ":" + newline
                + "    display: '" + display.replace("'", "''") + "'" + newline
                + "    description: ''" + newline
                + "    icon: NAME_TAG" + newline
                + "    purchase:" + newline
                + "      enabled: true" + newline
                + "      days: 0" + newline
                + "      gold: -1" + newline
                + "      internal: -1" + newline;
        return contents.substring(0, offset) + entry + contents.substring(offset);
    }

    private String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                result.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public boolean isGoldPurchaseEnabled() {
        return plugin.getConfig().getBoolean("economy.gold.enabled", true)
                && plugin.getConfig().getBoolean("economy.gold.purchase-enabled", true);
    }

    public boolean isInternalPurchaseEnabled() {
        return plugin.getConfig().getBoolean("economy.internal.enabled", true)
                && plugin.getConfig().getBoolean("economy.internal.purchase-enabled", true);
    }

    public PurchaseMethod getDefaultPurchaseMethod() {
        PurchaseMethod configured = PurchaseMethod.fromInput(
                plugin.getConfig().getString("economy.default-purchase-method", "internal"));
        if (configured == PurchaseMethod.GOLD && isGoldPurchaseEnabled()) {
            return configured;
        }
        if (configured == PurchaseMethod.INTERNAL && isInternalPurchaseEnabled()) {
            return configured;
        }
        return isInternalPurchaseEnabled() ? PurchaseMethod.INTERNAL : PurchaseMethod.GOLD;
    }

    private boolean isExchangeEnabled() {
        return plugin.getConfig().getBoolean("economy.internal.enabled", true)
                && plugin.getConfig().getBoolean("economy.exchange.enabled", true)
                && plugin.getConfig().getBoolean("economy.gold.enabled", true)
                && plugin.getConfig().getBoolean("economy.gold.exchange-enabled", true);
    }

    private boolean hasDirectAccess(Player player, TitleDefinition title) {
        return player.hasPermission("simpmctitle.title.*") || player.hasPermission("simpmctitle.admin")
                || (!title.getPermission().isBlank() && player.hasPermission(title.getPermission()));
    }

    private boolean profileCanUseOffline(PlayerProfile profile, TitleDefinition title) {
        return profile.ownsTitle(title.getId()) || (!title.isPurchaseEnabled() && title.getPermission().isBlank());
    }

    private long grantPurchasedTitle(PlayerProfile profile, TitleDefinition title) {
        int days = title.getPurchaseDays();
        if (days <= 0) {
            profile.grantTitle(title.getId(), 0L);
            return 0L;
        }
        long now = System.currentTimeMillis();
        long base = now;
        Long current = profile.getOwnedTitles().get(title.getId());
        if (current != null && current > now) {
            base = current;
        }
        long duration = safeDuration(days);
        long expiresAt = Long.MAX_VALUE - base < duration ? Long.MAX_VALUE : base + duration;
        profile.grantTitle(title.getId(), expiresAt);
        return expiresAt;
    }

    private long safeFutureTime(int days) {
        long duration = safeDuration(days);
        long now = System.currentTimeMillis();
        return Long.MAX_VALUE - now < duration ? Long.MAX_VALUE : now + duration;
    }

    private long safeDuration(int days) {
        long value = days;
        return Long.MAX_VALUE / DAY_MILLIS < value ? Long.MAX_VALUE : value * DAY_MILLIS;
    }

    private PurchaseResult logPurchase(Player player, String id, PurchaseMethod method, PurchaseResult result,
                                       TitleDefinition title, double goldBefore, long internalBefore,
                                       double goldAfter, long internalAfter, long expiresAt) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title_id", id == null ? "" : id);
        details.put("title", title == null ? "" : TextUtil.strip(title.getDisplay()));
        details.put("method", method == null ? "" : method.getId());
        details.put("result", result.name().toLowerCase(Locale.ROOT));
        details.put("gold_price", title == null ? "" : TextUtil.formatMoney(title.getGoldPrice()));
        details.put("internal_price", title == null ? 0L : title.getInternalPrice());
        details.put("gold_before", formatBalance(goldBefore));
        details.put("gold_after", formatBalance(goldAfter));
        details.put("internal_before", internalBefore);
        details.put("internal_after", internalAfter);
        details.put("expires_at", expiresAt);
        auditLogger.logPlayer("title.purchase", player, null, details);
        return result;
    }

    private ExchangeOutcome logExchange(Player player, ExchangeResult result, double spent, long added,
                                        double goldBefore, long internalBefore, double goldAfter,
                                        long internalAfter) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", result.name().toLowerCase(Locale.ROOT));
        details.put("gold_spent", TextUtil.formatMoney(spent));
        details.put("internal_added", added);
        details.put("gold_before", formatBalance(goldBefore));
        details.put("gold_after", formatBalance(goldAfter));
        details.put("internal_before", internalBefore);
        details.put("internal_after", internalAfter);
        auditLogger.logPlayer("currency.exchange", player, null, details);
        return new ExchangeOutcome(result, spent, added, goldAfter, internalAfter);
    }

    private String getDefaultTitleDisplay() {
        String defaultId = plugin.getConfig().getString("display.default-title", "");
        TitleDefinition defaultTitle = getTitle(defaultId);
        return defaultTitle == null ? "" : defaultTitle.getDisplay();
    }

    private String formatBalance(double balance) {
        return Double.isFinite(balance) ? TextUtil.formatMoney(balance) : "unavailable";
    }

    private record DisplaySnapshot(String title, String name) {
    }
}
