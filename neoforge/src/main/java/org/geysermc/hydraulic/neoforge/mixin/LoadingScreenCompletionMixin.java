package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures Bedrock clients receive proper loading completion signals
 * to prevent getting stuck at "Generating world..." loading screen.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class LoadingScreenCompletionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("LoadingScreenCompletionMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * After the player has been added to the world, ensure Geyser receives proper completion signals.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL"),
        require = 0
    )
    private void ensureLoadingScreenCompletion(CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("LoadingScreenCompletionMixin: Ensuring loading screen completion for Bedrock player: {}", playerName);
                    
                    // Schedule a task to ensure the client gets the completion signal
                    player.getServer().execute(() -> {
                        try {
                            // Force a position update to signal the client that the world is ready
                            player.setPos(player.getX(), player.getY(), player.getZ());
                            
                            // Send a game mode packet to ensure client state is synced
                            player.setGameMode(player.gameMode.getGameModeForPlayer());
                            
                            // Force chunk updates around the player
                            player.getLevel().getChunkSource().addRegionTicket(
                                net.minecraft.server.level.TicketType.PLAYER,
                                new net.minecraft.world.level.ChunkPos(player.blockPosition()),
                                3,
                                player
                            );
                            
                            LOGGER.info("LoadingScreenCompletionMixin: Sent loading completion signals for Bedrock player: {}", playerName);
                            
                        } catch (Exception e) {
                            LOGGER.error("LoadingScreenCompletionMixin: Exception sending completion signals: {}", e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.error("LoadingScreenCompletionMixin: Exception in loading screen completion: {}", e.getMessage(), e);
        }
    }

    /**
     * Intercept the tick method to periodically check if Bedrock players need loading screen fixes.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD"),
        require = 0
    )
    private void monitorBedrockPlayerState(CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    // Check if the player has been in the game for a while but might be stuck
                    if (player.tickCount > 100 && player.tickCount % 200 == 0) { // Every 10 seconds after 5 seconds
                        LOGGER.debug("LoadingScreenCompletionMixin: Monitoring Bedrock player: {} (tick: {})", 
                            playerName, player.tickCount);
                        
                        // Periodically refresh the player's state to ensure the client stays synced
                        try {
                            player.refreshDisplayName();
                            player.setPos(player.getX(), player.getY(), player.getZ());
                        } catch (Exception e) {
                            LOGGER.debug("LoadingScreenCompletionMixin: Exception in periodic refresh: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("LoadingScreenCompletionMixin: Exception in tick monitoring: {}", e.getMessage());
        }
    }
}
