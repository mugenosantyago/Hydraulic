package org.geysermc.hydraulic.neoforge.mixin;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin provides additional validation handling for move player packets to prevent
 * Bedrock players from being disconnected due to validation failures.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MovePlayerValidationMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MovePlayerValidationMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Redirects the validation check for move player packets to be more lenient for Bedrock players.
     */
    @Redirect(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z")
    )
    private boolean allowMovePlayerForBedrock(ServerPlayer player) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    // For Bedrock players, we're more lenient about dimension changes during movement
                    LOGGER.debug("MovePlayerValidationMixin: Allowing move player packet during dimension change for Bedrock player: {}", playerName);
                    return false; // Pretend they're not changing dimensions to allow movement
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerValidationMixin: Exception in dimension change check: {}", e.getMessage());
        }
        
        // For Java players, use the original logic
        return player != null && player.isChangingDimension();
    }

    /**
     * Intercepts validation failures to provide more lenient handling for Bedrock players.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isChangingDimension()Z", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void relaxMovementValidationForBedrock(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    // For Bedrock players, we apply more lenient movement validation
                    LOGGER.debug("MovePlayerValidationMixin: Applying lenient movement validation for Bedrock player: {}", playerName);
                    
                    try {
                        // Get the position and rotation data using the same approach as the original mixin
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        // Check for obviously invalid values that would cause crashes
                        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) &&
                            Float.isFinite(yRot) && Float.isFinite(xRot)) {
                            
                            // Apply the movement with minimal validation for Bedrock players
                            player.setPos(x, y, z);
                            player.setYRot(yRot);
                            player.setXRot(xRot);
                            
                            LOGGER.debug("MovePlayerValidationMixin: Applied lenient movement for Bedrock player {}", playerName);
                            ci.cancel(); // Skip the strict validation logic
                            return;
                        } else {
                            LOGGER.debug("MovePlayerValidationMixin: Ignoring invalid movement values for Bedrock player {}", playerName);
                            ci.cancel(); // Skip processing invalid packet
                            return;
                        }
                    } catch (Exception e) {
                        LOGGER.debug("MovePlayerValidationMixin: Exception applying lenient movement for Bedrock player {}: {}", playerName, e.getMessage());
                        // Fall back to original logic if our custom handling fails
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerValidationMixin: Exception in movement validation: {}", e.getMessage());
        }
    }
}
