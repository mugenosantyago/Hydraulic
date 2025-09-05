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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin handles move player packet validation for Bedrock players.
 * Bedrock players sometimes send move packets that don't pass Java Edition's validation,
 * causing them to be disconnected with "Invalid move player packet received".
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MovePlayerPacketMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MovePlayerPacketMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Intercepts move player packet handling to provide more lenient validation for Bedrock players.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void handleBedrockMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.debug("MovePlayerPacketMixin: Processing move packet for Bedrock player: {}", playerName);
                    
                    // For Bedrock players, we need to be more lenient with movement validation
                    // Check for obviously invalid values that would cause issues
                    boolean hasValidPosition = true;
                    boolean hasValidRotation = true;
                    
                    // Check if packet contains position data
                    try {
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        
                        // Check for NaN or infinite values
                        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
                            Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
                            hasValidPosition = false;
                            LOGGER.debug("MovePlayerPacketMixin: Invalid position values for Bedrock player {}: x={}, y={}, z={}", 
                                playerName, x, y, z);
                        }
                        
                        // Check for extremely large movement (possible teleport hack, but be very lenient for Bedrock)
                        double deltaX = Math.abs(x - player.getX());
                        double deltaY = Math.abs(y - player.getY());
                        double deltaZ = Math.abs(z - player.getZ());
                        double maxDelta = 1000.0; // Very lenient for Bedrock players - allow teleports and Geyser quirks
                        
                        if (deltaX > maxDelta || deltaY > maxDelta || deltaZ > maxDelta) {
                            LOGGER.debug("MovePlayerPacketMixin: Very large movement detected for Bedrock player {}: dx={}, dy={}, dz={}", 
                                playerName, deltaX, deltaY, deltaZ);
                            // Still don't reject - just log for debugging
                        }
                    } catch (Exception e) {
                        // If we can't get position data, assume it's not a position packet
                        LOGGER.debug("MovePlayerPacketMixin: No position data in packet for {}", playerName);
                    }
                    
                    // Check if packet contains rotation data
                    try {
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        // Check for NaN or infinite rotation values
                        if (Float.isNaN(yRot) || Float.isNaN(xRot) ||
                            Float.isInfinite(yRot) || Float.isInfinite(xRot)) {
                            hasValidRotation = false;
                            LOGGER.debug("MovePlayerPacketMixin: Invalid rotation values for Bedrock player {}: yRot={}, xRot={}", 
                                playerName, yRot, xRot);
                        }
                    } catch (Exception e) {
                        // If we can't get rotation data, assume it's not a rotation packet
                        LOGGER.debug("MovePlayerPacketMixin: No rotation data in packet for {}", playerName);
                    }
                    
                    // If the packet has invalid values, ignore it instead of disconnecting
                    if (!hasValidPosition || !hasValidRotation) {
                        LOGGER.info("MovePlayerPacketMixin: Ignoring invalid move packet for Bedrock player {} to prevent disconnect", 
                            playerName);
                        ci.cancel(); // Cancel processing this packet
                        return;
                    }
                    
                    // For valid packets from Bedrock players, let them through with debug logging
                    LOGGER.debug("MovePlayerPacketMixin: Processing valid move packet for Bedrock player {}", playerName);
                } else {
                    // For non-Bedrock players, add some debug logging too
                    LOGGER.debug("MovePlayerPacketMixin: Processing move packet for Java player: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerPacketMixin: Exception in move player packet handling: {}", e.getMessage());
            // Don't cancel on exceptions, let the original method handle it
        }
        
        // Continue with normal processing if we haven't cancelled
    }
    
    /**
     * Additional injection to catch and handle move player validation errors that might still occur.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"),
        cancellable = true
    )
    private void preventMovePlayerDisconnect(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("MovePlayerPacketMixin: Preventing disconnect for Bedrock player {} due to move player validation", 
                        playerName);
                    ci.cancel(); // Prevent the disconnect
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerPacketMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Broader disconnect prevention that catches all disconnect attempts in the game packet listener.
     * This is a safety net to catch any move player related disconnects we might have missed.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventInvalidMovePlayerDisconnect(net.minecraft.network.chat.Component reason, CallbackInfo ci) {
        try {
            if (player != null && reason != null) {
                String disconnectMessage = reason.getString();
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                // Specifically check for the "Invalid move player packet received" message
                if (isBedrockPlayer && 
                    (disconnectMessage.contains("Invalid move player packet received") ||
                     disconnectMessage.contains("multiplayer.disconnect.invalid_player_movement"))) {
                    
                    LOGGER.info("MovePlayerPacketMixin: Preventing 'Invalid move player packet received' disconnect for Bedrock player: {}", 
                        playerName);
                    ci.cancel(); // Prevent the disconnect
                    return;
                }
                
                // Also catch any other movement-related disconnects
                if (isBedrockPlayer && 
                    (disconnectMessage.toLowerCase().contains("move") || 
                     disconnectMessage.toLowerCase().contains("position") ||
                     disconnectMessage.toLowerCase().contains("movement"))) {
                    
                    LOGGER.info("MovePlayerPacketMixin: Preventing movement-related disconnect for Bedrock player: {} (Message: {})", 
                        playerName, disconnectMessage);
                    ci.cancel(); // Prevent the disconnect
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerPacketMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
}
