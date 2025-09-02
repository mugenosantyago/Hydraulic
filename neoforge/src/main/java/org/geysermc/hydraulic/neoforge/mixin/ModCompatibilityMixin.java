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
 * Comprehensive mod compatibility mixin that prevents problematic mods from
 * sending custom packets to Bedrock players. This mixin targets specific
 * mod event handlers that are known to cause issues.
 */
public class ModCompatibilityMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModCompatibilityMixin");

    /**
     * Prevents Good Night's Sleep mod from processing Bedrock players.
     */
    @Mixin(targets = "com.legacy.good_nights_sleep.event.GNSPlayerEvents", remap = false)
    public static class GoodNightsSleepMixin {
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
                        LOGGER.info("ModCompatibilityMixin: Preventing Good Night's Sleep mod event for Bedrock player: {}", playerName);
                        ci.cancel();
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("ModCompatibilityMixin: Exception in Good Night's Sleep prevention: {}", e.getMessage());
            }
        }
    }

    /**
     * Prevents Wormhole mod from processing Bedrock players.
     */
    @Mixin(targets = "com.supermartijn642.wormhole.PortalGroupCapability", remap = false)
    public static class WormholeMixin {
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
                        LOGGER.info("ModCompatibilityMixin: Preventing Wormhole mod event for Bedrock player: {}", playerName);
                        ci.cancel();
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("ModCompatibilityMixin: Exception in Wormhole prevention: {}", e.getMessage());
            }
        }
    }

    /**
     * Prevents DiscCord mod from processing Bedrock players.
     */
    @Mixin(targets = "com.diamondfire.discordintegration.events.PlayerJoin", remap = false)
    public static class DiscCordMixin {
        @Inject(
            method = "onPlayerJoin",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
        )
        private static void preventDiscCordForBedrock(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event, CallbackInfo ci) {
            try {
                if (event.getEntity() instanceof ServerPlayer player) {
                    String playerName = player.getGameProfile().getName();
                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("ModCompatibilityMixin: Preventing DiscCord mod event for Bedrock player: {}", playerName);
                        ci.cancel();
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("ModCompatibilityMixin: Exception in DiscCord prevention: {}", e.getMessage());
            }
        }
    }

    /**
     * Prevents Server Chat Sync mod from processing Bedrock players.
     */
    @Mixin(targets = "net.hypixel.serverchatsyncs.events.PlayerJoinEvent", remap = false)
    public static class ServerChatSyncMixin {
        @Inject(
            method = "onPlayerJoin",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
        )
        private static void preventServerChatSyncForBedrock(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event, CallbackInfo ci) {
            try {
                if (event.getEntity() instanceof ServerPlayer player) {
                    String playerName = player.getGameProfile().getName();
                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("ModCompatibilityMixin: Preventing Server Chat Sync mod event for Bedrock player: {}", playerName);
                        ci.cancel();
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("ModCompatibilityMixin: Exception in Server Chat Sync prevention: {}", e.getMessage());
            }
        }
    }

    /**
     * Prevents GlitchCore mod from processing Bedrock players.
     */
    @Mixin(targets = "com.glitchfiend.glitchcore.common.event.PlayerEvents", remap = false)
    public static class GlitchCoreMixin {
        @Inject(
            method = "onPlayerJoin",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
        )
        private static void preventGlitchCoreForBedrock(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event, CallbackInfo ci) {
            try {
                if (event.getEntity() instanceof ServerPlayer player) {
                    String playerName = player.getGameProfile().getName();
                    boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("ModCompatibilityMixin: Preventing GlitchCore mod event for Bedrock player: {}", playerName);
                        ci.cancel();
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("ModCompatibilityMixin: Exception in GlitchCore prevention: {}", e.getMessage());
            }
        }
    }
}
