package org.geysermc.hydraulic.neoforge.mixin;

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
 * This mixin provides aggressive packet validation bypass for Bedrock players
 * to prevent any "invalid packet" disconnections that might still be occurring.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 3000)
public class AggressivePacketValidationBypassMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AggressivePacketValidationBypassMixin");
    
    @Shadow
    public ServerPlayer player;
    
    /**
     * Intercepts any disconnect calls and prevents them for Bedrock players
     * unless they're legitimate disconnects (not validation related).
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventValidationDisconnects(net.minecraft.network.chat.Component reason, CallbackInfo ci) {
        try {
            if (reason == null || player == null) return;
            
            String playerName = player.getGameProfile().getName();
            boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
            
            if (isBedrockPlayer) {
                String reasonText = reason.getString().toLowerCase();
                
                // List of validation-related disconnect reasons to prevent
                boolean isValidationDisconnect = 
                    reasonText.contains("invalid") ||
                    reasonText.contains("packet") ||
                    reasonText.contains("protocol") ||
                    reasonText.contains("unexpected") ||
                    reasonText.contains("malformed") ||
                    reasonText.contains("illegal") ||
                    reasonText.contains("move") ||
                    reasonText.contains("position") ||
                    reasonText.contains("teleport") ||
                    reasonText.contains("validation") ||
                    reasonText.contains("error") ||
                    reasonText.contains("exception");
                
                // Allow legitimate disconnects (user leaving, server shutdown, etc.)
                boolean isLegitimateDisconnect = 
                    reasonText.contains("disconnect") ||
                    reasonText.contains("quit") ||
                    reasonText.contains("left") ||
                    reasonText.contains("timeout") ||
                    reasonText.contains("connection") ||
                    (reasonText.contains("server") && reasonText.contains("stop"));
                
                if (isValidationDisconnect && !isLegitimateDisconnect) {
                    LOGGER.warn("AggressivePacketValidationBypassMixin: Preventing validation disconnect for Bedrock player {}: {}", 
                        playerName, reason.getString());
                    ci.cancel(); // Prevent the disconnect
                    return;
                }
                
                // Log legitimate disconnects for debugging
                if (isLegitimateDisconnect) {
                    LOGGER.info("AggressivePacketValidationBypassMixin: Allowing legitimate disconnect for Bedrock player {}: {}", 
                        playerName, reason.getString());
                } else {
                    LOGGER.debug("AggressivePacketValidationBypassMixin: Unknown disconnect reason for Bedrock player {}: {}", 
                        playerName, reason.getString());
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("AggressivePacketValidationBypassMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
}