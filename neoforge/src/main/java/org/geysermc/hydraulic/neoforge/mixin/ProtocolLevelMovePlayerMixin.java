package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
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
 * This mixin intercepts move player packet processing at the protocol level
 * to completely prevent "invalid move player packet received" errors for Bedrock players.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ProtocolLevelMovePlayerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ProtocolLevelMovePlayerMixin");
    
    @Shadow
    public ServerPlayer player;
    
    @Shadow
    public Connection connection;

    /**
     * Intercepts the actual packet processing and completely bypasses it for problematic Bedrock packets.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void interceptMovePlayerAtProtocolLevel(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("ProtocolLevelMovePlayerMixin: Intercepting move player packet for Bedrock player: {}", playerName);
                    
                    // Completely custom handling for Bedrock players to avoid any validation issues
                    try {
                        // Extract position and rotation data safely
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        LOGGER.debug("ProtocolLevelMovePlayerMixin: Raw packet data for {}: pos=({}, {}, {}) rot=({}, {})", 
                            playerName, x, y, z, yRot, xRot);
                        
                        // Validate the data is reasonable
                        boolean validPosition = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) &&
                                              Math.abs(x) < 30000000 && Math.abs(z) < 30000000 && y > -2048 && y < 2048;
                        boolean validRotation = Float.isFinite(yRot) && Float.isFinite(xRot);
                        
                        if (validPosition && validRotation) {
                            // Apply the movement directly, bypassing ALL validation
                            player.setPos(x, y, z);
                            player.setYRot(yRot);
                            player.setXRot(xRot);
                            
                            // Update the player's last position to prevent future validation errors
                            player.xOld = x;
                            player.yOld = y;
                            player.zOld = z;
                            player.yRotO = yRot;
                            player.xRotO = xRot;
                            
                            LOGGER.info("ProtocolLevelMovePlayerMixin: Successfully applied movement for Bedrock player: {}", playerName);
                        } else {
                            LOGGER.warn("ProtocolLevelMovePlayerMixin: Invalid packet data for Bedrock player {} - ignoring (pos valid: {}, rot valid: {})", 
                                playerName, validPosition, validRotation);
                        }
                        
                        // Always cancel the original processing to prevent validation errors
                        ci.cancel();
                        return;
                        
                    } catch (Exception e) {
                        LOGGER.error("ProtocolLevelMovePlayerMixin: Exception handling Bedrock move packet for {}: {}", 
                            playerName, e.getMessage(), e);
                        // Cancel even on exceptions to prevent crashes
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("ProtocolLevelMovePlayerMixin: Critical exception in move player interception: {}", e.getMessage(), e);
        }
    }

    /**
     * Redirects connection.disconnect() calls to prevent "invalid move player packet received" disconnects.
     */
    @Redirect(
        method = "handleMovePlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/chat/Component;)V")
    )
    private void preventMovePlayerDisconnect(Connection connection, Component reason) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    String reasonText = reason != null ? reason.getString() : "unknown";
                    LOGGER.info("ProtocolLevelMovePlayerMixin: BLOCKED disconnect attempt for Bedrock player {}: {}", 
                        playerName, reasonText);
                    // Don't call the original disconnect method for Bedrock players
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ProtocolLevelMovePlayerMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
        
        // For Java players, allow normal disconnects
        connection.disconnect(reason);
    }

    /**
     * Intercepts any attempt to send disconnect packets related to movement validation.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventAllDisconnectsForBedrock(Component reason, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    String reasonText = reason != null ? reason.getString().toLowerCase() : "unknown";
                    
                    // Be extremely aggressive - prevent ALL disconnects for Bedrock players
                    // that could be related to movement or packet validation
                    if (reasonText.contains("invalid") || 
                        reasonText.contains("move") || 
                        reasonText.contains("player") ||
                        reasonText.contains("packet") ||
                        reasonText.contains("movement") || 
                        reasonText.contains("position") ||
                        reasonText.contains("flying") ||
                        reasonText.contains("speed") ||
                        reasonText.contains("validation") ||
                        reasonText.contains("received") ||
                        reasonText.equals("disconnected")) {
                        
                        LOGGER.info("ProtocolLevelMovePlayerMixin: AGGRESSIVELY preventing disconnect for Bedrock player {}: {}", 
                            playerName, reasonText);
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ProtocolLevelMovePlayerMixin: Exception in aggressive disconnect prevention: {}", e.getMessage());
        }
    }
}
