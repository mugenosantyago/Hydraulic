package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the ClientboundFinishConfigurationPacket from being sent to Bedrock players
 * since Geyser/Floodgate doesn't know how to translate this packet.
 */
@Mixin(value = Connection.class)
public class FinishConfigurationPacketMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FinishConfigurationPacketMixin");

    @Shadow
    private volatile net.minecraft.network.PacketListener packetListener;

    /**
     * Intercepts the sending of ClientboundFinishConfigurationPacket to Bedrock players.
     */
    @Inject(
        method = "doSendPacket",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventFinishConfigurationPacketForBedrock(
        net.minecraft.network.protocol.Packet<?> packet, 
        net.minecraft.network.PacketSendListener listener, 
        boolean flush, 
        CallbackInfo ci
    ) {
        try {
            // Check if this is a ClientboundFinishConfigurationPacket
            if (packet instanceof ClientboundFinishConfigurationPacket) {
                
                // Check if the current packet listener is a configuration listener for a Bedrock player
                if (packetListener instanceof ServerConfigurationPacketListenerImpl configListener) {
                    boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                    
                    if (isBedrockPlayer && configListener.getOwner() != null) {
                        String playerName = configListener.getOwner().getName();
                        LOGGER.info("FinishConfigurationPacketMixin: Preventing ClientboundFinishConfigurationPacket from being sent to Bedrock player: {}", playerName);
                        
                        // Cancel the packet sending to prevent the "unknown packet" error
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("FinishConfigurationPacketMixin: Exception in packet interception: {}", e.getMessage());
        }
    }
}
