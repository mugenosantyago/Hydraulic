package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin targets the Connection class to prevent NeoForge disconnects at the network level.
 */
@Mixin(value = Connection.class)
public class NeoForgeConnectionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeConnectionMixin");

    /**
     * Intercepts disconnect calls at the Connection level to prevent NeoForge version check disconnects.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventNeoForgeConnectionDisconnect(Component reason, CallbackInfo ci) {
        try {
            if (reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is the NeoForge version check disconnect message
                if (disconnectMessage.contains("trying to connect to a server that is running NeoForge") ||
                    disconnectMessage.contains("Please install NeoForge")) {
                    
                    Connection self = (Connection) (Object) this;
                    
                    // Try to get the packet listener to check if this is a Bedrock player
                    try {
                        if (self.getPacketListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                            boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                            
                            if (isBedrockPlayer) {
                                LOGGER.info("NeoForgeConnectionMixin: Preventing NeoForge connection-level disconnect for Bedrock player: {} (Message: {})", 
                                    configListener.getOwner().getName(), disconnectMessage);
                                ci.cancel(); // Prevent the disconnect
                                return;
                            }
                        }
                    } catch (Exception e) {
                        // If we can't determine the player type, check if the disconnect message suggests a Bedrock player
                        // This is a fallback for cases where we can't access the packet listener
                        LOGGER.debug("NeoForgeConnectionMixin: Could not determine player type, checking message context: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeConnectionMixin: Exception in connection disconnect prevention: {}", e.getMessage());
        }
    }
}
