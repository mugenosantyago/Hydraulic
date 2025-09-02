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
 * This mixin prevents problematic mod events from firing for Bedrock players
 * that would try to send custom packets.
 */
@Mixin(targets = "com.legacy.good_nights_sleep.event.GNSPlayerEvents", remap = false)
public class EntityJoinEventMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityJoinEventMixin");

    /**
     * Prevents the Good Night's Sleep mod from processing Bedrock players.
     */
    @Inject(
        method = "onEntityJoin",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void preventGoodNightsSleepForBedrock(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event, CallbackInfo ci) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("EntityJoinEventMixin: Preventing Good Night's Sleep mod event for Bedrock player: {}", playerName);
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("EntityJoinEventMixin: Exception in event prevention: {}", e.getMessage());
        }
    }
}
