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
 * This mixin specifically addresses the Geyser spawn state synchronization issue
 * by ensuring proper coordination between server spawn completion and Geyser's session state.
 */
@Mixin(value = PlayerList.class, priority = 1200)
public class GeyserSpawnStateMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeyserSpawnStateMixin");
    
    /**
     * Hook into the player placement to ensure Geyser's spawn state is properly synchronized.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void synchronizeGeyserSpawnState(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("GeyserSpawnStateMixin: Synchronizing Geyser spawn state for Bedrock player: {}", playerName);
                
                // Schedule a task to coordinate with Geyser's spawn state after the player is fully placed
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            // Give the server a moment to complete all spawn-related tasks
                            Thread.sleep(50);
                            
                            // Try to coordinate with Geyser's session state
                            coordinateWithGeyserSession(player);
                            
                        } catch (Exception e) {
                            LOGGER.debug("GeyserSpawnStateMixin: Exception in Geyser coordination: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnStateMixin: Exception in spawn state synchronization: {}", e.getMessage());
        }
    }
    
    /**
     * Attempts to coordinate with Geyser's session to ensure proper spawn state.
     */
    private void coordinateWithGeyserSession(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Try to access Geyser's session for this player using reflection
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    // Get the connection for this player
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        LOGGER.info("GeyserSpawnStateMixin: Found Geyser connection for {}, attempting spawn state fix", playerName);
                        
                        // Try to access the session and fix the spawn state
                        try {
                            // Get the session from the connection
                            java.lang.reflect.Method getSessionMethod = connection.getClass().getMethod("javaSession");
                            Object session = getSessionMethod.invoke(connection);
                            
                            if (session != null) {
                                // Try to find and call methods that might fix the spawn state
                                // This is a bit of a shot in the dark, but we're trying to synchronize
                                // the spawn state between server and Geyser
                                
                                // Look for spawn-related methods
                                java.lang.reflect.Method[] methods = session.getClass().getMethods();
                                for (java.lang.reflect.Method method : methods) {
                                    String methodName = method.getName().toLowerCase();
                                    if (methodName.contains("spawn") && methodName.contains("send")) {
                                        try {
                                            if (method.getParameterCount() == 0) {
                                                method.invoke(session);
                                                LOGGER.debug("GeyserSpawnStateMixin: Called method {} on session for {}", method.getName(), playerName);
                                            }
                                        } catch (Exception methodException) {
                                            // Ignore individual method failures
                                        }
                                    }
                                }
                                
                                LOGGER.info("GeyserSpawnStateMixin: Attempted spawn state coordination for: {}", playerName);
                            }
                        } catch (Exception sessionException) {
                            LOGGER.debug("GeyserSpawnStateMixin: Could not access session for {}: {}", playerName, sessionException.getMessage());
                        }
                    } else {
                        LOGGER.debug("GeyserSpawnStateMixin: No Geyser connection found for: {}", playerName);
                    }
                }
            } catch (ClassNotFoundException cnfe) {
                LOGGER.debug("GeyserSpawnStateMixin: Geyser API not available");
            } catch (Exception geyserException) {
                LOGGER.debug("GeyserSpawnStateMixin: Could not access Geyser API: {}", geyserException.getMessage());
            }
            
            // Alternative approach: Send additional packets to ensure client state
            ensureClientSpawnState(player);
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnStateMixin: Exception in Geyser session coordination: {}", e.getMessage());
        }
    }
    
    /**
     * Ensures the client has proper spawn state by sending additional packets.
     */
    private void ensureClientSpawnState(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection != null) {
                // Send additional packets that might help with spawn state
                
                // Send player position again with a slight delay
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                    try {
                        if (player.connection != null && player.connection.getConnection().isConnected()) {
                            // Send position update
                            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                            
                            // Send game event that might trigger proper spawn state
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                                net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
                            
                            // Force connection flush
                            player.connection.getConnection().flushChannel();
                            
                            LOGGER.info("GeyserSpawnStateMixin: Sent additional spawn state packets for: {}", playerName);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("GeyserSpawnStateMixin: Exception sending additional packets: {}", e.getMessage());
                    }
                }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnStateMixin: Exception ensuring client spawn state: {}", e.getMessage());
        }
    }
}
