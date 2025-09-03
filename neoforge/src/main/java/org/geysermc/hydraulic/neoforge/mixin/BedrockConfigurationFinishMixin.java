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
                            
                            // Try to force completion by calling the transition method directly
                            try {
                                // Look for a method that transitions to play phase
                                java.lang.reflect.Method[] methods = ServerConfigurationPacketListenerImpl.class.getDeclaredMethods();
                                for (java.lang.reflect.Method method : methods) {
                                    // Look for methods that might transition to play phase
                                    if (method.getName().contains("switchToMain") || 
                                        method.getName().contains("switchToPlay") ||
                                        method.getName().contains("transitionTo") ||
                                        method.getName().equals("finishCurrentTask")) {
                                        
                                        if (method.getParameterCount() == 0) {
                                            method.setAccessible(true);
                                            method.invoke(self);
                                            LOGGER.info("BedrockConfigurationFinishMixin: Successfully called {} for: {}", 
                                                method.getName(), playerName);
                                            ci.cancel();
                                            return;
                                        }
                                    }
                                }
                                
                                // If no transition method found, try to send the client finish configuration packet
                                try {
                                    // Get the connection and send the finish configuration packet to the client
                                    java.lang.reflect.Method getConnectionMethod = 
                                        net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredMethod("getConnection");
                                    getConnectionMethod.setAccessible(true);
                                    Object connection = getConnectionMethod.invoke(self);
                                    
                                    // Create and send the ClientboundFinishConfigurationPacket
                                    Class<?> clientFinishPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket");
                                    Object clientFinishPacket = clientFinishPacketClass.getDeclaredConstructor().newInstance();
                                    
                                    java.lang.reflect.Method sendMethod = connection.getClass().getDeclaredMethod("send", 
                                        Class.forName("net.minecraft.network.protocol.Packet"));
                                    sendMethod.setAccessible(true);
                                    sendMethod.invoke(connection, clientFinishPacket);
                                    
                                    LOGGER.info("BedrockConfigurationFinishMixin: Sent ClientboundFinishConfigurationPacket to Bedrock player: {}", playerName);
                                    ci.cancel();
                                    return;
                                    
                                } catch (Exception sendException) {
                                    LOGGER.debug("BedrockConfigurationFinishMixin: Failed to send finish packet: {}", sendException.getMessage());
                                }
                                
                                // Final fallback: just let the natural flow continue without canceling
                                LOGGER.info("BedrockConfigurationFinishMixin: Allowing natural completion flow for Bedrock player: {}", playerName);
                                
                            } catch (Exception completionException) {
                                LOGGER.error("BedrockConfigurationFinishMixin: Exception during completion attempt for {}: {}", 
                                    playerName, completionException.getMessage());
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
