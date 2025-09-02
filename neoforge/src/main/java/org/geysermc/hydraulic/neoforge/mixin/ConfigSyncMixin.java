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
            // Try multiple ways to detect Bedrock players
            boolean isBedrockFromGeyser = false;
            boolean isBedrockFromFloodgate = false;
            
            try {
                isBedrockFromGeyser = GeyserApi.api().isBedrockPlayer(cookie.gameProfile().getId());
            } catch (Exception geyserException) {
                LOGGER.debug("ConfigSyncMixin: Geyser check failed: {}", geyserException.getMessage());
            }
            
            // Check if this is a Floodgate player (Bedrock players via Geyser/Floodgate start with a dot)
            String playerName = cookie.gameProfile().getName();
            if (playerName != null && playerName.startsWith(".")) {
                isBedrockFromFloodgate = true;
            }
            
            this.isBedrockPlayer = isBedrockFromGeyser || isBedrockFromFloodgate;
            
            LOGGER.info("ConfigSyncMixin: Configuration created for player {} (Bedrock: {} - Geyser: {}, Floodgate: {})", 
                playerName, this.isBedrockPlayer, isBedrockFromGeyser, isBedrockFromFloodgate);
            
            if (this.isBedrockPlayer) {
                LOGGER.info("ConfigSyncMixin: Detected Bedrock player - will bypass NeoForge checks");
            }
        } catch (Exception e) {
            LOGGER.warn("ConfigSyncMixin: Error in configuration init: {}", e.getMessage());
        }
    }

    /**
     * Logs when configuration starts for Bedrock players.
     */
    @Inject(
        method = "startConfiguration",
        at = @At("HEAD")
    )
    private void logConfigurationStart(CallbackInfo ci) {
        if (this.isBedrockPlayer) {
            LOGGER.info("ConfigSyncMixin: Configuration starting for Bedrock player - NeoForge compatibility handled by Geyser");
        }
    }
}
