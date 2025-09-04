package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures the connection protocol state is properly synchronized
 * when Bedrock players transition from configuration to play phase.
 */
@Mixin(value = Connection.class)
public class BedrockConnectionStateMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockConnectionStateMixin");
    
    @Shadow
    private ConnectionProtocol protocol;
    
    /**
     * When sending packets, ensure we're in the correct protocol state for Bedrock players.
     */
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD")
    )
    private void checkProtocolStateForBedrock(Packet<?> packet, CallbackInfo ci) {
        try {
            Connection self = (Connection) (Object) this;
            
            // Check if this is a configuration finish packet
            String packetName = packet.getClass().getSimpleName();
            if (packetName.contains("ClientboundFinishConfiguration") || 
                packetName.contains("ServerboundFinishConfiguration")) {
                
                // Check if the packet listener is for a Bedrock player
                if (self.getPacketListener() instanceof ServerConfigurationPacketListenerImpl configListener) {
                    if (BedrockDetectionHelper.isBedrockPlayer(configListener)) {
                        LOGGER.info("BedrockConnectionStateMixin: Detected finish configuration packet for Bedrock player");
                        
                        // Schedule protocol change to PLAY after a short delay
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                Thread.sleep(100); // Small delay to ensure packet is processed
                                
                                // Force the connection to PLAY protocol
                                if (protocol != ConnectionProtocol.PLAY) {
                                    LOGGER.info("BedrockConnectionStateMixin: Forcing connection to PLAY protocol for Bedrock player");
                                    
                                    // Use reflection to set the protocol
                                    try {
                                        java.lang.reflect.Field protocolField = Connection.class.getDeclaredField("protocol");
                                        protocolField.setAccessible(true);
                                        protocolField.set(self, ConnectionProtocol.PLAY);
                                        LOGGER.info("BedrockConnectionStateMixin: Successfully updated protocol to PLAY");
                                    } catch (Exception reflectException) {
                                        LOGGER.error("BedrockConnectionStateMixin: Failed to set protocol via reflection: {}", reflectException.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.error("BedrockConnectionStateMixin: Failed to update protocol: {}", e.getMessage());
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockConnectionStateMixin: Exception in protocol check: {}", e.getMessage());
        }
    }
    
    /**
     * When the packet listener changes, ensure proper protocol state.
     */
    @Inject(
        method = "setListener",
        at = @At("TAIL")
    )
    private void ensureProtocolStateOnListenerChange(net.minecraft.network.PacketListener listener, CallbackInfo ci) {
        try {
            Connection self = (Connection) (Object) this;
            
            // If we're setting a game packet listener for a Bedrock player, ensure we're in PLAY protocol
            if (listener instanceof ServerGamePacketListenerImpl gameListener) {
                if (gameListener.player != null && 
                    BedrockDetectionHelper.isFloodgatePlayer(gameListener.player.getGameProfile().getName())) {
                    
                    if (protocol != ConnectionProtocol.PLAY) {
                        LOGGER.info("BedrockConnectionStateMixin: Updating protocol to PLAY for Bedrock player game listener");
                        
                        // Use reflection to set the protocol
                        try {
                            java.lang.reflect.Field protocolField = Connection.class.getDeclaredField("protocol");
                            protocolField.setAccessible(true);
                            protocolField.set(self, ConnectionProtocol.PLAY);
                            LOGGER.info("BedrockConnectionStateMixin: Successfully updated protocol to PLAY for game listener");
                        } catch (Exception reflectException) {
                            LOGGER.error("BedrockConnectionStateMixin: Failed to set protocol via reflection: {}", reflectException.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockConnectionStateMixin: Exception in listener change: {}", e.getMessage());
        }
    }
}
