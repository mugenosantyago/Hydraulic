package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.geysermc.hydraulic.neoforge.util.BedrockSpawnStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks Bedrock player spawn state to prevent overwhelming them during login.
 */
@Mixin(net.minecraft.server.players.PlayerList.class)
public class BedrockSpawnStateTrackerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockSpawnStateTrackerMixin");
    
    /**
     * Register Bedrock players when they start connecting.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("HEAD")
    )
    private void trackBedrockPlayerConnection(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                BedrockSpawnStateManager.registerConnecting(playerName);
                LOGGER.info("BedrockSpawnStateTrackerMixin: Registered Bedrock player {} in CONNECTING state - minimal processing mode", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockSpawnStateTrackerMixin: Exception tracking player connection: {}", e.getMessage());
        }
    }
    
    /**
     * Unregister players when they disconnect.
     */
    @Inject(
        method = "remove",
        at = @At("HEAD")
    )
    private void untrackPlayerDisconnection(ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                BedrockSpawnStateManager.unregisterPlayer(playerName);
                LOGGER.debug("BedrockSpawnStateTrackerMixin: Unregistered Bedrock player: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockSpawnStateTrackerMixin: Exception untracking player: {}", e.getMessage());
        }
    }
}
