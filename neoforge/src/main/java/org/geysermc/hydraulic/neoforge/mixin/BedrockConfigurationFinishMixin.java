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
 * This mixin ensures Bedrock players properly finish configuration by
 * sending the required finish configuration packet on their behalf.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class BedrockConfigurationFinishMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockConfigurationFinishMixin");

    /**
     * When startNextTask is called and there are no tasks for Bedrock players,
     * we need to send the finish configuration packet on their behalf.
     */
    @Inject(
        method = "startNextTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void handleBedrockConfigurationFinish(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer) {
                    String playerName = self.getOwner().getName();
                    
                    // Check if there are any tasks left
                    try {
                        java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                        tasksField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                            (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(self);
                        
                        if (tasks.isEmpty()) {
                            LOGGER.info("BedrockConfigurationFinishMixin: No tasks remaining for Bedrock player {}, sending finish configuration", 
                                playerName);
                            
                            // Cancel the original startNextTask to prevent loops
                            ci.cancel();
                            
                            // Try to directly call handleConfigurationFinished with null (some methods accept null)
                            try {
                                java.lang.reflect.Method handleFinishMethod = 
                                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", 
                                        Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket"));
                                handleFinishMethod.setAccessible(true);
                                handleFinishMethod.invoke(self, (Object) null);
                                
                                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called handleConfigurationFinished with null for: {}", 
                                    playerName);
                                
                            } catch (Exception directException) {
                                LOGGER.debug("BedrockConfigurationFinishMixin: handleConfigurationFinished with null failed: {}", directException.getMessage());
                                
                                // Try alternative approach: look for other completion methods
                                try {
                                    // Try to find any method that looks like it completes configuration
                                    java.lang.reflect.Method[] methods = ServerConfigurationPacketListenerImpl.class.getDeclaredMethods();
                                    for (java.lang.reflect.Method method : methods) {
                                        if (method.getName().contains("finish") || method.getName().contains("complete") || 
                                            method.getName().contains("transition") || method.getName().contains("play")) {
                                            
                                            if (method.getParameterCount() == 0) {
                                                method.setAccessible(true);
                                                method.invoke(self);
                                                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called {} for: {}", 
                                                    method.getName(), playerName);
                                                return;
                                            }
                                        }
                                    }
                                    
                                    LOGGER.warn("BedrockConfigurationFinishMixin: No suitable completion method found for: {}", playerName);
                                } catch (Exception methodException) {
                                    LOGGER.error("BedrockConfigurationFinishMixin: Method search failed for {}: {}", 
                                        playerName, methodException.getMessage());
                                }
                            }
                            
                            return;
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
}
