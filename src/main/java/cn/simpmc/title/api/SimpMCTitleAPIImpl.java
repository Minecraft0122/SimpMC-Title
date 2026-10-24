package cn.simpmc.title.api;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.model.TitleDefinition;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class SimpMCTitleAPIImpl implements SimpMCTitleAPI {
    private final SimpMCTitlePlugin plugin;
    private final TitleManager titleManager;

    public SimpMCTitleAPIImpl(SimpMCTitlePlugin plugin, TitleManager titleManager) {
        this.plugin = plugin;
        this.titleManager = titleManager;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public List<TitleInfo> getTitles() {
        return titleManager.getTitleList().stream().map(SimpMCTitleAPIImpl::toInfo).toList();
    }

    @Override
    public Optional<TitleInfo> getTitle(String id) {
        return Optional.ofNullable(titleManager.getTitle(id)).map(SimpMCTitleAPIImpl::toInfo);
    }

    @Override
    public String getTitleDisplay(UUID playerId) {
        return titleManager.getActiveTitleDisplay(playerId);
    }

    @Override
    public String getActiveTitleId(UUID playerId) {
        return titleManager.getActiveTitleId(playerId);
    }

    @Override
    public Map<String, Long> getOwnedTitles(UUID playerId) {
        return titleManager.getOwnedTitles(playerId);
    }

    @Override
    public String getEffectiveName(UUID playerId, String fallbackName) {
        return titleManager.getEffectiveName(playerId, fallbackName);
    }

    @Override
    public String getDisplayName(UUID playerId, String fallbackName) {
        return titleManager.getDisplayName(playerId, fallbackName);
    }

    @Override
    public String getFullDisplay(UUID playerId, String fallbackName) {
        return titleManager.getFullDisplay(playerId, fallbackName);
    }

    @Override
    public long getInternalBalance(UUID playerId) {
        return titleManager.getInternalBalance(playerId);
    }

    @Override
    public String getInternalCurrencyName() {
        return titleManager.getInternalCurrencyName();
    }

    @Override
    public boolean isGoldEconomyAvailable() {
        return titleManager.isEconomyAvailable();
    }

    @Override
    public double getGoldBalance(OfflinePlayer player) {
        return titleManager.getGoldBalance(player);
    }

    @Override
    public Optional<String> registerCustomTitle(String display) {
        return Optional.ofNullable(titleManager.registerCustomTitle(display));
    }

    @Override
    public CompletableFuture<Boolean> grantTitle(Player player, String titleId, int days) {
        if (days < 0) {
            return CompletableFuture.completedFuture(false);
        }
        return onPlayer(player, () -> titleManager.grant(player, titleId, days));
    }

    @Override
    public CompletableFuture<Boolean> equipTitle(Player player, String titleId) {
        return onPlayer(player, () -> titleManager.equip(player, titleId));
    }

    @Override
    public CompletableFuture<Boolean> forceEquipTitle(Player player, String titleId, int days) {
        if (days < 0) {
            return CompletableFuture.completedFuture(false);
        }
        return onPlayer(player, () -> titleManager.forceEquip(player, titleId, days));
    }

    @Override
    public CompletableFuture<Boolean> takeTitle(Player player, String titleId) {
        return onPlayer(player, () -> titleManager.take(player, titleId));
    }

    @Override
    public CompletableFuture<Boolean> clearTitle(Player player) {
        return onPlayer(player, () -> {
            titleManager.clearTitle(player);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> setInternalBalance(Player player, long amount) {
        return onPlayer(player, () -> titleManager.setInternalBalance(player, amount));
    }

    @Override
    public CompletableFuture<Boolean> giveInternalBalance(Player player, long amount) {
        return onPlayer(player, () -> titleManager.giveInternalBalance(player, amount));
    }

    @Override
    public CompletableFuture<Boolean> takeInternalBalance(Player player, long amount) {
        return onPlayer(player, () -> titleManager.takeInternalBalance(player, amount));
    }

    @Override
    public CompletableFuture<Boolean> setNickname(Player player, String nickname) {
        return onPlayer(player, () -> titleManager.updateNickname(player, nickname));
    }

    @Override
    public CompletableFuture<Boolean> setNameColor(Player player, String color) {
        return onPlayer(player, () -> titleManager.updateColor(player, color));
    }

    @Override
    public CompletableFuture<Boolean> resetName(Player player) {
        return onPlayer(player, () -> {
            titleManager.resetNickname(player);
            return true;
        });
    }

    @Override
    public Set<String> getTitleGroups() {
        return Set.copyOf(titleManager.getTitleGroups());
    }

    @Override
    public Optional<String> getTitleGroup(UUID playerId) {
        return Optional.ofNullable(titleManager.getPlayerTitleGroup(playerId));
    }

    @Override
    public boolean createTitleGroup(String group) {
        return titleManager.createTitleGroup(group);
    }

    @Override
    public boolean deleteTitleGroup(String group) {
        return titleManager.deleteTitleGroup(group);
    }

    @Override
    public boolean setTitleGroupTitle(String group, String display) {
        return titleManager.setTitleGroupTitle(group, display);
    }

    @Override
    public boolean addPlayerToTitleGroup(String group, UUID playerId) {
        return titleManager.addTitleGroupMember(group, playerId);
    }

    @Override
    public boolean removePlayerFromTitleGroup(String group, UUID playerId) {
        return titleManager.removeTitleGroupMember(group, playerId);
    }

    private CompletableFuture<Boolean> onPlayer(Player player, BooleanSupplier operation) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (player == null) {
            result.complete(false);
            return result;
        }
        try {
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline()) {
                    result.complete(false);
                    return;
                }
                try {
                    result.complete(operation.getAsBoolean());
                } catch (RuntimeException exception) {
                    result.completeExceptionally(exception);
                }
            }, () -> result.complete(false));
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    private static TitleInfo toInfo(TitleDefinition title) {
        return new TitleInfo(title.getId(), title.getDisplay(), title.getDescription(),
                title.isPurchaseEnabled(), title.getPurchaseDays(), title.getGoldPrice(), title.getInternalPrice());
    }
}
