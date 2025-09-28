package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Delays aggressive Bedrock fixes until after the player has fully spawned and connected.
 * This prevents overwhelming the mobile client during the critical login/spawn phase.
 */
@Mixin(net.minecraft.server.players.PlayerList.class)
public class DelayedBedrockFixMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("DelayedBedrockFixMixin");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    /**
     * Schedules delayed fixes after player spawn is complete.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void scheduleDelayedBedrockFixes(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("DelayedBedrockFixMixin: Scheduling delayed fixes for Bedrock player: {}", playerName);
                
                // Schedule a gentle fix 3 seconds after spawn to let the client settle
                scheduler.schedule(() -> {
                    try {
                        if (player.connection != null && !player.hasDisconnected()) {
                            LOGGER.info("DelayedBedrockFixMixin: Applying gentle post-spawn fix for: {}", playerName);
                            
                            // Gentle position validation (no forcing)
                            if (player.getY() < -50) { // Only fix if player is clearly in the void
                                LOGGER.info("DelayedBedrockFixMixin: Player {} appears to be in void, gently correcting position", playerName);
                                var level = player.level();
                                var spawn = level.getSharedSpawnPos();
                                player.teleportTo(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
                            }
                            
                            // Gentle chunk loading (no excessive forcing)
                            try {
                                var chunkSource = player.level().getChunkSource();
                                var playerChunk = player.chunkPosition();
                                chunkSource.addRegionTicket(net.minecraft.server.level.TicketType.PLAYER, 
                                    playerChunk, 3, player.getUUID());
                                LOGGER.debug("DelayedBedrockFixMixin: Added gentle chunk ticket for: {}", playerName);
                            } catch (Exception e) {
                                LOGGER.debug("DelayedBedrockFixMixin: Gentle chunk loading failed for {}: {}", playerName, e.getMessage());
                            }
                            
                            LOGGER.info("DelayedBedrockFixMixin: Completed gentle post-spawn fix for: {}", playerName);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("DelayedBedrockFixMixin: Exception in delayed fix for {}: {}", playerName, e.getMessage());
                    }
                }, 3, TimeUnit.SECONDS);
                
                // Schedule a second check 10 seconds later if needed
                scheduler.schedule(() -> {
                    try {
                        if (player.connection != null && !player.hasDisconnected()) {
                            LOGGER.debug("DelayedBedrockFixMixin: Final validation for: {}", playerName);
                            
                            // Only log if there are still issues
                            if (player.getY() < -50) {
                                LOGGER.warn("DelayedBedrockFixMixin: Player {} still in problematic position after 10 seconds", playerName);
                            } else {
                                LOGGER.info("DelayedBedrockFixMixin: Player {} appears stable", playerName);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("DelayedBedrockFixMixin: Exception in final validation for {}: {}", playerName, e.getMessage());
                    }
                }, 10, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            LOGGER.debug("DelayedBedrockFixMixin: Exception scheduling delayed fixes: {}", e.getMessage());
        }
    }
}
