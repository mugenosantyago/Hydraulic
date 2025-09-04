package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin intercepts when the server is about to call returnToWorld
 * and ensures the client receives the finish configuration packet first.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class BedrockClientFinishMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockClientFinishMixin");
    
    /**
     * Hook into returnToWorld to send the client finish packet before transitioning.
     */
    @Inject(
        method = "returnToWorld",
        at = @At("HEAD")
    )
    private void sendClientFinishBeforeReturn(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null && BedrockDetectionHelper.isBedrockPlayer(self)) {
                String playerName = self.getOwner().getName();
                LOGGER.info("BedrockClientFinishMixin: Intercepting returnToWorld for Bedrock player: {}", playerName);
                
                // Get the connection through reflection
                try {
                    java.lang.reflect.Field connectionField = self.getClass().getSuperclass().getDeclaredField("connection");
                    connectionField.setAccessible(true);
                    Connection connection = (Connection) connectionField.get(self);
                    
                    // Send the finish configuration packet to the client
                    try {
                        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket");
                        Object finishPacket = packetClass.getDeclaredConstructor().newInstance();
                        connection.send((Packet<?>) finishPacket);
                        LOGGER.info("BedrockClientFinishMixin: Successfully sent ClientboundFinishConfigurationPacket to: {}", playerName);
                    } catch (Exception packetException) {
                        LOGGER.error("BedrockClientFinishMixin: Failed to create ClientboundFinishConfigurationPacket: {}", packetException.getMessage());
                    }
                    
                    // Also try to flush the connection to ensure the packet is sent immediately
                    try {
                        connection.flushChannel();
                        LOGGER.info("BedrockClientFinishMixin: Flushed connection for immediate packet delivery to: {}", playerName);
                    } catch (Exception flushException) {
                        LOGGER.debug("BedrockClientFinishMixin: Could not flush connection: {}", flushException.getMessage());
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("BedrockClientFinishMixin: Failed to send client finish packet: {}", e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockClientFinishMixin: Exception in returnToWorld hook: {}", e.getMessage());
        }
    }
    
    /**
     * Also hook into finishConfiguration to ensure packet is sent.
     */
    @Inject(
        method = "finishConfiguration",
        at = @At("HEAD")
    )
    private void sendClientFinishBeforeFinish(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null && BedrockDetectionHelper.isBedrockPlayer(self)) {
                String playerName = self.getOwner().getName();
                LOGGER.info("BedrockClientFinishMixin: Intercepting finishConfiguration for Bedrock player: {}", playerName);
                
                // Get the connection through reflection
                try {
                    java.lang.reflect.Field connectionField = self.getClass().getSuperclass().getDeclaredField("connection");
                    connectionField.setAccessible(true);
                    Connection connection = (Connection) connectionField.get(self);
                    
                    // Send the finish configuration packet to the client
                    try {
                        Class<?> packetClass = Class.forName("net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket");
                        Object finishPacket = packetClass.getDeclaredConstructor().newInstance();
                        connection.send((Packet<?>) finishPacket);
                        LOGGER.info("BedrockClientFinishMixin: Successfully sent ClientboundFinishConfigurationPacket during finishConfiguration to: {}", playerName);
                    } catch (Exception packetException) {
                        LOGGER.error("BedrockClientFinishMixin: Failed to create ClientboundFinishConfigurationPacket: {}", packetException.getMessage());
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("BedrockClientFinishMixin: Failed to send client finish packet during finishConfiguration: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockClientFinishMixin: Exception in finishConfiguration hook: {}", e.getMessage());
        }
    }
}
