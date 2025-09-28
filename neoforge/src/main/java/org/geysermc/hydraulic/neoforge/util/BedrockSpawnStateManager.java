package org.geysermc.hydraulic.neoforge.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages spawn state for Bedrock players to prevent overwhelming them
 * during the critical login/spawn phase.
 */
public class BedrockSpawnStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockSpawnStateManager");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Track player spawn states
    private static final ConcurrentHashMap<String, SpawnState> playerStates = new ConcurrentHashMap<>();
    
    public enum SpawnState {
        CONNECTING,      // Just connected, be very gentle
        SPAWNING,        // In spawn process, be gentle  
        SPAWNED,         // Fully spawned, normal processing
        STABLE           // Been stable for a while, full processing
    }
    
    /**
     * Registers a Bedrock player as connecting.
     */
    public static void registerConnecting(String playerName) {
        playerStates.put(playerName, SpawnState.CONNECTING);
        LOGGER.info("BedrockSpawnStateManager: Player {} is CONNECTING - using minimal processing", playerName);
        
        // Schedule transition to SPAWNING after 2 seconds
        scheduler.schedule(() -> {
            if (playerStates.get(playerName) == SpawnState.CONNECTING) {
                playerStates.put(playerName, SpawnState.SPAWNING);
                LOGGER.info("BedrockSpawnStateManager: Player {} transitioned to SPAWNING - using gentle processing", playerName);
            }
        }, 2, TimeUnit.SECONDS);
        
        // Schedule transition to SPAWNED after 5 seconds
        scheduler.schedule(() -> {
            if (playerStates.get(playerName) == SpawnState.SPAWNING) {
                playerStates.put(playerName, SpawnState.SPAWNED);
                LOGGER.info("BedrockSpawnStateManager: Player {} transitioned to SPAWNED - using normal processing", playerName);
            }
        }, 5, TimeUnit.SECONDS);
        
        // Schedule transition to STABLE after 15 seconds
        scheduler.schedule(() -> {
            if (playerStates.get(playerName) == SpawnState.SPAWNED) {
                playerStates.put(playerName, SpawnState.STABLE);
                LOGGER.info("BedrockSpawnStateManager: Player {} transitioned to STABLE - using full processing", playerName);
            }
        }, 15, TimeUnit.SECONDS);
    }
    
    /**
     * Manually advance a player's state (for when we know they're ready).
     */
    public static void advanceState(String playerName) {
        SpawnState current = playerStates.get(playerName);
        if (current != null) {
            switch (current) {
                case CONNECTING -> {
                    playerStates.put(playerName, SpawnState.SPAWNING);
                    LOGGER.info("BedrockSpawnStateManager: Advanced {} to SPAWNING", playerName);
                }
                case SPAWNING -> {
                    playerStates.put(playerName, SpawnState.SPAWNED);
                    LOGGER.info("BedrockSpawnStateManager: Advanced {} to SPAWNED", playerName);
                }
                case SPAWNED -> {
                    playerStates.put(playerName, SpawnState.STABLE);
                    LOGGER.info("BedrockSpawnStateManager: Advanced {} to STABLE", playerName);
                }
            }
        }
    }
    
    /**
     * Gets the current spawn state for a player.
     */
    public static SpawnState getSpawnState(String playerName) {
        return playerStates.getOrDefault(playerName, SpawnState.STABLE);
    }
    
    /**
     * Checks if aggressive processing should be avoided for this player.
     */
    public static boolean shouldUseMinimalProcessing(String playerName) {
        SpawnState state = getSpawnState(playerName);
        return state == SpawnState.CONNECTING || state == SpawnState.SPAWNING;
    }
    
    /**
     * Checks if full processing is allowed for this player.
     */
    public static boolean canUseFullProcessing(String playerName) {
        SpawnState state = getSpawnState(playerName);
        return state == SpawnState.STABLE;
    }
    
    /**
     * Removes player from tracking when they disconnect.
     */
    public static void unregisterPlayer(String playerName) {
        playerStates.remove(playerName);
        LOGGER.debug("BedrockSpawnStateManager: Unregistered player: {}", playerName);
    }
}
