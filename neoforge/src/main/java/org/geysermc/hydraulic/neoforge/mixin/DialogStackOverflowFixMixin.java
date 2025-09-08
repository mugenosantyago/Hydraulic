package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the StackOverflowError caused by circular references in dialog toString() methods.
 * This prevents the infinite recursion that was crashing the server when dialog objects
 * reference each other in a circular manner.
 */
@Mixin(value = {
    net.minecraft.server.dialog.CommonDialogData.class,
    net.minecraft.server.dialog.MultiActionDialog.class,
    net.minecraft.server.dialog.ConfirmationDialog.class,
    net.minecraft.server.dialog.ActionButton.class,
    net.minecraft.server.dialog.action.StaticAction.class,
    net.minecraft.network.chat.ClickEvent.ShowDialog.class,
    net.minecraft.core.Holder.Reference.class
}, priority = 3000)
public class DialogStackOverflowFixMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("DialogStackOverflowFixMixin");
    
    // Thread-local to track toString() recursion depth
    private static final ThreadLocal<Integer> toStringDepth = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_TOSTRING_DEPTH = 10; // Prevent deep recursion
    
    /**
     * Intercepts toString() calls to prevent infinite recursion in dialog objects.
     */
    @Inject(
        method = "toString",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventToStringStackOverflow(CallbackInfoReturnable<String> cir) {
        try {
            int currentDepth = toStringDepth.get();
            
            if (currentDepth >= MAX_TOSTRING_DEPTH) {
                // We're in a deep recursion, break the cycle
                String className = this.getClass().getSimpleName();
                String safeString = className + "@" + Integer.toHexString(System.identityHashCode(this)) + "[RECURSION_PREVENTED]";
                
                LOGGER.debug("DialogStackOverflowFixMixin: Prevented toString() stack overflow for {} (depth: {})", 
                    className, currentDepth);
                
                cir.setReturnValue(safeString);
                return;
            }
            
            // Increment depth counter
            toStringDepth.set(currentDepth + 1);
            
        } catch (Exception e) {
            // If anything goes wrong, provide a safe fallback
            String className = this.getClass().getSimpleName();
            String safeString = className + "@" + Integer.toHexString(System.identityHashCode(this)) + "[ERROR_SAFE]";
            LOGGER.debug("DialogStackOverflowFixMixin: Exception in toString() protection, using safe fallback: {}", e.getMessage());
            cir.setReturnValue(safeString);
        }
    }
    
    /**
     * Clean up the thread-local depth counter after toString() completes.
     */
    @Inject(
        method = "toString",
        at = @At("RETURN")
    )
    private void cleanupToStringDepth(CallbackInfoReturnable<String> cir) {
        try {
            int currentDepth = toStringDepth.get();
            if (currentDepth > 0) {
                toStringDepth.set(currentDepth - 1);
            }
            
            // Clean up completely when we return to depth 0
            if (currentDepth <= 1) {
                toStringDepth.remove();
            }
        } catch (Exception e) {
            // If cleanup fails, just remove the thread local to be safe
            toStringDepth.remove();
        }
    }
}
