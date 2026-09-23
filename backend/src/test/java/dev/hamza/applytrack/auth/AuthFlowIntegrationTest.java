package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends ApiTestSupport {

    @Test
    void registerReturnsTokenThatAuthenticatesMe() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.com";
        String token = registerAndGetToken(email, "secret-pass-1");

        mvc.perform(withToken(get("/api/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.displayName", is("Test")));
    }

    @Test
    void loginWithCorrectPasswordIsCaseInsensitiveOnEmail() throws Exception {
        String email = "case-" + UUID.randomUUID() + "@example.com";
        registerAndGetToken(email, "secret-pass-1");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email.toUpperCase(), "password", "secret-pass-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.expiresAt", notNullValue()));
    }

    @Test
    void loginWithWrongPasswordReturns401Problem() throws Exception {
        String email = "wrong-" + UUID.randomUUID() + "@example.com";
        registerAndGetToken(email, "secret-pass-1");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is("Invalid email or password")));
    }

    @Test
    void registeringTheSameEmailTwiceReturns409() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        registerAndGetToken(email, "secret-pass-1");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "another-pass",
                                "displayName", "Again"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "not-an-email", "password", "short",
                                "displayName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation failed")))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", is("must be between 8 and 72 characters")))
                .andExpect(jsonPath("$.errors.displayName", is("must not be blank")));
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerAndGetToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        mvc.perform(withToken(get("/api/stats"), tampered))
                .andExpect(status().isUnauthorized());
    }
}
