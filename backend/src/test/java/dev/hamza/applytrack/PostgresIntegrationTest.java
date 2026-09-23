package dev.hamza.applytrack;

import dev.hamza.applytrack.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The same API, against a real PostgreSQL 16 in Docker instead of H2: the Flyway migrations, Hibernate schema
 * validation, the tag EXISTS sub-query, LIKE escaping, the unique email constraint and optimistic locking all
 * run on the production database engine. Skipped when Docker is not available (the H2 tests still run);
 * CI always has Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest extends ApiTestSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationsApplyAndTheMainFlowWorksOnPostgres() throws Exception {
        String token = registerAndGetToken();
        long id = createApplication(token, Map.of("company", "100% Remote_Co", "role", "Dev", "status", "APPLIED",
                "tags", List.of("Java", "spring")));
        createApplication(token, Map.of("company", "Other", "role", "Dev", "tags", List.of("react")));

        mvc.perform(withToken(get("/api/applications").param("q", "100%").param("tag", "JAVA"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].tags", contains("java", "spring")));

        changeStatus(token, id, "INTERVIEW");
        mvc.perform(withToken(patch("/api/applications/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("OFFER", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("stale_version")));

        mvc.perform(withToken(get("/api/stats"), token).header("X-Time-Zone", "Africa/Tunis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(2)))
                .andExpect(jsonPath("$.submitted", is(1)))
                .andExpect(jsonPath("$.interviewRate", is(100.0)));
    }

    @Test
    void theUniqueEmailConstraintHoldsOnPostgres() throws Exception {
        String email = "pg-" + UUID.randomUUID() + "@example.com";
        registerAndGetToken(email, "secret-pass-1");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email.toUpperCase(), "password",
                                "secret-pass-2", "displayName", "Twin"))))
                .andExpect(status().isConflict());
    }
}
