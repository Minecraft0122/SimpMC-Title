package cn.simpmc.title;

import cn.simpmc.title.economy.EconomyService;
import cn.simpmc.title.logging.AuditLogger;
import cn.simpmc.title.model.PlayerProfile;
import cn.simpmc.title.storage.ProfileStore;
import cn.simpmc.title.storage.TitleGroupStore;
import cn.simpmc.title.placeholder.SimpMCExpansion;
import cn.simpmc.title.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.mockito.ArgumentCaptor;
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
    private PluginManager plugins;
    private final List<Consumer<ScheduledTask>> callbacks = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        Files.copy(Path.of("src/main/resources/config.yml"), directory.resolve("config.yml"));
        plugin = mock(SimpMCTitlePlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("title-test"));
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
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
        assertEquals("&7[&f初来乍到&7]", manager.getActiveTitleDisplay(player));
        assertEquals("new_player_7d", store.load(player.getUniqueId()).getActiveTitle());
        assertTrue(store.load(player.getUniqueId()).isNewcomerDefaultInitialized());
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
        assertEquals("[玩家]", TextUtil.strip(manager.getChatTitleDisplay(player)));
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
        assertEquals("", manager.getTabTitleDisplay(player.getUniqueId()));
        verify(player, atLeastOnce()).setPlayerListName(argThat(value -> TextUtil.strip(value).equals("Alex")));
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

    @Test
    void oldPlayerKeepsPlayerTitleInChatButNotTab() {
        when(player.getFirstPlayed()).thenReturn(System.currentTimeMillis() - 8 * DAY);
        manager.handleJoin(player);
        manager.applyDisplay(player);
        assertEquals("[玩家]", TextUtil.strip(manager.getChatTitleDisplay(player)));
        assertEquals("", manager.getTabTitlePrefix(player.getUniqueId()));
        verify(player).setPlayerListName(argThat(value -> TextUtil.strip(value).equals("Alex")));
    }

    @Test
    void explicitPlayerSelectionSurvivesReconnectAndDoesNotRestoreNewcomer() {
        manager.handleJoin(player);
        manager.equip(player, "default_player");
        manager.handleQuit(player);
        manager.handleJoin(player);
        assertEquals("default_player", manager.getActiveTitleId(player.getUniqueId()));
    }

    @Test
    void upgradingPreservesExistingCustomSelection() {
        String id = manager.registerCustomTitle("&a[VIP]");
        PlayerProfile saved = new PlayerProfile(player.getUniqueId());
        saved.setActiveTitle(id);
        saved.grantTitle(id, 0);
        store.save(saved);
        manager.handleJoin(player);
        assertEquals(id, manager.getActiveTitleId(player.getUniqueId()));
    }

    @Test
    void expiredManualSelectionDoesNotAutomaticallyReequipNewcomer() {
        manager.handleJoin(player);
        String id = manager.registerCustomTitle("&a[临时]");
        manager.forceEquip(player, id, 1);
        PlayerProfile profile = manager.getProfile(player);
        profile.grantTitle(id, System.currentTimeMillis() - 1);
        profile.setActiveTitleExpiresAt(System.currentTimeMillis() - 1);
        manager.applyDisplay(player);
        assertEquals("default_player", manager.getActiveTitleId(player.getUniqueId()));
        manager.handleQuit(player);
        manager.handleJoin(player);
        assertEquals("default_player", manager.getActiveTitleId(player.getUniqueId()));
    }

    @Test
    void onlyExplicitHidePermissionAllowsChatWithoutTitleAndRevocationIsImmediate() {
        manager.handleJoin(player);
        manager.clearTitle(player);
        when(player.hasPermission("simpmctitle.admin")).thenReturn(true);
        when(player.isOp()).thenReturn(true);
        assertEquals("[玩家]", TextUtil.strip(manager.getChatTitleDisplay(player)));
        assertTrue(renderChat().startsWith("[玩家] Alex"));
        when(player.hasPermission(TitleManager.HIDE_CHAT_TITLE_PERMISSION)).thenReturn(true);
        assertEquals("", manager.getChatTitleDisplay(player));
        assertEquals("Alex » message", renderChat());
        when(player.hasPermission(TitleManager.HIDE_CHAT_TITLE_PERMISSION)).thenReturn(false);
        assertEquals("[玩家] Alex » message", renderChat());
    }

    @Test
    void missingTitleTokenAndDisabledDefaultDoNotBypassChatRequirement() throws Exception {
        plugin.getConfig().set("display.chat.format", "%name% %2$s");
        plugin.getConfig().set("display.default-title", "");
        plugin.getConfig().set("titles.default_player.display", "&7[自定义玩家]");
        plugin.getConfig().save(directory.resolve("config.yml").toFile());
        // Registering reloads the saved configuration, including its custom chat format.
        manager.registerCustomTitle("[TokenCheck]");
        manager.handleJoin(player);
        manager.clearTitle(player);
        assertEquals("[自定义玩家] Alex message", renderChat());
    }

    @Test
    void tabShowsNewcomerWithOneSpaceAndCanOptIntoPlayerTitle() {
        manager.handleJoin(player);
        manager.applyDisplay(player);
        assertEquals("[初来乍到] ", TextUtil.strip(manager.getTabTitlePrefix(player.getUniqueId())));
        assertTrue(manager.getTabTitlePrefix(player.getUniqueId()).endsWith("§r"));
        plugin.getConfig().set("display.tab.hide-player-title", false);
        manager.equip(player, "default_player");
        assertEquals("[玩家]", TextUtil.strip(manager.getTabTitleDisplay(player.getUniqueId())));
    }

    @Test
    void bridgeConsumesCachedPlaceholdersWithoutOverwritingTabListName() {
        when(plugins.isPluginEnabled("TAB-Bridge")).thenReturn(true);
        manager.handleJoin(player);
        manager.applyDisplay(player);
        when(plugin.getTitleManager()).thenReturn(manager);
        SimpMCExpansion expansion = new SimpMCExpansion(plugin);
        assertEquals("[初来乍到]", TextUtil.strip(expansion.onRequest(player, "tab_title")));
        assertEquals("[初来乍到] ", TextUtil.strip(expansion.onRequest(player, "tab_prefix")));
        assertEquals("Alex", TextUtil.strip(expansion.onRequest(player, "tab_name")));
        verify(player, never()).setPlayerListName(anyString());
        manager.clearTitle(player);
        assertEquals("", expansion.onRequest(player, "tab_prefix"));
        manager.handleQuit(player);
        assertEquals("", expansion.onRequest(player, "tab_name"));
    }

    @Test
    void backendTabAlsoUsesPlaceholdersAndNativeModeCanBeForced() {
        when(plugins.isPluginEnabled("TAB")).thenReturn(true);
        manager.handleJoin(player);
        manager.applyDisplay(player);
        verify(player, never()).setPlayerListName(anyString());
        plugin.getConfig().set("display.tab.mode", "native");
        manager.applyDisplay(player);
        verify(player).setPlayerListName(contains("初来乍到"));
    }

    @Test
    void tabDisabledHidesOnlyTabTitleAndClearsPreviouslySetListName() {
        manager.handleJoin(player);
        manager.applyDisplay(player);
        plugin.getConfig().set("display.tab.enabled", false);
        manager.applyDisplay(player);
        assertEquals("", manager.getTabTitlePrefix(player.getUniqueId()));
        assertTrue(manager.getChatTitleDisplay(player).contains("初来乍到"));
        verify(player).setPlayerListName(argThat(value -> TextUtil.strip(value).equals("Alex")));
    }

    @Test
    void deletedSelectedTitleFallsBackToPlayerWithoutLeakingIntoTab() {
        manager.handleJoin(player);
        manager.getProfile(player).setActiveTitle("removed-definition");
        manager.applyDisplay(player);
        assertEquals("[玩家]", TextUtil.strip(manager.getChatTitleDisplay(player)));
        assertEquals("", manager.getTabTitlePrefix(player.getUniqueId()));
    }

    @Test
    void groupRemainsVisibleInTabEvenWhenPersonalTitleIsPlayer() {
        TitleGroupStore groups = new TitleGroupStore(plugin);
        assertTrue(groups.create("builders"));
        assertTrue(groups.setTitle("builders", "&a[建造者]"));
        assertTrue(groups.addMember("builders", player.getUniqueId()));
        manager = new TitleManager(plugin, store, mock(EconomyService.class), mock(AuditLogger.class));
        manager.handleJoin(player);
        manager.equip(player, "default_player");
        assertEquals("[建造者] ", TextUtil.strip(manager.getTabTitlePrefix(player.getUniqueId())));
        manager.clearTitle(player);
        assertEquals("[建造者]", TextUtil.strip(manager.getChatTitleDisplay(player)));
        assertEquals("[建造者] ", TextUtil.strip(manager.getTabTitlePrefix(player.getUniqueId())));
    }

    @Test
    void upgradeDoesNotReplaceExpiredManualSelectionWithNewcomer() {
        String id = manager.registerCustomTitle("[Expired]");
        PlayerProfile saved = new PlayerProfile(player.getUniqueId());
        saved.setActiveTitle(id);
        saved.setActiveTitleExpiresAt(System.currentTimeMillis() - 1);
        saved.grantTitle(id, System.currentTimeMillis() - 1);
        store.save(saved);
        manager.handleJoin(player);
        assertEquals("default_player", manager.getActiveTitleId(player.getUniqueId()));
    }

    private String renderChat() {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        manager.formatChat(event);
        ArgumentCaptor<ChatRenderer> renderer = ArgumentCaptor.forClass(ChatRenderer.class);
        verify(event).renderer(renderer.capture());
        return PlainTextComponentSerializer.plainText().serialize(renderer.getValue().render(
                player, Component.text("OtherPluginName"), Component.text("message"), player));
    }
}
