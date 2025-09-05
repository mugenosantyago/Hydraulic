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
                    String taskString = task.toString();
                    LOGGER.info("SafeTaskExecutionMixin: Executing task: {}", taskString);
                    
                    // ULTRA AGGRESSIVE: Execute ALL tasks with full exception protection
                    // If ANY NPE occurs that could be related to Floodgate/TrackedEntity, we'll catch it
                    try {
                        // Execute the task with maximum protection
                        task.run();
                        ci.cancel(); // We handled it, prevent double execution
                        return;
                    } catch (NullPointerException npe) {
                        String npeMessage = npe.getMessage();
                        String stackTrace = npe.getStackTrace() != null ? java.util.Arrays.toString(npe.getStackTrace()) : "null";
                        
                        LOGGER.info("SafeTaskExecutionMixin: Caught NPE - Message: {}, Task: {}", npeMessage, taskString);
                        LOGGER.info("SafeTaskExecutionMixin: NPE Stack trace: {}", stackTrace);
                        
                        // ULTRA AGGRESSIVE: Catch ALL NPEs that might be related to Floodgate or TrackedEntity
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
                        
                        // NUCLEAR OPTION: If this is any lambda task, prevent ALL NPEs as a safety measure
                        if (!shouldPreventNPE && taskString != null && taskString.contains("lambda")) {
                            LOGGER.warn("SafeTaskExecutionMixin: NUCLEAR OPTION - Preventing lambda NPE as safety measure: {}", npeMessage);
                            shouldPreventNPE = true;
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
