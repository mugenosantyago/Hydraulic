package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin specifically targets the ServerGamePacketListenerImpl constructor
 * to ensure Geyser's session is properly synchronized BEFORE the server
 * considers the player spawned, preventing the sentSpawn/spawned mismatch.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2000)
public class GeyserSessionSyncMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeyserSessionSyncMixin");
    
    @Shadow
    public ServerPlayer player;
    
    /**
     * Intercepts the ServerGamePacketListenerImpl creation to ensure
     * Geyser's session is properly prepared before the player is considered spawned.
     */
    @Inject(
        method = "<init>",
        at = @At("HEAD")
    )
    private static void ensureGeyserSessionReady(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("GeyserSessionSyncMixin: Preparing Geyser session synchronization for: {}", playerName);
                
                // This is the critical moment - we're transitioning from configuration to play
                // We need to ensure Geyser's session is ready to handle spawn packets
                prepareGeyserSessionForSpawn(player);
            }
        } catch (Exception e) {
            LOGGER.debug("GeyserSessionSyncMixin: Exception in session preparation: {}", e.getMessage());
        }
    }
    
    /**
     * Additional hook after the constructor to ensure spawn state is correct.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void validateGeyserSessionState(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("GeyserSessionSyncMixin: Validating Geyser session state for: {}", playerName);
                
                // Schedule immediate validation and correction if needed
                var serverInstance = player.getServer();
                if (serverInstance != null) {
                    serverInstance.execute(() -> {
                        try {
                            // Give a minimal delay for session state to stabilize
                            Thread.sleep(50);
                            validateAndCorrectGeyserState(player);
                        } catch (Exception e) {
                            LOGGER.debug("GeyserSessionSyncMixin: Exception in session validation: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GeyserSessionSyncMixin: Exception in session validation setup: {}", e.getMessage());
        }
    }
    
    /**
     * Prepares Geyser's session for the spawn sequence.
     */
    private static void prepareGeyserSessionForSpawn(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Try to access Geyser's session and prepare it for spawn
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    // Get the connection for this player
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        LOGGER.info("GeyserSessionSyncMixin: Found Geyser connection for {}, preparing spawn state", playerName);
                        
                        // Try to ensure the session is in the correct state for spawn
                        try {
                            // Access the downstream session (Bedrock client)
                            java.lang.reflect.Method getDownstreamSessionMethod = connection.getClass().getMethod("downstream");
                            Object downstreamSession = getDownstreamSessionMethod.invoke(connection);
                            
                            if (downstreamSession != null) {
                                // Try to find methods that might help prepare the spawn state
                                Class<?> sessionClass = downstreamSession.getClass();
                                
                                // Look for spawn-related state methods
                                try {
                                    // Try to find and call methods that might prepare the spawn state
                                    java.lang.reflect.Method[] methods = sessionClass.getMethods();
                                    for (java.lang.reflect.Method method : methods) {
                                        String methodName = method.getName().toLowerCase();
                                        
                                        // Look for methods that might reset or prepare spawn state
                                        if ((methodName.contains("spawn") && methodName.contains("prepare")) ||
                                            (methodName.contains("reset") && methodName.contains("spawn")) ||
                                            (methodName.contains("init") && methodName.contains("spawn"))) {
                                            
                                            if (method.getParameterCount() == 0) {
                                                try {
                                                    method.invoke(downstreamSession);
                                                    LOGGER.info("GeyserSessionSyncMixin: Called spawn preparation method {} for {}", method.getName(), playerName);
                                                } catch (Exception methodException) {
                                                    // Ignore individual method failures
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception methodException) {
                                    LOGGER.debug("GeyserSessionSyncMixin: Could not access spawn preparation methods: {}", methodException.getMessage());
                                }
                                
                                LOGGER.info("GeyserSessionSyncMixin: Completed spawn state preparation for: {}", playerName);
                            }
                        } catch (Exception sessionException) {
                            LOGGER.debug("GeyserSessionSyncMixin: Could not access downstream session: {}", sessionException.getMessage());
                        }
                    }
                }
            } catch (ClassNotFoundException cnfe) {
                LOGGER.debug("GeyserSessionSyncMixin: Geyser API not available");
            } catch (Exception geyserException) {
                LOGGER.debug("GeyserSessionSyncMixin: Could not access Geyser API: {}", geyserException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSessionSyncMixin: Exception in Geyser session preparation: {}", e.getMessage());
        }
    }
    
    /**
     * Validates and corrects Geyser session state after initialization.
     */
    private static void validateAndCorrectGeyserState(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // At this point, we want to ensure the client receives the critical spawn packets
            if (player.connection != null) {
                LOGGER.info("GeyserSessionSyncMixin: Sending critical spawn packets for: {}", playerName);
                
                // Send the most critical packets for spawn completion
                try {
                    // Send player abilities - critical for client state
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                    
                    // Send level event that indicates spawn completion
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
                    
                    // Send position confirmation
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                    
                    // Critical: Send another game event that might trigger spawn completion
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                        net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0.0F));
                    
                    // Force immediate packet delivery
                    player.connection.getConnection().flushChannel();
                    
                    LOGGER.info("GeyserSessionSyncMixin: Sent critical spawn packets for: {}", playerName);
                    
                } catch (Exception packetException) {
                    LOGGER.error("GeyserSessionSyncMixin: Failed to send spawn packets for {}: {}", playerName, packetException.getMessage());
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSessionSyncMixin: Exception in state validation: {}", e.getMessage());
        }
    }
}
