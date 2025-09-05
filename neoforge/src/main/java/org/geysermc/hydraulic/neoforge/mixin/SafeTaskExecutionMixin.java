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
                    
                    // Check if this is a Floodgate skin application task
                    if (taskString.contains("ModSkinApplier") || taskString.contains("floodgate")) {
                        LOGGER.debug("SafeTaskExecutionMixin: Safely executing Floodgate task");
                        
                        try {
                            // Execute the task with NPE protection
                            task.run();
                            ci.cancel(); // We handled it, prevent double execution
                            return;
                        } catch (NullPointerException npe) {
                            if (npe.getMessage() != null && npe.getMessage().contains("TrackedEntity")) {
                                LOGGER.info("SafeTaskExecutionMixin: Prevented TrackedEntity NPE in Floodgate skin application");
                                ci.cancel(); // Prevent the crash
                                return;
                            } else {
                                // Re-throw other NPEs
                                throw npe;
                            }
                        } catch (Exception e) {
                            LOGGER.warn("SafeTaskExecutionMixin: Exception in Floodgate task execution: {}", e.getMessage());
                            ci.cancel(); // Prevent potential crashes
                            return;
                        }
                    }
                }
            } catch (Exception reflectionException) {
                LOGGER.debug("SafeTaskExecutionMixin: Could not access task field: {}", reflectionException.getMessage());
            }
            
            // For non-Floodgate tasks or if we can't determine the task type, let it run normally
            
        } catch (Exception e) {
            LOGGER.debug("SafeTaskExecutionMixin: Exception in safe task execution: {}", e.getMessage());
        }
    }
}
