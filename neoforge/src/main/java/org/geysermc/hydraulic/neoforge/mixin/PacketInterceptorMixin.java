package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
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
 * Intercepts all packets to see what's happening before handleMovePlayer is called.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class PacketInterceptorMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("PacketInterceptorMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Intercept all packet handling to see what packets are being processed.
     */
    @Inject(
        method = "handlePacket",
        at = @At("HEAD"),
        require = 0
    )
    private void interceptAllPackets(Packet<?> packet, CallbackInfo ci) {
        try {
            if (player != null && packet instanceof ServerboundMovePlayerPacket) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("PacketInterceptorMixin: Intercepted ServerboundMovePlayerPacket for Bedrock player: {} (packet: {})", 
                        playerName, packet.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("PacketInterceptorMixin: Exception in packet interception: {}", e.getMessage());
        }
    }
}
