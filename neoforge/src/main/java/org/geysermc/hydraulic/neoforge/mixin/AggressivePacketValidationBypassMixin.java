package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin provides aggressive packet validation bypass for Bedrock players
 * to prevent any "invalid packet" disconnections that might still be occurring.
 * 
 * This is a comprehensive safety net to catch any remaining validation issues
 * that other mixins might have missed.
 */
@Mixin(value = {ServerCommonPacketListenerImpl.class, ServerGamePacketListenerImpl.class}, priority = 3000)
public class AggressivePacketValidationBypassMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AggressivePacketValidationBypassMixin");
    
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
            if (reason == null) return;
            
            String reasonText = reason.getString().toLowerCase();
            boolean isBedrockPlayer = false;
            String playerName = "unknown";
            
            // Try to get player info from different listener types
            if (this instanceof ServerGamePacketListenerImpl gameListener) {
                if (gameListener.player != null) {
                    playerName = gameListener.player.getGameProfile().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            } else if (this instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                if (configListener.getOwner() != null) {
                    playerName = configListener.getOwner().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            }
            
            if (isBedrockPlayer) {
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
                    reasonText.contains("server") && reasonText.contains("stop");
                
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
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("AggressivePacketValidationBypassMixin: Exception in disconnect prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Intercepts packet handling errors and prevents disconnects for Bedrock players.
     */
    @Inject(
        method = "onPacketError",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void preventPacketErrorDisconnects(Packet<?> packet, Exception exception, CallbackInfo ci) {
        try {
            boolean isBedrockPlayer = false;
            String playerName = "unknown";
            
            // Try to get player info
            if (this instanceof ServerGamePacketListenerImpl gameListener) {
                if (gameListener.player != null) {
                    playerName = gameListener.player.getGameProfile().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            } else if (this instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                if (configListener.getOwner() != null) {
                    playerName = configListener.getOwner().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            }
            
            if (isBedrockPlayer) {
                LOGGER.warn("AggressivePacketValidationBypassMixin: Preventing packet error disconnect for Bedrock player {}: {} (packet: {})", 
                    playerName, exception.getMessage(), packet != null ? packet.getClass().getSimpleName() : "null");
                ci.cancel(); // Prevent the error handling that leads to disconnect
            }
            
        } catch (Exception e) {
            LOGGER.debug("AggressivePacketValidationBypassMixin: Exception in packet error prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Intercepts any validation methods that might cause disconnects.
     */
    @Inject(
        method = "*",
        at = @At(value = "INVOKE", target = "disconnect"),
        cancellable = true,
        require = 0
    )
    private void interceptValidationDisconnects(CallbackInfo ci) {
        try {
            boolean isBedrockPlayer = false;
            String playerName = "unknown";
            
            // Try to get player info
            if (this instanceof ServerGamePacketListenerImpl gameListener) {
                if (gameListener.player != null) {
                    playerName = gameListener.player.getGameProfile().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            } else if (this instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                if (configListener.getOwner() != null) {
                    playerName = configListener.getOwner().getName();
                    isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                }
            }
            
            if (isBedrockPlayer) {
                LOGGER.debug("AggressivePacketValidationBypassMixin: Intercepted potential validation disconnect for Bedrock player: {}", 
                    playerName);
                ci.cancel(); // Prevent the validation that leads to disconnect
            }
            
        } catch (Exception e) {
            LOGGER.debug("AggressivePacketValidationBypassMixin: Exception in validation interception: {}", e.getMessage());
        }
    }
}
