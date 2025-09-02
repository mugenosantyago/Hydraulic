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
 * Prevents Good Night's Sleep mod from processing Bedrock players.
 */
@Mixin(targets = "com.legacy.good_nights_sleep.event.GNSPlayerEvents", remap = false)
public class GoodNightsSleepMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoodNightsSleepMixin");

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
                    LOGGER.info("GoodNightsSleepMixin: Preventing Good Night's Sleep mod event for Bedrock player: {}", playerName);
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("GoodNightsSleepMixin: Exception in Good Night's Sleep prevention: {}", e.getMessage());
        }
    }
}
