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
     * Only filter out obviously invalid packets that would cause crashes.
     * Let normal validation work for most packets.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void filterInvalidPackets(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    // Only filter out packets with NaN/Infinite values that would cause crashes
                    try {
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        // Only cancel if values are NaN or infinite (which would crash the server)
                        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
                            Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z) ||
                            Float.isNaN(yRot) || Float.isNaN(xRot) ||
                            Float.isInfinite(yRot) || Float.isInfinite(xRot)) {
                            
                            LOGGER.debug("MovePlayerPacketMixin: Filtering invalid packet with NaN/Infinite values for Bedrock player: {}", playerName);
                            ci.cancel(); // Only cancel truly invalid packets
                            return;
                        }
                        
                        // Let all other packets through normal processing
                        LOGGER.debug("MovePlayerPacketMixin: Allowing move packet for Bedrock player: {}", playerName);
                        
                    } catch (Exception e) {
                        LOGGER.debug("MovePlayerPacketMixin: Exception checking packet validity: {}", e.getMessage());
                        // Don't cancel on exceptions, let normal processing handle it
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerPacketMixin: Exception in packet filtering: {}", e.getMessage());
        }
        
        // Continue with normal processing
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
     * Intercepts all disconnect calls in ServerGamePacketListenerImpl to prevent Bedrock players 
     * from being disconnected due to move player validation issues.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventBedrockDisconnect(net.minecraft.network.chat.Component reason, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    String reasonText = reason != null ? reason.getString() : "unknown";
                    
                    // Be very aggressive about preventing disconnects for Bedrock players
                    if (reasonText.toLowerCase().contains("invalid") || 
                        reasonText.toLowerCase().contains("move") || 
                        reasonText.toLowerCase().contains("player") ||
                        reasonText.toLowerCase().contains("movement") || 
                        reasonText.toLowerCase().contains("position") ||
                        reasonText.toLowerCase().contains("teleport") ||
                        reasonText.toLowerCase().contains("packet") ||
                        reasonText.toLowerCase().contains("flying") ||
                        reasonText.toLowerCase().contains("speed") ||
                        reasonText.toLowerCase().contains("hack") ||
                        reasonText.toLowerCase().contains("cheat") ||
                        reasonText.toLowerCase().contains("validation") ||
                        reasonText.equals("disconnected")) { // Generic disconnect
                        
                        LOGGER.info("MovePlayerPacketMixin: AGGRESSIVELY preventing disconnect for Bedrock player {}: {}", 
                            playerName, reasonText);
                        ci.cancel(); // Prevent the disconnect
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerPacketMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
}
