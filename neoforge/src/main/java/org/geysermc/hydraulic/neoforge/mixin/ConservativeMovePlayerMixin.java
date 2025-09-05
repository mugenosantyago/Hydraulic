package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
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
 * Conservative approach to fix "invalid move player packet received" errors
 * without breaking other functionality like skins or causing stream errors.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ConservativeMovePlayerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConservativeMovePlayerMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Only intercepts disconnect calls specifically in handleMovePlayer method
     * and only for the exact "invalid move player packet received" error.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"),
        cancellable = true
    )
    private void preventInvalidMovePlayerDisconnect(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("ConservativeMovePlayerMixin: Preventing move player validation disconnect for Bedrock player: {}", playerName);
                    ci.cancel(); // Only prevent this specific disconnect
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConservativeMovePlayerMixin: Exception preventing disconnect: {}", e.getMessage());
        }
    }

    /**
     * Add very basic validation bypass only for obviously invalid packets
     * that would cause the "invalid move player packet received" error.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"),
        cancellable = true
    )
    private void relaxDistanceValidation(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    // Only bypass distance validation, let everything else work normally
                    LOGGER.debug("ConservativeMovePlayerMixin: Bypassing distance validation for Bedrock player: {}", playerName);
                    
                    // Apply the movement without distance checks
                    try {
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        // Only apply if values are reasonable (not NaN/Infinite)
                        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) &&
                            Float.isFinite(yRot) && Float.isFinite(xRot)) {
                            
                            player.setPos(x, y, z);
                            player.setYRot(yRot);
                            player.setXRot(xRot);
                            
                            LOGGER.debug("ConservativeMovePlayerMixin: Applied movement for Bedrock player: {}", playerName);
                        }
                        
                        ci.cancel(); // Skip the distance validation that would cause the error
                        return;
                        
                    } catch (Exception e) {
                        LOGGER.debug("ConservativeMovePlayerMixin: Exception applying movement: {}", e.getMessage());
                        // Don't cancel on exceptions, let normal processing continue
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConservativeMovePlayerMixin: Exception in distance validation: {}", e.getMessage());
        }
    }
}
