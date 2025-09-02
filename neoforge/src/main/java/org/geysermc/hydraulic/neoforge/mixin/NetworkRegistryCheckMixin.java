package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin targets NeoForge's NetworkRegistry.checkPacket to prevent
 * the "Payload X may not be sent to the client!" exceptions for Bedrock players.
 */
@Mixin(targets = "net.neoforged.neoforge.network.registration.NetworkRegistry", remap = false)
public class NetworkRegistryCheckMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NetworkRegistryCheckMixin");

    /**
     * Bypasses NeoForge's packet check for Bedrock players to prevent custom packet errors.
     */
    @Inject(
        method = "checkPacket",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void bypassPacketCheckForBedrock(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload, 
                                                   net.minecraft.server.network.ServerCommonPacketListenerImpl listener, 
                                                   CallbackInfo ci) {
        try {
            if (listener != null && payload != null) {
                boolean isBedrockPlayer = false;
                String playerName = null;
                
                // Try to determine if this is a Bedrock player
                if (listener instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                    isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                    if (configListener.getOwner() != null) {
                        playerName = configListener.getOwner().getName();
                    }
                } else if (listener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl gameListener) {
                    if (gameListener.player != null) {
                        playerName = gameListener.player.getGameProfile().getName();
                        isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    }
                }
                
                if (isBedrockPlayer) {
                    String payloadType = payload.type().toString();
                    LOGGER.info("NetworkRegistryCheckMixin: Bypassing packet check for custom payload {} for Bedrock player: {}", 
                        payloadType, playerName);
                    ci.cancel(); // Skip the check entirely for Bedrock players
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NetworkRegistryCheckMixin: Exception in packet check bypass: {}", e.getMessage());
        }
    }
}
