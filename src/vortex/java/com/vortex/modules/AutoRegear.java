package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

public class AutoRegear extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> regearDelay = sgGeneral.add(new IntSetting.Builder()
        .name("regear-delay")
        .description("Ticks in between regear passes.")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> armor = sgGeneral.add(new BoolSetting.Builder()
        .name("armor")
        .description("Auto-equips better armor pieces found in your inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> totem = sgGeneral.add(new BoolSetting.Builder()
        .name("totem")
        .description("Keeps a totem of undying in your offhand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> regearItems = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-items")
        .description("Keeps selected key items in your hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Only regears when the inventory is closed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> keyItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("key-items")
        .description("Items to keep in your hotbar.")
        .defaultValue(Items.NETHERITE_SWORD, Items.END_CRYSTAL, Items.TOTEM_OF_UNDYING, Items.FIREWORK_ROCKET)
        .visible(regearItems::get)
        .build()
    );

    private final Setting<List<String>> customKeyIds = sgGeneral.add(new StringListSetting.Builder()
        .name("custom-key-ids")
        .description("Item ids to keep in your hotbar, for custom/modded items (e.g. crystal_anchor).")
        .defaultValue()
        .visible(regearItems::get)
        .build()
    );

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final int[] ARMOR_CONTAINER_SLOTS = {8, 7, 6, 5};

    private static AutoRegear INSTANCE;

    private int timer;

    public AutoRegear() {
        super(Vortex.CATEGORY, "auto-regear", "Automatically regears your armor, totem and key hotbar items.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isAlive()) return;

        if (silent.get() && !mc.player.containerMenu.getCarried().isEmpty()) return;

        if (timer <= 0) {
            doRegear();
            timer = regearDelay.get();
        } else {
            timer--;
        }
    }

    public static void requestRegear() {
        if (INSTANCE != null && !INSTANCE.isActive()) {
            INSTANCE.toggle();
            INSTANCE.timer = 0;
        }
    }

    private void doRegear() {
        if (armor.get()) regearArmor();
        if (totem.get()) regearTotem();
        if (regearItems.get() && (keyItems.get().size() > 0 || !customKeyIds.get().isEmpty())) regearHotbarItems();
    }

    // Armor

    private void regearArmor() {
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            EquipmentSlot slot = ARMOR_SLOTS[i];
            ItemStack worn = mc.player.getItemBySlot(slot);

            int best = findBestArmor(slot);
            if (best == -1) continue;

            ItemStack candidate = mc.player.getInventory().getItem(best);

            if (worn.isEmpty() || getDurability(candidate) > getDurability(worn)) moveToArmor(best, i);
        }
    }

    private int findBestArmor(EquipmentSlot slot) {
        int bestSlot = -1;
        int bestDurability = -1;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (i == mc.player.getInventory().getSelectedSlot()) continue;
            if (i >= 36 && i <= 39) continue;
            if (i == 40) continue;

            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.slot() != slot) continue;

            int durability = getDurability(stack);
            if (durability <= 0) continue;

            if (durability > bestDurability) {
                bestDurability = durability;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int getDurability(ItemStack stack) {
        if (stack.getMaxDamage() <= 0) return -1;
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private void moveToArmor(int fromIndex, int armorIndex) {
        InvUtils.move().from(fromIndex).toId(ARMOR_CONTAINER_SLOTS[armorIndex]);
    }

    // Totem

    private void regearTotem() {
        if (isTotem(mc.player.getOffhandItem())) return;

        int slot = findInInventory(this::isTotem, 0, 39);
        if (slot == -1) return;

        InvUtils.move().from(slot).toOffhand();
    }

    private boolean isTotem(ItemStack stack) {
        return stack.is(Items.TOTEM_OF_UNDYING);
    }

    // Hotbar

    private void regearHotbarItems() {
        for (Item item : keyItems.get()) {
            if (InvUtils.testInHotbar(item)) continue;

            int slot = findInInventory(stack -> stack.is(item), 9, 35);
            if (slot == -1) continue;

            moveToHotbar(slot);
        }

        if (!customKeyIds.get().isEmpty()) {
            for (String id : customKeyIds.get()) {
                if (containsInHotbarId(id)) continue;

                int slot = findInInventory(stack -> matchesId(stack, id), 9, 35);
                if (slot == -1) continue;

                moveToHotbar(slot);
            }
        }
    }

    private void moveToHotbar(int fromIndex) {
        int target = findFreeHotbarSlot();
        InvUtils.move().from(fromIndex).toHotbar(target);
    }

    private int findFreeHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return 8;
    }

    private boolean containsInHotbarId(String id) {
        for (int i = 0; i < 9; i++) {
            if (matchesId(mc.player.getInventory().getItem(i), id)) return true;
        }
        return false;
    }

    private boolean matchesId(ItemStack stack, String id) {
        if (stack.isEmpty()) return false;
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && (key.getPath().equalsIgnoreCase(id) || key.toString().equalsIgnoreCase(id));
    }

    private int findInInventory(java.util.function.Predicate<ItemStack> predicate, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (predicate.test(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }
}