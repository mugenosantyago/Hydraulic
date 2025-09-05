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
                    String reasonText = reason != null ? reason.getString().toLowerCase() : "";
                    
                    // ONLY prevent disconnects that are specifically about invalid move player packets
                    if (reasonText.contains("invalid") && 
                        (reasonText.contains("move") || reasonText.contains("player")) &&
                        reasonText.contains("packet")) {
                        
                        LOGGER.info("ConservativeMovePlayerMixin: Prevented 'invalid move player packet' disconnect for Bedrock player: {}", playerName);
                        ci.cancel(); // Prevent ONLY this specific disconnect
                        return;
                    }
                    
                    // Also catch the specific multiplayer disconnect message
                    if (reasonText.contains("multiplayer.disconnect.invalid_player_movement")) {
                        LOGGER.info("ConservativeMovePlayerMixin: Prevented invalid player movement disconnect for Bedrock player: {}", playerName);
                        ci.cancel();
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
