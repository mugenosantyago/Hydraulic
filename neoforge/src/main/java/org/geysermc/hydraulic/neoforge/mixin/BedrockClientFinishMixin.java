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
     * Hook into returnToWorld to ensure proper state transition for Bedrock players.
     */
    @Inject(
        method = "returnToWorld",
        at = @At("HEAD")
    )
    private void ensureProperTransitionForBedrock(CallbackInfo ci) {
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
                    
                    // Try to trigger the client finish through the send method
                    try {
                        // First, try to find and call the sendConfigurationFinishedPacket method if it exists
                        java.lang.reflect.Method sendFinishMethod = self.getClass().getDeclaredMethod("sendConfigurationFinishedPacket");
                        sendFinishMethod.setAccessible(true);
                        sendFinishMethod.invoke(self);
                        LOGGER.info("BedrockClientFinishMixin: Successfully called sendConfigurationFinishedPacket for: {}", playerName);
                    } catch (NoSuchMethodException nsme) {
                        LOGGER.debug("BedrockClientFinishMixin: sendConfigurationFinishedPacket method not found, trying alternative approach");
                        
                        // Alternative: Try to trigger it through the connection's protocol state
                        try {
                            // Force flush any pending packets before transition
                            connection.flushChannel();
                            LOGGER.info("BedrockClientFinishMixin: Flushed connection before transition for: {}", playerName);
                        } catch (Exception flushException) {
                            LOGGER.debug("BedrockClientFinishMixin: Could not flush connection: {}", flushException.getMessage());
                        }
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("BedrockClientFinishMixin: Failed during transition preparation: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockClientFinishMixin: Exception in returnToWorld hook: {}", e.getMessage());
        }
    }
    
}
