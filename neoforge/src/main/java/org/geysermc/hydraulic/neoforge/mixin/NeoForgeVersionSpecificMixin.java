package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin specifically targets NeoForge version-specific disconnect messages
 * like "Please use NeoForge 21.8.21" that include version numbers.
 */
@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 900)
public class NeoForgeVersionSpecificMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeVersionSpecificMixin");

    /**
     * Intercepts disconnect calls to prevent Bedrock players from being kicked for specific NeoForge versions.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventVersionSpecificDisconnect(Component reason, CallbackInfo ci) {
        try {
            ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null && reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is a version-specific NeoForge disconnect message
                // This catches messages like "Please use NeoForge 21.8.21" or similar
                if ((disconnectMessage.contains("NeoForge") && 
                     (disconnectMessage.contains("Please use") || disconnectMessage.contains("Please install"))) ||
                    (disconnectMessage.contains("Incompatible client") && disconnectMessage.contains("NeoForge"))) {
                    
                    boolean isBedrockPlayer = false;
                    
                    // Try to cast to ServerConfigurationPacketListenerImpl for BedrockDetectionHelper
                    if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl) {
                        isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer((net.minecraft.server.network.ServerConfigurationPacketListenerImpl) self);
                    } else if (self.getOwner() != null && self.getOwner().getName() != null) {
                        // Fallback to Floodgate naming convention check
                        isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(self.getOwner().getName());
                    }
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("NeoForgeVersionSpecificMixin: Preventing version-specific NeoForge disconnect for Bedrock player: {} (Message: {})", 
                            self.getOwner().getName(), disconnectMessage);
                        
                        // Try to continue the configuration process
                        try {
                            // Use reflection to call finishConfiguration if it exists
                            java.lang.reflect.Method finishConfigMethod = 
                                ServerCommonPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                            finishConfigMethod.setAccessible(true);
                            finishConfigMethod.invoke(self);
                            LOGGER.info("NeoForgeVersionSpecificMixin: Successfully finished configuration for Bedrock player: {}", 
                                self.getOwner().getName());
                        } catch (Exception configException) {
                            LOGGER.debug("NeoForgeVersionSpecificMixin: Could not finish configuration via reflection: {}", 
                                configException.getMessage());
                            
                            // If we can't finish configuration, at least prevent the disconnect
                            // The player might get stuck but won't be kicked
                        }
                        
                        ci.cancel(); // Prevent the disconnect
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeVersionSpecificMixin: Exception in version-specific disconnect prevention: {}", e.getMessage());
        }
    }
}
