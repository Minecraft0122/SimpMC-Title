package cn.simpmc.title;

import cn.simpmc.title.command.SimpMCTitleCommand;
import cn.simpmc.title.gui.GuiService;
import cn.simpmc.title.listener.DisplayListener;
import cn.simpmc.title.listener.MenuListener;
import cn.simpmc.title.placeholder.SimpMCExpansion;
import cn.simpmc.title.storage.ProfileStore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpMCTitlePlugin extends JavaPlugin {
    private ProfileStore profileStore;
    private TitleManager titleManager;
    private GuiService guiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        profileStore = new ProfileStore(this);
        titleManager = new TitleManager(this, profileStore);
        guiService = new GuiService(this, titleManager);

        SimpMCTitleCommand command = new SimpMCTitleCommand(this, titleManager, guiService);
        if (getCommand("simpmctitle") == null) {
            getLogger().severe("plugin.yml 中没有找到 simpmctitle 命令。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("simpmctitle").setExecutor(command);
        getCommand("simpmctitle").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new DisplayListener(this, titleManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, titleManager), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SimpMCExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion 已启用。");
        }

        for (Player player : getServer().getOnlinePlayers()) {
            titleManager.handleJoin(player);
        }
        getLogger().info("SimpMC-Title 已启用。");
    }

    @Override
    public void onDisable() {
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

    public GuiService getGuiService() {
        return guiService;
    }

    public String message(String key) {
        return getConfig().getString("messages." + key, "");
    }

    public void send(CommandSender sender, String key) {
        String prefix = key.equals("help") || key.equals("admin-help") ? "" : message("prefix");
        sender.sendMessage(cn.simpmc.title.util.TextUtil.colorize(prefix + message(key)));
    }
}
