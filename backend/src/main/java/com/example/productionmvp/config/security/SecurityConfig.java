package com.example.productionmvp.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final com.example.productionmvp.config.DeploymentMode deploymentMode;
    private final String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          com.example.productionmvp.config.DeploymentMode deploymentMode,
                          @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:}")
                          String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.deploymentMode = deploymentMode;
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins.trim();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/api/auth/**")).permitAll()
                // The hosting platform polls this to decide whether a newly started instance
                // may receive traffic, and it has no credentials to offer. Only "health" is
                // exposed at all (see application.yml) and it reports no details.
                .requestMatchers(org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                .requestMatchers(org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/actuator/health/**")).permitAll()
                // The UI itself. It is served by this application in a deployed environment so
                // that the browser's /api calls stay same-origin, which is also why there is no
                // CORS involved there. These files contain no data - every screen fetches what
                // it shows through /api, which still requires a token.
                .requestMatchers(
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/"),
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/*.html"),
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/*.css"),
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/*.ico"),
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/js/**"),
                    org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher("/css/**")
                ).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // The only CORS definition in the application. There used to be a second one in a
    // WebMvcConfigurer that allowed something different; it never applied to /api/** because
    // Spring Security answers the preflight first, so it was dead configuration that still
    // read as authoritative. It has been removed.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (!allowedOrigins.isEmpty()) {
            configuration.setAllowedOriginPatterns(
                    Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        } else if (deploymentMode.isEmbeddedDatabase()) {
            // Local development: the frontend is opened straight off disk or a throwaway static
            // server on an arbitrary port, so pinning an origin here would only cause friction.
            configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        } else {
            // A real deployment that never said which sites may call it. Any origin at all is
            // not a safe assumption to make on the operator's behalf, so allow none and let the
            // startup log say why - the frontend is normally served from the same origin as the
            // API anyway (see docker-compose), in which case CORS is not involved at all.
            logger.warn("app.cors.allowed-origins is not set on a persistent deployment - "
                    + "cross-origin browser requests will be refused. Set it to the frontend's "
                    + "origin (e.g. https://mes.example.com) if the UI is served from elsewhere.");
            configuration.setAllowedOriginPatterns(Arrays.asList());
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // Auth is a Bearer token attached manually in JS (no cookies), so there is nothing
        // credentialed for the browser to carry automatically. Leaving this true while origins
        // are wildcarded (via allowedOriginPatterns, which reflects the caller's Origin) lets
        // any website make credentialed cross-origin requests for no functional benefit here.
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
