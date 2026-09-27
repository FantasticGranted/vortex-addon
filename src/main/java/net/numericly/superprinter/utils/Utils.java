package net.numericly.superprinter.utils;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.player.PlayerUtils.squaredDistanceTo;

public class Utils {

    /** Overrides the placement/block interaction range. Set to -1 to use the player's default. */
    public static double placeRange = -1;

    public static Comparator<BlockPos> NEAREST = Comparator.comparingDouble(pos ->
        squaredDistanceTo(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5)
    );

    public static boolean isWithinBlockInteractionRange(BlockPos pos) {
        assert mc.player != null;

        return isWithinBlockInteractionRange(mc.player.getEyePosition(), pos);
    }

    public static boolean isWithinBlockInteractionRange(Vec3 eyePos, BlockPos pos) {
        double d = (placeRange > 0 ? placeRange : mc.player.blockInteractionRange()) + 1.0;
        return new AABB(pos).distanceToSqr(eyePos) < d * d;
    }

    public static boolean isValid(Level world, Vec3 pos) {
        EntityDimensions hitbox = mc.player.getDimensions(mc.player.getPose());

        return isValid(world, pos, hitbox);
    }

    public static boolean isValid(Level world, Vec3 pos, EntityDimensions hitbox) {
        AABB box = hitbox.makeBoundingBox(pos);

        if (!world.noCollision(mc.player, box)) {
            return false;
        }

        return true;
    }

    @Nullable
    public static Vec3 findSafeSpotForPlacement(BlockPos location) {
        assert mc.player != null;
        assert mc.level != null;

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();

        if (worldSchematic == null) return null;

        int maxRange = 6;

        List<Vec3> toCheck = new ArrayList<>();

        for (int x = -maxRange; x <= maxRange; x++) {
            for (int y = -maxRange; y <= maxRange; y++) {
                for (int z = -maxRange; z <= maxRange; z++) {
                    int magnitudeSquared = x*x + y*y + z*z;

                    if (magnitudeSquared <= maxRange * maxRange) {
                        toCheck.add(new Vec3(x, y, z));
                    }
                }
            }
        }

        toCheck.sort(Comparator.comparingDouble(Vec3::lengthSqr));
        toCheck.replaceAll(offset -> location.getBottomCenter().add(offset));

        EntityDimensions hitbox = mc.player.getDimensions(mc.player.getPose());

        Vec3 eyeOffset = new Vec3(0, mc.player.getEyeHeight(), 0);

        for (Vec3 pos : toCheck) {
            if (!Utils.isWithinBlockInteractionRange(pos.add(eyeOffset), location)) {
                continue;
            }

            if (!Utils.isValid(mc.level, pos, hitbox)) {
                continue;
            }

            if (!Utils.isValid(worldSchematic, pos, hitbox)) {
                continue;
            }

            return pos;
        }

        return null;
    }

    public static void interactBlock(BlockHitResult hitResult, InteractionHand hand) {
        assert mc.player != null;
        assert mc.gameMode != null;

        mc.gameMode.useItemOn(mc.player, hand, hitResult);

    }

    public static void interactBlock(BlockHitResult hitResult, InteractionHand hand, float pitch, float yaw, boolean sneaking) {
        assert mc.player != null;
        assert mc.gameMode != null;

        float oldPitch = mc.player.getXRot();
        float oldLastPitch = mc.player.xRotO;
        float oldYaw = mc.player.getYRot();
        float oldLastYaw = mc.player.yRotO;
        float oldHeadYaw = mc.player.getYHeadRot();
        float oldLastHeadYaw = mc.player.yHeadRotO;
        boolean oldSneaking = mc.player.isCrouching();

        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
        mc.player.setShiftKeyDown(sneaking);

        mc.gameMode.useItemOn(mc.player, hand, hitResult);

        mc.player.setXRot(oldPitch);
        mc.player.xRotO = oldLastPitch;
        mc.player.setYRot(oldYaw);
        mc.player.yRotO = oldLastYaw;
        mc.player.setYHeadRot(oldHeadYaw);
        mc.player.yHeadRotO = oldLastHeadYaw;
        mc.player.setShiftKeyDown(oldSneaking);

    }

    public static void rotate(float lookYaw, float lookPitch) {
        mc.getConnection().getConnection().send(new ServerboundMovePlayerPacket.Rot(lookYaw, lookPitch, false, false));
    }

    public static void setSneaking(boolean sneaking) {
        assert mc.player != null;

        Input keyPresses = mc.player.input.keyPresses;

        mc.getConnection().getConnection().send(new ServerboundPlayerInputPacket(
            new Input(
                keyPresses.forward(),
                keyPresses.backward(),
                keyPresses.left(),
                keyPresses.right(),
                keyPresses.jump(),
                sneaking,
                keyPresses.sprint()
            )
        ));
    }
}