package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents custom mod packets from being sent to Bedrock players,
 * which would cause networking errors since Bedrock clients don't support mod packets.
 */
@Mixin(value = ServerCommonPacketListenerImpl.class)
public class CustomPacketMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomPacketMixin");

    /**
     * Prevents custom packets from being sent to Bedrock players.
     */
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventCustomPacketsForBedrock(CustomPacketPayload payload, CallbackInfo ci) {
        try {
            ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
            
            if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                
                if (isBedrockPlayer && payload != null) {
                    String payloadType = payload.type().toString();
                    
                    // Skip custom mod packets for Bedrock players
                    if (!payloadType.startsWith("minecraft:")) {
                        LOGGER.info("CustomPacketMixin: Preventing custom packet {} from being sent to Bedrock player: {}", 
                            payloadType, configListener.getOwner().getName());
                        ci.cancel();
                        return;
                    }
                }
            } else if (self instanceof net.minecraft.server.network.ServerGamePacketListenerImpl gameListener) {
                // Also check for game phase
                if (gameListener.player != null && payload != null) {
                    String playerName = gameListener.player.getGameProfile().getName();
                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    
                    if (isBedrockPlayer) {
                        String payloadType = payload.type().toString();
                        
                        // Skip custom mod packets for Bedrock players
                        if (!payloadType.startsWith("minecraft:")) {
                            LOGGER.info("CustomPacketMixin: Preventing custom packet {} from being sent to Bedrock player: {}", 
                                payloadType, playerName);
                            ci.cancel();
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("CustomPacketMixin: Exception in custom packet prevention: {}", e.getMessage());
        }
    }
}
