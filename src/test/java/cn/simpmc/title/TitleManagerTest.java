package cn.simpmc.title;

import cn.simpmc.title.economy.EconomyService;
import cn.simpmc.title.logging.AuditLogger;
import cn.simpmc.title.model.PlayerProfile;
import cn.simpmc.title.storage.ProfileStore;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TitleManagerTest {
    @TempDir Path directory;
    private static final long DAY = 86_400_000L;
    private SimpMCTitlePlugin plugin;
    private TitleManager manager;
    private ProfileStore store;
    private Player player;
    private final List<Consumer<ScheduledTask>> callbacks = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        Files.copy(Path.of("src/main/resources/config.yml"), directory.resolve("config.yml"));
        plugin = mock(SimpMCTitlePlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("title-test"));
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile()));
        doAnswer(call -> {
            when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile()));
            return null;
        }).when(plugin).reloadConfig();
        store = new ProfileStore(plugin);
        manager = new TitleManager(plugin, store, mock(EconomyService.class), mock(AuditLogger.class));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        when(player.getFirstPlayed()).thenReturn(System.currentTimeMillis() - DAY);
        when(player.hasPlayedBefore()).thenReturn(true);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return mock(ScheduledTask.class);
        });
    }

    @Test
    void newcomerExpiryUsesFirstJoinAndDoesNotResetOnReconnect() {
        manager.handleJoin(player);
        long expected = player.getFirstPlayed() + 7 * DAY;
        assertEquals(expected, manager.getOwnedTitles(player.getUniqueId()).get("new_player_7d"));
        assertEquals("&7[玩家]", manager.getActiveTitleDisplay(player));
        assertTrue(manager.equip(player, "new_player_7d"));
        manager.handleQuit(player);
        manager.handleJoin(player);
        assertEquals(expected, manager.getOwnedTitles(player.getUniqueId()).get("new_player_7d"));
        assertEquals(expected, store.load(player.getUniqueId()).getActiveTitleExpiresAt());
    }

    @Test
    void oldPlayersIncludingAdminsCannotEquipOrReceiveNewcomerTitle() {
        when(player.getFirstPlayed()).thenReturn(System.currentTimeMillis() - 8 * DAY);
        when(player.hasPermission(anyString())).thenReturn(true);
        manager.handleJoin(player);
        assertFalse(manager.canUse(player, manager.getTitle("new_player_7d")));
        assertFalse(manager.equip(player, "new_player_7d"));
        assertFalse(manager.forceEquip(player, "new_player_7d", 0));
        assertFalse(manager.forceEquip(player, "new_player_7d"));
        assertFalse(manager.grant(player, "new_player_7d", 30));
        assertFalse(manager.getOwnedTitles(player.getUniqueId()).containsKey("new_player_7d"));
    }

    @Test
    void manualUnequipSurvivesReconnectAndPresetReconciliation() {
        manager.handleJoin(player);
        manager.clearTitle(player);
        manager.handleQuit(player);
        manager.handleJoin(player);
        manager.applyDisplay(player);
        assertEquals("", manager.getActiveTitleDisplay(player));
        assertEquals("", manager.getActiveTitleId(player.getUniqueId()));
        assertTrue(manager.equip(player, "default_player"));
        assertEquals("&7[玩家]", manager.getActiveTitleDisplay(player));
    }

    @Test
    void expiryTaskRemovesNewcomerWhileOnlineAndRefreshesDefault() {
        manager.handleJoin(player);
        assertTrue(manager.equip(player, "new_player_7d"));
        PlayerProfile profile = manager.getProfile(player);
        profile.setFirstJoinedAt(System.currentTimeMillis() - 8 * DAY);
        callbacks.get(callbacks.size() - 1).accept(mock(ScheduledTask.class));
        assertFalse(manager.getOwnedTitles(player.getUniqueId()).containsKey("new_player_7d"));
        assertEquals("&7[玩家]", manager.getActiveTitleDisplay(player));
        assertFalse(store.load(player.getUniqueId()).getOwnedTitles().containsKey("new_player_7d"));
        verify(player, atLeastOnce()).setPlayerListName(contains("[玩家]"));
    }

    @Test
    void staleExpiryCannotDeleteRenewedTitle() {
        manager.handleJoin(player);
        String id = manager.registerCustomTitle("&7[Test]");
        assertNotNull(id);
        assertTrue(manager.forceEquip(player, id, 1));
        Consumer<ScheduledTask> oldCallback = callbacks.get(callbacks.size() - 1);
        assertTrue(manager.grant(player, id, 30));
        long renewed = manager.getProfile(player).getOwnedTitles().get(id);
        manager.getProfile(player).setActiveTitleExpiresAt(System.currentTimeMillis() - 1);
        oldCallback.accept(mock(ScheduledTask.class));
        assertEquals(renewed, manager.getOwnedTitles(player.getUniqueId()).get(id));
        assertEquals(renewed, manager.getProfile(player).getActiveTitleExpiresAt());
        assertEquals("&7[Test]", manager.getActiveTitleDisplay(player));
    }

    @Test
    void firstJoinFallbackIsPersistedInsteadOfMovingWithClock() {
        when(player.getFirstPlayed()).thenReturn(0L);
        when(player.hasPlayedBefore()).thenReturn(false);
        manager.handleJoin(player);
        long first = manager.getProfile(player).getFirstJoinedAt();
        assertTrue(first > 0L);
        manager.handleQuit(player);
        manager.handleJoin(player);
        assertEquals(first, manager.getProfile(player).getFirstJoinedAt());
        assertEquals(first + 7 * DAY, manager.getOwnedTitles(player.getUniqueId()).get("new_player_7d"));
    }

    @Test
    void offlineExpiryIsReclaimedOnNextJoin() {
        PlayerProfile saved = new PlayerProfile(player.getUniqueId());
        saved.setFirstJoinedAt(System.currentTimeMillis() - 8 * DAY);
        saved.grantTitle("new_player_7d", System.currentTimeMillis() - DAY);
        saved.setActiveTitle("new_player_7d");
        saved.setActiveTitleExpiresAt(System.currentTimeMillis() - DAY);
        store.save(saved);
        manager.handleJoin(player);
        assertEquals("&7[玩家]", manager.getActiveTitleDisplay(player));
        assertFalse(store.load(player.getUniqueId()).getOwnedTitles().containsKey("new_player_7d"));
    }
}
