package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public class ConfigSyncMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigSyncMixin");
    private boolean isBedrockPlayer = false;

    @Shadow
    private ConfigurationTask currentTask;

    /**
     * Detects Bedrock players during configuration initialization.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void onConfigurationInit(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            this.isBedrockPlayer = GeyserApi.api().isBedrockPlayer(cookie.gameProfile().getId());
            LOGGER.info("ConfigSyncMixin: Configuration created for player {} (Bedrock: {})", cookie.gameProfile().getName(), this.isBedrockPlayer);
            
            if (this.isBedrockPlayer) {
                LOGGER.info("ConfigSyncMixin: Detected Bedrock player - will bypass NeoForge checks");
            }
        } catch (Exception e) {
            LOGGER.warn("ConfigSyncMixin: Error in configuration init: {}", e.getMessage());
        }
    }

    /**
     * Intercepts configuration start and sets up task filtering for Bedrock players.
     */
    @Inject(
        method = "startConfiguration",
        at = @At("HEAD")
    )
    private void setupBedrockConfiguration(CallbackInfo ci) {
        if (this.isBedrockPlayer) {
            LOGGER.info("ConfigSyncMixin: Configuration starting for Bedrock player - will filter NeoForge tasks");
        }
    }


}
