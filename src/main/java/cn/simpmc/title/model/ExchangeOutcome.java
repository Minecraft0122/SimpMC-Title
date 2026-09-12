package cn.simpmc.title.model;

public record ExchangeOutcome(ExchangeResult result, double goldSpent, long internalAdded,
                              double goldBalance, long internalBalance) {
    public static ExchangeOutcome failure(ExchangeResult result, double goldBalance, long internalBalance) {
        return new ExchangeOutcome(result, 0D, 0L, goldBalance, internalBalance);
    }
}
