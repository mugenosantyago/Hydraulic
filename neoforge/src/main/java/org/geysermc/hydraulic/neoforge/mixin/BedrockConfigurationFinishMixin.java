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
                            
                            // Cancel the original startNextTask since we have no tasks
                            ci.cancel();
                            
                            // Try to directly trigger the configuration finish process
                            try {
                                // First, try to get the server and create a new game packet listener
                                java.lang.reflect.Method getServerMethod = 
                                    net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredMethod("getServer");
                                getServerMethod.setAccessible(true);
                                Object server = getServerMethod.invoke(self);
                                
                                java.lang.reflect.Method getConnectionMethod = 
                                    net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredMethod("getConnection");
                                getConnectionMethod.setAccessible(true);
                                Object connection = getConnectionMethod.invoke(self);
                                
                                // Try to transition directly to game phase
                                // Look for the switchToMain method or similar
                                try {
                                    java.lang.reflect.Method switchMethod = connection.getClass().getDeclaredMethod("switchToMain", 
                                        Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl"));
                                    
                                    // Create a new game packet listener
                                    Class<?> gameListenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                                    java.lang.reflect.Constructor<?> gameConstructor = gameListenerClass.getDeclaredConstructor(
                                        Class.forName("net.minecraft.server.MinecraftServer"),
                                        Class.forName("net.minecraft.network.Connection"),
                                        Class.forName("net.minecraft.server.level.ServerPlayer"),
                                        Class.forName("net.minecraft.server.network.CommonListenerCookie"));
                                    
                                    // Get the player
                                    java.lang.reflect.Method getOwnerMethod = 
                                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("getOwner");
                                    getOwnerMethod.setAccessible(true);
                                    Object owner = getOwnerMethod.invoke(self);
                                    
                                    // Get cookie from the configuration listener
                                    java.lang.reflect.Field cookieField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("cookie");
                                    cookieField.setAccessible(true);
                                    Object cookie = cookieField.get(self);
                                    
                                    Object gameListener = gameConstructor.newInstance(server, connection, owner, cookie);
                                    
                                    switchMethod.setAccessible(true);
                                    switchMethod.invoke(connection, gameListener);
                                    
                                    LOGGER.info("BedrockConfigurationFinishMixin: Successfully switched to game phase for: {}", playerName);
                                    return;
                                    
                                } catch (Exception switchException) {
                                    LOGGER.debug("BedrockConfigurationFinishMixin: Failed to switch to game phase: {}", switchException.getMessage());
                                }
                                
                                // Alternative: Try to send the finish configuration packet to client first
                                try {
                                    Class<?> clientFinishPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket");
                                    Object clientFinishPacket = clientFinishPacketClass.getDeclaredConstructor().newInstance();
                                    
                                    java.lang.reflect.Method sendMethod = connection.getClass().getDeclaredMethod("send", 
                                        Class.forName("net.minecraft.network.protocol.Packet"));
                                    sendMethod.setAccessible(true);
                                    sendMethod.invoke(connection, clientFinishPacket);
                                    
                                    LOGGER.info("BedrockConfigurationFinishMixin: Sent ClientboundFinishConfigurationPacket to trigger completion for: {}", playerName);
                                    
                                    // After sending the packet, try to simulate the client response
                                    try {
                                        Class<?> serverboundFinishPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
                                        Object serverboundFinishPacket = serverboundFinishPacketClass.getDeclaredConstructor().newInstance();
                                        
                                        java.lang.reflect.Method handleFinishMethod = 
                                            ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", serverboundFinishPacketClass);
                                        handleFinishMethod.setAccessible(true);
                                        handleFinishMethod.invoke(self, serverboundFinishPacket);
                                        
                                        LOGGER.info("BedrockConfigurationFinishMixin: Successfully simulated client finish response for: {}", playerName);
                                        return;
                                        
                                    } catch (Exception handleException) {
                                        LOGGER.debug("BedrockConfigurationFinishMixin: Failed to handle finish packet: {}", handleException.getMessage());
                                    }
                                    
                                } catch (Exception packetException) {
                                    LOGGER.debug("BedrockConfigurationFinishMixin: Failed to send client finish packet: {}", packetException.getMessage());
                                }
                                
                            } catch (Exception e) {
                                LOGGER.error("BedrockConfigurationFinishMixin: Failed to complete configuration for {}: {}", 
                                    playerName, e.getMessage());
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
