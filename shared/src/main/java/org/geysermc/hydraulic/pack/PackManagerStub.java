package org.geysermc.hydraulic.pack;

import org.geysermc.hydraulic.HydraulicImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporary stub for PackManager to get server running without pack converter dependencies.
 */
public class PackManagerStub {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hydraulic/PackManager");
    
    private final HydraulicImpl hydraulic;
    
    public PackManagerStub(HydraulicImpl hydraulic) {
        this.hydraulic = hydraulic;
    }
    
    public void initialize() {
        LOGGER.info("Pack manager temporarily disabled - focusing on core Bedrock compatibility");
    }
}
