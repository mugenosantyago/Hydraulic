package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents BrandPayload packets from being sent to Bedrock players
 * since they can't handle them and it causes pipeline errors.
 */
@Mixin(value = Connection.class, priority = 1100)
public class BrandPayloadMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BrandPayloadMixin");

    /**
     * Intercepts packet sending to suppress problematic BrandPayload packets for Bedrock players.
     */
    @Inject(
        method = "doSendPacket",
        at = @At("HEAD"),
        cancellable = true
    )
    private void suppressBrandPayloadPacket(Packet<?> packet, CallbackInfo ci) {
        try {
            if (packet instanceof ClientboundCustomPayloadPacket) {
                ClientboundCustomPayloadPacket customPacket = (ClientboundCustomPayloadPacket) packet;
                
                // Check if this is a BrandPayload packet
                if (customPacket.payload() instanceof BrandPayload) {
                    BrandPayload brandPayload = (BrandPayload) customPacket.payload();
                    
                    Connection self = (Connection) (Object) this;
                    
                    // Try to determine if this connection is for a Bedrock player
                    try {
                        if (self.getPacketListener() != null) {
                            String listenerClass = self.getPacketListener().getClass().getSimpleName();
                            
                            // Check if this is a game listener (after configuration) for a Bedrock player
                            if (listenerClass.contains("ServerGamePacketListenerImpl")) {
                                net.minecraft.server.network.ServerGamePacketListenerImpl gameListener = 
                                    (net.minecraft.server.network.ServerGamePacketListenerImpl) self.getPacketListener();
                                
                                if (gameListener.getPlayer() != null) {
                                    String playerName = gameListener.getPlayer().getGameProfile().getName();
                                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                                    
                                    if (isBedrockPlayer) {
                                        LOGGER.info("BrandPayloadMixin: Suppressing BrandPayload[brand={}] for Bedrock player: {} to prevent pipeline error", 
                                            brandPayload.brand(), playerName);
                                        ci.cancel();
                                        return;
                                    }
                                }
                            } else if (listenerClass.contains("ServerConfigurationPacketListenerImpl")) {
                                // Also check during configuration phase
                                net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener = 
                                    (net.minecraft.server.network.ServerConfigurationPacketListenerImpl) self.getPacketListener();
                                
                                if (configListener.getOwner() != null) {
                                    boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                                    
                                    if (isBedrockPlayer) {
                                        LOGGER.info("BrandPayloadMixin: Suppressing BrandPayload[brand={}] during config for Bedrock player: {} to prevent pipeline error", 
                                            brandPayload.brand(), configListener.getOwner().getName());
                                        ci.cancel();
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception detectionException) {
                        LOGGER.debug("BrandPayloadMixin: Could not determine if player is Bedrock: {}", 
                            detectionException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BrandPayloadMixin: Exception in brand payload suppression: {}", e.getMessage());
        }
    }
}
