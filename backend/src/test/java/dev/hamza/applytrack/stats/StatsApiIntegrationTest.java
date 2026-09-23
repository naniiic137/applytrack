package dev.hamza.applytrack.stats;

import dev.hamza.applytrack.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsApiIntegrationTest extends ApiTestSupport {

    @Test
    void statsSummariseTheCurrentUsersApplications() throws Exception {
        String token = registerAndGetToken();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        createApplication(token, Map.of("company", "W", "role", "Dev")); // WISHLIST
        createApplication(token, Map.of("company", "A1", "role", "Dev", "status", "APPLIED",
                "followUpOn", today.plusDays(3).toString()));
        createApplication(token, Map.of("company", "A2", "role", "Dev", "status", "APPLIED"));
        long interviewed = createApplication(token, Map.of("company", "I", "role", "Dev", "status", "APPLIED"));
        changeStatus(token, interviewed, "INTERVIEW");
        // reached INTERVIEW, then went quiet: still counts as a response
        changeStatus(token, interviewed, "GHOSTED");

        mvc.perform(withToken(get("/api/stats"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(4)))
                .andExpect(jsonPath("$.submitted", is(3)))
                .andExpect(jsonPath("$.active", is(3)))
                .andExpect(jsonPath("$.byStatus.WISHLIST", is(1)))
                .andExpect(jsonPath("$.byStatus.APPLIED", is(2)))
                .andExpect(jsonPath("$.byStatus.GHOSTED", is(1)))
                .andExpect(jsonPath("$.byStatus.OFFER", is(0)))
                .andExpect(jsonPath("$.responseRate", is(33.3)))
                .andExpect(jsonPath("$.interviewRate", is(33.3)))
                .andExpect(jsonPath("$.applicationsPerWeek", hasSize(12)))
                .andExpect(jsonPath("$.applicationsPerWeek[11].count", is(3)))
                .andExpect(jsonPath("$.upcomingFollowUps", hasSize(1)))
                .andExpect(jsonPath("$.upcomingFollowUps[0].company", is("A1")))
                .andExpect(jsonPath("$.upcomingFollowUps[0].overdue", is(false)));
    }
}
