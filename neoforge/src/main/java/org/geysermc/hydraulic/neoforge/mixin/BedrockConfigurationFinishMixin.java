package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures Bedrock players properly finish configuration and
 * are spawned into the world correctly.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class, priority = 1500)
public class BedrockConfigurationFinishMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockConfigurationFinishMixin");
    
    // Track which players we've already handled to prevent duplicate processing
    private static final java.util.Set<String> handledPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * When startNextTask is called and there are no tasks for Bedrock players,
     * we need to immediately transition them to the play phase.
     */
    @Inject(
        method = "startNextTask",
        at = @At("HEAD")
    )
    private void handleBedrockConfigurationFinish(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer) {
                    String playerName = self.getOwner().getName();
                    
                    // Check if we've already handled this player to prevent duplicate processing
                    if (handledPlayers.contains(playerName)) {
                        LOGGER.debug("BedrockConfigurationFinishMixin: Already handled configuration for {}, skipping", playerName);
                        return;
                    }
                    
                    // Check if there are any tasks left
                    try {
                        java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                        tasksField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                            (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(self);
                        
                        if (tasks.isEmpty()) {
                            LOGGER.info("BedrockConfigurationFinishMixin: No tasks remaining for Bedrock player {}, immediately transitioning to world", 
                                playerName);
                            
                            // Mark this player as handled to prevent duplicate processing
                            handledPlayers.add(playerName);
                            
                            // Immediately attempt to complete configuration and spawn player
                            try {
                                // First, try to send the finish configuration packet
                                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
                                Object finishPacket = packetClass.getDeclaredConstructor().newInstance();
                                
                                java.lang.reflect.Method handleFinishMethod = 
                                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", packetClass);
                                handleFinishMethod.setAccessible(true);
                                handleFinishMethod.invoke(self, finishPacket);
                                
                                LOGGER.info("BedrockConfigurationFinishMixin: Successfully sent finish configuration packet for: {}", playerName);
                                
                                // After sending the finish packet, ensure the player transitions to the world
                                forcePlayerWorldTransition(self, playerName);
                                
                            } catch (Exception finishException) {
                                LOGGER.warn("BedrockConfigurationFinishMixin: Failed to send finish packet, trying direct transition: {}", 
                                    finishException.getMessage());
                                
                                // If the packet approach fails, try direct transition
                                forcePlayerWorldTransition(self, playerName);
                            }
                        }
                    } catch (Exception reflectionException) {
                        LOGGER.debug("BedrockConfigurationFinishMixin: Could not access task queue: {}", reflectionException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockConfigurationFinishMixin: Exception in configuration finish: {}", e.getMessage());
        }
    }
    
    /**
     * Forces the player to transition to the world by calling necessary methods.
     */
    private void forcePlayerWorldTransition(ServerConfigurationPacketListenerImpl listener, String playerName) {
        try {
            // Method 1: Try returnToWorld
            try {
                java.lang.reflect.Method returnToWorldMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("returnToWorld");
                returnToWorldMethod.setAccessible(true);
                returnToWorldMethod.invoke(listener);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called returnToWorld for: {}", playerName);
            } catch (Exception returnException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: returnToWorld failed: {}", returnException.getMessage());
            }
            
            // Method 2: Try to manually transition to play state
            try {
                // Get the connection
                java.lang.reflect.Field connectionField = listener.getClass().getSuperclass().getDeclaredField("connection");
                connectionField.setAccessible(true);
                Object connection = connectionField.get(listener);
                
                // Get the server
                java.lang.reflect.Method getServerMethod = listener.getClass().getMethod("getServer");
                MinecraftServer server = (MinecraftServer) getServerMethod.invoke(listener);
                
                // Try to get the player
                ServerPlayer player = server.getPlayerList().getPlayer(listener.getOwner().getId());
                if (player != null) {
                    LOGGER.info("BedrockConfigurationFinishMixin: Found player object, attempting to spawn in world");
                    
                    // Force the player to be added to the world if not already
                    if (!player.hasDisconnected()) {
                        server.execute(() -> {
                            try {
                                // Ensure player is in the correct world
                                if (player.getLevel() == null) {
                                    LOGGER.warn("BedrockConfigurationFinishMixin: Player has no level, attempting to set default world");
                                    player.setLevel(server.overworld());
                                }
                                
                                // Send necessary packets to complete the join process
                                PlayerList playerList = server.getPlayerList();
                                playerList.sendLevelInfo(player, player.getLevel());
                                playerList.sendPlayerPermissionLevel(player);
                                
                                // Teleport player to spawn if needed
                                if (player.getX() == 0 && player.getY() == 0 && player.getZ() == 0) {
                                    player.teleportTo(
                                        player.getLevel(),
                                        player.getLevel().getSharedSpawnPos().getX() + 0.5,
                                        player.getLevel().getSharedSpawnPos().getY(),
                                        player.getLevel().getSharedSpawnPos().getZ() + 0.5,
                                        0, 0
                                    );
                                }
                                
                                LOGGER.info("BedrockConfigurationFinishMixin: Successfully spawned Bedrock player {} in world", playerName);
                            } catch (Exception spawnException) {
                                LOGGER.error("BedrockConfigurationFinishMixin: Failed to spawn player in world: {}", 
                                    spawnException.getMessage());
                            }
                        });
                    }
                } else {
                    LOGGER.warn("BedrockConfigurationFinishMixin: Could not find player object for: {}", playerName);
                }
            } catch (Exception playException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: Manual play transition failed: {}", playException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.error("BedrockConfigurationFinishMixin: Failed to force world transition for {}: {}", 
                playerName, e.getMessage());
        }
    }
    
    /**
     * Clean up handled players when they disconnect to prevent memory leaks.
     */
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void cleanupHandledPlayer(net.minecraft.network.DisconnectionDetails details, CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                String playerName = self.getOwner().getName();
                if (handledPlayers.remove(playerName)) {
                    LOGGER.debug("BedrockConfigurationFinishMixin: Cleaned up handled player: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockConfigurationFinishMixin: Exception during cleanup: {}", e.getMessage());
        }
    }
}
