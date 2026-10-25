package cn.simpmc.title.listener;

import cn.simpmc.title.TitleManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class DisplayListener implements Listener {
    private final TitleManager titleManager;

    public DisplayListener(TitleManager titleManager) {
        this.titleManager = titleManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        titleManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        titleManager.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        titleManager.formatChat(event);
    }
}
