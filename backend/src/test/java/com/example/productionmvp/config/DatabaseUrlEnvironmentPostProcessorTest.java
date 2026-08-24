package com.example.productionmvp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    private MockEnvironment process(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, null);
        return environment;
    }

    @Test
    void convertsPlatformUrlIntoJdbcUrlAndCredentials() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://mes:s3cret@db.internal:5432/production"));

        assertEquals("jdbc:postgresql://db.internal:5432/production", env.getProperty("spring.datasource.url"));
        assertEquals("mes", env.getProperty("spring.datasource.username"));
        assertEquals("s3cret", env.getProperty("spring.datasource.password"));
    }

    // Heroku-style URLs use the shorter scheme; Railway and Render use the longer one.
    @Test
    void acceptsThePostgresSchemeAsWellAsPostgresql() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://u:p@host:6543/db"));

        assertEquals("jdbc:postgresql://host:6543/db", env.getProperty("spring.datasource.url"));
    }

    @Test
    void defaultsToThePostgresPortWhenTheUrlOmitsIt() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@host/db"));

        assertEquals("jdbc:postgresql://host:5432/db", env.getProperty("spring.datasource.url"));
    }

    // Generated passwords routinely contain characters that have to be percent-encoded in a URL.
    @Test
    void decodesEscapedCharactersInCredentials() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u%40corp:p%40ss%2Fword@host:5432/db"));

        assertEquals("u@corp", env.getProperty("spring.datasource.username"));
        assertEquals("p@ss/word", env.getProperty("spring.datasource.password"));
    }

    @Test
    void keepsQueryParametersSuchAsSslMode() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://u:p@host:5432/db?sslmode=require"));

        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require", env.getProperty("spring.datasource.url"));
    }

    // The private URL keeps traffic inside the platform's network: same database, but not
    // routed out through a public proxy and not billed as egress.
    @Test
    void prefersThePrivateUrlWhenThePlatformOffersBoth() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_PRIVATE_URL", "postgresql://u:p@internal.host:5432/db")
                .withProperty("DATABASE_URL", "postgresql://u:p@public.proxy:7777/db"));

        assertEquals("jdbc:postgresql://internal.host:5432/db", env.getProperty("spring.datasource.url"));
    }

    // The self-hosted compose setup sets SPRING_DATASOURCE_URL directly; that must keep
    // winning, or a stray DATABASE_URL would silently redirect the application elsewhere.
    @Test
    void explicitDatasourceUrlIsNeverOverridden() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://chosen:5432/mine")
                .withProperty("DATABASE_URL", "postgresql://u:p@somewhere-else:5432/other"));

        assertNull(env.getProperty("spring.datasource.url"));
        assertNull(env.getProperty("spring.datasource.username"));
    }

    // application.yml gives spring.datasource.url a default, so that property always resolves
    // to something even when nothing was configured. Treating that as "already configured" is
    // what made the first version of this quietly start on H2 while DATABASE_URL was ignored.
    @Test
    void theEmbeddedDefaultDoesNotCountAsBeingConfigured() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:production_mvp;DB_CLOSE_DELAY=-1")
                .withProperty("DATABASE_URL", "postgresql://u:p@real.db:5432/production"));

        assertEquals("jdbc:postgresql://real.db:5432/production", env.getProperty("spring.datasource.url"));
        assertEquals("u", env.getProperty("spring.datasource.username"));
    }

    @Test
    void leavesEnvironmentUntouchedWhenThereIsNoPlatformUrl() {
        MockEnvironment env = process(new MockEnvironment());

        assertNull(env.getProperty("spring.datasource.url"));
    }

    // A malformed value must not be turned into a plausible-looking wrong URL: failing on the
    // ordinary configuration path produces a far clearer error than connecting to nothing.
    @Test
    void ignoresAValueItCannotParse() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "this is not a url"));

        assertNull(env.getProperty("spring.datasource.url"));
    }

    @Test
    void ignoresAnUnrelatedDatabaseScheme() {
        MockEnvironment env = process(new MockEnvironment()
                .withProperty("DATABASE_URL", "mysql://u:p@host:3306/db"));

        assertNull(env.getProperty("spring.datasource.url"));
    }
}
