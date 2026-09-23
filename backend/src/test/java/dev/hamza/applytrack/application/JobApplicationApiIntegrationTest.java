package dev.hamza.applytrack.application;

import dev.hamza.applytrack.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobApplicationApiIntegrationTest extends ApiTestSupport {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndGetToken();
    }

    @Test
    void createReturns201WithLocationAndInitialTimelineEntry() throws Exception {
        mvc.perform(withToken(post("/api/applications"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "company", "  Acme  ",
                                "role", "Junior Developer",
                                "url", "https://acme.example.com/jobs/1",
                                "tags", List.of("Java", "java", " React ")))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/applications/")))
                .andExpect(jsonPath("$.company", is("Acme")))
                .andExpect(jsonPath("$.status", is("WISHLIST")))
                .andExpect(jsonPath("$.appliedOn", nullValue()))
                .andExpect(jsonPath("$.tags", contains("java", "react")))
                .andExpect(jsonPath("$.timeline", hasSize(1)))
                .andExpect(jsonPath("$.timeline[0].fromStatus", nullValue()))
                .andExpect(jsonPath("$.timeline[0].toStatus", is("WISHLIST")));
    }

    @Test
    void createWithAppliedStatusDefaultsAppliedOnToToday() throws Exception {
        mvc.perform(withToken(post("/api/applications"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("company", "Acme", "role", "Dev", "status", "APPLIED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedOn", is(LocalDate.now(ZoneOffset.UTC).toString())));
    }

    @Test
    void invalidPayloadReturnsProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(withToken(post("/api/applications"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "company", "",
                                "role", "Dev",
                                "url", "ftp://nope",
                                "appliedOn", "2026-05-10",
                                "followUpOn", "2026-05-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.errors.company", notNullValue()))
                .andExpect(jsonPath("$.errors.url", is("must be an http(s) URL")))
                .andExpect(jsonPath("$.errors.followUpAfterApplied",
                        is("follow-up date cannot be before the applied date")));
    }

    @Test
    void updateReplacesEditableFields() throws Exception {
        long id = createApplication(token, Map.of("company", "Acme", "role", "Dev", "tags", List.of("old")));

        mvc.perform(withToken(put("/api/applications/" + id), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "company", "Acme Corp",
                                "role", "Backend Developer",
                                "location", "Tunis",
                                "notes", "Call back Monday",
                                "tags", List.of("spring")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company", is("Acme Corp")))
                .andExpect(jsonPath("$.location", is("Tunis")))
                .andExpect(jsonPath("$.tags", contains("spring")))
                .andExpect(jsonPath("$.status", is("WISHLIST")));
    }

    @Test
    void changingStatusAppendsToTimelineAndIgnoresNoOps() throws Exception {
        long id = createApplication(token, Map.of("company", "Acme", "role", "Dev"));

        mvc.perform(withToken(patch("/api/applications/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.appliedOn", notNullValue()));

        mvc.perform(withToken(patch("/api/applications/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk());

        // same status again: no new timeline entry
        mvc.perform(withToken(patch("/api/applications/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline", hasSize(3)))
                .andExpect(jsonPath("$.timeline[1].fromStatus", is("WISHLIST")))
                .andExpect(jsonPath("$.timeline[1].toStatus", is("APPLIED")))
                .andExpect(jsonPath("$.timeline[2].fromStatus", is("APPLIED")))
                .andExpect(jsonPath("$.timeline[2].toStatus", is("INTERVIEW")));
    }

    @Test
    void unknownStatusValueIsA400() throws Exception {
        long id = createApplication(token, Map.of("company", "Acme", "role", "Dev"));

        mvc.perform(withToken(patch("/api/applications/" + id + "/status"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIRED_ON_THE_SPOT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listSupportsStatusTextAndTagFiltersWithPaging() throws Exception {
        createApplication(token, Map.of("company", "Alpha Labs", "role", "Frontend Dev", "status", "APPLIED",
                "tags", List.of("react")));
        createApplication(token, Map.of("company", "Beta Systems", "role", "Java Dev", "status", "INTERVIEW",
                "tags", List.of("java", "spring")));
        createApplication(token, Map.of("company", "Gamma", "role", "Full-stack", "location", "Remote Labs",
                "tags", List.of("react", "java")));

        mvc.perform(withToken(get("/api/applications"), token))
                .andExpect(jsonPath("$.totalElements", is(3)));

        mvc.perform(withToken(get("/api/applications").param("status", "APPLIED", "INTERVIEW"), token))
                .andExpect(jsonPath("$.content[*].company", containsInAnyOrder("Alpha Labs", "Beta Systems")));

        mvc.perform(withToken(get("/api/applications").param("q", "LABS"), token))
                .andExpect(jsonPath("$.content[*].company", containsInAnyOrder("Alpha Labs", "Gamma")));

        mvc.perform(withToken(get("/api/applications").param("tag", "java"), token))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[*].company", containsInAnyOrder("Beta Systems", "Gamma")));

        mvc.perform(withToken(get("/api/applications").param("size", "2").param("page", "1")
                        .param("sort", "company,asc"), token))
                .andExpect(jsonPath("$.content[*].company", contains("Gamma")))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void sortingByAnUnknownFieldIsA400() throws Exception {
        mvc.perform(withToken(get("/api/applications").param("sort", "ownerId"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.startsWith("Cannot sort by 'ownerId'")));
    }

    @Test
    void interviewsCanBeAddedAndRemoved() throws Exception {
        long id = createApplication(token, Map.of("company", "Acme", "role", "Dev", "status", "INTERVIEW"));

        String created = mvc.perform(withToken(post("/api/applications/" + id + "/interviews"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"2030-01-15T09:30:00Z\",\"type\":\"TECHNICAL\",\"notes\":\"Live coding\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.type", is("TECHNICAL")))
                .andReturn().getResponse().getContentAsString();
        long interviewId = readJson(created).get("id").asLong();

        mvc.perform(withToken(get("/api/applications/" + id), token))
                .andExpect(jsonPath("$.interviews", hasSize(1)))
                .andExpect(jsonPath("$.interviews[0].notes", is("Live coding")));

        mvc.perform(withToken(delete("/api/applications/" + id + "/interviews/" + interviewId), token))
                .andExpect(status().isNoContent());
        mvc.perform(withToken(delete("/api/applications/" + id + "/interviews/" + interviewId), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesTheApplication() throws Exception {
        long id = createApplication(token, Map.of("company", "Acme", "role", "Dev"));

        mvc.perform(withToken(delete("/api/applications/" + id), token)).andExpect(status().isNoContent());
        mvc.perform(withToken(get("/api/applications/" + id), token)).andExpect(status().isNotFound());
    }

    @Test
    void tagsEndpointListsDistinctSortedTags() throws Exception {
        createApplication(token, Map.of("company", "A", "role", "Dev", "tags", List.of("spring", "java")));
        createApplication(token, Map.of("company", "B", "role", "Dev", "tags", List.of("java", "angular")));

        mvc.perform(withToken(get("/api/applications/tags"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("angular", "java", "spring")));
    }
}
