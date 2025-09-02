package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ConfigSyncMixin {

    /**
     * Prevents NullPointerException when finishCurrentTask is called with null for Bedrock players.
     * This fixes the crash that occurs when Bedrock players try to connect to NeoForge servers.
     */
    @Inject(
        method = "finishCurrentTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void handleNullTaskForBedrock(ConfigurationTask.Type taskType, CallbackInfo ci) {
        ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
        
        // Check if this is a Bedrock player and taskType is null
        if (GeyserApi.api().isBedrockPlayer(self.getOwner().getId()) && taskType == null) {
            // For Bedrock players, if taskType is null, just skip this task completion
            // This prevents the NullPointerException while allowing the connection to proceed
            ci.cancel();
        }
    }
}
