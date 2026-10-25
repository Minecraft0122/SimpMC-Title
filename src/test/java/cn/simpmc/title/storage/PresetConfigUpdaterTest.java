package cn.simpmc.title.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PresetConfigUpdaterTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "titles: {}\n", "titles: # retained\n", "'titles': {} # retained\n",
            "titles:\n  vip:\n    display: '&6VIP'", "titles: {vip: {display: '&6VIP'}}\n",
            "titles:\n    vip:\n        display: '&6VIP'\nmessages:\n    hello: ok\n"})
    void addsPresetsAndCustomTitlesWithoutLosingExistingEntries(String original) throws Exception {
        String upgraded = PresetConfigUpdater.upgrade(original);
        YamlConfiguration config = YamlConfigEditor.parse(upgraded);
        assertEquals("&7[玩家]", config.getString("titles.default_player.display"));
        assertEquals(7, config.getInt("titles.new_player_7d.new-player-days"));
        assertEquals("default_player", config.getString("display.default-title"));
        if (original.contains("VIP")) {
            assertEquals("&6VIP", config.getString("titles.vip.display"));
        }
        if (original.contains("# retained")) {
            assertTrue(upgraded.contains("# retained"));
        }
        assertEquals(upgraded, PresetConfigUpdater.upgrade(upgraded));
        String withCustom = PresetConfigUpdater.appendCustomTitle(upgraded, "custom-test", "&7[Test]");
        assertEquals("&7[Test]", YamlConfigEditor.parse(withCustom).getString("titles.custom-test.display"));
    }

    @Test
    void keepsCommentsCrLfAndCustomDefault() throws Exception {
        String original = "# 我的配置\r\ndisplay: # display\r\n  default-title: vip # chosen\r\n"
                + "  tab: {enabled: false}\r\ntitles: # titles\r\n  vip:\r\n    display: '&6VIP' # keep\r\n";
        String updated = PresetConfigUpdater.upgrade(original);
        assertTrue(updated.contains("  default-title: vip # chosen\r\n"));
        assertTrue(updated.contains("    display: '&6VIP' # keep\r\n"));
        assertFalse(updated.replace("\r\n", "").contains("\n"));
        assertFalse(YamlConfigEditor.parse(updated).getBoolean("display.tab.enabled"));
    }

    @Test
    void migratesBlankDefaultOnceAndRespectsSubsequentDisable() throws Exception {
        String updated = PresetConfigUpdater.upgrade("display:\n  default-title: '' # retain\n");
        assertTrue(updated.contains("# retain"));
        String disabled = YamlConfigEditor.put(updated, "display.default-title", "");
        assertEquals(disabled, PresetConfigUpdater.upgrade(disabled));
        assertEquals("", YamlConfigEditor.parse(disabled).getString("display.default-title"));
    }

    @Test
    void retainsCustomizedPresetsAndRejectsInvalidYaml() throws Exception {
        String original = "titles:\n  default_player:\n    display: '&eRegular'\n"
                + "  new_player_7d:\n    display: '&aNew'\n    new-player-days: 3\n";
        YamlConfiguration config = YamlConfigEditor.parse(PresetConfigUpdater.upgrade(original));
        assertEquals("&eRegular", config.getString("titles.default_player.display"));
        assertEquals(3, config.getInt("titles.new_player_7d.new-player-days"));
        assertThrows(Exception.class, () -> PresetConfigUpdater.upgrade("titles: [broken"));
    }

    @Test
    void keepsUnicodeSourceOffsetsAndHandlesNullSections() throws Exception {
        String original = "# 😀 keep\ndisplay: null\ntitles: {} # 🏆 keep\n";
        String updated = PresetConfigUpdater.upgrade(original);
        assertTrue(updated.contains("# 😀 keep"));
        assertTrue(updated.contains("# 🏆 keep"));
        assertEquals("&7[玩家]", YamlConfigEditor.parse(updated).getString("titles.default_player.display"));
        assertEquals(updated, PresetConfigUpdater.upgrade(updated));
    }
}
