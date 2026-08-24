package com.example.productionmvp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Says out loud, once, which database the application actually came up on.
 *
 * <p>Falling back to the embedded database is right for local development and wrong everywhere
 * else, and until now it happened silently: a deployment with a missing or misspelled
 * DATABASE_URL started cleanly, served every page, accepted logins, and looked entirely healthy
 * while keeping all of its data in memory. The only way to notice was to know that the seeded
 * demo PIN should not have worked, or to go looking for a Hikari line in the logs. Both require
 * knowing in advance that there is something to suspect.
 */
@Component
public class StartupBanner {

    private static final Logger logger = LoggerFactory.getLogger(StartupBanner.class);

    private final DeploymentMode deploymentMode;

    public StartupBanner(DeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        // Worth stating plainly: timestamps are stored without a timezone, so if this clock is
        // not the workshop's, every time on every screen is silently off by the difference.
        logger.info("Clock: {} — current time {}", java.util.TimeZone.getDefault().getID(),
                java.time.LocalDateTime.now().withNano(0));

        if (deploymentMode.isPersistentDeployment()) {
            logger.info("Database: {} (persistent)", deploymentMode.getDatasourceUrl());
            return;
        }

        logger.warn("==================================================================");
        logger.warn("RUNNING ON AN IN-MEMORY DATABASE: {}", deploymentMode.getDatasourceUrl());
        logger.warn("Everything entered here is lost when this process restarts.");
        // An embedded database always gets the demo accounts, whatever the seed flag says -
        // see DataSeeder. Naming the PINs here makes the giveaway obvious: if 7777 signs you
        // in on a server, this is the reason.
        logger.warn("Demo accounts are active - manager 7777, admin 9999.");
        logger.warn("");
        logger.warn("This is correct for local development.");
        logger.warn("On a server it means the database was never configured: set DATABASE_URL");
        logger.warn("(postgresql://user:pass@host:5432/dbname) or SPRING_DATASOURCE_URL, then");
        logger.warn("restart. A deployment left like this looks healthy and loses everything.");
        logger.warn("==================================================================");
    }
}
