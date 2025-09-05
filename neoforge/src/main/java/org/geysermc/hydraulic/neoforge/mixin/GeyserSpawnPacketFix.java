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
                            LOGGER.info("GeyserSpawnPacketFix: Attempting to access downstream session for: {}", playerName);
                            LOGGER.info("GeyserSpawnPacketFix: Connection class: {}", connection.getClass().getName());
                            
                            // Log available methods
                            java.lang.reflect.Method[] methods = connection.getClass().getMethods();
                            LOGGER.info("GeyserSpawnPacketFix: Available methods: {}", 
                                java.util.Arrays.stream(methods).map(m -> m.getName()).toArray());
                            
                            // Check if this is already a GeyserSession (which IS the downstream session)
                            if (connection.getClass().getName().contains("GeyserSession")) {
                                LOGGER.info("GeyserSpawnPacketFix: Connection is already a GeyserSession, using it directly for: {}", playerName);
                                // Use the GeyserSession directly as the downstream session
                                forceSpawnPacketSending(connection, playerName);
                            } else {
                                // Try the original downstream method approach
                                java.lang.reflect.Method getDownstreamSessionMethod = connection.getClass().getMethod("downstream");
                                Object downstreamSession = getDownstreamSessionMethod.invoke(connection);
                                
                                LOGGER.info("GeyserSpawnPacketFix: Downstream session result: {}", downstreamSession != null ? downstreamSession.getClass().getName() : "null");
                                
                                if (downstreamSession != null) {
                                    LOGGER.info("GeyserSpawnPacketFix: Successfully accessed downstream session for: {}", playerName);
                                    // Force the spawn packet to be sent
                                    forceSpawnPacketSending(downstreamSession, playerName);
                                } else {
                                    LOGGER.warn("GeyserSpawnPacketFix: Downstream session is null for: {}", playerName);
                                }
                            }
                            
                            // Also try to access the upstream session (Java server)
                            try {
                                LOGGER.info("GeyserSpawnPacketFix: Attempting to access upstream session for: {}", playerName);
                                java.lang.reflect.Method getUpstreamSessionMethod = connection.getClass().getMethod("upstream");
                                Object upstreamSession = getUpstreamSessionMethod.invoke(connection);
                                
                                if (upstreamSession != null) {
                                    synchronizeUpstreamSession(upstreamSession, playerName);
                                }
                            } catch (Exception upstreamException) {
                                LOGGER.debug("GeyserSpawnPacketFix: Could not access upstream session: {}", upstreamException.getMessage());
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
            LOGGER.info("GeyserSpawnPacketFix: Starting spawn packet forcing for: {}", playerName);
            Class<?> sessionClass = downstreamSession.getClass();
            LOGGER.info("GeyserSpawnPacketFix: Session class for {}: {}", playerName, sessionClass.getName());
            
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
            
            // First, let's see ALL available fields in the GeyserSession
            try {
                java.lang.reflect.Field[] allFields = sessionClass.getDeclaredFields();
                LOGGER.info("GeyserSpawnPacketFix: ALL fields in GeyserSession for {}: {}", playerName, 
                    java.util.Arrays.stream(allFields).map(f -> f.getName() + ":" + f.getType().getSimpleName()).toArray());
            } catch (Exception fieldsException) {
                LOGGER.warn("GeyserSpawnPacketFix: Could not get field list: {}", fieldsException.getMessage());
            }
            
            // Try to directly set the sentSpawn flag to true
            try {
                java.lang.reflect.Field sentSpawnField = sessionClass.getDeclaredField("sentSpawn");
                sentSpawnField.setAccessible(true);
                boolean currentValue = sentSpawnField.getBoolean(downstreamSession);
                LOGGER.info("GeyserSpawnPacketFix: Found sentSpawn field for {}, current value: {}", playerName, currentValue);
                sentSpawnField.setBoolean(downstreamSession, true);
                LOGGER.info("GeyserSpawnPacketFix: Successfully forced sentSpawn to true for: {}", playerName);
                spawnPacketSent = true;
            } catch (NoSuchFieldException nsfe) {
                LOGGER.warn("GeyserSpawnPacketFix: sentSpawn field not found, trying alternative fields for: {}", playerName);
                
                // Try alternative field names that might control spawn state
                String[] possibleFields = {"spawned", "hasSpawned", "playerSpawned", "spawnSent", "spawnPacketSent", "loginComplete", "loggedIn"};
                for (String fieldName : possibleFields) {
                    try {
                        java.lang.reflect.Field field = sessionClass.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        if (field.getType() == boolean.class) {
                            boolean currentValue = field.getBoolean(downstreamSession);
                            LOGGER.info("GeyserSpawnPacketFix: Found field {} for {}, current value: {}", fieldName, playerName, currentValue);
                            field.setBoolean(downstreamSession, true);
                            LOGGER.info("GeyserSpawnPacketFix: Forced {} to true for: {}", fieldName, playerName);
                            spawnPacketSent = true;
                        }
                    } catch (Exception altFieldException) {
                        LOGGER.debug("GeyserSpawnPacketFix: Could not access {} field: {}", fieldName, altFieldException.getMessage());
                    }
                }
                
                // If we still haven't found the right field, scan for ANY boolean spawn-related fields
                if (!spawnPacketSent) {
                    try {
                        java.lang.reflect.Field[] fields = sessionClass.getDeclaredFields();
                        for (java.lang.reflect.Field field : fields) {
                            if (field.getType() == boolean.class && 
                                (field.getName().toLowerCase().contains("spawn") || 
                                 field.getName().toLowerCase().contains("sent") ||
                                 field.getName().toLowerCase().contains("login"))) {
                                try {
                                    field.setAccessible(true);
                                    boolean currentValue = field.getBoolean(downstreamSession);
                                    LOGGER.info("GeyserSpawnPacketFix: Found spawn-related field {} for {}, current value: {}", field.getName(), playerName, currentValue);
                                    field.setBoolean(downstreamSession, true);
                                    LOGGER.info("GeyserSpawnPacketFix: Forced spawn-related field {} to true for: {}", field.getName(), playerName);
                                    spawnPacketSent = true;
                                } catch (Exception setException) {
                                    LOGGER.debug("GeyserSpawnPacketFix: Could not set field {}: {}", field.getName(), setException.getMessage());
                                }
                            }
                        }
                    } catch (Exception fieldsException) {
                        LOGGER.warn("GeyserSpawnPacketFix: Could not scan fields: {}", fieldsException.getMessage());
                    }
                }
            } catch (Exception fieldException) {
                LOGGER.warn("GeyserSpawnPacketFix: Exception accessing sentSpawn field: {}", fieldException.getMessage());
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
                                LOGGER.info("GeyserSpawnPacketFix: Attempting to check sentSpawn field for: {}", playerName);
                                java.lang.reflect.Field sentSpawnField = downstreamSession.getClass().getDeclaredField("sentSpawn");
                                sentSpawnField.setAccessible(true);
                                boolean sentSpawn = sentSpawnField.getBoolean(downstreamSession);
                                
                                LOGGER.info("GeyserSpawnPacketFix: Current sentSpawn value for {}: {}", playerName, sentSpawn);
                                
                                if (!sentSpawn) {
                                    LOGGER.warn("GeyserSpawnPacketFix: sentSpawn still false for {}, forcing final fix", playerName);
                                    sentSpawnField.setBoolean(downstreamSession, true);
                                    LOGGER.info("GeyserSpawnPacketFix: Successfully set sentSpawn to true for: {}", playerName);
                                    
                                    // Send additional packets to ensure client state
                                    sendFinalSpawnPackets(player);
                                } else {
                                    LOGGER.info("GeyserSpawnPacketFix: Spawn state is now correct for: {}", playerName);
                                }
                            } catch (NoSuchFieldException nsfe) {
                                LOGGER.warn("GeyserSpawnPacketFix: sentSpawn field not found for {}, trying alternative approaches", playerName);
                                
                                // Try to find any spawn-related fields
                                try {
                                    java.lang.reflect.Field[] fields = downstreamSession.getClass().getDeclaredFields();
                                    LOGGER.info("GeyserSpawnPacketFix: Available fields in session for {}: {}", playerName, 
                                        java.util.Arrays.stream(fields).map(f -> f.getName()).toArray());
                                    
                                    // Try alternative field names
                                    String[] alternativeFields = {"spawned", "hasSpawned", "playerSpawned", "spawnSent"};
                                    for (String fieldName : alternativeFields) {
                                        try {
                                            java.lang.reflect.Field field = downstreamSession.getClass().getDeclaredField(fieldName);
                                            field.setAccessible(true);
                                            if (field.getType() == boolean.class) {
                                                boolean currentValue = field.getBoolean(downstreamSession);
                                                LOGGER.info("GeyserSpawnPacketFix: Found field {} for {}, current value: {}", fieldName, playerName, currentValue);
                                                field.setBoolean(downstreamSession, true);
                                                LOGGER.info("GeyserSpawnPacketFix: Set {} to true for: {}", fieldName, playerName);
                                            }
                                        } catch (Exception altException) {
                                            LOGGER.debug("GeyserSpawnPacketFix: Could not access field {}: {}", fieldName, altException.getMessage());
                                        }
                                    }
                                } catch (Exception fieldsException) {
                                    LOGGER.warn("GeyserSpawnPacketFix: Could not access fields for {}: {}", playerName, fieldsException.getMessage());
                                }
                            } catch (Exception fieldException) {
                                LOGGER.warn("GeyserSpawnPacketFix: Could not check sentSpawn field for {}: {}", playerName, fieldException.getMessage());
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
