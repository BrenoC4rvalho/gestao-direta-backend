package br.com.gestaodireta.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new TestController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @Test
    void shouldHandleBusinessExceptionAsBadRequest() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Business error"))
                .andExpect(jsonPath("$.path").value("/test/business"));
    }

    @Test
    void shouldHandleResourceNotFoundExceptionAsNotFound() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    void shouldHandleUnauthorizedExceptionAsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void shouldHandleForbiddenExceptionAsForbidden() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void shouldHandleValidationExceptionAsBadRequest() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid data"));
    }

    @Test
    void shouldHandleMissingRequestParameterAsBadRequest() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request parameter is missing"));
    }

    @Test
    void shouldHandleMethodArgumentNotValidExceptionAsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/test/body-validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]", containsString("name")));
    }

    @Test
    void shouldHandleGenericExceptionWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Unexpected internal server error"))
                .andExpect(jsonPath("$.details", empty()))
                .andExpect(jsonPath("$.message", not(containsString("Generic error"))));
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException("Business error");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Resource not found");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Authentication is required");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("Access denied");
        }

        @GetMapping("/validation")
        void validation() {
            throw new ValidationException("Invalid data");
        }

        @GetMapping("/required-param")
        void requiredParam(@RequestParam Long farmId) {}

        @PostMapping("/body-validation")
        void bodyValidation(@Valid @RequestBody TestRequest request) {}

        @GetMapping("/generic")
        void generic() {
            throw new RuntimeException("Generic error with internal details");
        }
    }

    private record TestRequest(@NotBlank String name) {}
}
