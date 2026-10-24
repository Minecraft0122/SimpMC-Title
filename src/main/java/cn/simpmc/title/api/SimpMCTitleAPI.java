package cn.simpmc.title.api;

import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public integration API for other plugins. Obtain it from Bukkit's ServicesManager.
 * Methods returning CompletableFuture schedule player mutations on the target entity scheduler.
 * Configuration and group mutations that return immediately must be called on the server thread
 * (or Folia global region scheduler).
 * When reading an online player's profile on Folia, call the getter on that player's entity scheduler.
 */
public interface SimpMCTitleAPI {
    int API_VERSION = 1;

    int getApiVersion();

    String getPluginVersion();

    List<TitleInfo> getTitles();

    Optional<TitleInfo> getTitle(String id);

    /** Returns the title currently displayed for this profile, including group/default titles. */
    String getTitleDisplay(UUID playerId);

    /** Returns the personal active title ID, or an empty string when none is equipped. */
    String getActiveTitleId(UUID playerId);

    /** Returns a defensive, unmodifiable copy of title IDs and expiry timestamps (0 means permanent). */
    Map<String, Long> getOwnedTitles(UUID playerId);

    String getEffectiveName(UUID playerId, String fallbackName);

    String getDisplayName(UUID playerId, String fallbackName);

    String getFullDisplay(UUID playerId, String fallbackName);

    long getInternalBalance(UUID playerId);

    String getInternalCurrencyName();

    boolean isGoldEconomyAvailable();

    /** Returns NaN when Vault or a compatible economy provider is unavailable. */
    double getGoldBalance(OfflinePlayer player);

    /** Creates or finds a title with the supplied display text and persists new definitions to config.yml. */
    Optional<String> registerCustomTitle(String display);

    CompletableFuture<Boolean> grantTitle(Player player, String titleId, int days);

    /** Equips a title only when the player owns it or has permission to use it. */
    CompletableFuture<Boolean> equipTitle(Player player, String titleId);

    /** Grants and equips a title, bypassing ownership checks. A zero duration means permanent. */
    CompletableFuture<Boolean> forceEquipTitle(Player player, String titleId, int days);

    CompletableFuture<Boolean> takeTitle(Player player, String titleId);

    CompletableFuture<Boolean> clearTitle(Player player);

    CompletableFuture<Boolean> setInternalBalance(Player player, long amount);

    CompletableFuture<Boolean> giveInternalBalance(Player player, long amount);

    CompletableFuture<Boolean> takeInternalBalance(Player player, long amount);

    CompletableFuture<Boolean> setNickname(Player player, String nickname);

    CompletableFuture<Boolean> setNameColor(Player player, String color);

    CompletableFuture<Boolean> resetName(Player player);

    Set<String> getTitleGroups();

    Optional<String> getTitleGroup(UUID playerId);

    boolean createTitleGroup(String group);

    boolean deleteTitleGroup(String group);

    boolean setTitleGroupTitle(String group, String display);

    boolean addPlayerToTitleGroup(String group, UUID playerId);

    boolean removePlayerFromTitleGroup(String group, UUID playerId);
}
