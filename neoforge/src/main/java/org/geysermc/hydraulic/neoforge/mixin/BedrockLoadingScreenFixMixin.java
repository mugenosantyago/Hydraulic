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
 * Aggressive fix for Bedrock players getting stuck on loading screen.
 * This mixin forces the configuration to complete by directly calling returnToWorld
 * when startNextTask is called and no tasks remain for Bedrock players.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class, priority = 2000)
public class BedrockLoadingScreenFixMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockLoadingScreenFixMixin");
    
    // Track which players we've already processed to prevent loops
    private static final java.util.Set<String> processedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Aggressive fix: when startNextTask is called for Bedrock players,
     * immediately try to complete their configuration if no tasks remain.
     */
    @Inject(
        method = "startNextTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void forceBedrockConfigurationCompletion(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer) {
                    String playerName = self.getOwner().getName();
                    
                    // Prevent processing the same player multiple times
                    if (processedPlayers.contains(playerName)) {
                        return;
                    }
                    
                    LOGGER.info("BedrockLoadingScreenFixMixin: Processing startNextTask for Bedrock player: {}", playerName);
                    
                    try {
                        // Check if there are any tasks left
                        java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                        tasksField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                            (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(self);
                        
                        // For Bedrock players, we need to be more aggressive - clear any remaining tasks
                        if (tasks.isEmpty() || tasks.size() <= 1) {
                            LOGGER.info("BedrockLoadingScreenFixMixin: {} tasks remaining for Bedrock player {}, forcing configuration completion", tasks.size(), playerName);
                            
                            // Clear any remaining tasks for Bedrock players
                            if (!tasks.isEmpty()) {
                                LOGGER.info("BedrockLoadingScreenFixMixin: Clearing {} remaining tasks for Bedrock player: {}", tasks.size(), playerName);
                                tasks.clear();
                            }
                            
                            // Also try to clear any currently running task
                            try {
                                java.lang.reflect.Field currentTaskField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("currentTask");
                                currentTaskField.setAccessible(true);
                                Object currentTask = currentTaskField.get(self);
                                if (currentTask != null) {
                                    LOGGER.info("BedrockLoadingScreenFixMixin: Clearing current task for Bedrock player: {}", playerName);
                                    currentTaskField.set(self, null);
                                }
                            } catch (Exception taskException) {
                                LOGGER.debug("BedrockLoadingScreenFixMixin: Could not clear current task for {}: {}", playerName, taskException.getMessage());
                            }
                            
                            // Mark as processed
                            processedPlayers.add(playerName);
                            
                            // Try multiple methods to complete the configuration
                            boolean completed = false;
                            
                            // Method 1: Try returnToWorld directly
                            try {
                                java.lang.reflect.Method returnToWorldMethod = 
                                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("returnToWorld");
                                returnToWorldMethod.setAccessible(true);
                                returnToWorldMethod.invoke(self);
                                LOGGER.info("BedrockLoadingScreenFixMixin: Successfully called returnToWorld for Bedrock player: {}", playerName);
                                completed = true;
                            } catch (Exception returnException) {
                                LOGGER.warn("BedrockLoadingScreenFixMixin: returnToWorld failed for {}: {}", playerName, returnException.getMessage());
                            }
                            
                            // Method 2: Try finishConfiguration if returnToWorld failed
                            if (!completed) {
                                try {
                                    java.lang.reflect.Method finishConfigMethod = 
                                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                                    finishConfigMethod.setAccessible(true);
                                    finishConfigMethod.invoke(self);
                                    LOGGER.info("BedrockLoadingScreenFixMixin: Successfully called finishConfiguration for Bedrock player: {}", playerName);
                                    completed = true;
                                } catch (Exception finishException) {
                                    LOGGER.warn("BedrockLoadingScreenFixMixin: finishConfiguration failed for {}: {}", playerName, finishException.getMessage());
                                }
                            }
                            
                            // Method 3: Try handleConfigurationFinished with null packet as last resort
                            if (!completed) {
                                try {
                                    java.lang.reflect.Method handleFinishedMethod = 
                                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", 
                                            net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket.class);
                                    handleFinishedMethod.setAccessible(true);
                                    handleFinishedMethod.invoke(self, (Object) null);
                                    LOGGER.info("BedrockLoadingScreenFixMixin: Successfully called handleConfigurationFinished for Bedrock player: {}", playerName);
                                    completed = true;
                                } catch (Exception handleException) {
                                    LOGGER.warn("BedrockLoadingScreenFixMixin: handleConfigurationFinished failed for {}: {}", playerName, handleException.getMessage());
                                }
                            }
                            
                            if (completed) {
                                // Cancel the original startNextTask to prevent any further processing
                                ci.cancel();
                                return;
                            } else {
                                LOGGER.error("BedrockLoadingScreenFixMixin: All configuration completion methods failed for Bedrock player: {}", playerName);
                            }
                        } else {
                            LOGGER.debug("BedrockLoadingScreenFixMixin: {} tasks remaining for Bedrock player {}, letting normal flow continue", tasks.size(), playerName);
                        }
                    } catch (Exception reflectionException) {
                        LOGGER.warn("BedrockLoadingScreenFixMixin: Could not access task queue for {}: {}", playerName, reflectionException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenFixMixin: Exception in loading screen fix: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Clean up processed players when they disconnect to prevent memory leaks.
     */
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void cleanupProcessedPlayer(net.minecraft.network.DisconnectionDetails details, CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                String playerName = self.getOwner().getName();
                if (processedPlayers.remove(playerName)) {
                    LOGGER.debug("BedrockLoadingScreenFixMixin: Cleaned up processed player: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenFixMixin: Exception during cleanup: {}", e.getMessage());
        }
    }
}
