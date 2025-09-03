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
@Mixin(value = ServerConfigurationPacketListenerImpl.class, priority = 1500)
public class BedrockConfigurationFinishMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockConfigurationFinishMixin");
    
    // Track which players we've already handled to prevent duplicate processing
    private static final java.util.Set<String> handledPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
                            LOGGER.info("BedrockConfigurationFinishMixin: No tasks remaining for Bedrock player {}, sending finish configuration", 
                                playerName);
                            
                            // Mark this player as handled to prevent duplicate processing
                            handledPlayers.add(playerName);
                            
                            // Don't cancel the original flow - let it try to complete naturally first
                            // This ensures all dimension data and mod initialization happens properly
                            LOGGER.info("BedrockConfigurationFinishMixin: Allowing natural flow, will use returnToWorld as fallback for: {}", playerName);
                            
                            // Schedule returnToWorld as a fallback in case the natural flow gets stuck
                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try {
                                    // Wait longer to let the natural configuration process attempt to complete
                                    // This gives time for Good Night's Sleep dimensions and other mod data to initialize
                                    Thread.sleep(2000); // 2 second delay
                                    
                                    LOGGER.info("BedrockConfigurationFinishMixin: Fallback timer expired, calling returnToWorld for: {}", playerName);
                                    
                                    // Try to call returnToWorld method as fallback
                                    java.lang.reflect.Method returnToWorldMethod = 
                                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("returnToWorld");
                                    returnToWorldMethod.setAccessible(true);
                                    returnToWorldMethod.invoke(self);
                                    
                                    LOGGER.info("BedrockConfigurationFinishMixin: Successfully called fallback returnToWorld for: {}", playerName);
                                    
                                } catch (Exception returnException) {
                                    LOGGER.error("BedrockConfigurationFinishMixin: Fallback returnToWorld failed for {}: {}", 
                                        playerName, returnException.getMessage());
                                }
                            });
                            
                            // Don't cancel - let the original startNextTask run to allow natural completion
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
