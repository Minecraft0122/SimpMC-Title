package cn.simpmc.title.model;

import java.util.Locale;

public enum PurchaseMethod {
    GOLD("gold"),
    INTERNAL("internal");

    private final String id;

    PurchaseMethod(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static PurchaseMethod fromInput(String input) {
        if (input == null) {
            return null;
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "gold", "money", "vault", "金币", "金钱" -> GOLD;
            case "internal", "currency", "coin", "plugin", "称号币", "内部货币" -> INTERNAL;
            default -> null;
        };
    }
}
