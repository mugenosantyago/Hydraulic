package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents the ClientboundFinishConfigurationPacket from being sent to Bedrock players
 * when the network pipeline isn't properly configured, which causes the EncoderException.
 */
@Mixin(value = Connection.class, priority = 1100)
public class FinishConfigurationPacketMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FinishConfigurationPacketMixin");

    /**
     * Intercepts packet sending to suppress problematic ClientboundFinishConfigurationPacket for Bedrock players.
     */
    @Inject(
        method = "doSendPacket",
        at = @At("HEAD"),
        cancellable = true
    )
    private void suppressFinishConfigurationPacket(Packet<?> packet, CallbackInfo ci) {
        try {
            if (packet instanceof ClientboundFinishConfigurationPacket) {
                Connection self = (Connection) (Object) this;
                
                // Try to determine if this connection is for a Bedrock player
                try {
                    // Check if the packet listener is for a Bedrock player
                    if (self.getPacketListener() != null) {
                        String listenerClass = self.getPacketListener().getClass().getSimpleName();
                        
                        // Check if this is a configuration listener and if it's for a Bedrock player
                        if (listenerClass.contains("ServerConfigurationPacketListenerImpl")) {
                            net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener = 
                                (net.minecraft.server.network.ServerConfigurationPacketListenerImpl) self.getPacketListener();
                            
                            if (configListener.getOwner() != null) {
                                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                                
                                if (isBedrockPlayer) {
                                    LOGGER.info("FinishConfigurationPacketMixin: Suppressing ClientboundFinishConfigurationPacket for Bedrock player: {} to prevent pipeline error", 
                                        configListener.getOwner().getName());
                                    ci.cancel();
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception detectionException) {
                    // If we can't determine if it's a Bedrock player, let the packet through
                    LOGGER.debug("FinishConfigurationPacketMixin: Could not determine if player is Bedrock: {}", 
                        detectionException.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("FinishConfigurationPacketMixin: Exception in packet suppression: {}", e.getMessage());
        }
    }
}
