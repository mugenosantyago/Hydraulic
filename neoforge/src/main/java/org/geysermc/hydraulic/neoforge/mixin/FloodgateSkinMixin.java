package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents NullPointerException in Floodgate's ModSkinApplier
 * by adding proper null checks and timing delays for skin application.
 * This fixes the loading screen issue where Bedrock players get stuck
 * due to skin application timing mismatches.
 */
@Mixin(targets = "org.geysermc.floodgate.skin.ModSkinApplier", remap = false, priority = 800)
public class FloodgateSkinMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FloodgateSkinMixin");
    
    /**
     * Intercepts skin application to add proper null checks and timing delays.
     * This prevents the NullPointerException that can occur when Floodgate
     * tries to apply skins before the player session is fully initialized.
     */
    @Inject(
        method = "applySkin(Lorg/geysermc/floodgate/api/player/FloodgatePlayer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventSkinApplicationErrors(Object floodgatePlayer, CallbackInfo ci) {
        try {
            if (floodgatePlayer == null) {
                LOGGER.debug("FloodgateSkinMixin: Preventing skin application - FloodgatePlayer is null");
                ci.cancel();
                return;
            }
            
            // Use reflection to safely check the player state
            try {
                Object javaPlayer = floodgatePlayer.getClass().getMethod("getJavaPlayer").invoke(floodgatePlayer);
                if (javaPlayer == null) {
                    LOGGER.debug("FloodgateSkinMixin: Preventing skin application - Java player not initialized yet");
                    ci.cancel();
                    return;
                }
                
                // Get player name for logging - make it final for lambda usage
                final String playerName = (String) javaPlayer.getClass().getMethod("getName").invoke(javaPlayer);
                
                // Additional validation to ensure the player is properly connected
                Object connection = javaPlayer.getClass().getMethod("getConnection").invoke(javaPlayer);
                if (connection == null) {
                    LOGGER.debug("FloodgateSkinMixin: Delaying skin application for {} - connection not ready", playerName);
                    
                    // Schedule skin application for later when the connection is ready
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                        try {
                            // Re-attempt skin application after delay
                            this.getClass().getMethod("applySkin", Object.class).invoke(this, floodgatePlayer);
                            LOGGER.info("FloodgateSkinMixin: Delayed skin application completed for: {}", playerName);
                        } catch (Exception delayedException) {
                            LOGGER.debug("FloodgateSkinMixin: Delayed skin application failed for {}: {}", 
                                playerName, delayedException.getMessage());
                        }
                    }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    ci.cancel();
                    return;
                }
                
                LOGGER.debug("FloodgateSkinMixin: Allowing skin application for properly initialized player: {}", playerName);
                
            } catch (Exception reflectionException) {
                LOGGER.debug("FloodgateSkinMixin: Could not validate player state via reflection: {}", reflectionException.getMessage());
                // If we can't validate safely, allow the original method to proceed
                // but add a small delay to reduce timing issues
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in skin application prevention: {}", e.getMessage());
            // If anything goes wrong, allow the original method to proceed
        }
    }
    
    /**
     * Alternative method hook for skin application that might be called.
     */
    @Inject(
        method = "applySkin(Ljava/util/UUID;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventSkinApplicationByUUID(java.util.UUID playerUUID, CallbackInfo ci) {
        try {
            if (playerUUID == null) {
                LOGGER.debug("FloodgateSkinMixin: Preventing skin application - Player UUID is null");
                ci.cancel();
                return;
            }
            
            LOGGER.debug("FloodgateSkinMixin: Processing skin application for UUID: {}", playerUUID);
            
            // Add a small delay to prevent timing issues
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in UUID-based skin application prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Hook into any skin-related initialization to add safety checks.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL"),
        remap = false,
        require = 0
    )
    private void onSkinApplierInit(CallbackInfo ci) {
        try {
            LOGGER.debug("FloodgateSkinMixin: ModSkinApplier initialized with safety enhancements");
        } catch (Exception e) {
            LOGGER.debug("FloodgateSkinMixin: Exception in skin applier initialization: {}", e.getMessage());
        }
    }
}
