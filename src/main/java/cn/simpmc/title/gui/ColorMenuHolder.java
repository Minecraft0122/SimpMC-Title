package cn.simpmc.title.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ColorMenuHolder implements InventoryHolder {
    private final Map<Integer, String> colorBySlot = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void put(int slot, String color) {
        colorBySlot.put(slot, color);
    }

    public String getColor(int slot) {
        return colorBySlot.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
