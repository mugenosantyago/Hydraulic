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
 * This mixin targets NeoForge's networking system to prevent version negotiations
 * that would disconnect Bedrock players.
 */
@Mixin(targets = "net.neoforged.neoforge.network.NetworkInitialization", remap = false)
public class NeoForgeNetworkingMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeNetworkingMixin");

    /**
     * Bypasses NeoForge network initialization for Bedrock players.
     */
    @Inject(
        method = "handleClientConfigurationPacketListenerImpl",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void bypassNetworkInitForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        try {
            if (listener != null && listener.getOwner() != null) {
                boolean isBedrockPlayer = false;
                try {
                    isBedrockPlayer = GeyserApi.api() != null && GeyserApi.api().isBedrockPlayer(listener.getOwner().getId());
                } catch (Exception geyserException) {
                    return;
                }
                
                if (isBedrockPlayer) {
                    LOGGER.info("NeoForgeNetworkingMixin: Bypassing NeoForge network initialization for Bedrock player: {}", 
                        listener.getOwner().getName());
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeNetworkingMixin: Exception in network init bypass: {}", e.getMessage());
        }
    }

    /**
     * Alternative method targeting - some versions might use different method names.
     */
    @Inject(
        method = "onConfigurationStart", 
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void bypassConfigurationStartForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        try {
            if (listener != null && listener.getOwner() != null) {
                boolean isBedrockPlayer = false;
                try {
                    isBedrockPlayer = GeyserApi.api() != null && GeyserApi.api().isBedrockPlayer(listener.getOwner().getId());
                } catch (Exception geyserException) {
                    return;
                }
                
                if (isBedrockPlayer) {
                    LOGGER.info("NeoForgeNetworkingMixin: Bypassing NeoForge configuration start for Bedrock player: {}", 
                        listener.getOwner().getName());
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeNetworkingMixin: Exception in configuration start bypass: {}", e.getMessage());
        }
    }
}
