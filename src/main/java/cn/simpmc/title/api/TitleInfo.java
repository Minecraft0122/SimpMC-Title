package cn.simpmc.title.api;

/** Immutable public view of a configured title. Display keeps its configured color-code syntax. */
public record TitleInfo(
        String id,
        String display,
        String description,
        boolean purchaseEnabled,
        int purchaseDays,
        double goldPrice,
        long internalPrice) {
}
