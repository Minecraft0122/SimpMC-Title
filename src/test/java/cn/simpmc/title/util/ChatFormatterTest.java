package cn.simpmc.title.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatFormatterTest {
    @Test
    void titleHoverDoesNotLeakIntoSeparatorNameOrMessage() {
        Component hover = Component.text("所有称号：\n[玩家]\n[初来乍到]");
        Component message = Component.text("hello %name% 100%").clickEvent(ClickEvent.suggestCommand("/hello"));
        Component rendered = ChatFormatter.render("%title%%name% &8» &r%2$s", "&7[&f初来乍到&7]",
                " ", "&fMinecraft0122", message, hover);
        assertEquals("[初来乍到] Minecraft0122 » hello %name% 100%",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        List<Run> runs = flatten(rendered, Style.empty());
        assertTrue(runs.stream().filter(run -> run.text.contains("初来乍到"))
                .allMatch(run -> run.style.hoverEvent() != null));
        assertTrue(runs.stream().filter(run -> run.text.contains("Minecraft0122") || run.text.contains("hello"))
                .allMatch(run -> run.style.hoverEvent() == null
                        && run.style.decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE));
        assertTrue(runs.stream().anyMatch(run -> run.text.contains("hello") && run.style.clickEvent() != null));
    }

    @Test
    void legacyNameTokenAndBoldTitleCannotMakePlayerNameBold() {
        Component rendered = ChatFormatter.render("&l%title%%1$s &r%2$s", "&7&l[玩家]", " ", "&fAlex",
                Component.text("message"), Component.text("titles"));
        List<Run> runs = flatten(rendered, Style.empty());
        assertEquals("[玩家] Alex message", PlainTextComponentSerializer.plainText().serialize(rendered));
        assertTrue(runs.stream().filter(run -> run.text.contains("Alex"))
                .allMatch(run -> run.style.decoration(TextDecoration.BOLD) == TextDecoration.State.FALSE));
    }

    @Test
    void noTitleHasNoLeadingSeparatorAndKeepsFormatColors() {
        Component rendered = ChatFormatter.render("%title%%name% &c%2$s", "", " ", "&fAlex",
                Component.text("message"), Component.text("titles"));
        assertEquals("Alex message", PlainTextComponentSerializer.plainText().serialize(rendered));
        assertTrue(flatten(rendered, Style.empty()).stream().filter(run -> run.text.equals("message"))
                .allMatch(run -> net.kyori.adventure.text.format.NamedTextColor.RED.equals(run.style.color())));
    }

    private static List<Run> flatten(Component component, Style inherited) {
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        List<Run> result = new ArrayList<>();
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            result.add(new Run(text.content(), effective));
        }
        for (Component child : component.children()) {
            result.addAll(flatten(child, effective));
        }
        return result;
    }

    private record Run(String text, Style style) { }
}
