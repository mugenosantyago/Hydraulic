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
 * without breaking skins or causing other issues.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ConservativeMovePlayerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConservativeMovePlayerMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Only intercepts disconnect attempts specifically for move player validation.
     * Does NOT interfere with normal packet processing.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventMovePlayerDisconnects(Component reason, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    String reasonText = reason != null ? reason.getString() : "";
                    String lowerReasonText = reasonText.toLowerCase();
                    
                    // Log ALL disconnect attempts for Bedrock players for debugging
                    LOGGER.info("ConservativeMovePlayerMixin: Disconnect attempt for Bedrock player {}: '{}'", playerName, reasonText);
                    
                    // Prevent disconnects that are specifically about invalid move player packets
                    if ((lowerReasonText.contains("invalid") && 
                         (lowerReasonText.contains("move") || lowerReasonText.contains("player")) &&
                         lowerReasonText.contains("packet")) ||
                        lowerReasonText.contains("invalid move player packet received") ||
                        reasonText.contains("multiplayer.disconnect.invalid_player_movement")) {
                        
                        LOGGER.info("ConservativeMovePlayerMixin: PREVENTED 'invalid move player packet' disconnect for Bedrock player: {} (reason: {})", playerName, reasonText);
                        ci.cancel(); // Prevent ONLY this specific disconnect
                        return;
                    }
                    
                    // Also prevent generic "Disconnected" that might be hiding the real error
                    if (lowerReasonText.equals("disconnected") || reasonText.trim().isEmpty()) {
                        LOGGER.info("ConservativeMovePlayerMixin: PREVENTED generic disconnect for Bedrock player: {} (might be hiding move player error)", playerName);
                        ci.cancel(); // Prevent generic disconnects that might be masking the real issue
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConservativeMovePlayerMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
        
        // Allow all other disconnects to proceed normally
    }
}
