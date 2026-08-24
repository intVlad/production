package com.example.productionmvp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tells the rest of the application whether it is running throwaway or for real, derived from
 * the database it was pointed at rather than from a profile flag.
 *
 * <p>An embedded database is wiped on every restart, so nothing in it can be production data
 * and the convenient defaults (demo accounts, an open CORS policy, a generated signing key)
 * cost nothing. A persistent database means the opposite on all three counts.
 *
 * <p>Deriving this from the datasource rather than {@code spring.profiles.active} is
 * deliberate: a profile has to be remembered, and the failure mode of forgetting it is a
 * production deployment quietly running with demo credentials and a throwaway signing key.
 * Nobody points a demo at PostgreSQL by accident, so the datasource is the more reliable
 * signal of intent.
 */
@Component
public class DeploymentMode {

    private final String datasourceUrl;

    public DeploymentMode(@Value("${spring.datasource.url:}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl == null ? "" : datasourceUrl;
    }

    /** True for H2/HSQLDB — a database whose contents do not survive a restart. */
    public boolean isEmbeddedDatabase() {
        return datasourceUrl.startsWith("jdbc:h2:") || datasourceUrl.startsWith("jdbc:hsqldb:");
    }

    /** True when pointed at a real database whose contents outlive the process. */
    public boolean isPersistentDeployment() {
        return !isEmbeddedDatabase();
    }

    public String getDatasourceUrl() {
        return datasourceUrl;
    }
}
