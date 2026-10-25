package cn.simpmc.title.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID uuid;
    private String nickname = "";
    private String nameColor = "<white>";
    private String activeTitle = "";
    private String customTitle = "";
    private long activeTitleExpiresAt;
    private long firstJoinedAt;
    private long internalBalance;
    private final Map<String, Long> ownedTitles = new LinkedHashMap<>();

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? "" : nickname;
    }

    public String getNameColor() {
        return nameColor;
    }

    public void setNameColor(String nameColor) {
        this.nameColor = nameColor == null || nameColor.isBlank() ? "<white>" : nameColor;
    }

    public String getActiveTitle() {
        return activeTitle;
    }

    public void setActiveTitle(String activeTitle) {
        this.activeTitle = activeTitle == null ? "" : activeTitle;
    }

    public String getCustomTitle() {
        return customTitle;
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle = customTitle == null ? "" : customTitle;
    }

    public long getActiveTitleExpiresAt() {
        return activeTitleExpiresAt;
    }

    public void setActiveTitleExpiresAt(long activeTitleExpiresAt) {
        this.activeTitleExpiresAt = Math.max(0L, activeTitleExpiresAt);
    }

    public long getInternalBalance() {
        return internalBalance;
    }

    public long getFirstJoinedAt() {
        return firstJoinedAt;
    }

    public void setFirstJoinedAt(long firstJoinedAt) {
        this.firstJoinedAt = Math.max(0L, firstJoinedAt);
    }

    public void setInternalBalance(long internalBalance) {
        this.internalBalance = Math.max(0L, internalBalance);
    }

    public boolean withdrawInternal(long amount) {
        if (amount < 0L || internalBalance < amount) {
            return false;
        }
        internalBalance -= amount;
        return true;
    }

    public boolean depositInternal(long amount) {
        if (amount < 0L || Long.MAX_VALUE - internalBalance < amount) {
            return false;
        }
        internalBalance += amount;
        return true;
    }

    public Map<String, Long> getOwnedTitles() {
        return ownedTitles;
    }

    public void grantTitle(String titleId, long expiresAt) {
        ownedTitles.put(titleId, expiresAt);
    }

    public boolean ownsTitle(String titleId) {
        Long expiresAt = ownedTitles.get(titleId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt == 0L || expiresAt > System.currentTimeMillis()) {
            return true;
        }
        ownedTitles.remove(titleId);
        return false;
    }

    public boolean purgeExpired() {
        return ownedTitles.entrySet().removeIf(entry -> entry.getValue() != 0L
                && entry.getValue() <= System.currentTimeMillis());
    }
}
