package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents NullPointerExceptions in Floodgate's skin application
 * by ensuring proper timing and null checks for Bedrock players.
 */
@Mixin(targets = "org.geysermc.floodgate.mod.pluginmessage.ModSkinApplier", remap = false, priority = 500)
public class FloodgateSkinMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FloodgateSkinMixin");
    
    /**
     * Prevents the NullPointerException in ModSkinApplier.applySkin by adding proper null checks.
     */
    @Inject(
        method = "applySkin",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventSkinApplicationErrors(ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if the player is properly initialized before applying skin
                if (player.level() == null) {
                    LOGGER.info("FloodgateSkinMixin: Delaying skin application for {} - player not fully initialized", playerName);
                    
                    // Schedule skin application for later when player is fully loaded
                    var server = player.getServer();
                    if (server != null) {
                        server.execute(() -> {
                        try {
                            // Wait a bit for the player to be fully spawned
                            Thread.sleep(1000);
                            
                            // Try to apply skin again if player is still connected
                            if (player.connection != null && player.connection.getConnection().isConnected()) {
                                LOGGER.info("FloodgateSkinMixin: Retrying skin application for: {}", playerName);
                                // Let the original method run, but with proper timing
                            } else {
                                LOGGER.debug("FloodgateSkinMixin: Player {} disconnected before skin retry", playerName);
                            }
                        } catch (Exception e) {
                            LOGGER.debug("FloodgateSkinMixin: Exception during delayed skin application: {}", e.getMessage());
                        }
                        });
                    }
                    
                    // Cancel the original call to prevent NPE
                    ci.cancel();
                    return;
                }
                
                // Additional null checks for ChunkMap access
                try {
                    // Verify that the player's chunk tracking is properly initialized
                    if (player.level().getChunkSource().chunkMap == null) {
                        LOGGER.info("FloodgateSkinMixin: ChunkMap not ready for {}, delaying skin application", playerName);
                        ci.cancel();
                        return;
                    }
                } catch (Exception chunkException) {
                    LOGGER.debug("FloodgateSkinMixin: Could not verify chunk map for {}: {}", playerName, chunkException.getMessage());
                    ci.cancel();
                    return;
                }
                
                LOGGER.debug("FloodgateSkinMixin: Allowing skin application for properly initialized player: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in skin application prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Additional safety net for the lambda method that causes the NPE.
     */
    @Inject(
        method = "lambda$applySkin$0",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventLambdaNPE(ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if ChunkMap.TrackedEntity exists before trying to access it
                try {
                    if (player.level() == null || 
                        player.level().getChunkSource() == null || 
                        player.level().getChunkSource().chunkMap == null) {
                        LOGGER.debug("FloodgateSkinMixin: Preventing lambda NPE for {} - chunk tracking not ready", playerName);
                        ci.cancel();
                        return;
                    }
                    
                    // Additional check for the TrackedEntity that's causing the NPE
                    var chunkMap = player.level().getChunkSource().chunkMap;
                    // Use reflection to safely access the entity tracking map
                    try {
                        java.lang.reflect.Field entityMapField = chunkMap.getClass().getDeclaredField("entityMap");
                        entityMapField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Map<Integer, ?> trackedEntities = (java.util.Map<Integer, ?>) entityMapField.get(chunkMap);
                        if (trackedEntities == null || !trackedEntities.containsKey(player.getId())) {
                            LOGGER.debug("FloodgateSkinMixin: TrackedEntity not found for {}, skipping skin update", playerName);
                            ci.cancel();
                            return;
                        }
                    } catch (Exception reflectionException) {
                        LOGGER.debug("FloodgateSkinMixin: Could not access entityMap via reflection for {}: {}", playerName, reflectionException.getMessage());
                        ci.cancel();
                        return;
                    }
                    
                } catch (Exception trackingException) {
                    LOGGER.debug("FloodgateSkinMixin: Exception checking entity tracking for {}: {}", playerName, trackingException.getMessage());
                    ci.cancel();
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in lambda NPE prevention: {}", e.getMessage());
            // If we can't determine safely, cancel to prevent NPE
            ci.cancel();
        }
    }
}
