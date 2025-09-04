package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
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
            // Method 1: Try returnToWorld which is the standard method for transitioning to play phase
            try {
                java.lang.reflect.Method returnToWorldMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("returnToWorld");
                returnToWorldMethod.setAccessible(true);
                returnToWorldMethod.invoke(listener);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called returnToWorld for: {}", playerName);
                return; // If this works, we're done
            } catch (Exception returnException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: returnToWorld failed: {}", returnException.getMessage());
            }
            
            // Method 2: Try finishConfiguration as an alternative
            try {
                java.lang.reflect.Method finishConfigMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                finishConfigMethod.setAccessible(true);
                finishConfigMethod.invoke(listener);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called finishConfiguration for: {}", playerName);
                return; // If this works, we're done
            } catch (Exception finishException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: finishConfiguration failed: {}", finishException.getMessage());
            }
            
            // Method 3: Try to trigger the configuration completed method
            try {
                java.lang.reflect.Method configCompletedMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", 
                        Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket"));
                configCompletedMethod.setAccessible(true);
                
                // Create the packet instance
                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
                Object packet = packetClass.getDeclaredConstructor().newInstance();
                
                configCompletedMethod.invoke(listener, packet);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully triggered configuration completed for: {}", playerName);
            } catch (Exception configException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: Configuration completed trigger failed: {}", configException.getMessage());
            }
            
            LOGGER.warn("BedrockConfigurationFinishMixin: All transition methods failed for player: {}", playerName);
            
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
