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
 * Aggressive approach to prevent ALL disconnects for Bedrock players that happen
 * shortly after movement packets, as the "Invalid move player packet received" 
 * error might be getting masked as generic "Disconnected".
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class AggressiveMovePlayerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AggressiveMovePlayerMixin");
    
    @Shadow
    public ServerPlayer player;
    
    // Track when we last processed a move player packet for a Bedrock player
    private long lastMovePlayerPacketTime = 0;
    private boolean isBedrockPlayer = false;

    /**
     * Track move player packets from Bedrock players.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("HEAD")
    )
    private void trackMovePlayerPacket(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    lastMovePlayerPacketTime = System.currentTimeMillis();
                    LOGGER.debug("AggressiveMovePlayerMixin: Tracking move player packet for Bedrock player: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("AggressiveMovePlayerMixin: Exception tracking move player packet: {}", e.getMessage());
        }
    }

    /**
     * Aggressively prevent disconnects for Bedrock players that happen within 
     * 5 seconds of a move player packet (likely caused by move player validation).
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventSuspiciousDisconnects(Component reason, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrock = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrock) {
                    String reasonText = reason != null ? reason.getString() : "";
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastMovePacket = currentTime - lastMovePlayerPacketTime;
                    
                    LOGGER.info("AggressiveMovePlayerMixin: Disconnect attempt for Bedrock player {}: '{}' ({}ms since last move packet)", 
                        playerName, reasonText, timeSinceLastMovePacket);
                    
                    // Prevent disconnects that happen within 5 seconds of a move player packet
                    // These are likely caused by move player validation issues
                    if (timeSinceLastMovePacket < 5000) { // 5 seconds
                        LOGGER.info("AggressiveMovePlayerMixin: PREVENTED suspicious disconnect for Bedrock player {} within {}ms of move packet: '{}'", 
                            playerName, timeSinceLastMovePacket, reasonText);
                        ci.cancel();
                        return;
                    }
                    
                    // Also prevent specific error messages regardless of timing
                    String lowerReasonText = reasonText.toLowerCase();
                    if ((lowerReasonText.contains("invalid") && 
                         (lowerReasonText.contains("move") || lowerReasonText.contains("player")) &&
                         lowerReasonText.contains("packet")) ||
                        lowerReasonText.contains("invalid move player packet received") ||
                        reasonText.contains("multiplayer.disconnect.invalid_player_movement")) {
                        
                        LOGGER.info("AggressiveMovePlayerMixin: PREVENTED specific move player error for Bedrock player {}: '{}'", 
                            playerName, reasonText);
                        ci.cancel();
                        return;
                    }
                    
                    // Allow other disconnects after logging
                    LOGGER.info("AggressiveMovePlayerMixin: Allowing disconnect for Bedrock player {}: '{}'", 
                        playerName, reasonText);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("AggressiveMovePlayerMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
}
