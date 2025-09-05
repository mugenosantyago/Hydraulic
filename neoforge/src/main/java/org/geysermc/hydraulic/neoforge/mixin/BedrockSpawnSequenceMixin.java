package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures proper spawn sequence for Bedrock players by coordinating
 * with Geyser's spawn packet handling to prevent loading screen issues.
 */
@Mixin(value = PlayerList.class, priority = 800)
public class BedrockSpawnSequenceMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockSpawnSequenceMixin");
    
    /**
     * Track Bedrock players during the placeNewPlayer process to ensure
     * proper spawn packet coordination with Geyser.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("HEAD")
    )
    private void onBedrockPlayerPlace(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("BedrockSpawnSequenceMixin: Preparing spawn sequence for Bedrock player: {}", playerName);
                
                // Schedule a task to run after the player is placed to ensure proper spawn coordination
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                    try {
                        // Give Geyser a moment to initialize the session properly
                        Thread.sleep(250);
                        
                        // Force a comprehensive spawn packet sequence
                        ensureProperSpawnSequence(player);
                        
                    } catch (Exception e) {
                        LOGGER.debug("BedrockSpawnSequenceMixin: Exception in spawn sequence preparation: {}", e.getMessage());
                    }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockSpawnSequenceMixin: Exception in placeNewPlayer hook: {}", e.getMessage());
        }
    }
    
    /**
     * Additional hook after the player is fully placed to ensure spawn completion.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void afterBedrockPlayerPlace(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("BedrockSpawnSequenceMixin: Finalizing spawn sequence for Bedrock player: {}", playerName);
                
                // Schedule a delayed finalization to ensure all systems are ready
                var server = player.getServer();
                if (server != null) {
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                    try {
                        // Final spawn sequence validation and correction
                        validateAndCorrectSpawnSequence(player);
                        
                    } catch (Exception e) {
                        LOGGER.debug("BedrockSpawnSequenceMixin: Exception in spawn sequence finalization: {}", e.getMessage());
                    }
                    }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockSpawnSequenceMixin: Exception in afterPlaceNewPlayer hook: {}", e.getMessage());
        }
    }
    
    /**
     * Ensures proper spawn packet sequence for Bedrock players.
     */
    private void ensureProperSpawnSequence(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.info("BedrockSpawnSequenceMixin: Ensuring proper spawn sequence for: {}", playerName);
            
            // Force a complete data sync to ensure the client has all necessary information
            if (player.connection != null) {
                // Send player abilities
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                
                // Send player position
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                
                // Send inventory contents
                if (player.containerMenu != null) {
                    player.containerMenu.sendAllDataToRemote();
                }
                
                // Send player info updates
                var server = player.getServer();
                if (server != null) {
                    PlayerList playerList = server.getPlayerList();
                    playerList.sendAllPlayerInfo(player);
                    
                    // Send level info
                    playerList.sendLevelInfo(player, player.level());
                    
                    // Force flush the connection to ensure packets are sent immediately
                    player.connection.getConnection().flushChannel();
                    
                    LOGGER.info("BedrockSpawnSequenceMixin: Sent comprehensive spawn data for: {}", playerName);
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("BedrockSpawnSequenceMixin: Failed to ensure spawn sequence for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Validates and corrects the spawn sequence if needed.
     */
    private void validateAndCorrectSpawnSequence(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Check if the player is properly spawned and connected
            if (player.connection != null && player.connection.getConnection().isConnected()) {
                LOGGER.info("BedrockSpawnSequenceMixin: Validating spawn sequence for: {}", playerName);
                
                // Send a final position update to ensure the client knows where the player is
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                
                // Send a final game mode update
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                    player.gameMode.getGameModeForPlayer().getId()));
                
                // Force another connection flush
                player.connection.getConnection().flushChannel();
                
                LOGGER.info("BedrockSpawnSequenceMixin: Completed spawn sequence validation for: {}", playerName);
            } else {
                LOGGER.warn("BedrockSpawnSequenceMixin: Player {} connection is not valid for spawn validation", playerName);
            }
            
        } catch (Exception e) {
            LOGGER.error("BedrockSpawnSequenceMixin: Failed to validate spawn sequence for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
}
