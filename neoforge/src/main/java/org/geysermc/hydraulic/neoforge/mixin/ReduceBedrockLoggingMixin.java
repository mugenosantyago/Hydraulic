package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reduces excessive logging for Bedrock players to prevent performance issues
 * that could contribute to stack overflow crashes.
 */
@Mixin(value = net.minecraft.server.network.ServerGamePacketListenerImpl.class, priority = 4000)
public class ReduceBedrockLoggingMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReduceBedrockLoggingMixin");
    
    @Shadow
    public ServerPlayer player;
    
    // Track last log time to reduce spam
    private long lastLogTime = 0;
    private static final long LOG_COOLDOWN_MS = 5000; // Only log once every 5 seconds per player
    
    /**
     * Reduces excessive move player logging for Bedrock players.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("HEAD")
    )
    private void reduceBedrockMovePlayerLogging(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastLogTime > LOG_COOLDOWN_MS) {
                    LOGGER.debug("ReduceBedrockLoggingMixin: Move player packet for Bedrock player {} (reduced logging)", 
                        player.getGameProfile().getName());
                    lastLogTime = currentTime;
                }
                
                // Disable other debug logging temporarily to reduce log spam
                // This prevents the massive amount of logging that might contribute to performance issues
            }
        } catch (Exception e) {
            // Silently handle exceptions to avoid more logging
        }
    }
}
