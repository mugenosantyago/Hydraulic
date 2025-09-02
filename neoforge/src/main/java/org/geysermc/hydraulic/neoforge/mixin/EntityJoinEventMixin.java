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
 * This mixin prevents Wormhole mod from processing Bedrock players during PlayerLoggedInEvent.
 */
@Mixin(targets = "com.supermartijn642.wormhole.PortalGroupCapability", remap = false)
public class EntityJoinEventMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityJoinEventMixin");

    /**
     * Prevents the Wormhole mod from processing Bedrock players.
     */
    @Inject(
        method = "onJoin",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void preventWormholeForBedrock(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event, CallbackInfo ci) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("EntityJoinEventMixin: Preventing Wormhole mod event for Bedrock player: {}", playerName);
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("EntityJoinEventMixin: Exception in Wormhole prevention: {}", e.getMessage());
        }
    }
}
