package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin provides a workaround for chunk loading issues by ensuring
 * Bedrock players are properly positioned and have their chunks loaded
 * even if there are chunk translation errors.
 */
@Mixin(value = PlayerList.class, priority = 2300)
public class BedrockPlayerTeleportFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockPlayerTeleportFix");
    
    /**
     * After player placement, ensure Bedrock players are properly positioned
     * and trigger chunk loading to work around chunk translation issues.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("RETURN")
    )
    private void ensureBedrockPlayerPosition(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("BedrockPlayerTeleportFix: Ensuring proper positioning for Bedrock player: {}", playerName);
                
                // Schedule position and chunk loading fix
                var server = player.getServer();
                if (server != null) {
                    // Immediate position fix
                    server.execute(() -> {
                        try {
                            Thread.sleep(100);
                            forceProperPositioning(player);
                        } catch (Exception e) {
                            LOGGER.debug("BedrockPlayerTeleportFix: Exception in immediate positioning: {}", e.getMessage());
                        }
                    });
                    
                    // Delayed position validation and chunk reload
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        try {
                            validateAndFixPosition(player);
                        } catch (Exception e) {
                            LOGGER.debug("BedrockPlayerTeleportFix: Exception in delayed positioning: {}", e.getMessage());
                        }
                    }, 3000, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockPlayerTeleportFix: Exception in position fix: {}", e.getMessage());
        }
    }
    
    /**
     * Forces proper positioning for Bedrock players.
     */
    private void forceProperPositioning(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection != null && player.connection.getConnection().isConnected()) {
                LOGGER.info("BedrockPlayerTeleportFix: Forcing position sync for: {}", playerName);
                
                // Get current position
                double x = player.getX();
                double y = player.getY();
                double z = player.getZ();
                float yRot = player.getYRot();
                float xRot = player.getXRot();
                
                // Force teleport to ensure position is synced
                player.connection.teleport(x, y, z, yRot, xRot);
                
                // Send player abilities
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                
                // Force chunk loading around player
                try {
                    var level = player.level();
                    if (level != null) {
                        var chunkSource = level.getChunkSource();
                        if (chunkSource != null) {
                            int chunkX = (int) x >> 4;
                            int chunkZ = (int) z >> 4;
                            
                            // Force load chunks around player
                            for (int dx = -2; dx <= 2; dx++) {
                                for (int dz = -2; dz <= 2; dz++) {
                                    try {
                                        chunkSource.getChunk(chunkX + dx, chunkZ + dz, true);
                                    } catch (Exception chunkException) {
                                        LOGGER.debug("BedrockPlayerTeleportFix: Could not force load chunk: {}", chunkException.getMessage());
                                    }
                                }
                            }
                            
                            LOGGER.info("BedrockPlayerTeleportFix: Forced chunk loading around: {}", playerName);
                        }
                    }
                } catch (Exception chunkException) {
                    LOGGER.debug("BedrockPlayerTeleportFix: Exception in chunk loading: {}", chunkException.getMessage());
                }
                
                // Force connection flush
                player.connection.getConnection().flushChannel();
                
                LOGGER.info("BedrockPlayerTeleportFix: Completed position sync for: {}", playerName);
            }
            
        } catch (Exception e) {
            LOGGER.error("BedrockPlayerTeleportFix: Failed to force positioning for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Validates and fixes position after a delay.
     */
    private void validateAndFixPosition(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.debug("BedrockPlayerTeleportFix: Player {} no longer connected during validation", playerName);
                return;
            }
            
            LOGGER.info("BedrockPlayerTeleportFix: Final position validation for: {}", playerName);
            
            // Final position confirmation
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Send final abilities update
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Send game mode update
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                player.gameMode.getGameModeForPlayer().getId()));
            
            // Force final flush
            player.connection.getConnection().flushChannel();
            
            LOGGER.info("BedrockPlayerTeleportFix: Completed final validation for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.debug("BedrockPlayerTeleportFix: Exception in position validation: {}", e.getMessage());
        }
    }
}
