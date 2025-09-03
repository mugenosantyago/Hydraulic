package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
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
 * This mixin specifically targets the "Incompatible client! Please use NeoForge" disconnect
 * that happens after players join the game.
 */
@Mixin(value = ServerGamePacketListenerImpl.class)
public class IncompatibleClientMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("IncompatibleClientMixin");

    @Shadow
    public ServerPlayer player;

    /**
     * Prevents the "Incompatible client" disconnect for Bedrock players.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventIncompatibleClientDisconnect(Component reason, CallbackInfo ci) {
        try {
            if (reason != null && this.player != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is the "Incompatible client" disconnect message
                if (disconnectMessage.contains("Incompatible client") && 
                    (disconnectMessage.contains("Please use NeoForge") || disconnectMessage.contains("Please install NeoForge"))) {
                    
                    String playerName = this.player.getGameProfile().getName();
                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("IncompatibleClientMixin: Preventing 'Incompatible client' disconnect for Bedrock player: {} (Message: {})", 
                            playerName, disconnectMessage);
                        ci.cancel(); // Prevent the disconnect
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("IncompatibleClientMixin: Exception in incompatible client prevention: {}", e.getMessage());
        }
    }
}
