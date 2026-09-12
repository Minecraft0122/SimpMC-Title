package cn.simpmc.title.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class TitleContentPolicy {
    private final JavaPlugin plugin;
    private boolean enabled;
    private int maxLength;
    private boolean allowPlaceholders;
    private Pattern allowedPattern;
    private List<String> bannedWords = List.of();
    private List<Pattern> bannedPatterns = List.of();

    public TitleContentPolicy(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("content.enabled", true);
        maxLength = Math.max(1, plugin.getConfig().getInt("content.max-length", 24));
        allowPlaceholders = plugin.getConfig().getBoolean("content.allow-placeholders", false);

        String configuredPattern = plugin.getConfig().getString("content.allowed-pattern",
                "^[\\p{L}\\p{N}\\p{Zs}\\[\\]【】()（）_+\\-·.!?！？:：]+$");
        try {
            allowedPattern = Pattern.compile(configuredPattern == null || configuredPattern.isBlank()
                    ? ".*" : configuredPattern);
        } catch (PatternSyntaxException exception) {
            allowedPattern = Pattern.compile(".*");
            plugin.getLogger().warning("content.allowed-pattern 无效，将暂时允许所有字符: " + exception.getMessage());
        }

        List<String> configuredWords = plugin.getConfig().getStringList("content.banned-words");
        bannedWords = configuredWords.stream()
                .filter(word -> word != null && !word.isBlank())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();

        List<Pattern> patterns = new ArrayList<>();
        for (String rawPattern : plugin.getConfig().getStringList("content.banned-patterns")) {
            if (rawPattern == null || rawPattern.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException exception) {
                plugin.getLogger().warning("忽略无效的 content.banned-patterns: " + rawPattern);
            }
        }
        bannedPatterns = List.copyOf(patterns);
    }

    public Validation validate(String rawText) {
        if (!enabled) {
            return Validation.ok();
        }
        String visible = TextUtil.strip(rawText == null ? "" : rawText).replace('\n', ' ').replace('\r', ' ');
        if (allowPlaceholders) {
            visible = visible.replaceAll("%[^%\\r\\n]{1,64}%", "");
        }
        int length = visible.codePointCount(0, visible.length());
        if (length == 0) {
            return Validation.invalid("不能是空内容");
        }
        if (length > maxLength) {
            return Validation.invalid("可见字符数超过 " + maxLength);
        }
        if (!allowedPattern.matcher(visible).matches()) {
            return Validation.invalid("包含未允许的字符");
        }
        String lower = visible.toLowerCase(Locale.ROOT);
        for (String bannedWord : bannedWords) {
            if (lower.contains(bannedWord)) {
                return Validation.invalid("包含禁用词");
            }
        }
        for (Pattern bannedPattern : bannedPatterns) {
            if (bannedPattern.matcher(visible).find()) {
                return Validation.invalid("命中禁用规则");
            }
        }
        return Validation.ok();
    }

    public record Validation(boolean valid, String reason) {
        public static Validation ok() {
            return new Validation(true, "");
        }

        public static Validation invalid(String reason) {
            return new Validation(false, reason);
        }
    }
}
