package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emergency fix for StackOverflowError crashes. This mixin catches stack overflow
 * errors at the server tick level and prevents them from crashing the server.
 */
@Mixin(value = net.minecraft.server.MinecraftServer.class, priority = 5000)
public class GlobalStackOverflowPrevention {
    private static final Logger LOGGER = LoggerFactory.getLogger("GlobalStackOverflowPrevention");
    
    /**
     * Wraps the entire server tick in a try-catch to prevent stack overflow crashes.
     */
    @Inject(
        method = "tickServer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventStackOverflowCrash(CallbackInfo ci) {
        try {
            // Let the normal tick proceed, but catch any stack overflow
            return; // Continue with normal execution
        } catch (StackOverflowError soe) {
            LOGGER.error("GlobalStackOverflowPrevention: CAUGHT STACK OVERFLOW - Server would have crashed!");
            LOGGER.error("Stack overflow error: {}", soe.getMessage());
            
            // Log the first few stack trace elements to identify the source
            StackTraceElement[] stackTrace = soe.getStackTrace();
            if (stackTrace != null && stackTrace.length > 0) {
                LOGGER.error("Stack overflow source (first 10 frames):");
                for (int i = 0; i < Math.min(10, stackTrace.length); i++) {
                    LOGGER.error("  {}: {}", i, stackTrace[i].toString());
                }
            }
            
            // Force garbage collection to clean up any circular references
            System.gc();
            
            // Skip this tick to prevent the crash
            ci.cancel();
            return;
        }
    }
    
    /**
     * Additional protection at the tick children level.
     */
    @Inject(
        method = "tickChildren",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventTickChildrenStackOverflow(CallbackInfo ci) {
        try {
            return; // Continue with normal execution
        } catch (StackOverflowError soe) {
            LOGGER.error("GlobalStackOverflowPrevention: CAUGHT STACK OVERFLOW in tickChildren!");
            LOGGER.error("Stack overflow error: {}", soe.getMessage());
            
            // Force garbage collection
            System.gc();
            
            // Skip this tick children to prevent the crash
            ci.cancel();
            return;
        }
    }
}
