package cn.simpmc.title.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Uses Vault through reflection so the plugin can still start without Vault installed.
 */
public final class EconomyService {
    private final JavaPlugin plugin;
    private Class<?> economyType;
    private Object provider;
    private Method getBalance;
    private Method has;
    private Method withdrawPlayer;
    private Method depositPlayer;

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public synchronized void refresh() {
        provider = null;
        economyType = null;
        getBalance = null;
        has = null;
        withdrawPlayer = null;
        depositPlayer = null;

        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return;
        }
        try {
            economyType = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager()
                    .getRegistration((Class) economyType);
            if (registration == null || registration.getProvider() == null) {
                return;
            }
            provider = registration.getProvider();
            getBalance = economyType.getMethod("getBalance", OfflinePlayer.class);
            has = economyType.getMethod("has", OfflinePlayer.class, double.class);
            withdrawPlayer = economyType.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositPlayer = economyType.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            provider = null;
            plugin.getLogger().warning("无法连接 Vault 经济服务，金币功能将不可用: " + exception.getMessage());
        }
    }

    public synchronized boolean isAvailable() {
        return provider != null;
    }

    public synchronized double getBalance(OfflinePlayer player) {
        if (!isAvailable()) {
            return Double.NaN;
        }
        try {
            Object value = getBalance.invoke(provider, player);
            return value instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure("读取金币余额", exception);
            return Double.NaN;
        }
    }

    public synchronized boolean has(OfflinePlayer player, double amount) {
        if (!isAvailable() || !Double.isFinite(amount) || amount < 0D) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(has.invoke(provider, player, amount));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure("检查金币余额", exception);
            return false;
        }
    }

    public synchronized TransactionResult withdraw(OfflinePlayer player, double amount) {
        return transaction(withdrawPlayer, player, amount, "扣除金币");
    }

    public synchronized TransactionResult deposit(OfflinePlayer player, double amount) {
        return transaction(depositPlayer, player, amount, "发放金币");
    }

    private TransactionResult transaction(Method method, OfflinePlayer player, double amount, String action) {
        if (!isAvailable() || method == null || !Double.isFinite(amount) || amount < 0D) {
            return new TransactionResult(false, Double.NaN, "经济服务不可用");
        }
        try {
            Object response = method.invoke(provider, player, amount);
            boolean success = responseSuccess(response);
            double balance = getBalance(player);
            return new TransactionResult(success, balance, success ? "" : responseError(response));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logFailure(action, exception);
            return new TransactionResult(false, getBalance(player), exception.getMessage());
        }
    }

    private boolean responseSuccess(Object response) throws ReflectiveOperationException {
        if (response == null) {
            return false;
        }
        Method method = response.getClass().getMethod("transactionSuccess");
        return Boolean.TRUE.equals(method.invoke(response));
    }

    private String responseError(Object response) {
        if (response == null) {
            return "经济服务没有返回结果";
        }
        try {
            Method method = response.getClass().getMethod("errorMessage");
            Object value = method.invoke(response);
            return value == null ? "交易被经济服务拒绝" : value.toString();
        } catch (ReflectiveOperationException exception) {
            return "交易被经济服务拒绝";
        }
    }

    private void logFailure(String action, Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : exception;
        plugin.getLogger().warning(action + "失败: " + cause.getMessage());
    }

    public record TransactionResult(boolean success, double balance, String error) {
    }
}
