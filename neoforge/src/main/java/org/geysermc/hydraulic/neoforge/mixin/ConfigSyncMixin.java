package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.configuration.SyncConfig;
import org.geysermc.geyser.api.GeyserApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SyncConfig.class, remap = false)
public class ConfigSyncMixin {

    /**
     * Prevents NeoForge config sync from running for Bedrock players.
     * This prevents the "you need NeoForge" error message from appearing for Bedrock players.
     */
    @Inject(
        method = "run",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void preventConfigSyncForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        // Check if this is a Bedrock player
        if (GeyserApi.api().isBedrockPlayer(listener.getOwner().getId())) {
            // Skip config sync for Bedrock players to prevent NeoForge version error
            // Complete the task immediately without sending any packets
            listener.finishCurrentTask(SyncConfig.TYPE);
            ci.cancel();
        }
    }
}
