package dev.hamza.applytrack.application;

import dev.hamza.applytrack.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User B must not be able to read, change or even detect user A's data.
 */
class OwnershipIsolationIntegrationTest extends ApiTestSupport {

    private String alice;
    private String bob;
    private long aliceApplication;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerAndGetToken();
        bob = registerAndGetToken();
        aliceApplication = createApplication(alice, Map.of("company", "Secret Co", "role", "Dev",
                "status", "INTERVIEW", "tags", List.of("private")));
    }

    @Test
    void otherUsersApplicationLooksLikeItDoesNotExist() throws Exception {
        mvc.perform(withToken(get("/api/applications/" + aliceApplication), bob))
                .andExpect(status().isNotFound());
        mvc.perform(withToken(get("/api/applications/999999999"), bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUserCannotModifyOrDelete() throws Exception {
        mvc.perform(withToken(put("/api/applications/" + aliceApplication), bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Hijacked\",\"role\":\"Dev\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(withToken(patch("/api/applications/" + aliceApplication + "/status"), bob)
                        .contentType(MediaType.APPLICATION_JSON).content(statusBody("REJECTED", 0)))
                .andExpect(status().isNotFound());
        mvc.perform(withToken(post("/api/applications/" + aliceApplication + "/interviews"), bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2030-01-01T10:00:00Z\",\"type\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(withToken(delete("/api/applications/" + aliceApplication), bob))
                .andExpect(status().isNotFound());

        // Alice's data is untouched
        mvc.perform(withToken(get("/api/applications/" + aliceApplication), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company", is("Secret Co")))
                .andExpect(jsonPath("$.status", is("INTERVIEW")));
    }

    @Test
    void listsTagsAndStatsOnlyContainOwnData() throws Exception {
        mvc.perform(withToken(get("/api/applications"), bob))
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements", is(0)));
        mvc.perform(withToken(get("/api/applications/tags"), bob))
                .andExpect(jsonPath("$", empty()));
        mvc.perform(withToken(get("/api/stats"), bob))
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(jsonPath("$.byStatus.INTERVIEW", is(0)));

        mvc.perform(withToken(get("/api/stats"), alice))
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.byStatus.INTERVIEW", is(1)));
    }
}
