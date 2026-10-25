package cn.simpmc.title.model;

import org.bukkit.Material;

public final class TitleDefinition {
    private final String id;
    private final String display;
    private final String description;
    private final String permission;
    private final Material icon;
    private final boolean purchaseEnabled;
    private final int purchaseDays;
    private final double goldPrice;
    private final long internalPrice;
    private final int newPlayerDays;

    public TitleDefinition(String id, String display, String description, String permission, Material icon,
                           boolean purchaseEnabled, int purchaseDays, double goldPrice, long internalPrice) {
        this(id, display, description, permission, icon, purchaseEnabled, purchaseDays, goldPrice, internalPrice, 0);
    }

    public TitleDefinition(String id, String display, String description, String permission, Material icon,
                           boolean purchaseEnabled, int purchaseDays, double goldPrice, long internalPrice,
                           int newPlayerDays) {
        this.id = id;
        this.display = display;
        this.description = description;
        this.permission = permission;
        this.icon = icon;
        this.purchaseEnabled = purchaseEnabled;
        this.purchaseDays = purchaseDays;
        this.goldPrice = goldPrice;
        this.internalPrice = internalPrice;
        this.newPlayerDays = Math.max(0, newPlayerDays);
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }

    public String getPermission() {
        return permission;
    }

    public Material getIcon() {
        return icon;
    }

    public boolean isPurchaseEnabled() {
        return purchaseEnabled;
    }

    public int getPurchaseDays() {
        return purchaseDays;
    }

    public double getGoldPrice() {
        return goldPrice;
    }

    public long getInternalPrice() {
        return internalPrice;
    }

    /** Number of days after a player's first join during which this title can be used. */
    public int getNewPlayerDays() {
        return newPlayerDays;
    }

    public boolean isGoldPurchasable() {
        return purchaseEnabled && Double.isFinite(goldPrice) && goldPrice > 0D;
    }

    public boolean isInternalPurchasable() {
        return purchaseEnabled && internalPrice > 0L;
    }
}
