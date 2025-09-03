package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents NeoForge from disconnecting Bedrock players due to missing client-side mods.
 * It only affects Bedrock players - Java players are completely unaffected.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class NeoForgeRegistryMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeRegistryMixin");

    /**
     * Intercepts task execution to bypass NeoForge mod synchronization for Bedrock players.
     * This prevents the "Please install NeoForge" disconnect message for Bedrock clients.
     */
    @Inject(
        method = "startNextTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bypassNeoForgeTasksForBedrock(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer) {
                    // Get the current task type and check if it's a NeoForge task
                    try {
                        // Use reflection to access the current task queue
                        java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                        tasksField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                            (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(self);
                        
                        if (!tasks.isEmpty()) {
                            net.minecraft.server.network.ConfigurationTask nextTask = tasks.peek();
                            String taskClassName = nextTask.getClass().getName();
                            
                            // Skip NeoForge configuration tasks
                            if (taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig")) {
                                LOGGER.info("NeoForgeRegistryMixin: Skipping NeoForge task {} for Bedrock player: {}", 
                                    taskClassName, self.getOwner().getName());
                                
                                // Remove the task from the queue
                                tasks.poll();
                                
                                // Use reflection to call the private startNextTask method
                                try {
                                    java.lang.reflect.Method startNextTaskMethod = 
                                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("startNextTask");
                                    startNextTaskMethod.setAccessible(true);
                                    startNextTaskMethod.invoke(self);
                                } catch (Exception methodException) {
                                    LOGGER.debug("Could not call startNextTask via reflection: {}", methodException.getMessage());
                                }
                                
                                ci.cancel();
                                return;
                            }
                        }
                    } catch (Exception reflectionException) {
                        LOGGER.debug("NeoForgeRegistryMixin: Could not access task queue via reflection: {}", 
                            reflectionException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // If there's any error, just let the normal flow continue for safety
            LOGGER.debug("NeoForgeRegistryMixin: Exception in mixin, allowing normal task flow: {}", e.getMessage());
        }
    }
}
