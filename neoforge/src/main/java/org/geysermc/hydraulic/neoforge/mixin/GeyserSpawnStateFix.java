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
 * This mixin focuses specifically on fixing Geyser's session spawn state
 * to prevent the "sentSpawn: false, spawned: true" issue that causes loading screen hang.
 */
@Mixin(value = PlayerList.class, priority = 1300)
public class GeyserSpawnStateFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeyserSpawnStateFix");
    
    /**
     * Hook into player placement to coordinate with Geyser's session state management.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void fixGeyserSpawnState(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("GeyserSpawnStateFix: Attempting to fix Geyser spawn state for: {}", playerName);
                
                // Schedule spawn state fix after player is fully placed
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            // Wait for initial placement to complete
                            Thread.sleep(200);
                            attemptGeyserSpawnStateFix(player);
                        } catch (Exception e) {
                            LOGGER.debug("GeyserSpawnStateFix: Exception in spawn state fix: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnStateFix: Exception in spawn state hook: {}", e.getMessage());
        }
    }
    
    /**
     * Attempts to directly fix Geyser's session spawn state using reflection.
     */
    private static void attemptGeyserSpawnStateFix(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Try to access Geyser's session and fix the spawn state directly
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    // Get the connection for this player
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        LOGGER.info("GeyserSpawnStateFix: Found Geyser connection for {}, attempting spawn state fix", playerName);
                        
                        try {
                            // Try to access the downstream session (Bedrock client)
                            java.lang.reflect.Method getDownstreamMethod = connection.getClass().getMethod("downstream");
                            Object downstreamSession = getDownstreamMethod.invoke(connection);
                            
                            if (downstreamSession != null) {
                                // Try to find and fix the spawn state fields
                                Class<?> sessionClass = downstreamSession.getClass();
                                
                                // Look for spawn-related fields that might need fixing
                                try {
                                    // Try to find the sentSpawn field and set it to true
                                    java.lang.reflect.Field[] fields = sessionClass.getDeclaredFields();
                                    for (java.lang.reflect.Field field : fields) {
                                        String fieldName = field.getName().toLowerCase();
                                        
                                        // Look for spawn-related boolean fields
                                        if (field.getType() == boolean.class && 
                                            (fieldName.contains("spawn") || fieldName.contains("sent"))) {
                                            
                                            field.setAccessible(true);
                                            boolean currentValue = field.getBoolean(downstreamSession);
                                            
                                            LOGGER.info("GeyserSpawnStateFix: Found spawn field '{}' with value: {} for {}", 
                                                field.getName(), currentValue, playerName);
                                            
                                            // If this looks like a "sentSpawn" field and it's false, set it to true
                                            if (fieldName.contains("sent") && fieldName.contains("spawn") && !currentValue) {
                                                field.setBoolean(downstreamSession, true);
                                                LOGGER.info("GeyserSpawnStateFix: Set {} to true for {}", field.getName(), playerName);
                                            }
                                        }
                                    }
                                    
                                    // Also try to call methods that might trigger proper spawn state
                                    java.lang.reflect.Method[] methods = sessionClass.getMethods();
                                    for (java.lang.reflect.Method method : methods) {
                                        String methodName = method.getName().toLowerCase();
                                        
                                        // Look for methods that might complete the spawn process
                                        if ((methodName.contains("complete") && methodName.contains("spawn")) ||
                                            (methodName.contains("finish") && methodName.contains("spawn")) ||
                                            methodName.equals("onspawn")) {
                                            
                                            if (method.getParameterCount() == 0) {
                                                try {
                                                    method.invoke(downstreamSession);
                                                    LOGGER.info("GeyserSpawnStateFix: Called spawn completion method {} for {}", 
                                                        method.getName(), playerName);
                                                } catch (Exception methodException) {
                                                    LOGGER.debug("GeyserSpawnStateFix: Method {} failed: {}", 
                                                        method.getName(), methodException.getMessage());
                                                }
                                            }
                                        }
                                    }
                                    
                                } catch (Exception fieldException) {
                                    LOGGER.debug("GeyserSpawnStateFix: Could not access spawn fields: {}", fieldException.getMessage());
                                }
                                
                                LOGGER.info("GeyserSpawnStateFix: Completed spawn state fix attempt for: {}", playerName);
                            }
                        } catch (Exception sessionException) {
                            LOGGER.debug("GeyserSpawnStateFix: Could not access downstream session: {}", sessionException.getMessage());
                        }
                    } else {
                        LOGGER.debug("GeyserSpawnStateFix: No Geyser connection found for: {}", playerName);
                    }
                }
            } catch (ClassNotFoundException cnfe) {
                LOGGER.debug("GeyserSpawnStateFix: Geyser API not available");
            } catch (Exception geyserException) {
                LOGGER.debug("GeyserSpawnStateFix: Could not access Geyser API: {}", geyserException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.debug("GeyserSpawnStateFix: Exception in spawn state fix: {}", e.getMessage());
        }
    }
}
