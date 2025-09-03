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
 * This mixin handles the transition from configuration to play phase
 * for Bedrock players to avoid packet encoding issues.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class ConnectionTransitionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConnectionTransitionMixin");

    /**
     * Ensures proper transition to play phase for Bedrock players.
     */
    @Inject(
        method = "finishConfiguration",
        at = @At("HEAD")
    )
    private void handleBedrockFinishConfiguration(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer) {
                    LOGGER.info("ConnectionTransitionMixin: Finishing configuration for Bedrock player: {}", 
                        self.getOwner().getName());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConnectionTransitionMixin: Exception in finish configuration: {}", e.getMessage());
        }
    }

    /**
     * Alternative method that might be called during configuration completion.
     */
    @Inject(
        method = "startConfiguration", 
        at = @At("TAIL")
    )
    private void handleBedrockStartConfiguration(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer) {
                    LOGGER.info("ConnectionTransitionMixin: Configuration started for Bedrock player: {}", 
                        self.getOwner().getName());
                    
                    // Try to skip directly to finishing configuration if no tasks are left
                    try {
                        java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                        tasksField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                            (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(self);
                        
                        // Remove any remaining NeoForge tasks
                        tasks.removeIf(task -> {
                            String taskClassName = task.getClass().getName();
                            boolean isNeoForgeTask = taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig");
                            if (isNeoForgeTask) {
                                LOGGER.info("ConnectionTransitionMixin: Removing NeoForge task {} for Bedrock player", taskClassName);
                            }
                            return isNeoForgeTask;
                        });
                        
                    } catch (Exception reflectionException) {
                        LOGGER.debug("ConnectionTransitionMixin: Could not access task queue: {}", reflectionException.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConnectionTransitionMixin: Exception in start configuration: {}", e.getMessage());
        }
    }
}
