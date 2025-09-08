package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Targets the specific StringConcatHelper.stringOf method that was causing 
 * the stack overflow in the crash report. This prevents infinite recursion
 * when converting objects to strings.
 */
@Mixin(value = java.lang.StringConcatHelper.class, priority = 6000)
public class StringConcatHelperFixMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("StringConcatHelperFixMixin");
    
    // Thread-local to track stringOf recursion depth
    private static final ThreadLocal<Integer> stringOfDepth = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_STRINGOF_DEPTH = 50; // Allow reasonable depth but prevent infinite recursion
    
    /**
     * Intercepts StringConcatHelper.stringOf to prevent infinite recursion.
     * This was the exact method shown in the stack overflow crash.
     */
    @Inject(
        method = "stringOf",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void preventStringOfStackOverflow(Object value, CallbackInfoReturnable<String> cir) {
        try {
            int currentDepth = stringOfDepth.get();
            
            if (currentDepth >= MAX_STRINGOF_DEPTH) {
                // We're in deep recursion, break the cycle
                String safeString;
                if (value == null) {
                    safeString = "null";
                } else {
                    String className = value.getClass().getSimpleName();
                    safeString = className + "@" + Integer.toHexString(System.identityHashCode(value)) + "[RECURSION_PREVENTED_DEPTH_" + currentDepth + "]";
                }
                
                LOGGER.warn("StringConcatHelperFixMixin: Prevented stringOf() stack overflow (depth: {}) - returning safe string: {}", 
                    currentDepth, safeString);
                
                cir.setReturnValue(safeString);
                return;
            }
            
            // Increment depth counter
            stringOfDepth.set(currentDepth + 1);
            
        } catch (Exception e) {
            // If anything goes wrong, provide a safe fallback
            String safeString = value != null ? 
                (value.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(value)) + "[ERROR_SAFE]") : 
                "null[ERROR_SAFE]";
            LOGGER.warn("StringConcatHelperFixMixin: Exception in stringOf() protection, using safe fallback: {}", e.getMessage());
            cir.setReturnValue(safeString);
        }
    }
    
    /**
     * Clean up the thread-local depth counter after stringOf() completes.
     */
    @Inject(
        method = "stringOf",
        at = @At("RETURN")
    )
    private static void cleanupStringOfDepth(Object value, CallbackInfoReturnable<String> cir) {
        try {
            int currentDepth = stringOfDepth.get();
            if (currentDepth > 0) {
                stringOfDepth.set(currentDepth - 1);
            }
            
            // Clean up completely when we return to depth 0
            if (currentDepth <= 1) {
                stringOfDepth.remove();
            }
        } catch (Exception e) {
            // If cleanup fails, just remove the thread local to be safe
            stringOfDepth.remove();
        }
    }
}
