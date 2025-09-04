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
 * This mixin ensures Bedrock players properly finish configuration and
 * are spawned into the world correctly.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class, priority = 1500)
public class BedrockConfigurationFinishMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockConfigurationFinishMixin");
    
    // Track which players we've already handled to prevent duplicate processing
    private static final java.util.Set<String> handledPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * When startNextTask is called and there are no tasks for Bedrock players,
     * we need to immediately transition them to the play phase.
     */
    @Inject(
        method = "startNextTask",
        at = @At("HEAD")
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
                            LOGGER.info("BedrockConfigurationFinishMixin: No tasks remaining for Bedrock player {}, immediately transitioning to world", 
                                playerName);
                            
                            // Mark this player as handled to prevent duplicate processing
                            handledPlayers.add(playerName);
                            
                            // Send the client-side finish configuration packet first
                            try {
                                // Get the connection to send packet to client
                                java.lang.reflect.Field connectionField = self.getClass().getSuperclass().getDeclaredField("connection");
                                connectionField.setAccessible(true);
                                net.minecraft.network.Connection connection = (net.minecraft.network.Connection) connectionField.get(self);
                                
                                // Send ClientboundFinishConfigurationPacket to the client
                                try {
                                    Class<?> clientPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket");
                                    Object clientFinishPacket = clientPacketClass.getDeclaredConstructor().newInstance();
                                    connection.send((net.minecraft.network.protocol.Packet<?>) clientFinishPacket);
                                    LOGGER.info("BedrockConfigurationFinishMixin: Sent ClientboundFinishConfigurationPacket to client for: {}", playerName);
                                } catch (Exception clientPacketException) {
                                    LOGGER.debug("BedrockConfigurationFinishMixin: Could not send client finish packet: {}", clientPacketException.getMessage());
                                }
                            } catch (Exception connectionException) {
                                LOGGER.debug("BedrockConfigurationFinishMixin: Could not access connection: {}", connectionException.getMessage());
                            }
                            
                            // Handle the server-side transition directly without packet creation
                            LOGGER.info("BedrockConfigurationFinishMixin: Attempting direct server-side transition for: {}", playerName);
                            
                            // Skip packet creation and go directly to transition methods
                            forcePlayerWorldTransition(self, playerName);
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
     * Forces the player to transition to the world by calling necessary methods.
     */
    private void forcePlayerWorldTransition(ServerConfigurationPacketListenerImpl listener, String playerName) {
        try {
            // Method 1: Try returnToWorld which is the standard method for transitioning to play phase
            try {
                java.lang.reflect.Method returnToWorldMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("returnToWorld");
                returnToWorldMethod.setAccessible(true);
                returnToWorldMethod.invoke(listener);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called returnToWorld for: {}", playerName);
                return; // If this works, we're done
            } catch (Exception returnException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: returnToWorld failed: {}", returnException.getMessage());
            }
            
            // Method 2: Try finishConfiguration as an alternative
            try {
                java.lang.reflect.Method finishConfigMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                finishConfigMethod.setAccessible(true);
                finishConfigMethod.invoke(listener);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called finishConfiguration for: {}", playerName);
                return; // If this works, we're done
            } catch (Exception finishException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: finishConfiguration failed: {}", finishException.getMessage());
            }
            
            // Method 3: Try to access and call startNextTask to force completion
            try {
                java.lang.reflect.Method startNextTaskMethod = 
                    ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("startNextTask");
                startNextTaskMethod.setAccessible(true);
                startNextTaskMethod.invoke(listener);
                LOGGER.info("BedrockConfigurationFinishMixin: Successfully called startNextTask for: {}", playerName);
                return; // If this works, we're done
            } catch (Exception startTaskException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: startNextTask failed: {}", startTaskException.getMessage());
            }
            
            // Method 4: Try to force the connection to change protocol state
            try {
                // Get the connection and force protocol change
                java.lang.reflect.Field connectionField = listener.getClass().getSuperclass().getDeclaredField("connection");
                connectionField.setAccessible(true);
                net.minecraft.network.Connection connection = (net.minecraft.network.Connection) connectionField.get(listener);
                
                // Try to set the protocol to PLAY directly
                try {
                    java.lang.reflect.Field protocolField = connection.getClass().getDeclaredField("protocol");
                    protocolField.setAccessible(true);
                    Object playProtocol = net.minecraft.network.ConnectionProtocol.PLAY;
                    protocolField.set(connection, playProtocol);
                    LOGGER.info("BedrockConfigurationFinishMixin: Forced connection protocol to PLAY for: {}", playerName);
                } catch (Exception protocolException) {
                    LOGGER.debug("BedrockConfigurationFinishMixin: Could not set protocol: {}", protocolException.getMessage());
                }
            } catch (Exception connectionException) {
                LOGGER.debug("BedrockConfigurationFinishMixin: Could not access connection for protocol change: {}", connectionException.getMessage());
            }
            
            LOGGER.warn("BedrockConfigurationFinishMixin: All transition methods failed for player: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockConfigurationFinishMixin: Failed to force world transition for {}: {}", 
                playerName, e.getMessage());
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
