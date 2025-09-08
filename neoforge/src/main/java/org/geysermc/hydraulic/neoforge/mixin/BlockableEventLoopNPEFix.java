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
                // SAFETY: Only call toString() if we suspect this might be a Floodgate task
                // to avoid stack overflow from dialog circular references on Java players
                String taskString = null;
                boolean isFloodgateTask = false;
                
                try {
                    // Safe check for Floodgate-related tasks using class name first
                    String taskClassName = task.getClass().getName();
                    if (taskClassName.contains("ModSkinApplier") || 
                        taskClassName.contains("floodgate") || 
                        taskClassName.contains("applySkin")) {
                        // Only call toString() if we're confident this is a Floodgate task
                        taskString = task.toString();
                        isFloodgateTask = taskString.contains("ModSkinApplier") || 
                                        taskString.contains("floodgate") || 
                                        taskString.contains("applySkin") ||
                                        taskString.contains("lambda$applySkin");
                    }
                } catch (Exception toStringException) {
                    // If toString() fails (like with circular references), treat as non-Floodgate task
                    LOGGER.debug("BlockableEventLoopNPEFix: toString() failed for task, skipping protection: {}", toStringException.getMessage());
                    return; // Let the task run normally
                }
                
                if (isFloodgateTask && taskString != null) {
                    LOGGER.warn("BlockableEventLoopNPEFix: DETECTED FLOODGATE TASK - Executing with protection: {}", taskString);
                }
                
                // Execute the task with comprehensive NPE protection only for Floodgate tasks
                if (isFloodgateTask) {
                    try {
                        task.run();
                        LOGGER.info("BlockableEventLoopNPEFix: Successfully executed Floodgate task without NPE");
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
                            
                            // Instead of completely canceling, try to complete the skin application safely
                            // by scheduling a delayed retry when TrackedEntity might be ready
                            if (stackTrace != null && (stackTrace.contains("ModSkinApplier") || stackTrace.contains("floodgate"))) {
                                LOGGER.info("BlockableEventLoopNPEFix: Scheduling delayed skin application retry");
                                
                                // Try to extract the player from the task if possible
                                try {
                                    // Use the taskString we already have or safely get it
                                    String taskStr = taskString != null ? taskString : "unknown task";
                                    if (taskStr.contains("ServerPlayer") || stackTrace.contains("ServerPlayer")) {
                                        // Schedule a delayed skin application attempt
                                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                                            try {
                                                LOGGER.debug("BlockableEventLoopNPEFix: Attempting delayed skin application");
                                                // Try to run the task again after a delay when TrackedEntity might be ready
                                                task.run();
                                            } catch (Exception delayedException) {
                                                LOGGER.debug("BlockableEventLoopNPEFix: Delayed skin application also failed: {}", delayedException.getMessage());
                                            }
                                        }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
                                    }
                                } catch (Exception scheduleException) {
                                    LOGGER.debug("BlockableEventLoopNPEFix: Could not schedule delayed retry: {}", scheduleException.getMessage());
                                }
                            }
                            
                            ci.cancel(); // Prevent the immediate crash
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
                } else {
                    // For non-Floodgate tasks, let them run normally without any protection
                    // This prevents us from interfering with Java player tasks
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BlockableEventLoopNPEFix: Exception in NPE prevention: {}", e.getMessage());
        }
    }
}
