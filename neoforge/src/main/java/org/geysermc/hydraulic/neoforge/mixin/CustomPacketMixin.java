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
        method = "send(Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventCustomPacketsForBedrock(net.minecraft.network.protocol.Packet<?> packet, CallbackInfo ci) {
        try {
            ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
            
            if (packet != null) {
                String packetType = packet.getClass().getName();
                
                // Check if this is a custom packet (not vanilla Minecraft)
                if (packetType.contains("good_nights_sleep") || packetType.contains("custom") || 
                    (!packetType.startsWith("net.minecraft.network.protocol.game") && 
                     !packetType.startsWith("net.minecraft.network.protocol.common") &&
                     !packetType.startsWith("net.minecraft.network.protocol.configuration"))) {
                    
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
                        LOGGER.info("CustomPacketMixin: Preventing custom packet {} from being sent to Bedrock player: {}", 
                            packetType, playerName);
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("CustomPacketMixin: Exception in custom packet prevention: {}", e.getMessage());
        }
    }
}
