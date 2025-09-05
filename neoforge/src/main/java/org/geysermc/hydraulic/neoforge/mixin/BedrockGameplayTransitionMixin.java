package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
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
 * This mixin forces the final transition from loading to gameplay for Bedrock players
 * by ensuring they properly exit the configuration/loading phase and enter active gameplay.
 * 
 * The core issue is that Bedrock clients can receive world data but get stuck waiting
 * for a final "ready to play" signal that never comes due to configuration phase issues.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2500)
public class BedrockGameplayTransitionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockGameplayTransitionMixin");
    
    @Shadow
    public ServerPlayer player;
    
    // Track which players have had gameplay forced
    private static final java.util.Set<String> gameplayForced = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    /**
     * Intercepts the constructor to force gameplay transition for Bedrock players.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void forceBedrockGameplayTransition(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if we've already forced gameplay for this player
                if (gameplayForced.contains(playerName)) {
                    LOGGER.debug("BedrockGameplayTransitionMixin: Gameplay already forced for: {}", playerName);
                    return;
                }
                
                LOGGER.info("BedrockGameplayTransitionMixin: Forcing gameplay transition for Bedrock player: {}", playerName);
                gameplayForced.add(playerName);
                
                // Schedule the gameplay transition after a short delay
                server.execute(() -> {
                    try {
                        // Give the system a moment to settle
                        Thread.sleep(500);
                        
                        forceGameplayStart(player, server);
                        
                    } catch (Exception e) {
                        LOGGER.error("BedrockGameplayTransitionMixin: Exception forcing gameplay for {}: {}", 
                            playerName, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockGameplayTransitionMixin: Exception in constructor: {}", e.getMessage());
        }
    }
    
    /**
     * Forces the gameplay to start for a Bedrock player.
     */
    private void forceGameplayStart(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.warn("BedrockGameplayTransitionMixin: Player {} connection is invalid", playerName);
                return;
            }
            
            LOGGER.info("BedrockGameplayTransitionMixin: Starting gameplay force sequence for: {}", playerName);
            
            // Phase 1: Send critical gameplay packets
            sendGameplayStartPackets(player);
            
            // Phase 2: Force player state synchronization
            synchronizePlayerForGameplay(player, server);
            
            // Phase 3: Send final confirmation packets
            sendGameplayConfirmationPackets(player);
            
            // Phase 4: Schedule validation
            scheduleGameplayValidation(player, server);
            
            LOGGER.info("BedrockGameplayTransitionMixin: Completed gameplay transition for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockGameplayTransitionMixin: Failed to force gameplay for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Sends critical packets to start gameplay.
     */
    private void sendGameplayStartPackets(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockGameplayTransitionMixin: Sending gameplay start packets for: {}", playerName);
            
            // Send player abilities (critical for gameplay)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Send game mode
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                player.gameMode.getGameModeForPlayer().getId()));
            
            // Send position (critical for client state)
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Send inventory state
            player.inventoryMenu.sendAllDataToRemote();
            
            // Force immediate delivery
            player.connection.getConnection().flushChannel();
            
            LOGGER.debug("BedrockGameplayTransitionMixin: Sent gameplay start packets for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockGameplayTransitionMixin: Failed to send gameplay start packets for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes player state for gameplay.
     */
    private void synchronizePlayerForGameplay(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockGameplayTransitionMixin: Synchronizing player state for: {}", playerName);
            
            var playerList = server.getPlayerList();
            
            // Send all player info
            playerList.sendAllPlayerInfo(player);
            
            // Send level information
            playerList.sendLevelInfo(player, player.level());
            
            // Send permission level
            playerList.sendPlayerPermissionLevel(player);
            
            // Ensure proper health/food sync
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
            
            // Sync experience
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(
                player.experienceProgress, player.totalExperience, player.experienceLevel));
            
            // Force connection flush
            player.connection.getConnection().flushChannel();
            
            LOGGER.debug("BedrockGameplayTransitionMixin: Synchronized player state for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockGameplayTransitionMixin: Failed to synchronize player state for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Sends final confirmation packets to complete gameplay transition.
     */
    private void sendGameplayConfirmationPackets(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockGameplayTransitionMixin: Sending gameplay confirmation for: {}", playerName);
            
            // Send weather state (often triggers client gameplay start)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            
            // Send time update
            long worldTime = player.level().getGameTime();
            long dayTime = player.level().getDayTime();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(worldTime, dayTime, true));
            
            // Send a final position confirmation
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Final flush
            player.connection.getConnection().flushChannel();
            
            LOGGER.info("BedrockGameplayTransitionMixin: Sent gameplay confirmation packets for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockGameplayTransitionMixin: Failed to send confirmation packets for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Schedules validation to ensure gameplay started properly.
     */
    private void scheduleGameplayValidation(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        String playerName = player.getGameProfile().getName();
        
        // Schedule validation after a delay
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                if (player.connection != null && player.connection.getConnection().isConnected()) {
                    LOGGER.info("BedrockGameplayTransitionMixin: Final gameplay validation for: {}", playerName);
                    
                    // Send one final position update
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                    
                    // Send final abilities update
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                    
                    // Final flush
                    player.connection.getConnection().flushChannel();
                    
                    LOGGER.info("BedrockGameplayTransitionMixin: Gameplay transition should now be complete for: {}", playerName);
                }
            } catch (Exception e) {
                LOGGER.debug("BedrockGameplayTransitionMixin: Exception in gameplay validation for {}: {}", 
                    playerName, e.getMessage());
            }
        }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Clean up when players disconnect.
     */
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void cleanupGameplayForced(net.minecraft.network.DisconnectionDetails details, CallbackInfo ci) {
        try {
            if (this.player != null) {
                String playerName = this.player.getGameProfile().getName();
                if (gameplayForced.remove(playerName)) {
                    LOGGER.debug("BedrockGameplayTransitionMixin: Cleaned up gameplay tracking for: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockGameplayTransitionMixin: Exception during cleanup: {}", e.getMessage());
        }
    }
}
