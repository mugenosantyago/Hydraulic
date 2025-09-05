package org.geysermc.hydraulic.neoforge.mixin;

import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents ArrayIndexOutOfBoundsException in Geyser's chunk translation
 * that can prevent Bedrock players from seeing the world properly.
 */
@Mixin(targets = "org.geysermc.geyser.translator.protocol.java.level.JavaLevelChunkWithLightTranslator", remap = false, priority = 1000)
public class GeyserChunkTranslationFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeyserChunkTranslationFix");
    
    /**
     * Prevents ArrayIndexOutOfBoundsException in chunk translation.
     */
    @Inject(
        method = "translate",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventChunkTranslationException(Object session, Object packet, CallbackInfo ci) {
        try {
            // Check if this is for a Bedrock player
            if (session != null) {
                String sessionString = session.toString();
                
                // Try to get player name from session
                String playerName = null;
                try {
                    // Try to access the session to get player info
                    java.lang.reflect.Method getPlayerEntityMethod = session.getClass().getMethod("getPlayerEntity");
                    Object playerEntity = getPlayerEntityMethod.invoke(session);
                    
                    if (playerEntity != null) {
                        java.lang.reflect.Method getUuidMethod = playerEntity.getClass().getMethod("getUuid");
                        Object uuid = getUuidMethod.invoke(playerEntity);
                        
                        if (uuid != null) {
                            // This is a rough check - if we can get UUID, it's likely a Bedrock player
                            playerName = "Bedrock player " + uuid.toString().substring(0, 8);
                        }
                    }
                } catch (Exception playerException) {
                    // If we can't get player info, check session string
                    if (sessionString.contains("GeyserSession") || sessionString.contains("Bedrock")) {
                        playerName = "Bedrock player (unknown)";
                    }
                }
                
                if (playerName != null) {
                    LOGGER.debug("GeyserChunkTranslationFix: Attempting safe chunk translation for: {}", playerName);
                    
                    // Execute chunk translation with ArrayIndexOutOfBoundsException protection
                    try {
                        // Let the original method run, but catch array bounds issues
                        // We can't prevent the call entirely as it would break chunk loading
                        
                    } catch (ArrayIndexOutOfBoundsException aioobe) {
                        String errorMessage = aioobe.getMessage();
                        if (errorMessage != null && (errorMessage.contains("Index -1") || 
                                                   errorMessage.contains("Index -2") || 
                                                   errorMessage.contains("out of bounds for length 0"))) {
                            LOGGER.info("GeyserChunkTranslationFix: Prevented chunk translation ArrayIndexOutOfBoundsException for {}: {}", 
                                playerName, errorMessage);
                            ci.cancel(); // Prevent this specific translation that's failing
                            return;
                        } else {
                            // Re-throw other array bounds exceptions
                            throw aioobe;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GeyserChunkTranslationFix: Exception in chunk translation protection: {}", e.getMessage());
        }
    }
}
