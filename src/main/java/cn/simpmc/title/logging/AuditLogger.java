package cn.simpmc.title.logging;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuditLogger {
    private final JavaPlugin plugin;
    private File file;
    private boolean enabled;
    private long maxBytes;

    public AuditLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        enabled = plugin.getConfig().getBoolean("audit.enabled", true);
        String configuredFile = plugin.getConfig().getString("audit.file", "logs/actions.log");
        File configured = new File(configuredFile == null ? "logs/actions.log" : configuredFile);
        file = configured.isAbsolute() ? configured : new File(plugin.getDataFolder(), configured.getPath());
        maxBytes = Math.max(1L, plugin.getConfig().getLong("audit.max-file-size-mb", 10L))
                * 1024L * 1024L;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("无法创建审计日志目录: " + parent);
        }
    }

    public void log(String action, String actorName, UUID actorUuid, String targetName, UUID targetUuid,
                    Map<String, ?> details) {
        if (!enabled || file == null) {
            return;
        }
        synchronized (this) {
            try {
                rotateIfNecessary();
                StringBuilder line = new StringBuilder(256);
                line.append('{');
                appendField(line, "time", Instant.now().toString());
                appendField(line, "action", action);
                appendActor(line, "actor", actorName, actorUuid);
                if (targetName != null || targetUuid != null) {
                    appendActor(line, "target", targetName, targetUuid);
                }
                line.append(",\"details\":");
                appendObject(line, details);
                line.append('}').append(System.lineSeparator());
                Files.writeString(file.toPath(), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                plugin.getLogger().warning("写入审计日志失败: " + exception.getMessage());
            }
        }
    }

    public void logPlayer(String action, Player actor, Player target, Map<String, ?> details) {
        log(action, actor == null ? null : actor.getName(), actor == null ? null : actor.getUniqueId(),
                target == null ? null : target.getName(), target == null ? null : target.getUniqueId(), details);
    }

    public void logCommand(String action, CommandSender actor, Player target, Map<String, ?> details) {
        UUID uuid = actor instanceof Player player ? player.getUniqueId() : null;
        log(action, actor == null ? null : actor.getName(), uuid,
                target == null ? null : target.getName(), target == null ? null : target.getUniqueId(), details);
    }

    public synchronized List<String> readRecent(String filter, int limit) {
        if (file == null || !file.exists() || limit <= 0) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            Collections.reverse(lines);
            String needle = filter == null ? "" : filter.trim().toLowerCase();
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                if (needle.isEmpty() || line.toLowerCase().contains(needle)) {
                    result.add(line);
                }
                if (result.size() >= limit) {
                    break;
                }
            }
            return result;
        } catch (IOException exception) {
            plugin.getLogger().warning("读取审计日志失败: " + exception.getMessage());
            return List.of();
        }
    }

    public synchronized String getFilePath() {
        return file == null ? "" : file.getPath();
    }

    private void rotateIfNecessary() throws IOException {
        if (!file.exists() || file.length() < maxBytes) {
            return;
        }
        File archive = new File(file.getPath() + ".1");
        Files.move(file.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void appendActor(StringBuilder line, String field, String name, UUID uuid) {
        line.append(',').append(jsonString(field)).append(':');
        line.append('{');
        appendField(line, "name", name == null ? "" : name);
        appendField(line, "uuid", uuid == null ? "" : uuid.toString());
        line.append('}');
    }

    private void appendObject(StringBuilder line, Map<String, ?> values) {
        line.append('{');
        if (values != null) {
            boolean first = true;
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                if (!first) {
                    line.append(',');
                }
                first = false;
                line.append(jsonString(entry.getKey())).append(':').append(jsonValue(entry.getValue()));
            }
        }
        line.append('}');
    }

    private void appendField(StringBuilder line, String key, String value) {
        if (line.length() > 1 && line.charAt(line.length() - 1) != '{') {
            line.append(',');
        }
        line.append(jsonString(key)).append(':').append(jsonString(value));
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return jsonString(value.toString());
    }

    private String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (char character : value.toCharArray()) {
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
