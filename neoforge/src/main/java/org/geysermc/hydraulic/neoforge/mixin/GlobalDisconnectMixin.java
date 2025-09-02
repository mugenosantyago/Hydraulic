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
 * This mixin targets the ServerGamePacketListenerImpl to catch disconnects that happen during gameplay.
 */
@Mixin(value = ServerGamePacketListenerImpl.class)
public class GlobalDisconnectMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("GlobalDisconnectMixin");

    @Shadow
    public ServerPlayer player;

    /**
     * Intercepts disconnect calls at the game packet listener level.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventGameDisconnectForBedrock(Component reason, CallbackInfo ci) {
        try {
            if (reason != null && this.player != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is the NeoForge version check disconnect message
                if (disconnectMessage.contains("trying to connect to a server that is running NeoForge") ||
                    disconnectMessage.contains("Please install NeoForge")) {
                    
                    // Check if this is a Bedrock player using the player name (Floodgate naming convention)
                    String playerName = this.player.getGameProfile().getName();
                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("GlobalDisconnectMixin: Preventing NeoForge game-level disconnect for Bedrock player: {} (Message: {})", 
                            playerName, disconnectMessage);
                        ci.cancel(); // Prevent the disconnect
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GlobalDisconnectMixin: Exception in game disconnect prevention: {}", e.getMessage());
        }
    }
}
