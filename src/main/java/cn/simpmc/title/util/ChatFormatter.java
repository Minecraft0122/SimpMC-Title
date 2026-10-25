package cn.simpmc.title.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/** Keeps message components intact and confines title hover/formatting to the title itself. */
public final class ChatFormatter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern TOKEN = Pattern.compile("%title%|%name%|%[12]\\$s");

    private ChatFormatter() { }

    public static Component render(String format, String title, String separator, String name,
                                   Component message, Component hover) {
        Component titleComponent = title.isEmpty() ? Component.empty()
                : plainStyle(LEGACY.deserialize(TextUtil.colorize(title)))
                        .hoverEvent(HoverEvent.showText(hover));
        Component spacedTitle = title.isEmpty() ? titleComponent : Component.empty().append(titleComponent)
                .append(plainStyle(LEGACY.deserialize(TextUtil.colorize(separator))));
        Component nameComponent = plainStyle(LEGACY.deserialize(TextUtil.colorize(name)))
                .decoration(TextDecoration.BOLD, false);
        return plainStyle(LEGACY.deserialize(TextUtil.colorize(format)))
                .replaceText(builder -> builder.match(TOKEN).replacement((match, ignored) -> switch (match.group()) {
                    case "%title%" -> spacedTitle;
                    case "%name%", "%1$s" -> nameComponent;
                    case "%2$s" -> message;
                    default -> Component.empty();
                }));
    }

    private static Component plainStyle(Component component) {
        component = component.colorIfAbsent(NamedTextColor.WHITE);
        for (TextDecoration decoration : TextDecoration.values()) {
            if (component.decoration(decoration) == TextDecoration.State.NOT_SET) {
                component = component.decoration(decoration, false);
            }
        }
        return component;
    }
}
