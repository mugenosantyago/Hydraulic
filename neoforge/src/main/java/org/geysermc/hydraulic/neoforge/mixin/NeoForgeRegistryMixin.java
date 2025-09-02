package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.neoforged.neoforge.network.configuration.SyncConfig", remap = false)
public class NeoForgeRegistryMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeRegistryMixin");

    /**
     * Simple test to see if this mixin is being applied at all.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL"),
        remap = false
    )
    private void onSyncConfigInit(CallbackInfo ci) {
        LOGGER.info("NeoForgeRegistryMixin: SyncConfig instance created - mixin is working!");
    }

    /**
     * Directly intercepts NeoForge's SyncConfig.run method and prevents it for Bedrock players.
     */
    @Inject(
        method = "run",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void preventSyncConfigForBedrock(ServerConfigurationPacketListenerImpl listener, CallbackInfo ci) {
        LOGGER.info("NeoForgeRegistryMixin: SyncConfig.run called - checking if Bedrock player");
        try {
            if (GeyserApi.api().isBedrockPlayer(listener.getOwner().getId())) {
                LOGGER.info("NeoForgeRegistryMixin: Preventing SyncConfig.run for Bedrock player: {}", listener.getOwner().getName());
                // Complete the task without running the sync
                listener.finishCurrentTask(net.neoforged.neoforge.network.configuration.SyncConfig.TYPE);
                ci.cancel();
            } else {
                LOGGER.info("NeoForgeRegistryMixin: Java player detected, allowing normal SyncConfig.run");
            }
        } catch (Exception e) {
            LOGGER.warn("NeoForgeRegistryMixin: Error checking Bedrock player: {}", e.getMessage());
        }
    }
}
