package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses a distinct client IP per test so the per-IP counters never leak into other tests.
 * Defaults: 5 failures per account, 20 per IP, 15-minute window.
 */
class LoginRateLimitIntegrationTest extends ApiTestSupport {

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    @Test
    void anAccountIsLockedAfterFiveFailuresEvenForTheRightPassword() throws Exception {
        String email = "locked-" + UUID.randomUUID() + "@example.com";
        registerAndGetToken(email, "secret-pass-1");

        for (int i = 0; i < 5; i++) {
            login("10.1.0.1", email, "wrong-guess-" + i).andExpect(status().isUnauthorized());
        }

        login("10.1.0.2", email, "secret-pass-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", is("Too many requests")));
    }

    @Test
    void oneIpTryingManyAccountsIsThrottled() throws Exception {
        for (int i = 0; i < 20; i++) {
            login("10.2.0.1", "nobody-" + UUID.randomUUID() + "@example.com", "whatever-1")
                    .andExpect(status().isUnauthorized());
        }

        login("10.2.0.1", "someone-else@example.com", "whatever-1")
                .andExpect(status().isTooManyRequests());
        login("10.2.0.2", "someone-else@example.com", "whatever-1")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
        String email = "exists-" + UUID.randomUUID() + "@example.com";
        registerAndGetToken(email, "secret-pass-1");

        String wrongPassword = login("10.3.0.1", email, "not-it-at-all").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("10.3.0.1", "missing-" + UUID.randomUUID() + "@example.com", "not-it-at-all")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(readJson(unknownEmail).get("detail"))
                .isEqualTo(readJson(wrongPassword).get("detail"));
    }
}
