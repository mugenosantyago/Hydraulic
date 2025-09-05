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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin provides aggressive bypass of move player validation for Bedrock players
 * to prevent "invalid move player packet received" disconnects.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MovePlayerValidationBypassMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MovePlayerValidationBypassMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Intercepts ALL disconnect calls and prevents them if they're related to movement validation for Bedrock players.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventAllMovementDisconnects(Component reason, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    String reasonText = reason != null ? reason.getString().toLowerCase() : "unknown";
                    
                    LOGGER.info("MovePlayerValidationBypassMixin: Disconnect attempt for Bedrock player {}: {}", 
                        playerName, reasonText);
                    
                    // Prevent ALL disconnects for Bedrock players that might be movement-related
                    if (reasonText.contains("invalid") || 
                        reasonText.contains("move") || 
                        reasonText.contains("player") ||
                        reasonText.contains("movement") ||
                        reasonText.contains("packet") ||
                        reasonText.contains("position") ||
                        reasonText.contains("teleport") ||
                        reasonText.contains("flying") ||
                        reasonText.contains("speed") ||
                        reasonText.contains("hack") ||
                        reasonText.contains("cheat") ||
                        reasonText.contains("validation") ||
                        reasonText.equals("disconnected")) { // Generic disconnect
                        
                        LOGGER.info("MovePlayerValidationBypassMixin: PREVENTING disconnect for Bedrock player {}: {}", 
                            playerName, reasonText);
                        ci.cancel(); // Prevent the disconnect
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerValidationBypassMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }

    /**
     * Redirects the distance check that often causes "invalid move player packet received" errors.
     */
    @Redirect(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D")
    )
    private double allowLargerDistanceForBedrock(ServerPlayer player, double x, double y, double z) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    // For Bedrock players, always return a small distance to pass validation
                    LOGGER.debug("MovePlayerValidationBypassMixin: Bypassing distance check for Bedrock player: {}", playerName);
                    return 0.1; // Always pass distance validation
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerValidationBypassMixin: Exception in distance check bypass: {}", e.getMessage());
        }
        
        // For Java players, use the normal distance calculation
        return player.distanceToSqr(x, y, z);
    }

    /**
     * Bypasses the "too far" movement check that causes many invalid move player packet errors.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"),
        cancellable = true
    )
    private void bypassTooFarCheck(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.debug("MovePlayerValidationBypassMixin: Bypassing 'too far' movement check for Bedrock player: {}", playerName);
                    
                    // Apply the movement directly without validation
                    try {
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        // Only apply if values are finite
                        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) &&
                            Float.isFinite(yRot) && Float.isFinite(xRot)) {
                            
                            player.setPos(x, y, z);
                            player.setYRot(yRot);
                            player.setXRot(xRot);
                            
                            LOGGER.debug("MovePlayerValidationBypassMixin: Applied movement for Bedrock player {} without validation", playerName);
                            ci.cancel(); // Skip the original validation
                            return;
                        }
                    } catch (Exception e) {
                        LOGGER.debug("MovePlayerValidationBypassMixin: Exception applying movement: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerValidationBypassMixin: Exception in too far check bypass: {}", e.getMessage());
        }
    }
}
