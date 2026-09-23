package dev.hamza.applytrack.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests: full application context, in-memory H2, real security filter chain.
 * Every test registers its own user with a random email, so tests never see each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected String registerAndGetToken() throws Exception {
        return registerAndGetToken("user-" + UUID.randomUUID() + "@example.com", "correct-horse-1");
    }

    protected String registerAndGetToken(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password, "displayName", "Test"));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    protected long createApplication(String token, Map<String, Object> fields) throws Exception {
        String response = mvc.perform(withToken(post("/api/applications"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(fields)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    /** Current optimistic-lock version of an application, as a client would have read it. */
    protected long version(String token, long id) throws Exception {
        String response = mvc.perform(withToken(get("/api/applications/" + id), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("version").asLong();
    }

    /** PATCH /status with the current version, the way the UI does it. */
    protected void changeStatus(String token, long id, String newStatus) throws Exception {
        mvc.perform(withToken(patch("/api/applications/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody(newStatus, version(token, id))))
                .andExpect(status().isOk());
    }

    protected String statusBody(String newStatus, long version) throws Exception {
        return json.writeValueAsString(Map.of("status", newStatus, "version", version));
    }

    protected JsonNode readJson(String content) throws Exception {
        return json.readTree(content);
    }

    protected static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
