package cn.simpmc.title.command;

import cn.simpmc.title.SimpMCTitlePlugin;
import cn.simpmc.title.TitleManager;
import cn.simpmc.title.gui.GuiService;
import cn.simpmc.title.model.ExchangeOutcome;
import cn.simpmc.title.model.PurchaseMethod;
import cn.simpmc.title.model.PurchaseResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class SimpMCTitleCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "menu", "help", "shop", "colors", "list", "buy", "balance", "exchange", "equip", "unequip",
            "color", "resetname", "give", "take", "set", "group", "currency", "audit", "reload");

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
        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player != null) {
                guiService.openTitles(player);
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (List.of("nickname", "color", "resetname").contains(subcommand)
                && !plugin.getConfig().getBoolean("nickname.enabled", false)) {
            plugin.send(sender, "nickname-disabled");
            return true;
        }
        switch (subcommand) {
            case "menu" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    guiService.openTitles(player);
                }
            }
            case "shop" -> {
                Player player = requirePermissionPlayer(sender, "simpmctitle.shop");
                if (player != null) {
                    guiService.openShop(player);
                }
            }
            case "colors" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    guiService.openColors(player);
                }
            }
            case "list" -> listTitles(sender);
            case "buy" -> buy(sender, args);
            case "balance" -> balance(sender);
            case "exchange" -> exchange(sender, args);
            case "equip" -> equip(sender, args);
            case "unequip" -> unequip(sender);
            case "nickname" -> nickname(sender, args);
            case "color" -> color(sender, args);
            case "resetname" -> resetName(sender, args);
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
            case "set" -> set(sender, args);
            case "group" -> group(sender, args);
            case "currency" -> currency(sender, args);
            case "audit" -> audit(sender, args);
            case "reload" -> reload(sender);
            default -> plugin.send(sender, "unknown-command");
        }
        return true;
    }

    private void group(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&c用法：/smtitle group <create|delete|title|add|remove|list> ..."));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            sender.sendMessage(TextUtil.colorize("&b称号组：&f" + String.join(", ", titleManager.getTitleGroups())));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("&c请指定称号组名称。"));
            return;
        }
        String group = args[2];
        if (!group.matches("[A-Za-z0-9_-]{1,32}")) {
            sender.sendMessage(TextUtil.colorize("&c组名仅能包含字母、数字、下划线和短横线，长度最多 32。"));
            return;
        }
        switch (action) {
            case "create" -> sender.sendMessage(TextUtil.colorize(titleManager.createTitleGroup(group)
                    ? "&a已创建称号组：&f" + group : "&c称号组已存在：&f" + group));
            case "delete" -> sender.sendMessage(TextUtil.colorize(titleManager.deleteTitleGroup(group)
                    ? "&a已删除称号组：&f" + group : "&c称号组不存在：&f" + group));
            case "title" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.colorize("&c用法：/smtitle group title <组名> <称号文本>"));
                    return;
                }
                String title = join(args, 3);
                sender.sendMessage(TextUtil.colorize(titleManager.setTitleGroupTitle(group, title)
                        ? "&a已更新称号组 &f" + group + " &a的共享称号。"
                        : "&c组不存在或称号文本无效。"));
            }
            case "add", "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.colorize("&c用法：/smtitle group " + action + " <组名> <在线玩家>"));
                    return;
                }
                Player target = findTarget(sender, args[3]);
                if (target == null) {
                    return;
                }
                boolean changed = action.equals("add")
                        ? titleManager.addTitleGroupMember(group, target)
                        : titleManager.removeTitleGroupMember(group, target);
                sender.sendMessage(TextUtil.colorize(changed
                        ? "&a已" + (action.equals("add") ? "将 &f" + target.getName() + " &a加入" : "从")
                                + "称号组 &f" + group + (action.equals("remove") ? " &a移除玩家。" : "。")
                        : "&c操作失败，请确认组存在且成员状态正确。"));
            }
            default -> sender.sendMessage(TextUtil.colorize("&c用法：/smtitle group <create|delete|title|add|remove|list> ..."));
        }
    }

    private void listTitles(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&8&m----------------------------------------"));
        sender.sendMessage(TextUtil.colorize("&bSimpMC-Title &7称号列表"));
        for (TitleDefinition title : titleManager.getTitleList()) {
            boolean available = sender instanceof Player player && titleManager.canUse(player, title);
            String status = available ? "&a已解锁" : "&c未解锁";
            String sale = title.isPurchaseEnabled() ? " &8[&e可购买&8]" : "";
            sender.sendMessage(TextUtil.colorize("&f" + title.getId() + " &8- " + title.getDisplay()
                    + " &8[" + status + "&8]" + sale));
        }
        sender.sendMessage(TextUtil.colorize("&8&m----------------------------------------"));
    }

    private void buy(CommandSender sender, String[] args) {
        Player player = requirePermissionPlayer(sender, "simpmctitle.purchase");
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, "unknown-command");
            return;
        }
        TitleDefinition title = titleManager.getTitle(args[1]);
        PurchaseMethod method;
        if (args.length >= 3) {
            method = PurchaseMethod.fromInput(args[2]);
            if (method == null) {
                plugin.send(sender, "invalid-purchase-method");
                return;
            }
        } else {
            method = titleManager.getDefaultPurchaseMethod();
        }
        PurchaseResult result = titleManager.purchase(player, args[1], method);
        plugin.sendPurchaseResult(sender, result, title, method);
    }

    private void balance(CommandSender sender) {
        Player player = requirePermissionPlayer(sender, "simpmctitle.balance");
        if (player == null) {
            return;
        }
        double gold = titleManager.getGoldBalance(player);
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("%gold%", TextUtil.formatMoney(gold));
        replacements.put("%internal%", String.valueOf(titleManager.getInternalBalance(player)));
        replacements.put("%currency%", titleManager.getInternalCurrencyName());
        replacements.put("%vault%", titleManager.isEconomyAvailable() ? "已连接" : "未连接");
        plugin.send(sender, "balance", replacements);
    }

    private void exchange(CommandSender sender, String[] args) {
        Player player = requirePermissionPlayer(sender, "simpmctitle.exchange");
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, "unknown-command");
            return;
        }
        ExchangeOutcome outcome = titleManager.exchange(player, args[1]);
        plugin.sendExchangeResult(sender, outcome);
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
            plugin.send(sender, "title-not-found", Map.of("%id%", args[1]));
            return;
        }
        if (!titleManager.equip(player, title.getId())) {
            plugin.send(sender, "title-no-access");
            return;
        }
        plugin.send(sender, "title-equipped", Map.of("%title%", title.getDisplay()));
    }

    private void unequip(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        titleManager.clearTitle(player);
        plugin.send(sender, player.hasPermission(TitleManager.HIDE_CHAT_TITLE_PERMISSION)
                ? "title-cleared" : "title-cleared-chat-required");
    }

    private void nickname(CommandSender sender, String[] args) {
        Player target;
        int start;
        boolean adminForm = args.length >= 3 && sender.hasPermission("simpmctitle.admin")
                && Bukkit.getPlayerExact(args[1]) != null;
        if (adminForm) {
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
        executeForTarget(target, () -> {
            boolean success = titleManager.updateNickname(target, value);
            if (!success) {
                if (adminForm) {
                    logAdmin(sender, "admin.nickname", target, "invalid", Map.of("nickname", value));
                }
                plugin.send(sender, "invalid-nickname", Map.of(
                        "%max%", String.valueOf(plugin.getConfig().getInt("nickname.max-length", 16))));
                return;
            }
            if (adminForm) {
                logAdmin(sender, "admin.nickname", target, "success", Map.of("nickname", value));
            }
            plugin.send(sender, "nickname-updated", Map.of("%name%", titleManager.getDisplayName(target)));
        });
    }

    private void color(CommandSender sender, String[] args) {
        Player target;
        int valueIndex;
        boolean adminForm = args.length >= 3 && sender.hasPermission("simpmctitle.admin")
                && Bukkit.getPlayerExact(args[1]) != null;
        if (adminForm) {
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
        String value = join(args, valueIndex);
        executeForTarget(target, () -> {
            if (!titleManager.updateColor(target, value)) {
                if (adminForm) {
                    logAdmin(sender, "admin.color", target, "invalid", Map.of("color", value));
                }
                plugin.send(sender, "invalid-color");
                return;
            }
            if (adminForm) {
                logAdmin(sender, "admin.color", target, "success", Map.of("color", value));
            }
            plugin.send(sender, "color-updated", Map.of("%name%", titleManager.getDisplayName(target)));
        });
    }

    private void resetName(CommandSender sender, String[] args) {
        Player target;
        boolean adminForm = args.length >= 2;
        if (adminForm) {
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
        executeForTarget(target, () -> {
            titleManager.resetNickname(target);
            if (adminForm) {
                logAdmin(sender, "admin.reset-name", target, "success", Map.of());
            }
            plugin.send(sender, "nickname-reset");
        });
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
        int titleEnd = args.length;
        if (args.length >= 4 && args[args.length - 1].matches("-?\\d+")) {
            try {
                days = Integer.parseInt(args[args.length - 1]);
                if (days < 0 || days > 36500) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                plugin.send(sender, "invalid-days");
                return;
            }
            titleEnd--;
        }
        String requested = String.join(" ", Arrays.copyOfRange(args, 2, titleEnd));
        TitleDefinition title = titleEnd == 3 ? titleManager.getTitle(requested) : null;
        String titleId = title == null ? titleManager.registerCustomTitle(requested) : title.getId();
        String display = title == null ? requested : title.getDisplay();
        if (titleId == null) {
            plugin.send(sender, "invalid-custom-title");
            return;
        }
        int grantDays = days;
        executeForTarget(target, () -> {
            boolean success = titleManager.grant(target, titleId, grantDays);
            logAdmin(sender, "admin.give-title", target, success ? "success" : "failed", Map.of(
                    "title_id", titleId, "title", display, "days", grantDays));
            if (!success) {
                plugin.send(sender, "title-not-found", Map.of("%id%", titleId));
                return;
            }
            plugin.send(sender, "title-given", Map.of("%title%", display, "%player%", target.getName()));
        });
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
        executeForTarget(target, () -> {
            boolean success = title != null && titleManager.take(target, args[2]);
            logAdmin(sender, "admin.take-title", target, success ? "success" : "failed",
                    Map.of("title_id", args[2]));
            if (!success) {
                plugin.send(sender, title == null ? "title-not-found" : "title-take-failed",
                        Map.of("%id%", args[2]));
                return;
            }
            plugin.send(sender, "title-taken", Map.of("%title%", title.getDisplay(), "%player%", target.getName()));
        });
    }

    private void set(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        Player target = findTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        int days = 0;
        int titleEnd = args.length;
        if (args.length >= 4 && args[args.length - 1].matches("-?\\d+")) {
            try {
                days = Integer.parseInt(args[args.length - 1]);
                if (days < 0 || days > 36500) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                plugin.send(sender, "invalid-days");
                return;
            }
            titleEnd--;
        }
        String requested = String.join(" ", Arrays.copyOfRange(args, 2, titleEnd));
        TitleDefinition title = titleEnd == 3 ? titleManager.getTitle(requested) : null;
        String display = title == null ? requested : title.getDisplay();
        String titleId = title == null ? titleManager.registerCustomTitle(display) : title.getId();
        if (titleId == null) {
            plugin.send(sender, "invalid-custom-title");
            return;
        }
        int expiresInDays = days;
        executeForTarget(target, () -> {
            boolean success = titleManager.forceEquip(target, titleId, expiresInDays);
            logAdmin(sender, "admin.set-title", target, success ? "success" : "failed",
                    Map.of("title_id", titleId, "title", display, "days", expiresInDays));
            if (!success) {
                plugin.send(sender, "title-not-found", Map.of("%id%", titleId));
                return;
            }
            plugin.send(sender, "title-set", Map.of(
                    "%title%", display,
                    "%days%", expiresInDays == 0 ? "永久" : expiresInDays + " 天"));
        });
    }

    private void currency(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 4) {
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!List.of("give", "take", "set").contains(action)) {
            plugin.send(sender, "unknown-command");
            return;
        }
        Player target = findTarget(sender, args[2]);
        if (target == null) {
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
            if (amount < 0L) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            plugin.send(sender, "invalid-currency-amount");
            return;
        }
        executeForTarget(target, () -> {
            long before = titleManager.getInternalBalance(target);
            boolean success = switch (action) {
                case "give" -> titleManager.giveInternalBalance(target, amount);
                case "take" -> titleManager.takeInternalBalance(target, amount);
                case "set" -> titleManager.setInternalBalance(target, amount);
                default -> false;
            };
            long after = titleManager.getInternalBalance(target);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("operation", action);
            details.put("amount", amount);
            details.put("before", before);
            details.put("after", after);
            details.put("result", success ? "success" : "failed");
            plugin.getAuditLogger().logCommand("admin.currency", sender, target, details);
            if (!success) {
                plugin.send(sender, action.equals("take") ? "currency-insufficient" : "currency-operation-failed");
                return;
            }
            plugin.send(sender, "currency-updated", Map.of(
                    "%player%", target.getName(), "%amount%", String.valueOf(amount),
                    "%balance%", String.valueOf(after), "%currency%", titleManager.getInternalCurrencyName()));
        });
    }

    private void audit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simpmctitle.audit") && !sender.hasPermission("simpmctitle.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        String filter = args.length >= 2 ? args[1] : "";
        int limit = Math.max(1, Math.min(50, plugin.getConfig().getInt("audit.command-limit", 10)));
        List<String> lines = plugin.getAuditLogger().readRecent(filter, limit);
        sender.sendMessage(TextUtil.colorize("&8[&bSimpMC-Title&8] &7最近的审计记录: &f" +
                (filter.isBlank() ? "全部" : filter)));
        if (lines.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("&7没有找到记录。日志文件: &f" + plugin.getAuditLogger().getFilePath()));
        } else {
            for (String line : lines) {
                sender.sendMessage(TextUtil.colorize("&8" + line));
            }
        }
        plugin.getAuditLogger().logCommand("admin.audit-view", sender, null, Map.of("filter", filter));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("simpmctitle.reload") && !sender.hasPermission("simpmctitle.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        titleManager.reload();
        plugin.getAuditLogger().logCommand("admin.reload", sender, null, Map.of("result", "success"));
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
        return requirePermissionPlayer(sender, "simpmctitle.use");
    }

    private Player requirePermissionPlayer(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return null;
        }
        if (!player.hasPermission(permission) && !player.hasPermission("simpmctitle.admin")) {
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
            plugin.send(sender, "target-not-found", Map.of("%player%", name));
        }
        return target;
    }

    private void logAdmin(CommandSender sender, String action, Player target, String result,
                          Map<String, ?> details) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (details != null) {
            values.putAll(details);
        }
        values.put("result", result);
        plugin.getAuditLogger().logCommand(action, sender, target, values);
    }

    private void executeForTarget(Player target, Runnable action) {
        target.getScheduler().run(plugin, task -> action.run(), null);
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(SUBCOMMANDS);
            if (plugin.getConfig().getBoolean("nickname.enabled", false)) {
                commands.addAll(List.of("nickname", "color", "resetname"));
            }
            return partial(commands, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("group") && args.length == 2 && sender.hasPermission("simpmctitle.admin")) {
            return partial(List.of("create", "delete", "title", "add", "remove", "list"), args[1]);
        }
        if (subcommand.equals("group") && args.length == 3 && sender.hasPermission("simpmctitle.admin")
                && !args[1].equalsIgnoreCase("list")) {
            return partial(new ArrayList<>(titleManager.getTitleGroups()), args[2]);
        }
        if (subcommand.equals("group") && args.length == 4 && sender.hasPermission("simpmctitle.admin")
                && List.of("add", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
            return partial(onlineNames(), args[3]);
        }
        if ((subcommand.equals("equip") || subcommand.equals("buy")) && args.length == 2) {
            return partial(new ArrayList<>(titleManager.getTitles().keySet()), args[1]);
        }
        if (subcommand.equals("buy") && args.length == 3) {
            return partial(List.of("gold", "internal"), args[2]);
        }
        if (subcommand.equals("exchange") && args.length == 2) {
            return partial(List.of("all"), args[1]);
        }
        if ((subcommand.equals("give") || subcommand.equals("take") || subcommand.equals("set"))
                && args.length == 2) {
            return partial(onlineNames(), args[1]);
        }
        if ((subcommand.equals("take") || subcommand.equals("set")) && args.length == 3) {
            return partial(new ArrayList<>(titleManager.getTitles().keySet()), args[2]);
        }
        if (subcommand.equals("currency") && args.length == 2) {
            return partial(List.of("give", "take", "set"), args[1]);
        }
        if (subcommand.equals("currency") && args.length == 3) {
            return partial(onlineNames(), args[2]);
        }
        if ((subcommand.equals("nickname") || subcommand.equals("color") || subcommand.equals("resetname"))
                && args.length == 2 && plugin.getConfig().getBoolean("nickname.enabled", false)
                && sender.hasPermission("simpmctitle.admin")) {
            return partial(onlineNames(), args[1]);
        }
        if (subcommand.equals("audit") && args.length == 2) {
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
