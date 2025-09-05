package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
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
 * This mixin specifically targets the teleport validation issue that causes
 * "Invalid move player packet received" disconnections for Bedrock players.
 * 
 * The issue occurs in handleAcceptTeleportPacket when the server expects a specific
 * teleport ID but the Bedrock client sends a different one, leading to a disconnect.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 4000)
public class TeleportValidationBypassMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TeleportValidationBypassMixin");
    
    @Shadow
    public ServerPlayer player;
    
    @Shadow
    private int awaitingTeleport;
    
    /**
     * Completely bypasses teleport validation for Bedrock players to prevent
     * the "Invalid move player packet received" disconnection.
     */
    @Inject(
        method = "handleAcceptTeleportPacket",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bypassTeleportValidation(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                LOGGER.info("TeleportValidationBypassMixin: Bypassing teleport validation for Bedrock player: {} (packet ID: {}, expected: {})", 
                    playerName, packet.getId(), this.awaitingTeleport);
                
                // For Bedrock players, always accept teleport packets and clear the awaiting state
                this.awaitingTeleport = 0;
                
                LOGGER.info("TeleportValidationBypassMixin: Teleport validation bypassed successfully for: {}", playerName);
                
                // Cancel the original method to prevent validation
                ci.cancel();
            }
        } catch (Exception e) {
            LOGGER.error("TeleportValidationBypassMixin: Exception in teleport validation bypass: {}", e.getMessage());
        }
    }
    
    /**
     * Additional safety net - intercept any Component.translatable calls that might
     * be creating the "Invalid move player packet received" message.
     */
    @Inject(
        method = "handleAcceptTeleportPacket",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
        cancellable = true
    )
    private void preventTeleportErrorMessage(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                LOGGER.warn("TeleportValidationBypassMixin: Preventing teleport error message creation for Bedrock player: {}", playerName);
                
                // Clear awaiting teleport and cancel the error
                this.awaitingTeleport = 0;
                ci.cancel();
            }
        } catch (Exception e) {
            LOGGER.debug("TeleportValidationBypassMixin: Exception preventing error message: {}", e.getMessage());
        }
    }
}
