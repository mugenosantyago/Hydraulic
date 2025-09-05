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
                    
                    // Always execute ALL tasks with comprehensive NPE protection
                    // This is more aggressive but necessary to catch the TrackedEntity NPE
                    try {
                        // Execute the task with comprehensive NPE protection
                        task.run();
                        ci.cancel(); // We handled it, prevent double execution
                        return;
                    } catch (NullPointerException npe) {
                        String npeMessage = npe.getMessage();
                        String stackTrace = java.util.Arrays.toString(npe.getStackTrace());
                        
                        LOGGER.info("SafeTaskExecutionMixin: Caught NPE - Message: {}", npeMessage);
                        LOGGER.debug("SafeTaskExecutionMixin: NPE Stack trace contains: {}", stackTrace);
                        
                        // Check for TrackedEntity NPE with multiple detection methods
                        boolean isTrackedEntityNPE = false;
                        
                        if (npeMessage != null) {
                            isTrackedEntityNPE = npeMessage.contains("TrackedEntity") || 
                                               npeMessage.contains("removePlayer") ||
                                               npeMessage.contains("entry") ||
                                               npeMessage.contains("ChunkMap") ||
                                               npeMessage.contains("because \"entry\" is null");
                        }
                        
                        // Also check stack trace for Floodgate ModSkinApplier
                        if (!isTrackedEntityNPE && stackTrace != null) {
                            isTrackedEntityNPE = stackTrace.contains("ModSkinApplier") ||
                                               stackTrace.contains("floodgate") ||
                                               stackTrace.contains("lambda$applySkin$0");
                        }
                        
                        if (isTrackedEntityNPE) {
                            LOGGER.info("SafeTaskExecutionMixin: Prevented TrackedEntity/Floodgate NPE: {}", npeMessage);
                            ci.cancel(); // Prevent the crash
                            return;
                        } else {
                            // For other NPEs, log and re-throw
                            LOGGER.debug("SafeTaskExecutionMixin: Re-throwing non-TrackedEntity NPE: {}", npeMessage);
                            throw npe;
                        }
                    } catch (Exception e) {
                        // Log other exceptions but let them through unless they're critical
                        LOGGER.debug("SafeTaskExecutionMixin: Exception in task execution: {} - {}", e.getClass().getSimpleName(), e.getMessage());
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
