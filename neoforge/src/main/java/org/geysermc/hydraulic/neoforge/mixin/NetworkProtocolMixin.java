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
 * This mixin targets the Connection class to catch NeoForge version check disconnects
 * at the network protocol level.
 */
@Mixin(value = Connection.class)
public class NetworkProtocolMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NetworkProtocolMixin");

    /**
     * Intercepts disconnect calls at the network Connection level to catch version check disconnects.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventNetworkProtocolDisconnect(Component reason, CallbackInfo ci) {
        try {
            if (reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is any NeoForge version/compatibility check disconnect
                if (disconnectMessage.contains("Incompatible client") || 
                    disconnectMessage.contains("Please use NeoForge") ||
                    disconnectMessage.contains("trying to connect to a server that is running NeoForge")) {
                    
                    Connection self = (Connection) (Object) this;
                    
                    // Try to determine if this is for a Bedrock player by checking the packet listener
                    try {
                        if (self.getPacketListener() != null) {
                            String playerName = null;
                            boolean isBedrockPlayer = false;
                            
                            // Check if we can get player information
                            if (self.getPacketListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                                if (configListener.getOwner() != null) {
                                    playerName = configListener.getOwner().getName();
                                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                                }
                            } else if (self.getPacketListener() instanceof net.minecraft.server.network.ServerGamePacketListenerImpl gameListener) {
                                if (gameListener.player != null) {
                                    playerName = gameListener.player.getGameProfile().getName();
                                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                                }
                            }
                            
                            if (isBedrockPlayer) {
                                LOGGER.info("NetworkProtocolMixin: Preventing network-level NeoForge disconnect for Bedrock player: {} (Message: {})", 
                                    playerName, disconnectMessage);
                                ci.cancel(); // Prevent the disconnect
                                return;
                            }
                            
                            // Even if we can't identify the player, log this for debugging
                            LOGGER.info("NetworkProtocolMixin: Detected NeoForge disconnect message at network level: {} (Player: {})", 
                                disconnectMessage, playerName != null ? playerName : "unknown");
                        }
                    } catch (Exception e) {
                        LOGGER.debug("NetworkProtocolMixin: Exception in network disconnect prevention: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NetworkProtocolMixin: Exception in network protocol disconnect prevention: {}", e.getMessage());
        }
    }
}
