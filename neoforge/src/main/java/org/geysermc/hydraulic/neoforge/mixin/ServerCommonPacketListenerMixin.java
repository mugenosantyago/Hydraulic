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
 * This mixin targets the ServerCommonPacketListenerImpl (parent class) to catch disconnects
 * that might be happening at a higher level in the class hierarchy.
 */
@Mixin(value = ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ServerCommonPacketListenerMixin");

    /**
     * Intercepts disconnect calls at the common packet listener level.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventCommonDisconnectForBedrock(Component reason, CallbackInfo ci) {
        try {
            if (reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is the NeoForge version check disconnect message
                if (disconnectMessage.contains("trying to connect to a server that is running NeoForge") ||
                    disconnectMessage.contains("Please install NeoForge")) {
                    
                    ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
                    
                    // Try to determine if this is a Bedrock player
                    boolean isBedrockPlayer = false;
                    String playerName = null;
                    
                    try {
                        // Check if we can get player info
                        if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                            isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                            if (configListener.getOwner() != null) {
                                playerName = configListener.getOwner().getName();
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("ServerCommonPacketListenerMixin: Could not determine player type: {}", e.getMessage());
                    }
                    
                    // If we detected a Bedrock player, prevent the disconnect
                    if (isBedrockPlayer) {
                        LOGGER.info("ServerCommonPacketListenerMixin: Preventing NeoForge common-level disconnect for Bedrock player: {} (Message: {})", 
                            playerName, disconnectMessage);
                        ci.cancel();
                        return;
                    }
                    
                    // Even if we couldn't definitively identify the player, log this for debugging
                    LOGGER.info("ServerCommonPacketListenerMixin: Detected NeoForge disconnect message: {} (Player: {})", 
                        disconnectMessage, playerName != null ? playerName : "unknown");
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ServerCommonPacketListenerMixin: Exception in common disconnect prevention: {}", e.getMessage());
        }
    }
}
