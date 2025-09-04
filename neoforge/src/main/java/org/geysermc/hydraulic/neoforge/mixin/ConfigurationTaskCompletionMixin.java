package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces completion of any configuration tasks for Bedrock players.
 * This mixin specifically targets the task completion mechanism to ensure
 * that Bedrock players don't get stuck waiting for tasks that can't complete.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class, priority = 1500)
public class ConfigurationTaskCompletionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigurationTaskCompletionMixin");

    /**
     * When a configuration task starts for a Bedrock player, immediately complete it
     * to prevent them from getting stuck.
     */
    @Inject(
        method = "startNextTask",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ConfigurationTask;start(Ljava/util/function/Consumer;)V"),
        cancellable = true
    )
    private void forceCompleteTaskForBedrock(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null && BedrockDetectionHelper.isBedrockPlayer(self)) {
                String playerName = self.getOwner().getName();
                
                try {
                    // Get the current task that's about to start
                    java.lang.reflect.Field currentTaskField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("currentTask");
                    currentTaskField.setAccessible(true);
                    ConfigurationTask currentTask = (ConfigurationTask) currentTaskField.get(self);
                    
                    if (currentTask != null) {
                        String taskType = currentTask.getClass().getSimpleName();
                        LOGGER.info("ConfigurationTaskCompletionMixin: Force completing task {} for Bedrock player: {}", taskType, playerName);
                        
                        // Force complete the task by calling the completion consumer directly
                        try {
                            // Create a completion consumer that will advance to the next task
                            java.util.function.Consumer<ConfigurationTask> completionConsumer = task -> {
                                try {
                                    LOGGER.info("ConfigurationTaskCompletionMixin: Task {} completed for Bedrock player: {}", taskType, playerName);
                                    // Clear the current task
                                    currentTaskField.set(self, null);
                                    // Recursively call startNextTask to continue the flow
                                    java.lang.reflect.Method startNextTaskMethod = ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("startNextTask");
                                    startNextTaskMethod.setAccessible(true);
                                    startNextTaskMethod.invoke(self);
                                } catch (Exception e) {
                                    LOGGER.error("ConfigurationTaskCompletionMixin: Error in completion consumer for {}: {}", playerName, e.getMessage());
                                }
                            };
                            
                            // Complete the task immediately
                            completionConsumer.accept(currentTask);
                            
                            // Cancel the original task start to prevent it from actually running
                            ci.cancel();
                            return;
                            
                        } catch (Exception taskException) {
                            LOGGER.error("ConfigurationTaskCompletionMixin: Failed to force complete task for {}: {}", playerName, taskException.getMessage());
                        }
                    }
                } catch (Exception reflectionException) {
                    LOGGER.error("ConfigurationTaskCompletionMixin: Reflection error for {}: {}", playerName, reflectionException.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.error("ConfigurationTaskCompletionMixin: Exception in task completion fix: {}", e.getMessage(), e);
        }
    }
}
