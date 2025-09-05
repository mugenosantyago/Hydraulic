package org.geysermc.hydraulic.neoforge.mixin;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This mixin filters out repetitive Geyser error logs to reduce log spam
 * while still allowing important errors to be logged.
 */
@Mixin(value = Logger.class, remap = false)
public class LogSpamFilterMixin {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("LogSpamFilter");
    private static final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private static final long SPAM_THRESHOLD = 5; // Allow first 5 occurrences
    private static final long REPORT_INTERVAL = 100; // Report every 100 occurrences after threshold

    /**
     * Filters repetitive Geyser error logs to reduce spam.
     */
    @Inject(
        method = "logMessage",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void filterGeyserLogSpam(String fqcn, org.apache.logging.log4j.Level level, org.apache.logging.log4j.Marker marker, Object message, Throwable throwable, CallbackInfo ci) {
        try {
            // Only filter ERROR level logs
            if (level != org.apache.logging.log4j.Level.ERROR || throwable == null) {
                return;
            }

            String errorMessage = throwable.getMessage();
            String throwableClass = throwable.getClass().getSimpleName();
            
            // Check for the specific Geyser TemperatureVariantAnimal NPE
            if ("NullPointerException".equals(throwableClass) && 
                errorMessage != null && 
                errorMessage.contains("TemperatureVariantAnimal$BuiltInVariant.toBedrock()") &&
                errorMessage.contains("variant") &&
                errorMessage.contains("null")) {
                
                String errorKey = "geyser_variant_npe";
                AtomicLong count = errorCounts.computeIfAbsent(errorKey, k -> new AtomicLong(0));
                long currentCount = count.incrementAndGet();
                
                if (currentCount <= SPAM_THRESHOLD) {
                    // Allow the first few occurrences to be logged normally
                    return;
                } else if (currentCount % REPORT_INTERVAL == 0) {
                    // Log a summary every REPORT_INTERVAL occurrences
                    LOGGER.warn("LogSpamFilter: Geyser TemperatureVariantAnimal NPE has occurred {} times (suppressing individual logs)", currentCount);
                    ci.cancel(); // Don't log the individual error
                    return;
                } else {
                    // Suppress this occurrence
                    ci.cancel();
                    return;
                }
            }
            
            // Check for other Geyser packet translation errors
            if (message != null && message.toString().contains("geyser.network.translator.packet.failed")) {
                String errorKey = "geyser_packet_failed";
                AtomicLong count = errorCounts.computeIfAbsent(errorKey, k -> new AtomicLong(0));
                long currentCount = count.incrementAndGet();
                
                if (currentCount <= SPAM_THRESHOLD) {
                    return; // Allow first few
                } else if (currentCount % REPORT_INTERVAL == 0) {
                    LOGGER.warn("LogSpamFilter: Geyser packet translation failures have occurred {} times (suppressing individual logs)", currentCount);
                    ci.cancel();
                    return;
                } else {
                    ci.cancel();
                    return;
                }
            }
            
        } catch (Exception e) {
            // Don't let the log filter cause issues
            LOGGER.debug("LogSpamFilter: Exception in log filtering: {}", e.getMessage());
        }
    }
}
