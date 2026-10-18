package com.vortex.mixin;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.commands.commands.FriendsCommand;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

@Mixin(FriendsCommand.class)
public class FriendsCommandMixin {
    @Inject(method = "build", at = @At("TAIL"))
    private void addNearbySubcommand(LiteralArgumentBuilder<ClientSuggestionProvider> builder, CallbackInfo ci) {
        builder.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal("nearby")
            .then(RequiredArgumentBuilder.<ClientSuggestionProvider, Double>argument("distance", DoubleArgumentType.doubleArg(1))
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || mc.level == null) return SINGLE_SUCCESS;

                    double max = DoubleArgumentType.getDouble(ctx, "distance");
                    List<AbstractClientPlayer> found = new ArrayList<>();
                    for (AbstractClientPlayer p : mc.level.players()) {
                        if (Friends.get().get(p.getName().getString()) == null) continue;
                        if (mc.player.position().distanceTo(p.position()) <= max) found.add(p);
                    }
                    found.sort(Comparator.comparingDouble(p -> mc.player.position().distanceTo(p.position())));

                    if (found.isEmpty()) {
                        mc.player.sendSystemMessage(Component.literal("No friends within " + String.format("%.0f", max) + " blocks"));
                    } else {
                        for (AbstractClientPlayer p : found) {
                            double d = mc.player.position().distanceTo(p.position());
                            mc.player.sendSystemMessage(Component.literal(p.getName().getString() + " - " + String.format("%.1f", d) + " blocks"));
                        }
                        mc.player.sendSystemMessage(Component.literal(found.size() + " friend(s) within " + String.format("%.0f", max) + " blocks"));
                    }
                    return SINGLE_SUCCESS;
                })));
    }
}
