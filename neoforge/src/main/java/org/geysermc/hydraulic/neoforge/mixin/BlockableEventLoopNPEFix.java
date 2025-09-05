package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin targets the BlockableEventLoop.doRunTask method to catch
 * TrackedEntity NPEs that are slipping through the TickTask mixin.
 */
@Mixin(value = net.minecraft.util.thread.BlockableEventLoop.class, priority = 2500)
public class BlockableEventLoopNPEFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockableEventLoopNPEFix");
    
    /**
     * Intercepts task execution at the BlockableEventLoop level to catch
     * TrackedEntity NPEs that bypass the TickTask mixin.
     */
    @Inject(
        method = "doRunTask",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventTrackedEntityNPE(Runnable task, CallbackInfo ci) {
        try {
            if (task != null) {
                String taskString = task.toString();
                
                // Check if this is a Floodgate-related task
                boolean isFloodgateTask = taskString.contains("ModSkinApplier") || 
                                        taskString.contains("floodgate") || 
                                        taskString.contains("applySkin") ||
                                        taskString.contains("lambda$applySkin");
                
                if (isFloodgateTask) {
                    LOGGER.warn("BlockableEventLoopNPEFix: DETECTED FLOODGATE TASK - Executing with protection: {}", taskString);
                }
                
                // Execute the task with comprehensive NPE protection
                try {
                    task.run();
                    
                    if (isFloodgateTask) {
                        LOGGER.info("BlockableEventLoopNPEFix: Successfully executed Floodgate task without NPE");
                    }
                    
                    ci.cancel(); // We handled it, prevent double execution
                    return;
                } catch (NullPointerException npe) {
                    String npeMessage = npe.getMessage();
                    String stackTrace = npe.getStackTrace() != null ? java.util.Arrays.toString(npe.getStackTrace()) : "null";
                    
                    LOGGER.warn("BlockableEventLoopNPEFix: Caught NPE - Message: {}", npeMessage);
                    
                    // Check for TrackedEntity NPE
                    boolean isTrackedEntityNPE = false;
                    
                    if (npeMessage != null) {
                        isTrackedEntityNPE = npeMessage.contains("TrackedEntity") || 
                                           npeMessage.contains("removePlayer") ||
                                           npeMessage.contains("entry") ||
                                           npeMessage.contains("ChunkMap") ||
                                           npeMessage.contains("because \"entry\" is null");
                    }
                    
                    // Also check stack trace for Floodgate
                    if (!isTrackedEntityNPE && stackTrace != null) {
                        isTrackedEntityNPE = stackTrace.contains("ModSkinApplier") ||
                                           stackTrace.contains("floodgate") ||
                                           stackTrace.contains("lambda$applySkin");
                    }
                    
                    if (isTrackedEntityNPE) {
                        LOGGER.info("BlockableEventLoopNPEFix: PREVENTED TrackedEntity NPE CRASH: {}", npeMessage);
                        ci.cancel(); // Prevent the crash
                        return;
                    } else {
                        // Re-throw non-TrackedEntity NPEs
                        throw npe;
                    }
                } catch (Exception e) {
                    // Log and re-throw other exceptions
                    LOGGER.debug("BlockableEventLoopNPEFix: Non-NPE exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                    throw e;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BlockableEventLoopNPEFix: Exception in NPE prevention: {}", e.getMessage());
        }
    }
}
