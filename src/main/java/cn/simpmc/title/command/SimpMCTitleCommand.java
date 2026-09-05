package cn.simpmc.title.command;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.gui.GuiService;
import cn.simpmc.title.model.TitleDefinition;
import cn.simpmc.title.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class SimpMCTitleCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "menu", "colors", "list", "equip", "unequip", "nickname", "color", "resetname",
            "give", "take", "set", "reload");

    private final SimpMCTitlePlugin plugin;
    private final TitleManager titleManager;
    private final GuiService guiService;

    public SimpMCTitleCommand(SimpMCTitlePlugin plugin, TitleManager titleManager, GuiService guiService) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.guiService = guiService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "menu" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    guiService.openTitles(player);
                }
            }
            case "colors" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    guiService.openColors(player);
                }
            }
            case "list" -> listTitles(sender);
            case "equip" -> equip(sender, args);
            case "unequip" -> unequip(sender);
            case "nickname" -> nickname(sender, args);
            case "color" -> color(sender, args);
            case "resetname" -> resetName(sender, args);
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
            case "set" -> set(sender, args);
            case "reload" -> reload(sender);
            default -> plugin.send(sender, "unknown-command");
        }
        return true;
    }

    private void listTitles(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&8&m----------------------------------------"));
        sender.sendMessage(TextUtil.colorize("&bSimpMC-Title &7称号列表"));
        for (TitleDefinition title : titleManager.getTitleList()) {
            boolean available = sender instanceof Player player && titleManager.canUse(player, title);
            String status = available ? "&a已解锁" : "&c未解锁";
            sender.sendMessage(TextUtil.colorize("&f" + title.getId() + " &8- " + title.getDisplay()
                    + " &8[" + status + "&8]"));
        }
        sender.sendMessage(TextUtil.colorize("&8&m----------------------------------------"));
    }

    private void equip(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, "unknown-command");
            return;
        }
        TitleDefinition title = titleManager.getTitle(args[1]);
        if (title == null) {
            send(sender, "title-not-found", Map.of("%id%", args[1]));
            return;
        }
        if (!titleManager.equip(player, title.getId())) {
            plugin.send(sender, "title-no-access");
            return;
        }
        send(sender, "title-equipped", Map.of("%title%", title.getDisplay()));
    }

    private void unequip(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        titleManager.clearTitle(player);
        plugin.send(sender, "title-cleared");
    }

    private void nickname(CommandSender sender, String[] args) {
        Player target;
        int start;
        if (args.length >= 3) {
            if (!requireAdmin(sender)) {
                return;
            }
            target = findTarget(sender, args[1]);
            start = 2;
        } else {
            target = requirePlayer(sender);
            start = 1;
            if (target != null && !target.hasPermission("simpmctitle.nickname")
                    && !target.hasPermission("simpmctitle.admin")) {
                plugin.send(sender, "no-permission");
                return;
            }
        }
        if (target == null) {
            return;
        }
        if (args.length <= start) {
            plugin.send(sender, "unknown-command");
            return;
        }
        String value = join(args, start);
        if (!titleManager.updateNickname(target, value)) {
            send(sender, "invalid-nickname", Map.of("%max%",
                    String.valueOf(plugin.getConfig().getInt("nickname.max-length", 16))));
            return;
        }
        send(sender, "nickname-updated", Map.of("%name%", titleManager.getDisplayName(target)));
    }

    private void color(CommandSender sender, String[] args) {
        Player target;
        int valueIndex;
        if (args.length >= 3) {
            if (!requireAdmin(sender)) {
                return;
            }
            target = findTarget(sender, args[1]);
            valueIndex = 2;
        } else {
            target = requirePlayer(sender);
            valueIndex = 1;
            if (target != null && !target.hasPermission("simpmctitle.nickname")
                    && !target.hasPermission("simpmctitle.admin")) {
                plugin.send(sender, "no-permission");
                return;
            }
        }
        if (target == null || args.length <= valueIndex) {
            plugin.send(sender, "unknown-command");
            return;
        }
        if (!titleManager.updateColor(target, join(args, valueIndex))) {
            plugin.send(sender, "invalid-color");
            return;
        }
        send(sender, "color-updated", Map.of("%name%", titleManager.getDisplayName(target)));
    }

    private void resetName(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!requireAdmin(sender)) {
                return;
            }
            target = findTarget(sender, args[1]);
        } else {
            target = requirePlayer(sender);
            if (target != null && !target.hasPermission("simpmctitle.nickname")
                    && !target.hasPermission("simpmctitle.admin")) {
                plugin.send(sender, "no-permission");
                return;
            }
        }
        if (target == null) {
            return;
        }
        titleManager.resetNickname(target);
        plugin.send(sender, "nickname-reset");
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        Player target = findTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        int days = 0;
        if (args.length >= 4) {
            try {
                days = Integer.parseInt(args[3]);
                if (days < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                plugin.send(sender, "unknown-command");
                return;
            }
        }
        TitleDefinition title = titleManager.getTitle(args[2]);
        if (title == null || !titleManager.grant(target, title.getId(), days)) {
            send(sender, "title-not-found", Map.of("%id%", args[2]));
            return;
        }
        send(sender, "title-given", Map.of("%title%", title.getDisplay(), "%player%", target.getName()));
    }

    private void take(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        Player target = findTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        TitleDefinition title = titleManager.getTitle(args[2]);
        if (title == null || !titleManager.take(target, title.getId())) {
            send(sender, "title-not-found", Map.of("%id%", args[2]));
            return;
        }
        send(sender, "title-taken", Map.of("%title%", title.getDisplay(), "%player%", target.getName()));
    }

    private void set(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        Player target = findTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        TitleDefinition title = titleManager.getTitle(args[2]);
        if (title == null || !titleManager.grant(target, title.getId(), 0)
                || !titleManager.forceEquip(target, title.getId())) {
            send(sender, "title-not-found", Map.of("%id%", args[2]));
            return;
        }
        send(sender, "title-equipped", Map.of("%title%", title.getDisplay()));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("simpmctitle.reload") && !sender.hasPermission("simpmctitle.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        titleManager.reload();
        plugin.send(sender, "reloaded");
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.getConfig().getStringList("messages.help")) {
            sender.sendMessage(TextUtil.colorize(line));
        }
        if (sender.hasPermission("simpmctitle.admin")) {
            for (String line : plugin.getConfig().getStringList("messages.admin-help")) {
                sender.sendMessage(TextUtil.colorize(line));
            }
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return null;
        }
        if (!player.hasPermission("simpmctitle.use") && !player.hasPermission("simpmctitle.admin")) {
            plugin.send(sender, "no-permission");
            return null;
        }
        return player;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("simpmctitle.admin")) {
            plugin.send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private Player findTarget(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            send(sender, "target-not-found", Map.of("%player%", name));
        }
        return target;
    }

    private void send(CommandSender sender, String key, Map<String, String> replacements) {
        String value = plugin.message(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        sender.sendMessage(TextUtil.colorize(plugin.message("prefix") + value));
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(SUBCOMMANDS, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("equip") && args.length == 2) {
            return partial(new ArrayList<>(titleManager.getTitles().keySet()), args[1]);
        }
        if ((subcommand.equals("give") || subcommand.equals("take") || subcommand.equals("set"))
                && args.length == 2) {
            return partial(onlineNames(), args[1]);
        }
        if ((subcommand.equals("give") || subcommand.equals("take") || subcommand.equals("set"))
                && args.length == 3) {
            return partial(new ArrayList<>(titleManager.getTitles().keySet()), args[2]);
        }
        if ((subcommand.equals("nickname") || subcommand.equals("color") || subcommand.equals("resetname"))
                && args.length == 2 && sender.hasPermission("simpmctitle.admin")) {
            return partial(onlineNames(), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> partial(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).limit(80)
                .collect(Collectors.toList());
    }
}
