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
 * This mixin specifically targets NeoForge version-specific disconnect messages
 * like "Please use NeoForge 21.8.21" that include version numbers.
 */
@Mixin(value = ServerCommonPacketListenerImpl.class, priority = 900)
public class NeoForgeVersionSpecificMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoForgeVersionSpecificMixin");

    /**
     * Intercepts disconnect calls to prevent Bedrock players from being kicked for specific NeoForge versions.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventVersionSpecificDisconnect(Component reason, CallbackInfo ci) {
        try {
            ServerCommonPacketListenerImpl self = (ServerCommonPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null && reason != null) {
                String disconnectMessage = reason.getString();
                
                // Check if this is a version-specific NeoForge disconnect message
                // This catches messages like "Please use NeoForge 21.8.21" or similar
                if ((disconnectMessage.contains("NeoForge") && 
                     (disconnectMessage.contains("Please use") || disconnectMessage.contains("Please install"))) ||
                    (disconnectMessage.contains("Incompatible client") && disconnectMessage.contains("NeoForge"))) {
                    
                    boolean isBedrockPlayer = false;
                    
                    // Try to cast to ServerConfigurationPacketListenerImpl for BedrockDetectionHelper
                    if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl) {
                        isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer((net.minecraft.server.network.ServerConfigurationPacketListenerImpl) self);
                    } else if (self.getOwner() != null && self.getOwner().getName() != null) {
                        // Fallback to Floodgate naming convention check
                        isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(self.getOwner().getName());
                    }
                    
                    if (isBedrockPlayer) {
                        LOGGER.info("NeoForgeVersionSpecificMixin: Preventing version-specific NeoForge disconnect for Bedrock player: {} (Message: {})", 
                            self.getOwner().getName(), disconnectMessage);
                        
                        // Prevent the disconnect and help complete configuration safely
                        LOGGER.info("NeoForgeVersionSpecificMixin: Disconnect prevented, helping complete configuration for: {}", 
                            self.getOwner().getName());
                        
                        ci.cancel(); // Prevent the disconnect
                        
                        // Try a safer approach - just clear remaining NeoForge tasks and trigger next task
                        if (self instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl) {
                            net.minecraft.server.network.ServerConfigurationPacketListenerImpl configListener = 
                                (net.minecraft.server.network.ServerConfigurationPacketListenerImpl) self;
                            
                            try {
                                // Clear any remaining NeoForge configuration tasks
                                java.lang.reflect.Field tasksField = 
                                    net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                                tasksField.setAccessible(true);
                                @SuppressWarnings("unchecked")
                                java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                                    (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(configListener);
                                
                                // Remove NeoForge tasks
                                boolean removedTasks = tasks.removeIf(task -> {
                                    String taskClassName = task.getClass().getName();
                                    return taskClassName.contains("neoforge") || taskClassName.contains("SyncConfig");
                                });
                                
                                if (removedTasks) {
                                    LOGGER.info("NeoForgeVersionSpecificMixin: Removed NeoForge tasks for Bedrock player: {}", 
                                        configListener.getOwner().getName());
                                }
                                
                                // If no tasks remain, trigger startNextTask to complete configuration naturally
                                if (tasks.isEmpty()) {
                                    LOGGER.info("NeoForgeVersionSpecificMixin: No tasks remaining, triggering natural completion for: {}", 
                                        configListener.getOwner().getName());
                                    
                                    // Trigger startNextTask to let the natural flow complete configuration
                                    try {
                                        java.lang.reflect.Method startNextTaskMethod = 
                                            net.minecraft.server.network.ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("startNextTask");
                                        startNextTaskMethod.setAccessible(true);
                                        startNextTaskMethod.invoke(configListener);
                                        
                                        LOGGER.info("NeoForgeVersionSpecificMixin: Triggered natural completion flow for: {}", 
                                            configListener.getOwner().getName());
                                    } catch (Exception startException) {
                                        LOGGER.warn("NeoForgeVersionSpecificMixin: Could not trigger natural completion: {}", startException.getMessage());
                                    }
                                }
                                
                            } catch (Exception e) {
                                LOGGER.warn("NeoForgeVersionSpecificMixin: Could not clear NeoForge tasks for {}: {}", 
                                    configListener.getOwner().getName(), e.getMessage());
                            }
                        }
                        
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NeoForgeVersionSpecificMixin: Exception in version-specific disconnect prevention: {}", e.getMessage());
        }
    }
}
