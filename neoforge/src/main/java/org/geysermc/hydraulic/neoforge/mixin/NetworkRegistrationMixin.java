package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin targets NeoForge's network registration to prevent Bedrock players
 * from being disconnected during the network negotiation phase.
 */
@Mixin(targets = "net.neoforged.neoforge.network.registration.NetworkRegistry", remap = false)
public class NetworkRegistrationMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NetworkRegistrationMixin");

    /**
     * Intercepts network registration to bypass checks for Bedrock players.
     */
    @Inject(
        method = "onConfigureClient",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void bypassNetworkRegistrationForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        try {
            if (listener != null && listener.getOwner() != null) {
                boolean isBedrockPlayer = false;
                try {
                    isBedrockPlayer = GeyserApi.api() != null && GeyserApi.api().isBedrockPlayer(listener.getOwner().getId());
                } catch (Exception geyserException) {
                    // If Geyser check fails, assume it's a Java player and continue normally
                    return;
                }
                
                if (isBedrockPlayer) {
                    LOGGER.info("NetworkRegistrationMixin: Bypassing NeoForge network registration for Bedrock player: {}", 
                        listener.getOwner().getName());
                    ci.cancel(); // Skip network registration for Bedrock players
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NetworkRegistrationMixin: Exception in network registration mixin: {}", e.getMessage());
        }
    }
}
