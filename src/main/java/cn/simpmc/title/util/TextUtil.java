package cn.simpmc.title.util;

import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {
    private static final Pattern RGB_TAG = Pattern.compile("(?i)<#([0-9a-f]{6})>");
    private static final Pattern RGB_AMP = Pattern.compile("(?i)&(#)([0-9a-f]{6})");
    private static final Pattern GRADIENT = Pattern.compile(
            "(?is)<gradient:(#[0-9a-f]{6}):(#[0-9a-f]{6})>(.*?)</gradient>");
    private static final Pattern GRADIENT_PREFIX = Pattern.compile(
            "(?i)^<gradient:(#[0-9a-f]{6}):(#[0-9a-f]{6})>$");
    private static final Pattern MINI_TAG = Pattern.compile("(?i)</?[a-z_]+>");
    private static final Pattern NICKNAME_TAG = Pattern.compile("(?i)<[^>]{1,32}>");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§x(§[0-9a-f]){6}|§[0-9a-fk-or]");
    private static final Map<String, String> NAMED_COLORS = namedColors();

    private TextUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String value = expandGradients(input);
        Matcher rgbMatcher = RGB_TAG.matcher(value);
        StringBuffer rgbBuffer = new StringBuffer();
        while (rgbMatcher.find()) {
            rgbMatcher.appendReplacement(rgbBuffer,
                    Matcher.quoteReplacement(hex(rgbMatcher.group(1))));
        }
        rgbMatcher.appendTail(rgbBuffer);
        value = rgbBuffer.toString();

        Matcher ampMatcher = RGB_AMP.matcher(value);
        StringBuffer ampBuffer = new StringBuffer();
        while (ampMatcher.find()) {
            ampMatcher.appendReplacement(ampBuffer,
                    Matcher.quoteReplacement(hex(ampMatcher.group(2))));
        }
        ampMatcher.appendTail(ampBuffer);
        value = ampBuffer.toString();

        for (Map.Entry<String, String> entry : NAMED_COLORS.entrySet()) {
            value = value.replaceAll("(?i)<" + Pattern.quote(entry.getKey()) + ">", entry.getValue());
        }
        value = MINI_TAG.matcher(value).replaceAll("");
        value = ChatColor.translateAlternateColorCodes('&', value);
        return value.replace("\\n", "\n");
    }

    public static String cleanNickname(String input) {
        if (input == null) {
            return "";
        }
        String value = strip(input);
        value = NICKNAME_TAG.matcher(value).replaceAll("");
        value = value.replace('&', ' ').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return value.replaceAll(" +", " ").trim();
    }

    public static String renderName(String color, String name) {
        Matcher gradientMatcher = GRADIENT_PREFIX.matcher(color == null ? "" : color.trim());
        if (gradientMatcher.matches()) {
            return gradient(gradientMatcher.group(1), gradientMatcher.group(2), name == null ? "" : name);
        }
        String normalized = normalizeColor(color);
        return colorize((normalized == null ? "<white>" : normalized) + (name == null ? "" : name));
    }

    public static String strip(String input) {
        return LEGACY_COLOR.matcher(colorize(input)).replaceAll("");
    }

    public static String escapeFormat(String input) {
        if (input == null) {
            return "";
        }
        String value = input.replace("%1$s", "\u0001").replace("%2$s", "\u0002");
        value = value.replace("%", "%%");
        return value.replace("\u0001", "%1$s").replace("\u0002", "%2$s");
    }

    public static String formatMoney(double amount) {
        if (!Double.isFinite(amount)) {
            return "-";
        }
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    public static String normalizeColor(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.matches("(?i)#[0-9a-f]{6}")) {
            return "<" + value + ">";
        }
        if (value.matches("(?i)&#[0-9a-f]{6}")) {
            return value;
        }
        if (value.matches("(?i)&[0-9a-f]")) {
            return value;
        }
        if (value.matches("(?i)<#[0-9a-f]{6}>")
                || (value.matches("(?i)<[a-z_]+>")
                && NAMED_COLORS.containsKey(value.substring(1, value.length() - 1).toLowerCase()))
                || value.matches("(?i)<gradient:#[0-9a-f]{6}:#[0-9a-f]{6}>")
                || value.matches("(?i)gradient:#[0-9a-f]{6}:#[0-9a-f]{6}")) {
            return value.startsWith("<") ? value : "<" + value + ">";
        }
        if (value.matches("(?i)[a-z_]+")) {
            return NAMED_COLORS.containsKey(value.toLowerCase()) ? "<" + value + ">" : null;
        }
        return null;
    }

    private static String expandGradients(String input) {
        String value = input;
        Matcher matcher = GRADIENT.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String body = ChatColor.stripColor(colorize(matcher.group(3)));
            matcher.appendReplacement(output, Matcher.quoteReplacement(
                    gradient(matcher.group(1), matcher.group(2), body)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String gradient(String start, String end, String text) {
        int from = Integer.parseInt(start.substring(1), 16);
        int to = Integer.parseInt(end.substring(1), 16);
        int[] codePoints = text.codePoints().toArray();
        if (codePoints.length == 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < codePoints.length; index++) {
            double progress = codePoints.length == 1 ? 0 : (double) index / (codePoints.length - 1);
            int red = interpolate((from >> 16) & 0xff, (to >> 16) & 0xff, progress);
            int green = interpolate((from >> 8) & 0xff, (to >> 8) & 0xff, progress);
            int blue = interpolate(from & 0xff, to & 0xff, progress);
            result.append(hex(String.format("%02x%02x%02x", red, green, blue)));
            result.appendCodePoint(codePoints[index]);
        }
        return result.toString();
    }

    private static int interpolate(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }

    private static String hex(String value) {
        StringBuilder result = new StringBuilder("§x");
        for (char character : value.toCharArray()) {
            result.append('§').append(character);
        }
        return result.toString();
    }

    private static Map<String, String> namedColors() {
        Map<String, String> colors = new HashMap<>();
        colors.put("black", "&0");
        colors.put("dark_blue", "&1");
        colors.put("dark_green", "&2");
        colors.put("dark_aqua", "&3");
        colors.put("dark_red", "&4");
        colors.put("dark_purple", "&5");
        colors.put("gold", "&6");
        colors.put("gray", "&7");
        colors.put("dark_gray", "&8");
        colors.put("blue", "&9");
        colors.put("green", "&a");
        colors.put("aqua", "&b");
        colors.put("red", "&c");
        colors.put("light_purple", "&d");
        colors.put("yellow", "&e");
        colors.put("white", "&f");
        return colors;
    }
}
