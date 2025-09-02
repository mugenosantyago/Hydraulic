package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin targets the ServerCommonPacketListenerImpl (parent class) to catch disconnects
 * that might be happening at a higher level in the class hierarchy.
 */
@Mixin(value = ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ServerCommonPacketListenerMixin");

    /**
     * Intercepts disconnect calls at the common packet listener level.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventCommonDisconnectForBedrock(Component reason, CallbackInfo ci) {
        try {
            if (reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is the NeoForge version check disconnect message
                if (disconnectMessage.contains("trying to connect to a server that is running NeoForge") ||
                    disconnectMessage.contains("Please install NeoForge")) {
                    
                    ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
                    
                    // Try to determine if this is a Bedrock player
                    boolean isBedrockPlayer = false;
                    String playerName = null;
                    
                    try {
                        // Check if we can get player info
                        if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                            isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(configListener);
                            if (configListener.getOwner() != null) {
                                playerName = configListener.getOwner().getName();
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("ServerCommonPacketListenerMixin: Could not determine player type: {}", e.getMessage());
                    }
                    
                    // If we detected a Bedrock player, prevent the disconnect
                    if (isBedrockPlayer) {
                        LOGGER.info("ServerCommonPacketListenerMixin: Preventing NeoForge common-level disconnect for Bedrock player: {} (Message: {})", 
                            playerName, disconnectMessage);
                        
                        // Try to help the connection complete by transitioning to play phase
                        try {
                            if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener) {
                                LOGGER.info("ServerCommonPacketListenerMixin: Attempting to complete configuration for Bedrock player: {}", playerName);
                                
                                // First, clear any remaining tasks from the queue
                                try {
                                    java.lang.reflect.Field tasksField = net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                                    tasksField.setAccessible(true);
                                    @SuppressWarnings("unchecked")
                                    java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                                        (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(configListener);
                                    
                                    int removedTasks = 0;
                                    while (!tasks.isEmpty()) {
                                        net.minecraft.server.network.ConfigurationTask task = tasks.poll();
                                        removedTasks++;
                                        LOGGER.info("ServerCommonPacketListenerMixin: Removed task: {}", task.getClass().getName());
                                    }
                                    
                                    if (removedTasks > 0) {
                                        LOGGER.info("ServerCommonPacketListenerMixin: Cleared {} remaining tasks for Bedrock player: {}", removedTasks, playerName);
                                    }
                                } catch (Exception taskException) {
                                    LOGGER.debug("ServerCommonPacketListenerMixin: Could not clear task queue: {}", taskException.getMessage());
                                }
                                
                                // Now try to finish the configuration
                                try {
                                    java.lang.reflect.Method finishMethod = 
                                        net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                                    finishMethod.setAccessible(true);
                                    finishMethod.invoke(configListener);
                                    LOGGER.info("ServerCommonPacketListenerMixin: Successfully completed configuration for Bedrock player: {}", playerName);
                                } catch (Exception methodException) {
                                    LOGGER.debug("ServerCommonPacketListenerMixin: Could not call finishConfiguration: {}", methodException.getMessage());
                                    
                                    // Try alternative completion methods
                                    try {
                                        // Try to transition to play phase directly
                                        java.lang.reflect.Method switchToPlayMethod = 
                                            net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("switchToPlayPhase");
                                        switchToPlayMethod.setAccessible(true);
                                        switchToPlayMethod.invoke(configListener);
                                        LOGGER.info("ServerCommonPacketListenerMixin: Successfully switched to play phase for Bedrock player: {}", playerName);
                                    } catch (Exception switchException) {
                                        LOGGER.debug("ServerCommonPacketListenerMixin: Could not switch to play phase: {}", switchException.getMessage());
                                    }
                                }
                            }
                        } catch (Exception completionException) {
                            LOGGER.debug("ServerCommonPacketListenerMixin: Exception during configuration completion: {}", completionException.getMessage());
                        }
                        
                        ci.cancel();
                        return;
                    }
                    
                    // Even if we couldn't definitively identify the player, log this for debugging
                    LOGGER.info("ServerCommonPacketListenerMixin: Detected NeoForge disconnect message: {} (Player: {})", 
                        disconnectMessage, playerName != null ? playerName : "unknown");
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ServerCommonPacketListenerMixin: Exception in common disconnect prevention: {}", e.getMessage());
        }
    }
}
