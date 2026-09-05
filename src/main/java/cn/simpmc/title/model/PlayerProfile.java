package cn.simpmc.title.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID uuid;
    private String nickname = "";
    private String nameColor = "<white>";
    private String activeTitle = "";
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

    public void purgeExpired() {
        ownedTitles.entrySet().removeIf(entry -> entry.getValue() != 0L
                && entry.getValue() <= System.currentTimeMillis());
    }
}
