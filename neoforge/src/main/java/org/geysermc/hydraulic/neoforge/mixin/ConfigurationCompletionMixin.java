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
                            LOGGER.info("ConfigurationCompletionMixin: No more tasks for Bedrock player {}, completing configuration", 
                                self.getOwner().getName());
                            
                            // Try to finish the configuration since no tasks are left
                            try {
                                // Try multiple method names for finishing configuration (avoid returnToWorld to prevent protocol errors)
                                String[] finishMethodNames = {
                                    "finishConfiguration",
                                    "m_294354_", // Common obfuscated name
                                    "completeConfiguration",
                                    "transitionToGame"
                                };
                                
                                boolean finished = false;
                                for (String methodName : finishMethodNames) {
                                    try {
                                        java.lang.reflect.Method finishMethod = 
                                            ServerConfigurationPacketListenerImpl.class.getDeclaredMethod(methodName);
                                        finishMethod.setAccessible(true);
                                        finishMethod.invoke(self);
                                        LOGGER.info("ConfigurationCompletionMixin: Successfully finished configuration using {} for Bedrock player: {}", 
                                            methodName, playerName);
                                        finished = true;
                                        ci.cancel();
                                        return;
                                    } catch (NoSuchMethodException e) {
                                        continue; // Try next method
                                    }
                                }
                                
                                if (!finished) {
                                    LOGGER.warn("ConfigurationCompletionMixin: Could not find safe finish method, skipping returnToWorld to avoid protocol errors");
                                    
                                    // Don't use returnToWorld as it causes protocol pipeline errors
                                    // Just let the natural flow handle it
                                    
                                    // List all methods to find the right one
                                    try {
                                        java.lang.reflect.Method[] allMethods = 
                                            ServerConfigurationPacketListenerImpl.class.getDeclaredMethods();
                                        
                                        LOGGER.info("ConfigurationCompletionMixin: Available methods for debugging:");
                                        for (java.lang.reflect.Method method : allMethods) {
                                            if (method.getParameterCount() == 0) { // Only no-parameter methods
                                                LOGGER.info("ConfigurationCompletionMixin: Method: {} (returns: {})", 
                                                    method.getName(), method.getReturnType().getSimpleName());
                                            }
                                        }
                                        
                                        // Try methods that might be related to finishing/completing
                                        for (java.lang.reflect.Method method : allMethods) {
                                            if (method.getParameterCount() == 0) {
                                                String methodName = method.getName().toLowerCase();
                                                if (methodName.contains("finish") || 
                                                    methodName.contains("complete") || 
                                                    methodName.contains("transition") ||
                                                    methodName.contains("game") ||
                                                    methodName.startsWith("m_") && methodName.length() < 15) { // Obfuscated methods are usually short
                                                    
                                                    try {
                                                        method.setAccessible(true);
                                                        method.invoke(self);
                                                        LOGGER.info("ConfigurationCompletionMixin: SUCCESS! Transitioned using {} for Bedrock player: {}", 
                                                            method.getName(), self.getOwner().getName());
                                                        finished = true;
                                                        break;
                                                    } catch (Exception methodException) {
                                                        LOGGER.debug("ConfigurationCompletionMixin: Method {} failed: {}", 
                                                            method.getName(), methodException.getMessage());
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception reflectionException) {
                                        LOGGER.warn("ConfigurationCompletionMixin: Could not examine methods: {}", reflectionException.getMessage());
                                    }
                                }
                                
                                if (finished) {
                                    ci.cancel();
                                    return;
                                }
                            } catch (Exception finishException) {
                                LOGGER.warn("ConfigurationCompletionMixin: Could not finish configuration: {}", finishException.getMessage());
                            }
                        } else {
                            // Check if remaining tasks are NeoForge tasks that should be skipped
                            boolean hasNeoForgeTasks = tasks.stream().anyMatch(task -> {
                                String taskClassName = task.getClass().getName();
                                return taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig");
                            });
                            
                            if (hasNeoForgeTasks) {
                                LOGGER.info("ConfigurationCompletionMixin: Removing remaining NeoForge tasks for Bedrock player: {}", 
                                    self.getOwner().getName());
                                
                                // Remove all NeoForge tasks
                                tasks.removeIf(task -> {
                                    String taskClassName = task.getClass().getName();
                                    boolean isNeoForgeTask = taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig");
                                    if (isNeoForgeTask) {
                                        LOGGER.info("ConfigurationCompletionMixin: Removing task: {}", taskClassName);
                                    }
                                    return isNeoForgeTask;
                                });
                                
                                // If no tasks left after removal, finish configuration
                                if (tasks.isEmpty()) {
                                    LOGGER.info("ConfigurationCompletionMixin: All NeoForge tasks removed, finishing configuration for Bedrock player: {}", 
                                        self.getOwner().getName());
                                    
                                    // Try multiple method names for finishing configuration (avoid returnToWorld to prevent protocol errors)
                                    String[] finishMethodNames = {
                                        "finishConfiguration",
                                        "m_294354_", // Common obfuscated name
                                        "completeConfiguration",
                                        "transitionToGame"
                                    };
                                    
                                    boolean finished = false;
                                    for (String methodName : finishMethodNames) {
                                        try {
                                            java.lang.reflect.Method finishMethod = 
                                                ServerConfigurationPacketListenerImpl.class.getDeclaredMethod(methodName);
                                            finishMethod.setAccessible(true);
                                            finishMethod.invoke(self);
                                            LOGGER.info("ConfigurationCompletionMixin: Configuration completed using {} for Bedrock player: {}", 
                                                methodName, playerName);
                                            finished = true;
                                            ci.cancel();
                                            return;
                                        } catch (NoSuchMethodException e) {
                                            continue; // Try next method
                                        } catch (Exception finishException) {
                                            LOGGER.warn("ConfigurationCompletionMixin: Failed to finish configuration with {}: {}", 
                                                methodName, finishException.getMessage());
                                        }
                                    }
                                    
                                    if (!finished) {
                                        LOGGER.warn("ConfigurationCompletionMixin: Could not complete configuration after removing NeoForge tasks");
                                    }
                                }
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
