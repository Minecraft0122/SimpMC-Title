package cn.simpmc.title;

import cn.simpmc.title.api.SimpMCTitleAPI;
import cn.simpmc.title.api.SimpMCTitleAPIImpl;
import cn.simpmc.title.command.SimpMCTitleCommand;
import cn.simpmc.title.economy.EconomyService;
import cn.simpmc.title.gui.GuiService;
import cn.simpmc.title.listener.DisplayListener;
import cn.simpmc.title.listener.MenuListener;
import cn.simpmc.title.logging.AuditLogger;
import cn.simpmc.title.model.ExchangeOutcome;
import cn.simpmc.title.model.ExchangeResult;
import cn.simpmc.title.model.PurchaseMethod;
import cn.simpmc.title.model.PurchaseResult;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.placeholder.SimpMCExpansion;
import cn.simpmc.title.storage.ProfileStore;
import cn.simpmc.title.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SimpMCTitlePlugin extends JavaPlugin {
    private ProfileStore profileStore;
    private TitleManager titleManager;
    private GuiService guiService;
    private EconomyService economyService;
    private AuditLogger auditLogger;
    private SimpMCTitleAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        auditLogger = new AuditLogger(this);
        economyService = new EconomyService(this);
        profileStore = new ProfileStore(this);
        titleManager = new TitleManager(this, profileStore, economyService, auditLogger);
        api = new SimpMCTitleAPIImpl(this, titleManager);
        getServer().getServicesManager().register(SimpMCTitleAPI.class, api, this, ServicePriority.Normal);
        guiService = new GuiService(this, titleManager);

        SimpMCTitleCommand command = new SimpMCTitleCommand(this, titleManager, guiService);
        if (getCommand("simpmctitle") == null) {
            getLogger().severe("plugin.yml 中没有找到 simpmctitle 命令。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("simpmctitle").setExecutor(command);
        getCommand("simpmctitle").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new DisplayListener(titleManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, titleManager), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SimpMCExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion 已启用。");
        }
        if (getConfig().getBoolean("display.tab.enabled", true) && titleManager.usesExternalTabFormatting()) {
            getLogger().info("TAB / TAB-Bridge 占位符模式：请将 TAB 的 tabprefix 设为 %simpmctitle_tab_prefix%，"
                    + "customtabname 设为 %simpmctitle_tab_name%。接入说明见 TAB_BRIDGE.md。");
            if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                getLogger().warning("TAB 占位符模式需要后端安装 PlaceholderAPI，否则称号无法传递给 TAB。");
            }
        }

        for (Player player : getServer().getOnlinePlayers()) {
            titleManager.handleJoin(player);
        }
        getLogger().info("SimpMC-Title 已启用。");
    }

    @Override
    public void onDisable() {
        if (api != null) {
            getServer().getServicesManager().unregister(SimpMCTitleAPI.class, api);
        }
        if (titleManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                titleManager.handleQuit(player);
            }
        }
        getLogger().info("SimpMC-Title 已关闭。");
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }

    public SimpMCTitleAPI getApi() {
        return api;
    }

    public GuiService getGuiService() {
        return guiService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public String message(String key) {
        return getConfig().getString("messages." + key, "");
    }

    public void send(CommandSender sender, String key) {
        String prefix = key.equals("help") || key.equals("admin-help") ? "" : message("prefix");
        sender.sendMessage(cn.simpmc.title.util.TextUtil.colorize(prefix + message(key)));
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String value = message(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        sender.sendMessage(TextUtil.colorize(message("prefix") + value));
    }

    public void sendPurchaseResult(CommandSender sender, PurchaseResult result, TitleDefinition title,
                                   PurchaseMethod method) {
        String key = switch (result) {
            case SUCCESS -> method == PurchaseMethod.GOLD ? "purchase-success-gold" : "purchase-success-internal";
            case TITLE_NOT_FOUND -> "title-not-found";
            case NOT_FOR_SALE -> "purchase-not-for-sale";
            case ALREADY_OWNED -> "purchase-already-owned";
            case GOLD_DISABLED -> "gold-purchase-disabled";
            case INTERNAL_DISABLED -> "internal-purchase-disabled";
            case ECONOMY_UNAVAILABLE -> "economy-unavailable";
            case INSUFFICIENT_GOLD -> "insufficient-gold";
            case INSUFFICIENT_INTERNAL -> "insufficient-internal";
            case INVALID_PRICE -> "purchase-invalid-price";
            case TRANSACTION_FAILED -> "transaction-failed";
        };
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("%id%", title == null ? "" : title.getId());
        replacements.put("%title%", title == null ? "" : title.getDisplay());
        replacements.put("%gold%", title == null ? "-" : TextUtil.formatMoney(title.getGoldPrice()));
        replacements.put("%internal%", title == null ? "-" : String.valueOf(title.getInternalPrice()));
        replacements.put("%currency%", titleManager == null ? "称号币" : titleManager.getInternalCurrencyName());
        replacements.put("%days%", title == null || title.getPurchaseDays() <= 0
                ? "永久" : title.getPurchaseDays() + " 天");
        send(sender, key, replacements);
    }

    public void sendExchangeResult(CommandSender sender, ExchangeOutcome outcome) {
        String key = switch (outcome.result()) {
            case SUCCESS -> "exchange-success";
            case DISABLED -> "exchange-disabled";
            case ECONOMY_UNAVAILABLE -> "economy-unavailable";
            case INVALID_AMOUNT -> "exchange-invalid-amount";
            case TOO_SMALL -> "exchange-too-small";
            case INSUFFICIENT_GOLD -> "insufficient-gold";
            case TRANSACTION_FAILED -> "transaction-failed";
        };
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("%gold%", TextUtil.formatMoney(outcome.goldSpent()));
        replacements.put("%internal%", String.valueOf(outcome.internalAdded()));
        replacements.put("%balance%", TextUtil.formatMoney(outcome.goldBalance()));
        replacements.put("%currency-balance%", String.valueOf(outcome.internalBalance()));
        replacements.put("%currency%", titleManager == null ? "称号币" : titleManager.getInternalCurrencyName());
        send(sender, key, replacements);
    }
}
