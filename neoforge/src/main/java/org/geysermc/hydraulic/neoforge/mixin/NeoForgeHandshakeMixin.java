package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin specifically targets NeoForge's handshake process to prevent
 * Bedrock players from being disconnected due to missing NeoForge client.
 */
@Mixin(targets = "net.neoforged.neoforge.network.HandshakeHandler", remap = false)
public class NeoForgeHandshakeMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeHandshakeMixin");

    /**
     * Intercepts the handshake process to bypass NeoForge client checks for Bedrock players.
     */
    @Inject(
        method = "handleServerConfigurationPacketListenerImpl",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void bypassHandshakeForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        try {
            if (listener != null && listener.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(listener);
                
                if (isBedrockPlayer) {
                    LOGGER.info("NeoForgeHandshakeMixin: Bypassing NeoForge handshake for Bedrock player: {}", 
                        listener.getOwner().getName());
                    ci.cancel(); // Skip the handshake entirely for Bedrock players
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeHandshakeMixin: Exception in handshake mixin: {}", e.getMessage());
        }
    }
}
