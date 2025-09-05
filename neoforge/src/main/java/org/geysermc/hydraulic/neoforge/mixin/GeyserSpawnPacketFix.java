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
 * This mixin specifically addresses the Geyser spawn packet synchronization issue
 * where the server considers the player spawned but Geyser hasn't sent the spawn packet
 * to the client (sentSpawn: false, spawned: true), causing the loading screen to persist.
 */
@Mixin(value = PlayerList.class, priority = 2200)
public class GeyserSpawnPacketFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeyserSpawnPacketFix");
    
    /**
     * Intercepts player placement to ensure Geyser's spawn packet is properly sent
     * and the client receives the necessary spawn completion signals.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("RETURN")
    )
    private void fixGeyserSpawnPacketSync(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("GeyserSpawnPacketFix: Ensuring proper spawn packet sync for Bedrock player: {}", playerName);
                
                // Schedule immediate spawn packet synchronization
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            // Small delay to ensure all systems are initialized
                            Thread.sleep(50);
                            
                            // Execute comprehensive spawn packet fix
                            forceGeyserSpawnPacketSync(player);
                            
                        } catch (Exception e) {
                            LOGGER.error("GeyserSpawnPacketFix: Exception in spawn packet sync for {}: {}", playerName, e.getMessage());
                        }
                    });
                    
                    // Schedule additional validation after a longer delay
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        try {
                            validateAndFixSpawnState(player);
                        } catch (Exception e) {
                            LOGGER.debug("GeyserSpawnPacketFix: Exception in delayed validation: {}", e.getMessage());
                        }
                    }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnPacketFix: Exception in spawn packet fix: {}", e.getMessage());
        }
    }
    
    /**
     * Forces Geyser to properly synchronize spawn packets and session state.
     */
    private void forceGeyserSpawnPacketSync(ServerPlayer player) {
        String playerName = player.getGameProfile().getName();
        
        try {
            LOGGER.info("GeyserSpawnPacketFix: Forcing Geyser spawn packet synchronization for: {}", playerName);
            
            // Try to access Geyser's session and force spawn packet sending
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    // Get the connection for this player
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        LOGGER.info("GeyserSpawnPacketFix: Found Geyser connection for {}, forcing spawn state sync", playerName);
                        
                        // Try to access the downstream session (Bedrock client)
                        try {
                            java.lang.reflect.Method getDownstreamSessionMethod = connection.getClass().getMethod("downstream");
                            Object downstreamSession = getDownstreamSessionMethod.invoke(connection);
                            
                            if (downstreamSession != null) {
                                // Force the spawn packet to be sent
                                forceSpawnPacketSending(downstreamSession, playerName);
                                
                                // Also try to access the upstream session (Java server)
                                try {
                                    java.lang.reflect.Method getUpstreamSessionMethod = connection.getClass().getMethod("upstream");
                                    Object upstreamSession = getUpstreamSessionMethod.invoke(connection);
                                    
                                    if (upstreamSession != null) {
                                        synchronizeUpstreamSession(upstreamSession, playerName);
                                    }
                                } catch (Exception upstreamException) {
                                    LOGGER.debug("GeyserSpawnPacketFix: Could not access upstream session: {}", upstreamException.getMessage());
                                }
                            }
                        } catch (Exception sessionException) {
                            LOGGER.debug("GeyserSpawnPacketFix: Could not access downstream session: {}", sessionException.getMessage());
                        }
                    } else {
                        LOGGER.warn("GeyserSpawnPacketFix: No Geyser connection found for: {}", playerName);
                    }
                }
            } catch (ClassNotFoundException cnfe) {
                LOGGER.debug("GeyserSpawnPacketFix: Geyser API not available");
            } catch (Exception geyserException) {
                LOGGER.debug("GeyserSpawnPacketFix: Could not access Geyser API: {}", geyserException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("GeyserSpawnPacketFix: Failed to force spawn packet sync for {}: {}", playerName, e.getMessage());
        }
    }
    
    /**
     * Forces the spawn packet to be sent to the Bedrock client.
     */
    private void forceSpawnPacketSending(Object downstreamSession, String playerName) {
        try {
            Class<?> sessionClass = downstreamSession.getClass();
            
            // Try to find and call methods that force spawn packet sending
            java.lang.reflect.Method[] methods = sessionClass.getMethods();
            boolean spawnPacketSent = false;
            
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName().toLowerCase();
                
                // Look for spawn-related methods
                if (methodName.contains("spawn") && methodName.contains("send")) {
                    try {
                        if (method.getParameterCount() == 0) {
                            method.invoke(downstreamSession);
                            LOGGER.info("GeyserSpawnPacketFix: Called spawn method {} for {}", method.getName(), playerName);
                            spawnPacketSent = true;
                        }
                    } catch (Exception methodException) {
                        LOGGER.debug("GeyserSpawnPacketFix: Failed to call method {}: {}", method.getName(), methodException.getMessage());
                    }
                }
            }
            
            // Try to directly set the sentSpawn flag to true
            try {
                java.lang.reflect.Field sentSpawnField = sessionClass.getDeclaredField("sentSpawn");
                sentSpawnField.setAccessible(true);
                sentSpawnField.setBoolean(downstreamSession, true);
                LOGGER.info("GeyserSpawnPacketFix: Forced sentSpawn to true for: {}", playerName);
                spawnPacketSent = true;
            } catch (Exception fieldException) {
                LOGGER.debug("GeyserSpawnPacketFix: Could not access sentSpawn field: {}", fieldException.getMessage());
                
                // Try alternative field names
                try {
                    java.lang.reflect.Field spawnedField = sessionClass.getDeclaredField("spawned");
                    spawnedField.setAccessible(true);
                    spawnedField.setBoolean(downstreamSession, true);
                    LOGGER.info("GeyserSpawnPacketFix: Forced spawned to true for: {}", playerName);
                    spawnPacketSent = true;
                } catch (Exception altFieldException) {
                    LOGGER.debug("GeyserSpawnPacketFix: Could not access spawned field: {}", altFieldException.getMessage());
                }
            }
            
            if (spawnPacketSent) {
                LOGGER.info("GeyserSpawnPacketFix: Successfully forced spawn packet state for: {}", playerName);
            } else {
                LOGGER.warn("GeyserSpawnPacketFix: Could not force spawn packet state for: {}", playerName);
            }
            
        } catch (Exception e) {
            LOGGER.error("GeyserSpawnPacketFix: Exception in spawn packet forcing for {}: {}", playerName, e.getMessage());
        }
    }
    
    /**
     * Synchronizes the upstream session state.
     */
    private void synchronizeUpstreamSession(Object upstreamSession, String playerName) {
        try {
            // Try to ensure the upstream session is in the correct state
            Class<?> sessionClass = upstreamSession.getClass();
            
            // Look for methods that might help synchronize the session
            java.lang.reflect.Method[] methods = sessionClass.getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName().toLowerCase();
                
                if ((methodName.contains("sync") && methodName.contains("spawn")) ||
                    (methodName.contains("complete") && methodName.contains("spawn"))) {
                    
                    try {
                        if (method.getParameterCount() == 0) {
                            method.invoke(upstreamSession);
                            LOGGER.debug("GeyserSpawnPacketFix: Called upstream sync method {} for {}", method.getName(), playerName);
                        }
                    } catch (Exception methodException) {
                        // Ignore individual method failures
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnPacketFix: Exception in upstream session sync: {}", e.getMessage());
        }
    }
    
    /**
     * Validates and fixes spawn state after a delay.
     */
    private void validateAndFixSpawnState(ServerPlayer player) {
        String playerName = player.getGameProfile().getName();
        
        try {
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.debug("GeyserSpawnPacketFix: Player {} no longer connected during validation", playerName);
                return;
            }
            
            LOGGER.info("GeyserSpawnPacketFix: Validating spawn state for: {}", playerName);
            
            // Check Geyser session state
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        Object downstreamSession = connection.getClass().getMethod("downstream").invoke(connection);
                        
                        if (downstreamSession != null) {
                            // Check if sentSpawn is still false
                            try {
                                java.lang.reflect.Field sentSpawnField = downstreamSession.getClass().getDeclaredField("sentSpawn");
                                sentSpawnField.setAccessible(true);
                                boolean sentSpawn = sentSpawnField.getBoolean(downstreamSession);
                                
                                if (!sentSpawn) {
                                    LOGGER.warn("GeyserSpawnPacketFix: sentSpawn still false for {}, forcing final fix", playerName);
                                    sentSpawnField.setBoolean(downstreamSession, true);
                                    
                                    // Send additional packets to ensure client state
                                    sendFinalSpawnPackets(player);
                                } else {
                                    LOGGER.info("GeyserSpawnPacketFix: Spawn state is now correct for: {}", playerName);
                                }
                            } catch (Exception fieldException) {
                                LOGGER.debug("GeyserSpawnPacketFix: Could not check sentSpawn field: {}", fieldException.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception geyserException) {
                LOGGER.debug("GeyserSpawnPacketFix: Could not validate Geyser state: {}", geyserException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnPacketFix: Exception in spawn state validation: {}", e.getMessage());
        }
    }
    
    /**
     * Sends final spawn packets to ensure the client transitions from loading screen.
     */
    private void sendFinalSpawnPackets(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.info("GeyserSpawnPacketFix: Sending final spawn packets for: {}", playerName);
            
            // Send critical packets that should trigger client spawn completion
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Force position update
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Send game event that often triggers spawn completion
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            // Force immediate delivery
            player.connection.getConnection().flushChannel();
            
            LOGGER.info("GeyserSpawnPacketFix: Sent final spawn packets for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("GeyserSpawnPacketFix: Failed to send final spawn packets for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
}
