package cn.simpmc.title.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ShopMenuHolder implements InventoryHolder {
    private final Map<Integer, String> titleBySlot = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void put(int slot, String titleId) {
        titleBySlot.put(slot, titleId);
    }

    public String getTitle(int slot) {
        return titleBySlot.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
