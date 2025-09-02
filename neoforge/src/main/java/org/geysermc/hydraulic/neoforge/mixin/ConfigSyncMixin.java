package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ConfigSyncMixin {

    /**
     * Prevents NeoForge-specific configuration from being processed for Bedrock players.
     * This prevents the "you need NeoForge" error message from appearing for Bedrock players.
     */
    @Inject(
        method = "startConfiguration",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventConfigurationForBedrock(CallbackInfo ci) {
        ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
        
        // Check if this is a Bedrock player
        if (GeyserApi.api().isBedrockPlayer(self.getOwner().getId())) {
            // Skip configuration phase for Bedrock players to prevent NeoForge version error
            // This essentially allows them to bypass the mod checks
            self.finishCurrentTask(null);
            ci.cancel();
        }
    }
}
