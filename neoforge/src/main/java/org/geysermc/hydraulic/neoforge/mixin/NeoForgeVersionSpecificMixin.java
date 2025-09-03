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
                        
                        // After preventing the disconnect, manually trigger configuration completion
                        LOGGER.info("NeoForgeVersionSpecificMixin: Disconnect prevented, now triggering configuration completion for: {}", 
                            self.getOwner().getName());
                        
                        ci.cancel(); // Prevent the disconnect
                        
                        // Try to manually complete the configuration since we prevented the disconnect
                        if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl) {
                            net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener = 
                                (net.minecraft.server.network.ServerConfigurationPacketListenerImpl) self;
                            
                            // Try to complete configuration immediately
                            try {
                                LOGGER.info("NeoForgeVersionSpecificMixin: Attempting to complete configuration for: {}", 
                                    configListener.getOwner().getName());
                                
                                java.lang.reflect.Method finishConfigMethod = 
                                    net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                                finishConfigMethod.setAccessible(true);
                                finishConfigMethod.invoke(configListener);
                                
                                LOGGER.info("NeoForgeVersionSpecificMixin: Successfully completed configuration for Bedrock player: {}", 
                                    configListener.getOwner().getName());
                            } catch (Exception e) {
                                LOGGER.warn("NeoForgeVersionSpecificMixin: Failed to complete configuration for {}: {}", 
                                    configListener.getOwner().getName(), e.getMessage());
                            }
                        }
                        
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeVersionSpecificMixin: Exception in version-specific disconnect prevention: {}", e.getMessage());
        }
    }
}
