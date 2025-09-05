package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents packet errors from causing disconnects for Bedrock players.
 * Specifically targets the onPacketError method that handles packet failures.
 */
@Mixin(value = ServerCommonPacketListenerImpl.class)
public class PacketErrorHandlerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("PacketErrorHandlerMixin");

    /**
     * Prevents packet errors from causing disconnects for Bedrock players.
     */
    @Inject(
        method = "onPacketError",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventPacketErrorDisconnectForBedrock(net.minecraft.network.protocol.Packet<?> packet, Exception exception, CallbackInfo ci) {
        try {
            ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
            
            // Check if this is a Bedrock player
            boolean isBedrockPlayer = false;
            String playerName = null;
            
            if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                if (configListener.getOwner() != null) {
                    playerName = configListener.getOwner().getName();
                }
            } else if (self instanceof net.minecraft.server.network.ServerGamePacketListenerImpl gameListener) {
                if (gameListener.player != null) {
                    playerName = gameListener.player.getGameProfile().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            }
            
            if (isBedrockPlayer) {
                // Check if the packet is null or the exception is related to custom packets that Bedrock clients can't handle
                String errorMessage = exception != null ? exception.getMessage() : "null";
                
                if (packet == null || 
                    (errorMessage != null && (errorMessage.contains("may not be sent to the client") || 
                                            errorMessage.contains("UnsupportedOperationException") ||
                                            errorMessage.contains("Payload") ||
                                            errorMessage.contains("Invalid move player packet received") ||
                                            errorMessage.contains("Invalid move player") ||
                                            errorMessage.contains("multiplayer.disconnect.invalid_player_movement") ||
                                            errorMessage.contains("null")))) {
                    
                    LOGGER.debug("PacketErrorHandlerMixin: Preventing packet error for Bedrock player: {} (Packet: {}, Error: {})", 
                        playerName, packet != null ? packet.getClass().getSimpleName() : "null", errorMessage);
                    ci.cancel(); // Don't let the packet error cause a disconnect
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("PacketErrorHandlerMixin: Exception in packet error handling: {}", e.getMessage());
        }
    }
}
