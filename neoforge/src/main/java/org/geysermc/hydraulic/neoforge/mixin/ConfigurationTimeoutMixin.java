package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This mixin provides a timeout mechanism for Bedrock players stuck in configuration phase.
 * If a Bedrock player's configuration doesn't complete within a reasonable time, this will
 * force the completion to prevent infinite hanging.
 */
@Mixin(value = ServerConfigurationPacketListenerImpl.class)
public class ConfigurationTimeoutMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigurationTimeoutMixin");
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newScheduledThreadPool(2);
    private static final ConcurrentHashMap<String, Long> BEDROCK_PLAYER_START_TIMES = new ConcurrentHashMap<>();
    private static final long CONFIGURATION_TIMEOUT_MS = 30000; // 30 seconds timeout

    /**
     * Track when configuration starts for Bedrock players.
     */
    @Inject(
        method = "startConfiguration",
        at = @At("TAIL")
    )
    private void trackBedrockConfigurationStart(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                String playerName = self.getOwner().getName();
                
                if (isBedrockPlayer) {
                    LOGGER.info("ConfigurationTimeoutMixin: Starting timeout tracking for Bedrock player: {}", playerName);
                    
                    long startTime = System.currentTimeMillis();
                    BEDROCK_PLAYER_START_TIMES.put(playerName, startTime);
                    
                    // Schedule a timeout check
                    TIMEOUT_EXECUTOR.schedule(() -> {
                        try {
                            Long recordedStartTime = BEDROCK_PLAYER_START_TIMES.get(playerName);
                            if (recordedStartTime != null && recordedStartTime.equals(startTime)) {
                                // Player is still in configuration after timeout
                                LOGGER.warn("ConfigurationTimeoutMixin: Bedrock player {} has been stuck in configuration for {}ms, forcing completion", 
                                    playerName, CONFIGURATION_TIMEOUT_MS);
                                
                                forceConfigurationCompletion(self, playerName);
                                BEDROCK_PLAYER_START_TIMES.remove(playerName);
                            }
                        } catch (Exception e) {
                            LOGGER.error("ConfigurationTimeoutMixin: Error in timeout handler for player {}: {}", playerName, e.getMessage());
                        }
                    }, CONFIGURATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConfigurationTimeoutMixin: Exception in configuration start tracking: {}", e.getMessage());
        }
    }

    /**
     * Remove tracking when configuration finishes successfully.
     */
    @Inject(
        method = "finishConfiguration",
        at = @At("HEAD")
    )
    private void removeBedrockConfigurationTracking(CallbackInfo ci) {
        try {
            ServerConfigurationPacketListenerImpl self = (ServerConfigurationPacketListenerImpl) (Object) this;
            
            if (self.getOwner() != null) {
                String playerName = self.getOwner().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isBedrockPlayer(self);
                
                if (isBedrockPlayer && BEDROCK_PLAYER_START_TIMES.containsKey(playerName)) {
                    long configurationTime = System.currentTimeMillis() - BEDROCK_PLAYER_START_TIMES.get(playerName);
                    LOGGER.info("ConfigurationTimeoutMixin: Bedrock player {} completed configuration in {}ms", 
                        playerName, configurationTime);
                    BEDROCK_PLAYER_START_TIMES.remove(playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ConfigurationTimeoutMixin: Exception in configuration finish tracking: {}", e.getMessage());
        }
    }

    /**
     * Force configuration completion for stuck Bedrock players.
     */
    @Unique
    private void forceConfigurationCompletion(ServerConfigurationPacketListenerImpl listener, String playerName) {
        try {
            // First, clear any remaining tasks
            try {
                java.lang.reflect.Field tasksField = ServerConfigurationPacketListenerImpl.class.getDeclaredField("configurationTasks");
                tasksField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Queue<net.minecraft.server.network.ConfigurationTask> tasks = 
                    (java.util.Queue<net.minecraft.server.network.ConfigurationTask>) tasksField.get(listener);
                
                int clearedTasks = 0;
                while (!tasks.isEmpty()) {
                    tasks.poll();
                    clearedTasks++;
                }
                
                if (clearedTasks > 0) {
                    LOGGER.info("ConfigurationTimeoutMixin: Cleared {} remaining tasks for stuck Bedrock player: {}", 
                        clearedTasks, playerName);
                }
            } catch (Exception taskException) {
                LOGGER.debug("ConfigurationTimeoutMixin: Could not clear tasks: {}", taskException.getMessage());
            }

            // Try multiple methods to complete configuration
            boolean completed = false;

            // Method 1: Try finishConfiguration
            if (!completed) {
                try {
                    java.lang.reflect.Method finishMethod = 
                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("finishConfiguration");
                    finishMethod.setAccessible(true);
                    finishMethod.invoke(listener);
                    LOGGER.info("ConfigurationTimeoutMixin: Successfully forced completion via finishConfiguration for: {}", playerName);
                    completed = true;
                } catch (Exception e) {
                    LOGGER.debug("ConfigurationTimeoutMixin: finishConfiguration failed: {}", e.getMessage());
                }
            }

            // Method 2: Try handleConfigurationFinished
            if (!completed) {
                try {
                    java.lang.reflect.Method handleFinishedMethod = 
                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("handleConfigurationFinished", 
                            net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket.class);
                    handleFinishedMethod.setAccessible(true);
                    handleFinishedMethod.invoke(listener, (Object) null);
                    LOGGER.info("ConfigurationTimeoutMixin: Successfully forced completion via handleConfigurationFinished for: {}", playerName);
                    completed = true;
                } catch (Exception e) {
                    LOGGER.debug("ConfigurationTimeoutMixin: handleConfigurationFinished failed: {}", e.getMessage());
                }
            }

            // Method 3: Try startNextTask (which should complete if no tasks remain)
            if (!completed) {
                try {
                    java.lang.reflect.Method startNextTaskMethod = 
                        ServerConfigurationPacketListenerImpl.class.getDeclaredMethod("startNextTask");
                    startNextTaskMethod.setAccessible(true);
                    startNextTaskMethod.invoke(listener);
                    LOGGER.info("ConfigurationTimeoutMixin: Successfully triggered startNextTask for: {}", playerName);
                    completed = true;
                } catch (Exception e) {
                    LOGGER.debug("ConfigurationTimeoutMixin: startNextTask failed: {}", e.getMessage());
                }
            }

            if (!completed) {
                LOGGER.error("ConfigurationTimeoutMixin: All completion methods failed for stuck Bedrock player: {}", playerName);
            }

        } catch (Exception e) {
            LOGGER.error("ConfigurationTimeoutMixin: Exception in force completion for player {}: {}", playerName, e.getMessage());
        }
    }

    /**
     * Cleanup method to prevent memory leaks.
     */
    private static void cleanup() {
        BEDROCK_PLAYER_START_TIMES.clear();
        TIMEOUT_EXECUTOR.shutdown();
    }
}
