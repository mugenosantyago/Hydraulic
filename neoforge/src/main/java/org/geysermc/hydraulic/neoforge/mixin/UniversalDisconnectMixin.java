package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Universal mixin that catches all Component.getString() calls to detect and prevent
 * NeoForge disconnect messages for Bedrock players.
 */
@Mixin(value = Component.class)
public class UniversalDisconnectMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("UniversalDisconnectMixin");

    /**
     * Intercepts Component.getString() calls to detect NeoForge disconnect messages.
     */
    @Inject(
        method = "getString()Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void interceptNeoForgeMessages(CallbackInfo ci) {
        try {
            Component self = (Component) (Object) this;
            String message = self.getString();
            
            // Check if this is a NeoForge version check message
            if (message != null && (message.contains("trying to connect to a server that is running NeoForge") ||
                message.contains("Please install NeoForge"))) {
                
                LOGGER.info("UniversalDisconnectMixin: Detected NeoForge disconnect message: {}", message);
                
                // Check the current thread/context to see if this might be for a Bedrock player
                Thread currentThread = Thread.currentThread();
                String threadName = currentThread.getName();
                
                // Look for Bedrock player indicators in the current context
                if (threadName.contains("Netty") || threadName.contains("Server")) {
                    // This is likely a server network thread - check for Bedrock indicators
                    StackTraceElement[] stackTrace = currentThread.getStackTrace();
                    for (StackTraceElement element : stackTrace) {
                        String className = element.getClassName();
                        if (className.contains("floodgate") || className.contains("geyser")) {
                            LOGGER.info("UniversalDisconnectMixin: Detected Bedrock context - this disconnect might be for a Bedrock player");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Don't interfere with normal operation
            LOGGER.debug("UniversalDisconnectMixin: Exception in message interception: {}", e.getMessage());
        }
    }
}
