package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.TickTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin provides safe execution of server tasks to prevent crashes
 * from NPEs in Floodgate skin application for Bedrock players.
 */
@Mixin(value = TickTask.class, priority = 900)
public class SafeTaskExecutionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("SafeTaskExecutionMixin");
    
    /**
     * Safely executes server tasks and catches NPEs from Floodgate skin application.
     * Uses a more aggressive approach to catch all TrackedEntity NPEs.
     */
    @Inject(
        method = "run",
        at = @At("HEAD"),
        cancellable = true
    )
    private void safeTaskExecution(CallbackInfo ci) {
        try {
            // Get the task runnable
            TickTask self = (TickTask) (Object) this;
            
            // Use reflection to access the task field
            try {
                java.lang.reflect.Field taskField = TickTask.class.getDeclaredField("task");
                taskField.setAccessible(true);
                Runnable task = (Runnable) taskField.get(self);
                
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
                        LOGGER.debug("SafeTaskExecutionMixin: toString() failed for task, skipping protection: {}", toStringException.getMessage());
                        return; // Let the task run normally
                    }
                    
                    if (isFloodgateTask && taskString != null) {
                        LOGGER.warn("SafeTaskExecutionMixin: DETECTED FLOODGATE TASK - Will execute with protection: {}", taskString);
                    }
                    
                    // Only apply NPE protection to Floodgate tasks to avoid interfering with Java players
                    if (isFloodgateTask) {
                        try {
                            // Execute the task with maximum protection for Floodgate tasks
                            task.run();
                            ci.cancel(); // We handled it, prevent double execution
                            return;
                    } catch (NullPointerException npe) {
                        String npeMessage = npe.getMessage();
                        String stackTrace = npe.getStackTrace() != null ? java.util.Arrays.toString(npe.getStackTrace()) : "null";
                        
                            LOGGER.info("SafeTaskExecutionMixin: Caught NPE - Message: {}, Task: {}", npeMessage, taskString != null ? taskString : "unknown");
                            LOGGER.info("SafeTaskExecutionMixin: NPE Stack trace: {}", stackTrace);
                            
                            // For Floodgate tasks, check if this is a TrackedEntity NPE
                            boolean shouldPreventNPE = false;
                            
                            // Check message for TrackedEntity-related content
                            if (npeMessage != null) {
                                shouldPreventNPE = npeMessage.contains("TrackedEntity") || 
                                                 npeMessage.contains("removePlayer") ||
                                                 npeMessage.contains("entry") ||
                                                 npeMessage.contains("ChunkMap") ||
                                                 npeMessage.contains("because \"entry\" is null") ||
                                                 npeMessage.contains("ModSkinApplier");
                            }
                            
                            // Check stack trace for Floodgate-related content
                            if (!shouldPreventNPE && stackTrace != null) {
                                shouldPreventNPE = stackTrace.contains("ModSkinApplier") ||
                                                 stackTrace.contains("floodgate") ||
                                                 stackTrace.contains("lambda$applySkin") ||
                                                 stackTrace.contains("TrackedEntity") ||
                                                 stackTrace.contains("removePlayer");
                            }
                            
                            // Check task string for Floodgate-related content
                            if (!shouldPreventNPE && taskString != null) {
                                shouldPreventNPE = taskString.contains("ModSkinApplier") ||
                                                 taskString.contains("floodgate") ||
                                                 taskString.contains("applySkin");
                            }
                            
                            if (shouldPreventNPE) {
                                LOGGER.info("SafeTaskExecutionMixin: PREVENTED NPE CRASH: {}", npeMessage);
                                ci.cancel(); // Prevent the crash
                                return;
                            } else {
                                // For clearly unrelated NPEs, re-throw them
                                LOGGER.debug("SafeTaskExecutionMixin: Re-throwing unrelated NPE: {}", npeMessage);
                                throw npe;
                            }
                        } catch (Exception e) {
                            // For non-NPE exceptions, log and re-throw
                            LOGGER.debug("SafeTaskExecutionMixin: Non-NPE exception in task execution: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                            throw e;
                        }
                    } else {
                        // For non-Floodgate tasks, let them run normally without any protection
                        // This prevents us from interfering with Java player tasks
                        return;
                    }
                }
            } catch (Exception reflectionException) {
                LOGGER.debug("SafeTaskExecutionMixin: Could not access task field: {}", reflectionException.getMessage());
            }
            
            // If we can't access the task or it's null, let the normal execution proceed
            
        } catch (Exception e) {
            LOGGER.debug("SafeTaskExecutionMixin: Exception in safe task execution setup: {}", e.getMessage());
        }
    }
}
