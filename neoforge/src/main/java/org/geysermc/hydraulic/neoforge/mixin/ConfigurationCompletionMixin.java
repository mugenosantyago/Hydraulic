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
 * This mixin specifically helps Bedrock players complete the configuration phase
 * by ensuring all necessary steps are completed after NeoForge tasks are bypassed.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class ConfigurationCompletionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigurationCompletionMixin");

    /**
     * Helps complete configuration for Bedrock players by ensuring the transition to play phase.
     */
    @Inject(
        method = "startNextTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ensureBedrockConfigurationCompletion(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                String playerName = self.getOwner().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                LOGGER.info("ConfigurationCompletionMixin: startNextTask called for player {} (Bedrock: {})", 
                    playerName, isBedrockPlayer);
                
                if (isBedrockPlayer) {
                    try {
                        // Check if there are any tasks left in the queue
                        java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                        tasksField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                            (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(self);
                        
                        if (tasks.isEmpty()) {
                            // For Bedrock players with no tasks, try to simulate finish configuration
                            LOGGER.info("ConfigurationCompletionMixin: No tasks remaining for Bedrock player {}, attempting to complete configuration", 
                                playerName);
                            
                            // Try to simulate the client sending a finish configuration packet
                            try {
                                // Create a mock finish configuration packet
                                Class<?> finishPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
                                Object finishPacket = finishPacketClass.getDeclaredConstructor().newInstance();
                                
                                // Try to handle it directly
                                java.lang.reflect.Method handleFinishMethod = 
                                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", finishPacketClass);
                                handleFinishMethod.setAccessible(true);
                                handleFinishMethod.invoke(self, finishPacket);
                                
                                LOGGER.info("ConfigurationCompletionMixin: Successfully simulated finish configuration for Bedrock player: {}", playerName);
                                ci.cancel(); // Only cancel if we successfully completed
                                return;
                            } catch (Exception finishException) {
                                LOGGER.debug("ConfigurationCompletionMixin: Could not simulate finish configuration: {}", finishException.getMessage());
                                // If simulation fails, let the natural flow proceed
                                LOGGER.info("ConfigurationCompletionMixin: Allowing natural startNextTask flow for Bedrock player: {}", playerName);
                            }
                            
                            return;
                        } else {
                            // Check if remaining tasks are NeoForge tasks that should be skipped
                            boolean hasNeoForgeTasks = tasks.stream().anyMatch(task -> {
                                String taskClassName = task.getClass().getName();
                                return taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig");
                            });
                            
                            if (hasNeoForgeTasks) {
                                LOGGER.info("ConfigurationCompletionMixin: Removing remaining NeoForge tasks for Bedrock player: {}", 
                                    playerName);
                                
                                // Remove all NeoForge tasks
                                tasks.removeIf(task -> {
                                    String taskClassName = task.getClass().getName();
                                    boolean isNeoForgeTask = taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig");
                                    if (isNeoForgeTask) {
                                        LOGGER.info("ConfigurationCompletionMixin: Removing task: {}", taskClassName);
                                    }
                                    return isNeoForgeTask;
                                });
                                
                                // After removing tasks, let the natural flow continue
                                LOGGER.info("ConfigurationCompletionMixin: NeoForge tasks removed, continuing natural flow for: {}", playerName);
                                return;
                            }
                        }
                    } catch (Exception reflectionException) {
                        LOGGER.debug("ConfigurationCompletionMixin: Could not access task queue: {}", reflectionException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConfigurationCompletionMixin: Exception in configuration completion: {}", e.getMessage());
        }
    }
}