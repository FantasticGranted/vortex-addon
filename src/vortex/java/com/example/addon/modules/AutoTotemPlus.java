package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;

public class AutoTotemPlus extends Module {

    private final SettingGroup sgGeneral =
        settings.getDefaultGroup();

    // ============================================================
    // SETTINGS
    // ============================================================

    private final Setting<Keybind> xpKeybind =
        sgGeneral.add(
            new KeybindSetting.Builder()
                .name("xp-keybind")
                .description(
                    "Hold this key to throw XP bottles. Does nothing while a GUI is open."
                )
                .defaultValue(Keybind.none())
                .build()
        );

    private final Setting<Integer> xpDelay =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("xp-delay")
                .description("Ticks between XP bottle throws.")
                .defaultValue(2)
                .min(0)
                .max(20)
                .sliderMin(0)
                .sliderMax(20)
                .build()
        );

    private final Setting<Integer> totemDelay =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("totem-delay")
                .description(
                    "Ticks to wait before putting a Totem in the offhand."
                )
                .defaultValue(0)
                .min(0)
                .max(5)
                .sliderMin(0)
                .sliderMax(5)
                .build()
        );

    private final Setting<Integer> minHealth =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("minimum-health")
                .description(
                    "Health threshold for protecting the offhand item. At or below this health, the module uses a Totem instead of a Shield or enchanted golden apple. 0 disables the health check."
                )
                .defaultValue(10)
                .min(0)
                .max(36)
                .sliderMin(0)
                .sliderMax(36)
                .build()
        );

    private final Setting<Boolean> shieldMode =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("shield-mode")
                .description(
                    "Uses a Shield in the offhand by default and switches to a Totem when health reaches the minimum-health threshold."
                )
                .defaultValue(false)
                .build()
        );

    // ============================================================
    // RIGHT-CLICK APPLE MODE
    // ============================================================

    private final Setting<RightClickMode> rightClickMode =
        sgGeneral.add(
            new EnumSetting.Builder<RightClickMode>()
                .name("right-click-apple")
                .description(
                    "Controls when an enchanted golden apple is moved to the offhand while right-clicking."
                )
                .defaultValue(RightClickMode.NONE)
                .build()
        );

    // ============================================================
    // STATE
    // ============================================================

    private int xpTimer;
    private int totemTimer;

    private boolean wasThrowingXp;
    private boolean waitingForTotem;

    /*
     * PlayerInventory indexes:
     *
     * 0-8  = hotbar
     * 9-35 = main inventory
     */
    private int xpInventorySlot = -1;

    private boolean xpSwapPending;

    // ============================================================
    // TOTEM STATE
    // ============================================================

    private boolean totemSwapPending;
    private int totemInventorySlot = -1;

    // ============================================================
    // SHIELD STATE
    // ============================================================

    /*
     * Original PlayerInventory index where the Shield came from.
     *
     * 0-8  = hotbar
     * 9-35 = main inventory
     */
    private int shieldInventorySlot = -1;

    /*
     * True after a Shield has been successfully moved
     * into the offhand.
     */
    private boolean shieldSwapActive;

    /*
     * A Shield swap has been sent but the inventory update
     * has not been confirmed yet.
     */
    private boolean shieldSwapPending;

    // ============================================================
    // ENCHANTED GOLDEN APPLE STATE
    // ============================================================

    /*
     * Original PlayerInventory index where the apple came from.
     */
    private int appleInventorySlot = -1;

    /*
     * True only after the apple has actually been confirmed
     * in the offhand.
     */
    private boolean appleSwapActive;

    /*
     * A swap packet has been sent but the inventory update has
     * not been confirmed yet.
     */
    private boolean appleSwapPending;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public AutoTotemPlus() {
        super(
            QuinnAddon.CATEGORY,
            "autototem+",
            "Automatically manages the offhand with Totems, Shields, XP, and enchanted golden apples.\nFrom: Quinn"
        );
    }

    // ============================================================
    // ACTIVATE
    // ============================================================

    @Override
    public void onActivate() {
        xpTimer = 0;
        totemTimer = 0;

        wasThrowingXp = false;
        waitingForTotem = false;

        xpInventorySlot = -1;
        xpSwapPending = false;

        totemSwapPending = false;
        totemInventorySlot = -1;

        shieldInventorySlot = -1;
        shieldSwapActive = false;
        shieldSwapPending = false;

        appleInventorySlot = -1;
        appleSwapActive = false;
        appleSwapPending = false;
    }

    // ============================================================
    // DEACTIVATE
    // ============================================================

    @Override
    public void onDeactivate() {

        /*
         * Return the apple to its original location if possible.
         */
        if (
            mc.player != null
                && mc.gameMode != null
                && appleInventorySlot >= 0
                && appleInventorySlot <= 35
                && isEnchantedGoldenApple(
                    mc.player.getOffhandItem()
                )
        ) {
            swapSlotWithOffhand(
                appleInventorySlot
            );
        }

        xpTimer = 0;
        totemTimer = 0;

        wasThrowingXp = false;
        waitingForTotem = false;

        xpInventorySlot = -1;
        xpSwapPending = false;

        totemSwapPending = false;
        totemInventorySlot = -1;

        shieldInventorySlot = -1;
        shieldSwapActive = false;
        shieldSwapPending = false;

        appleInventorySlot = -1;
        appleSwapActive = false;
        appleSwapPending = false;
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        if (
            mc.player == null
                || mc.level == null
                || mc.gameMode == null
        ) {
            return;
        }

        boolean guiOpen =
            mc.screen != null;

        // ========================================================
        // RIGHT-CLICK APPLE
        // ========================================================

        if (!guiOpen) {
            handleRightClickApple();
        } else {

            /*
             * Don't leave the apple in the offhand if a GUI opens.
             */
            if (appleSwapActive) {
                returnAppleToInventory();
            }
        }

        // ========================================================
        // XP SYSTEM
        // ========================================================

        if (!guiOpen) {

            boolean throwingXp =
                xpKeybind.get().isPressed();

            if (throwingXp) {

                /*
                 * Don't interfere with an active enchanted apple.
                 */
                if (!appleSwapActive) {
                    handleXpMode();
                }

                wasThrowingXp = true;

            } else {

                /*
                 * Return XP to its original slot.
                 */
                if (
                    xpInventorySlot != -1
                        && isXp(
                            mc.player.getOffhandItem()
                        )
                ) {
                    returnXpToInventory();
                }

                wasThrowingXp = false;
            }
        }

        // ========================================================
        // SHIELD / TOTEM SYSTEM
        // ========================================================

        /*
         * Don't replace an enchanted golden apple or XP.
         */
        if (
            !appleSwapActive
                && (guiOpen || !wasThrowingXp)
        ) {

            if (shieldMode.get()) {
                handleShieldMode();
            } else {
                handleTotemMode();
            }
        }
    }

    // ============================================================
    // RIGHT-CLICK APPLE
    // ============================================================

    private void handleRightClickApple() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        // --------------------------------------------------------
        // NONE
        // --------------------------------------------------------

        if (
            rightClickMode.get()
                == RightClickMode.NONE
        ) {

            if (appleSwapActive) {
                returnAppleToInventory();
            }

            return;
        }

        // --------------------------------------------------------
        // RIGHT CLICK
        // --------------------------------------------------------

        boolean rightClicking =
            mc.options.keyUse.isDown();

        if (!rightClicking) {

            if (appleSwapActive) {
                returnAppleToInventory();
            }

            return;
        }

        // --------------------------------------------------------
        // CHECK MODE
        // --------------------------------------------------------

        if (!isRightClickAppleAllowed()) {

            if (appleSwapActive) {
                returnAppleToInventory();
            }

            return;
        }

        // --------------------------------------------------------
        // HEALTH CHECK
        // --------------------------------------------------------

        /*
         * Don't remove the Totem / Shield or equip the apple
         * when health is at or below the configured threshold.
         */
        if (
            !isEnchantedGoldenApple(
                mc.player.getOffhandItem()
            )
                && shouldProtectTotem()
        ) {
            return;
        }

        // --------------------------------------------------------
        // APPLE ALREADY IN OFFHAND
        // --------------------------------------------------------

        if (
            isEnchantedGoldenApple(
                mc.player.getOffhandItem()
            )
        ) {
            appleSwapPending = false;
            appleSwapActive = true;

            return;
        }

        // --------------------------------------------------------
        // WAIT FOR PREVIOUS SWAP
        // --------------------------------------------------------

        if (appleSwapPending) {

            if (
                isEnchantedGoldenApple(
                    mc.player.getOffhandItem()
                )
            ) {
                appleSwapPending = false;
                appleSwapActive = true;
            }

            return;
        }

        // --------------------------------------------------------
        // FIND APPLE
        // --------------------------------------------------------

        if (appleInventorySlot == -1) {
            appleInventorySlot =
                findEnchantedGoldenApple();
        }

        if (appleInventorySlot == -1) {
            return;
        }

        // --------------------------------------------------------
        // VERIFY ORIGINAL SLOT
        // --------------------------------------------------------

        if (
            !isEnchantedGoldenApple(
                getInventoryStack(
                    appleInventorySlot
                )
            )
        ) {

            appleInventorySlot =
                findEnchantedGoldenApple();

            if (appleInventorySlot == -1) {
                return;
            }
        }

        // --------------------------------------------------------
        // SWAP APPLE
        // --------------------------------------------------------

        if (
            swapSlotWithOffhand(
                appleInventorySlot
            )
        ) {
            appleSwapPending = true;
        }
    }

    // ============================================================
    // RIGHT-CLICK MODE CHECK
    // ============================================================

    private boolean isRightClickAppleAllowed() {

        if (mc.player == null) {
            return false;
        }

        switch (rightClickMode.get()) {

            case NONE:
                return false;

            case ALL:
                return true;

            case SWORD:

                ItemStack mainHand =
                    mc.player.getMainHandItem();

                return !mainHand.isEmpty()
                    && mainHand.is(
                        ItemTags.SWORDS
                    );
        }

        return false;
    }

    // ============================================================
    // RETURN APPLE
    // ============================================================

    private void returnAppleToInventory() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        if (
            !isEnchantedGoldenApple(
                mc.player.getOffhandItem()
            )
        ) {
            appleInventorySlot = -1;
            appleSwapActive = false;
            appleSwapPending = false;

            return;
        }

        if (
            appleInventorySlot < 0
                || appleInventorySlot > 35
        ) {
            return;
        }

        if (
            swapSlotWithOffhand(
                appleInventorySlot
            )
        ) {
            appleInventorySlot = -1;
            appleSwapActive = false;
            appleSwapPending = false;
        }
    }

    // ============================================================
    // XP MODE
    // ============================================================

    private void handleXpMode() {

        /*
         * Don't take the Totem or Shield away at or below
         * the health threshold.
         */
        if (
            !isXp(
                mc.player.getOffhandItem()
            )
                && shouldProtectTotem()
        ) {
            return;
        }

        // --------------------------------------------------------
        // XP ALREADY IN OFFHAND
        // --------------------------------------------------------

        if (
            isXp(
                mc.player.getOffhandItem()
            )
        ) {

            xpSwapPending = false;

            throwOffhand();

            return;
        }

        // --------------------------------------------------------
        // WAIT FOR SWAP
        // --------------------------------------------------------

        if (xpSwapPending) {

            if (
                isXp(
                    mc.player.getOffhandItem()
                )
            ) {
                xpSwapPending = false;
                return;
            }

            xpSwapPending = false;
        }

        // --------------------------------------------------------
        // FIND XP
        // --------------------------------------------------------

        if (xpInventorySlot == -1) {
            xpInventorySlot =
                findXpInInventory();
        }

        if (xpInventorySlot == -1) {
            return;
        }

        // --------------------------------------------------------
        // VERIFY SLOT
        // --------------------------------------------------------

        if (
            !isXp(
                getInventoryStack(
                    xpInventorySlot
                )
            )
        ) {

            xpInventorySlot =
                findXpInInventory();

            if (xpInventorySlot == -1) {
                return;
            }
        }

        // --------------------------------------------------------
        // SWAP
        // --------------------------------------------------------

        if (
            swapSlotWithOffhand(
                xpInventorySlot
            )
        ) {
            xpSwapPending = true;
        }
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    private boolean shouldProtectTotem() {

        if (mc.player == null) {
            return true;
        }

        int threshold =
            minHealth.get();

        /*
         * 0 = health protection disabled.
         */
        if (threshold <= 0) {
            return false;
        }

        return mc.player.getHealth() <= threshold;
    }

    // ============================================================
    // RETURN XP
    // ============================================================

    private void returnXpToInventory() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        if (
            !isXp(
                mc.player.getOffhandItem()
            )
        ) {
            xpInventorySlot = -1;
            xpSwapPending = false;

            return;
        }

        if (
            xpInventorySlot < 0
                || xpInventorySlot > 35
        ) {
            return;
        }

        if (
            swapSlotWithOffhand(
                xpInventorySlot
            )
        ) {
            xpInventorySlot = -1;
            xpSwapPending = false;
        }
    }

    // ============================================================
    // SHIELD MODE
    // ============================================================

    private void handleShieldMode() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        // --------------------------------------------------------
        // APPLE
        // --------------------------------------------------------

        if (
            isEnchantedGoldenApple(
                mc.player.getOffhandItem()
            )
        ) {
            return;
        }

        // --------------------------------------------------------
        // XP
        // --------------------------------------------------------

        if (
            isXp(
                mc.player.getOffhandItem()
            )
        ) {
            return;
        }

        /*
         * At or below minimum-health:
         *
         * Shield -> Totem
         */
        if (shouldProtectTotem()) {
            handleTotemMode();
            return;
        }

        /*
         * Above minimum-health:
         *
         * Totem -> Shield
         */
        handleShieldNormal();
    }

    // ============================================================
    // NORMAL SHIELD
    // ============================================================

    private void handleShieldNormal() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        // --------------------------------------------------------
        // SHIELD ALREADY EQUIPPED
        // --------------------------------------------------------

        if (
            isShield(
                mc.player.getOffhandItem()
            )
        ) {

            shieldSwapPending = false;
            shieldSwapActive = true;

            shieldInventorySlot = -1;

            return;
        }

        // --------------------------------------------------------
        // WAIT FOR PREVIOUS SWAP
        // --------------------------------------------------------

        if (shieldSwapPending) {

            if (
                isShield(
                    mc.player.getOffhandItem()
                )
            ) {
                shieldSwapPending = false;
                shieldSwapActive = true;
                shieldInventorySlot = -1;
            }

            return;
        }

        // --------------------------------------------------------
        // FIND SHIELD
        // --------------------------------------------------------

        if (shieldInventorySlot == -1) {
            shieldInventorySlot =
                findShieldInInventory();
        }

        if (shieldInventorySlot == -1) {
            return;
        }

        // --------------------------------------------------------
        // VERIFY SLOT
        // --------------------------------------------------------

        if (
            !isShield(
                getInventoryStack(
                    shieldInventorySlot
                )
            )
        ) {

            shieldInventorySlot =
                findShieldInInventory();

            if (shieldInventorySlot == -1) {
                return;
            }
        }

        // --------------------------------------------------------
        // SWAP SHIELD
        // --------------------------------------------------------

        if (
            swapSlotWithOffhand(
                shieldInventorySlot
            )
        ) {
            shieldSwapPending = true;
            shieldSwapActive = false;
        }
    }

    // ============================================================
    // TOTEM MODE
    // ============================================================

    private void handleTotemMode() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        // --------------------------------------------------------
        // DON'T REPLACE APPLE
        // --------------------------------------------------------

        if (
            isEnchantedGoldenApple(
                mc.player.getOffhandItem()
            )
        ) {
            return;
        }

        // --------------------------------------------------------
        // DON'T REPLACE XP
        // --------------------------------------------------------

        if (
            isXp(
                mc.player.getOffhandItem()
            )
        ) {
            return;
        }

        // --------------------------------------------------------
        // TOTEM ALREADY EQUIPPED
        // --------------------------------------------------------

        if (
            isTotem(
                mc.player.getOffhandItem()
            )
        ) {

            waitingForTotem = false;
            totemTimer = 0;

            totemSwapPending = false;
            totemInventorySlot = -1;

            shieldSwapPending = false;
            shieldSwapActive = false;
            shieldInventorySlot = -1;

            xpInventorySlot = -1;

            return;
        }

        // --------------------------------------------------------
        // CHECK PREVIOUS SWAP
        // --------------------------------------------------------

        if (totemSwapPending) {

            if (
                isTotem(
                    mc.player.getOffhandItem()
                )
            ) {

                totemSwapPending = false;
                totemInventorySlot = -1;

                waitingForTotem = false;
                totemTimer = 0;

                xpInventorySlot = -1;

                return;
            }

            totemSwapPending = false;
        }

        // --------------------------------------------------------
        // START DELAY
        // --------------------------------------------------------

        if (!waitingForTotem) {
            waitingForTotem = true;
            totemTimer = totemDelay.get();
        }

        if (totemTimer > 0) {
            totemTimer--;

            return;
        }

        // --------------------------------------------------------
        // FIND TOTEM
        // --------------------------------------------------------

        totemInventorySlot =
            findTotemInInventory();

        if (totemInventorySlot == -1) {

            waitingForTotem = true;

            totemTimer =
                Math.max(
                    1,
                    totemDelay.get()
                );

            return;
        }

        // --------------------------------------------------------
        // SWAP
        // --------------------------------------------------------

        if (
            swapSlotWithOffhand(
                totemInventorySlot
            )
        ) {
            totemSwapPending = true;
        }
    }

    // ============================================================
    // SWAP INVENTORY SLOT <-> OFFHAND
    // ============================================================

    private boolean swapSlotWithOffhand(
        int inventorySlot
    ) {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return false;
        }

        if (
            inventorySlot < 0
                || inventorySlot > 35
        ) {
            return false;
        }

        /*
         * PlayerInventory:
         *
         * 0-8  = hotbar
         * 9-35 = main inventory
         *
         * ScreenHandler:
         *
         * 36-44 = hotbar
         * 9-35  = main inventory
         */
        int handlerSlot;

        if (inventorySlot <= 8) {
            handlerSlot =
                36 + inventorySlot;
        } else {
            handlerSlot =
                inventorySlot;
        }

        int syncId =
            mc.player.containerMenu.containerId;

        /*
         * Button 40 = offhand.
         */
        mc.gameMode.handleContainerInput(
            syncId,
            handlerSlot,
            40,
            ContainerInput.SWAP,
            mc.player
        );

        return true;
    }

    // ============================================================
    // THROW XP
    // ============================================================

    private void throwOffhand() {

        if (
            mc.player == null
                || mc.gameMode == null
        ) {
            return;
        }

        if (mc.screen != null) {
            return;
        }

        if (
            !isXp(
                mc.player.getOffhandItem()
            )
        ) {
            return;
        }

        if (xpTimer > 0) {
            xpTimer--;

            return;
        }

        mc.gameMode.useItem(
            mc.player,
            InteractionHand.OFF_HAND
        );

        mc.player.swing(
            InteractionHand.OFF_HAND
        );

        xpTimer =
            xpDelay.get();
    }

    // ============================================================
    // FIND XP
    // ============================================================

    private int findXpInInventory() {

        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i <= 8; i++) {

            if (
                isXp(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        for (int i = 9; i <= 35; i++) {

            if (
                isXp(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // FIND TOTEM
    // ============================================================

    private int findTotemInInventory() {

        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i <= 8; i++) {

            if (
                isTotem(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        for (int i = 9; i <= 35; i++) {

            if (
                isTotem(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // FIND SHIELD
    // ============================================================

    private int findShieldInInventory() {

        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i <= 8; i++) {

            if (
                isShield(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        for (int i = 9; i <= 35; i++) {

            if (
                isShield(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // FIND ENCHANTED GOLDEN APPLE
    // ============================================================

    private int findEnchantedGoldenApple() {

        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i <= 8; i++) {

            if (
                isEnchantedGoldenApple(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        for (int i = 9; i <= 35; i++) {

            if (
                isEnchantedGoldenApple(
                    mc.player.getInventory()
                        .getItem(i)
                )
            ) {
                return i;
            }
        }

        return -1;
    }

    // ============================================================
    // GET INVENTORY STACK
    // ============================================================

    private ItemStack getInventoryStack(
        int slot
    ) {

        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        if (
            slot < 0
                || slot > 35
        ) {
            return ItemStack.EMPTY;
        }

        return mc.player.getInventory()
            .getItem(slot);
    }

    // ============================================================
    // ITEM CHECKS
    // ============================================================

    private boolean isXp(
        ItemStack stack
    ) {

        return stack != null
            && !stack.isEmpty()
            && stack.is(
                Items.EXPERIENCE_BOTTLE
            );
    }

    private boolean isTotem(
        ItemStack stack
    ) {

        return stack != null
            && !stack.isEmpty()
            && stack.is(
                Items.TOTEM_OF_UNDYING
            );
    }

    private boolean isShield(
        ItemStack stack
    ) {

        return stack != null
            && !stack.isEmpty()
            && stack.is(
                Items.SHIELD
            );
    }

    private boolean isEnchantedGoldenApple(
        ItemStack stack
    ) {

        return stack != null
            && !stack.isEmpty()
            && stack.is(
                Items.ENCHANTED_GOLDEN_APPLE
            );
    }

    // ============================================================
    // RIGHT-CLICK MODES
    // ============================================================

    public enum RightClickMode {

        NONE,

        SWORD,

        ALL
    }
}