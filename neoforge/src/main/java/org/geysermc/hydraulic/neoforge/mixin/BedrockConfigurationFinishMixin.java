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
                            
                            // Send finish configuration packet on behalf of the Bedrock client
                            try {
                                // Get the connection to send the packet
                                java.lang.reflect.Field connectionField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("connection");
                                connectionField.setAccessible(true);
                                net.minecraft.network.Connection connection = (net.minecraft.network.Connection) connectionField.get(self);
                                
                                // Create and send a finish configuration packet
                                Class<?> finishPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
                                Object finishPacket = finishPacketClass.getDeclaredConstructor().newInstance();
                                
                                // Send the packet to the connection which should trigger handleConfigurationFinished
                                java.lang.reflect.Method sendMethod = net.minecraft.network.Connection.class.getDeclaredMethod("send", 
                                    net.minecraft.network.protocol.Packet.class);
                                sendMethod.setAccessible(true);
                                sendMethod.invoke(connection, finishPacket);
                                
                                LOGGER.info("BedrockConfigurationFinishMixin: Successfully sent finish configuration packet for Bedrock player: {}", 
                                    playerName);
                                
                            } catch (Exception packetException) {
                                LOGGER.warn("BedrockConfigurationFinishMixin: Could not send finish packet for {}: {}", 
                                    playerName, packetException.getMessage());
                                
                                // If packet sending fails, try direct method invocation
                                try {
                                    java.lang.reflect.Method handleFinishMethod = 
                                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", 
                                            Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket"));
                                    handleFinishMethod.setAccessible(true);
                                    
                                    Class<?> finishPacketClass = Class.forName("net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket");
                                    Object finishPacket = finishPacketClass.getDeclaredConstructor().newInstance();
                                    handleFinishMethod.invoke(self, finishPacket);
                                    
                                    LOGGER.info("BedrockConfigurationFinishMixin: Successfully called handleConfigurationFinished directly for: {}", 
                                        playerName);
                                } catch (Exception directException) {
                                    LOGGER.error("BedrockConfigurationFinishMixin: All completion methods failed for {}: {}", 
                                        playerName, directException.getMessage());
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
