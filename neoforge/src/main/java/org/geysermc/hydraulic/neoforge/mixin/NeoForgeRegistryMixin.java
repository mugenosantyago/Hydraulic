package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents NeoForge from disconnecting Bedrock players due to missing client-side mods.
 * It only affects Bedrock players - Java players are completely unaffected.
 */
@Mixin(targets = "net.neoforged.neoforge.network.configuration.SyncConfig", remap = false)
public class NeoForgeRegistryMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeRegistryMixin");

    /**
     * Intercepts the SyncConfig.run method to bypass mod synchronization for Bedrock players only.
     * This prevents the "Please install NeoForge" disconnect message for Bedrock clients.
     */
    @Inject(
        method = "run",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0  // Make this injection optional to avoid issues if the target changes
    )
    private void preventSyncConfigForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        try {
            // Safely check if this is a Bedrock player
            if (listener != null && listener.getOwner() != null) {
                // Use a defensive approach - only bypass if we're certain it's a Bedrock player
                boolean isBedrockPlayer = false;
                try {
                    isBedrockPlayer = GeyserApi.api() != null && GeyserApi.api().isBedrockPlayer(listener.getOwner().getId());
                } catch (Exception geyserException) {
                    // If Geyser check fails, assume it's a Java player and continue normally
                    LOGGER.debug("NeoForgeRegistryMixin: Could not check if player is Bedrock, assuming Java player");
                    return;
                }
                
                if (isBedrockPlayer) {
                    LOGGER.info("NeoForgeRegistryMixin: Bypassing NeoForge config sync for Bedrock player: {}", listener.getOwner().getName());
                    // Complete the task without running the sync
                    listener.finishCurrentTask(net.neoforged.neoforge.network.configuration.SyncConfig.TYPE);
                    ci.cancel();
                }
                // For Java players, we do nothing and let the normal flow continue
            }
        } catch (Exception e) {
            // If there's any error, just let the normal flow continue for safety
            LOGGER.debug("NeoForgeRegistryMixin: Exception in mixin, allowing normal config sync: {}", e.getMessage());
            // Explicitly do NOT cancel - let normal flow continue
        }
    }
}
