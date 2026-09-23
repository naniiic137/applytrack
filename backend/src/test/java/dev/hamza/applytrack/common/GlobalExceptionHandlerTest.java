package dev.hamza.applytrack.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class FailingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("password cannot be more than 72 bytes (internal detail)");
        }

        @GetMapping("/duplicate")
        String duplicate() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uk_users_email");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unexpectedExceptionsBecomeAGeneric500WithoutInternals() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", is("Internal server error")))
                .andExpect(content().string(not(containsString("72 bytes"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void constraintViolationsBecomeA409WithoutTheConstraintName() throws Exception {
        mvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", is("The request conflicts with existing data")))
                .andExpect(content().string(not(containsString("uk_users_email"))));
    }
}
