package com.example.productionmvp.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Accepts the {@code DATABASE_URL} that hosting platforms hand out and turns it into the three
 * properties Spring actually wants.
 *
 * <p>Railway, Render and Heroku all publish a database as one variable in libpq form:
 * {@code postgresql://user:password@host:5432/dbname}. Spring needs a JDBC URL
 * ({@code jdbc:postgresql://host:5432/dbname}) with the credentials supplied separately, and it
 * will not make that conversion itself. Left to the operator, this becomes a manual assembly of
 * three variables out of five, done in a web form, where a single typo produces a container that
 * boots and then fails to reach its database — one of the more common ways a first deployment
 * goes wrong, and an unpleasant one to debug from logs alone.
 *
 * <p>Anything explicitly configured wins: if {@code SPRING_DATASOURCE_URL} is set, this does
 * nothing at all. That keeps the self-hosted compose setup, which sets the JDBC URL directly,
 * behaving exactly as before.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    // A private-network URL is preferred where the platform offers one: it is the same database,
    // but the traffic stays inside the project instead of going out and back through a public
    // proxy, which is both faster and not billed as egress.
    private static final String[] CANDIDATE_KEYS = {"DATABASE_PRIVATE_URL", "DATABASE_URL"};

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Deliberately checks the explicit setting rather than the resolved
        // spring.datasource.url: application.yml gives that property a default (the embedded
        // database), so it always resolves to something and a guard on it would decide the
        // datasource was already configured every single time - which is exactly what this
        // did at first, silently starting on H2 while DATABASE_URL sat there unused.
        if (hasText(environment.getProperty("SPRING_DATASOURCE_URL"))) {
            return;
        }

        for (String key : CANDIDATE_KEYS) {
            String raw = environment.getProperty(key);
            if (!hasText(raw)) {
                continue;
            }
            Map<String, Object> converted = convert(raw.trim());
            if (converted != null) {
                environment.getPropertySources()
                        .addFirst(new MapPropertySource("platform-database-url", converted));
                return;
            }
        }
    }

    private Map<String, Object> convert(String raw) {
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            // Already a JDBC URL, or a database this conversion does not cover. Leave it be
            // rather than guess - a wrong URL fails more confusingly than a missing one.
            return null;
        }
        try {
            URI uri = new URI(raw);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String database = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

            Map<String, Object> properties = new HashMap<>();
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host).append(':').append(port);
            if (!database.isEmpty()) {
                jdbc.append('/').append(database);
            }
            if (hasText(uri.getQuery())) {
                jdbc.append('?').append(uri.getQuery());
            }
            properties.put("spring.datasource.url", jdbc.toString());

            String userInfo = uri.getUserInfo();
            if (hasText(userInfo)) {
                int separator = userInfo.indexOf(':');
                if (separator >= 0) {
                    properties.put("spring.datasource.username", decode(userInfo.substring(0, separator)));
                    properties.put("spring.datasource.password", decode(userInfo.substring(separator + 1)));
                } else {
                    properties.put("spring.datasource.username", decode(userInfo));
                }
            }
            return properties;
        } catch (Exception e) {
            // Deliberately quiet: a malformed value here simply means the ordinary
            // SPRING_DATASOURCE_* configuration applies, and Spring reports that clearly.
            return null;
        }
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
