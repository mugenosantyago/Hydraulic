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
 * This mixin prevents NullPointerException in Floodgate's ModSkinApplier
 * by intercepting the lambda method that causes the TrackedEntity NPE.
 * 
 * The root cause is that Floodgate tries to access TrackedEntity before it's
 * properly initialized, causing NPEs that prevent proper spawn completion.
 */
@Mixin(targets = "org.geysermc.floodgate.mod.pluginmessage.ModSkinApplier", remap = false, priority = 1100)
public class FloodgateSkinMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FloodgateSkinMixin");
    
    /**
     * Prevents NPE in Floodgate's skin application lambda by intercepting the problematic method.
     */
    @Inject(
        method = "lambda$applySkin$0",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void preventTrackedEntityNPE(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if the player's tracking is properly initialized
                if (player.getServer() == null || 
                    player.connection == null || 
                    !player.connection.getConnection().isConnected()) {
                    
                    LOGGER.info("FloodgateSkinMixin: Preventing skin application for unready Bedrock player: {}", playerName);
                    ci.cancel(); // Prevent the lambda execution
                    return;
                }
                
                // Check if TrackedEntity exists before proceeding
                try {
                    var level = player.level();
                    if (level != null) {
                        var chunkSource = level.getChunkSource();
                        if (chunkSource != null) {
                            var chunkMap = chunkSource.chunkMap;
                            if (chunkMap != null) {
                                // Try to access the entity map to verify tracking exists
                                try {
                                    java.lang.reflect.Field entityMapField = chunkMap.getClass().getDeclaredField("entityMap");
                                    entityMapField.setAccessible(true);
                                    @SuppressWarnings("unchecked")
                                    java.util.Map<Integer, ?> entityMap = (java.util.Map<Integer, ?>) entityMapField.get(chunkMap);
                                    
                                    if (entityMap == null || !entityMap.containsKey(player.getId())) {
                                        LOGGER.info("FloodgateSkinMixin: TrackedEntity not ready for Bedrock player {}, delaying skin application", playerName);
                                        
                                        // Schedule delayed skin application
                                        var server = player.getServer();
                                        if (server != null) {
                                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                                                try {
                                                    // Re-check and apply skin safely
                                                    applySkinSafely(player);
                                                } catch (Exception e) {
                                                    LOGGER.debug("FloodgateSkinMixin: Exception in delayed skin application: {}", e.getMessage());
                                                }
                                            }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                                        }
                                        
                                        ci.cancel(); // Prevent immediate execution
                                        return;
                                    }
                                } catch (Exception reflectionException) {
                                    LOGGER.debug("FloodgateSkinMixin: Could not verify TrackedEntity, allowing skin application to proceed");
                                }
                            }
                        }
                    }
                } catch (Exception trackingException) {
                    LOGGER.info("FloodgateSkinMixin: Tracking verification failed for {}, preventing skin application: {}", 
                        playerName, trackingException.getMessage());
                    ci.cancel();
                    return;
                }
                
                LOGGER.debug("FloodgateSkinMixin: TrackedEntity appears ready for Bedrock player: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in NPE prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Alternative method to intercept the applySkin method directly.
     */
    @Inject(
        method = "applySkin",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventSkinApplicationNPE(Object player, CallbackInfo ci) {
        try {
            if (player instanceof ServerPlayer serverPlayer) {
                String playerName = serverPlayer.getGameProfile().getName();
                
                if (BedrockDetectionHelper.isFloodgatePlayer(playerName)) {
                    // Check if the player's tracking is properly initialized
                    if (serverPlayer.getServer() == null || 
                        serverPlayer.connection == null || 
                        !serverPlayer.connection.getConnection().isConnected()) {
                        
                        LOGGER.debug("FloodgateSkinMixin: Preventing skin application for unready Bedrock player: {}", playerName);
                        ci.cancel(); // Prevent skin application until player is properly initialized
                        return;
                    }
                    
                    // Add a small delay to ensure all systems are ready
                    var server = serverPlayer.getServer();
                    if (server != null) {
                        server.execute(() -> {
                            try {
                                Thread.sleep(100); // Small delay to ensure tracking is initialized
                                
                                // Verify the player is still connected and properly tracked
                                if (serverPlayer.connection != null && 
                                    serverPlayer.connection.getConnection().isConnected()) {
                                    
                                    // Try to access the level's chunk source to ensure tracking is ready
                                    try {
                                        var chunkSource = serverPlayer.level().getChunkSource();
                                        if (chunkSource != null) {
                                            LOGGER.debug("FloodgateSkinMixin: Delayed skin application for Bedrock player: {}", playerName);
                                            
                                            // Apply skin with safety checks
                                            applySkinSafely(serverPlayer);
                                        }
                                    } catch (Exception trackingException) {
                                        LOGGER.debug("FloodgateSkinMixin: Tracking not ready for {}: {}", playerName, trackingException.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.debug("FloodgateSkinMixin: Exception in delayed skin application: {}", e.getMessage());
                            }
                        });
                        
                        ci.cancel(); // Cancel the immediate skin application
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in skin application prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Safely applies skin with proper null checks.
     */
    private void applySkinSafely(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Use reflection to call the original applySkin method safely
            try {
                Class<?> modSkinApplierClass = Class.forName("org.geysermc.floodgate.addon.data.ModSkinApplier");
                
                // Get the applySkin method
                java.lang.reflect.Method applySkinMethod = modSkinApplierClass.getDeclaredMethod("applySkin", Object.class);
                applySkinMethod.setAccessible(true);
                
                // Create an instance of ModSkinApplier
                Object modSkinApplier = modSkinApplierClass.getDeclaredConstructor().newInstance();
                
                // Call applySkin with additional safety checks
                try {
                    applySkinMethod.invoke(modSkinApplier, player);
                    LOGGER.debug("FloodgateSkinMixin: Successfully applied skin for Bedrock player: {}", playerName);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    if (ite.getCause() instanceof NullPointerException) {
                        LOGGER.info("FloodgateSkinMixin: Prevented NPE in skin application for Bedrock player: {}", playerName);
                    } else {
                        LOGGER.debug("FloodgateSkinMixin: Exception in skin application: {}", ite.getCause().getMessage());
                    }
                }
                
            } catch (ClassNotFoundException cnfe) {
                LOGGER.debug("FloodgateSkinMixin: ModSkinApplier not found, Floodgate may not be installed");
            } catch (Exception reflectionException) {
                LOGGER.debug("FloodgateSkinMixin: Could not access ModSkinApplier: {}", reflectionException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in safe skin application: {}", e.getMessage());
        }
    }
}
