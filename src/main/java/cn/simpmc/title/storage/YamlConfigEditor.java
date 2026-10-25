package cn.simpmc.title.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses YAML source positions to edit additions only, retaining existing text and comments. */
final class YamlConfigEditor {
    private YamlConfigEditor() { }

    static YamlConfiguration parse(String text) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    static String put(String text, String path, Object value) throws InvalidConfigurationException {
        parse(text);
        Node root = new Yaml().compose(new StringReader(text));
        String[] keys = path.split("\\.");
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        if (root == null) {
            return terminate(text, newline) + block(nest(keys, 0, value), 0, newline);
        }
        String result = put(text, root, keys, 0, value, newline);
        parse(result);
        return result;
    }

    private static String put(String text, Node node, String[] keys, int index, Object value, String newline) {
        if (!(node instanceof MappingNode mapping)) {
            if (node instanceof ScalarNode scalar && (scalar.getValue().isEmpty()
                    || scalar.getTag().equals(org.yaml.snakeyaml.nodes.Tag.NULL))) {
                return replace(text, node, flow(nest(keys, index, value)));
            }
            throw new IllegalArgumentException("配置路径不是 YAML 映射: " + String.join(".", keys));
        }
        for (NodeTuple entry : mapping.getValue()) {
            if (entry.getKeyNode() instanceof ScalarNode key && key.getValue().equals(keys[index])) {
                return index == keys.length - 1
                        ? replace(text, entry.getValueNode(), flow(value))
                        : put(text, entry.getValueNode(), keys, index + 1, value, newline);
            }
        }
        Map<String, Object> addition = nest(keys, index, value);
        if (mapping.getFlowStyle() == DumperOptions.FlowStyle.FLOW) {
            int end = offset(text, mapping.getEndMark().getIndex()) - 1;
            String encoded = flow(addition);
            return text.substring(0, end) + (mapping.getValue().isEmpty() ? "" : ", ")
                    + encoded.substring(1, encoded.length() - 1) + text.substring(end);
        }
        int indent = mapping.getValue().isEmpty() ? 0
                : mapping.getValue().get(0).getKeyNode().getStartMark().getColumn();
        int end = offset(text, mapping.getEndMark().getIndex());
        int lineStart = text.lastIndexOf('\n', Math.max(0, end - 1)) + 1;
        if (text.substring(lineStart, end).isBlank()) {
            end = lineStart;
        }
        return terminate(text.substring(0, end), newline) + block(addition, indent, newline)
                + text.substring(end);
    }

    private static Map<String, Object> nest(String[] keys, int index, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(keys[index], index == keys.length - 1 ? value : nest(keys, index + 1, value));
        return result;
    }

    private static String replace(String text, Node node, String value) {
        int start = offset(text, node.getStartMark().getIndex());
        int end = offset(text, node.getEndMark().getIndex());
        String prefix = start > 0 && text.charAt(start - 1) == ':' ? " " : "";
        return text.substring(0, start) + prefix + value + text.substring(end);
    }

    private static int offset(String text, int codePoints) {
        return text.offsetByCodePoints(0, codePoints);
    }

    private static String flow(Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setWidth(Integer.MAX_VALUE);
        return new Yaml(options).dump(value).stripTrailing().replaceFirst("\\n\\.\\.\\.$", "");
    }

    private static String block(Map<String, Object> values, int indent, String newline) {
        YamlConfiguration fragment = new YamlConfiguration();
        values.forEach(fragment::set);
        String prefix = " ".repeat(indent);
        return fragment.saveToString().lines().map(line -> prefix + line + newline)
                .collect(java.util.stream.Collectors.joining());
    }

    private static String terminate(String text, String newline) {
        return text.isEmpty() || text.endsWith("\n") || text.endsWith("\r") ? text : text + newline;
    }
}
