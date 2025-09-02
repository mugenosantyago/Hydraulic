package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin specifically targets the NeoForge version check that disconnects players
 * who don't have NeoForge installed on their client.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class NeoForgeVersionCheckMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeVersionCheckMixin");

    /**
     * Intercepts disconnect calls to prevent Bedrock players from being kicked for missing NeoForge.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventNeoForgeDisconnectForBedrock(Component reason, CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null && reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is the NeoForge version check disconnect message
                if (disconnectMessage.contains("trying to connect to a server that is running NeoForge") ||
                    disconnectMessage.contains("Please install NeoForge")) {
                    
                    boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("NeoForgeVersionCheckMixin: Preventing NeoForge version check disconnect for Bedrock player: {} (Message: {})", 
                            self.getOwner().getName(), disconnectMessage);
                        
                        // Instead of just canceling, try to continue the configuration process
                        try {
                            // Use reflection to call finishConfiguration if it exists
                            java.lang.reflect.Method finishConfigMethod = 
                                ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                            finishConfigMethod.setAccessible(true);
                            finishConfigMethod.invoke(self);
                            LOGGER.info("NeoForgeVersionCheckMixin: Successfully finished configuration for Bedrock player: {}", 
                                self.getOwner().getName());
                        } catch (Exception configException) {
                            LOGGER.debug("NeoForgeVersionCheckMixin: Could not finish configuration via reflection: {}", 
                                configException.getMessage());
                        }
                        
                        ci.cancel(); // Prevent the disconnect
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeVersionCheckMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
}
