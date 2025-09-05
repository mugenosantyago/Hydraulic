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
                    
                    // Multiple delayed fixes to ensure loading screen dismissal
                    // Fix 1: Early validation (1 second)
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        try {
                            earlyPositionFix(player);
                        } catch (Exception e) {
                            LOGGER.debug("BedrockPlayerTeleportFix: Exception in early positioning: {}", e.getMessage());
                        }
                    }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // Fix 2: Medium validation (3 seconds)
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        try {
                            validateAndFixPosition(player);
                        } catch (Exception e) {
                            LOGGER.debug("BedrockPlayerTeleportFix: Exception in delayed positioning: {}", e.getMessage());
                        }
                    }, 3000, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // Fix 3: Final validation (5 seconds) - aggressive loading screen fix
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        try {
                            finalLoadingScreenFix(player);
                        } catch (Exception e) {
                            LOGGER.debug("BedrockPlayerTeleportFix: Exception in final fix: {}", e.getMessage());
                        }
                    }, 5000, java.util.concurrent.TimeUnit.MILLISECONDS);
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
     * Early position fix to help with loading screen issues.
     */
    private void earlyPositionFix(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                return;
            }
            
            LOGGER.info("BedrockPlayerTeleportFix: Early position fix for: {}", playerName);
            
            // Send critical packets that might help with loading screen
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            // Force position update
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Send abilities
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Force flush
            player.connection.getConnection().flushChannel();
            
            LOGGER.info("BedrockPlayerTeleportFix: Completed early position fix for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.debug("BedrockPlayerTeleportFix: Exception in early position fix: {}", e.getMessage());
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
    
    /**
     * Final aggressive loading screen fix for persistent issues.
     */
    private void finalLoadingScreenFix(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.debug("BedrockPlayerTeleportFix: Player {} no longer connected during final fix", playerName);
                return;
            }
            
            LOGGER.warn("BedrockPlayerTeleportFix: FINAL AGGRESSIVE LOADING SCREEN FIX for: {}", playerName);
            
            // Aggressive approach: Force multiple loading completion signals
            
            // 1. Send multiple game events that might trigger loading completion
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            
            // 2. Force multiple position updates
            for (int i = 0; i < 3; i++) {
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
            
            // 3. Send multiple ability updates
            for (int i = 0; i < 3; i++) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            }
            
            // 4. Try to trigger spawn completion through Geyser API
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        LOGGER.info("BedrockPlayerTeleportFix: Forcing final Geyser session completion for: {}", playerName);
                        
                        // Force sentSpawnPacket again
                        try {
                            java.lang.reflect.Field sentSpawnPacketField = connection.getClass().getDeclaredField("sentSpawnPacket");
                            sentSpawnPacketField.setAccessible(true);
                            sentSpawnPacketField.setBoolean(connection, true);
                            LOGGER.info("BedrockPlayerTeleportFix: Re-forced sentSpawnPacket to true for: {}", playerName);
                        } catch (Exception fieldException) {
                            LOGGER.debug("BedrockPlayerTeleportFix: Could not access sentSpawnPacket in final fix: {}", fieldException.getMessage());
                        }
                    }
                }
            } catch (Exception geyserException) {
                LOGGER.debug("BedrockPlayerTeleportFix: Could not access Geyser in final fix: {}", geyserException.getMessage());
            }
            
            // 5. Force final connection flush multiple times
            for (int i = 0; i < 5; i++) {
                player.connection.getConnection().flushChannel();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            LOGGER.warn("BedrockPlayerTeleportFix: COMPLETED FINAL AGGRESSIVE FIX for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockPlayerTeleportFix: Exception in final loading screen fix for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
}
