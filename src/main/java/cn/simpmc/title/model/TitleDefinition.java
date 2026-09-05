package cn.simpmc.title.model;

import org.bukkit.Material;

public final class TitleDefinition {
    private final String id;
    private final String display;
    private final String description;
    private final String permission;
    private final Material icon;

    public TitleDefinition(String id, String display, String description, String permission, Material icon) {
        this.id = id;
        this.display = display;
        this.description = description;
        this.permission = permission;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }

    public String getPermission() {
        return permission;
    }

    public Material getIcon() {
        return icon;
    }
}
