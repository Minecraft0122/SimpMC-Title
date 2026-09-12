package cn.simpmc.title.gui;

import cn.simpmc.title.model.PurchaseMethod;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class PurchaseMenuHolder implements InventoryHolder {
    private final String titleId;
    private final Map<Integer, PurchaseMethod> methodBySlot = new HashMap<>();
    private Inventory inventory;

    public PurchaseMenuHolder(String titleId) {
        this.titleId = titleId;
    }

    public String getTitleId() {
        return titleId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void put(int slot, PurchaseMethod method) {
        methodBySlot.put(slot, method);
    }

    public PurchaseMethod getMethod(int slot) {
        return methodBySlot.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
