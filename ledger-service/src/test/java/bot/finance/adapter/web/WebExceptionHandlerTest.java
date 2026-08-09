package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.port.AcceptExpensesPort;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for {@link WebExceptionHandler}, the advice shared by every read in {@code bot.finance.adapter.web}.
 *
 * <p>It carries only the mappings that hold for every controller - a failure any port can raise, answered the same
 * way whichever endpoint was called. A refusal one endpoint alone can produce belongs to that endpoint's own test,
 * so that retiring the endpoint takes its whole contract with it. The advice's two 401 handlers are
 * {@code SessionControllerTest}'s for the same reason.
 *
 * <p>An advice has to be entered through some controller. {@link ExpensesController} is that controller here, and
 * nothing below asserts anything about the expenses endpoint itself.
 */
@WebAdapterTest
@WebMvcTest(ExpensesController.class)
class WebExceptionHandlerTest {

    private static final String PATH = "/api/v1/expenses";
    private static final String EXTERNAL_ID = "445566778";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseExpensesPort browseExpensesPort;

    @MockitoBean
    private AcceptExpensesPort acceptExpensesPort;

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port throws EntityNotFoundException - then the response is 404 with a Problem body")
        void whenPortThrowsEntityNotFoundException_thenResponseIs404WithProblemBody() throws Exception {
            when(browseExpensesPort.browse(any()))
                    .thenThrow(new EntityNotFoundException("User", "no user stored for external id " + EXTERNAL_ID));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertSingleJsonMessage(result);
        }

        @Test
        @DisplayName("when the port throws PersistenceFailedException - then the response is 503, naming no "
                + "session, table or constraint")
        void whenPortThrowsPersistenceFailedException_thenResponseIs503NamingRequestNotSessionOrInternals()
                throws Exception {
            when(browseExpensesPort.browse(any()))
                    .thenThrow(new PersistenceFailedException(
                            "duplicate key value violates unique constraint \"pk_expense\" on table \"expense\"",
                            new RuntimeException("cause")));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            String message = assertSingleJsonMessage(result);
            assertThat(message).doesNotContainIgnoringCase("session");
            assertThat(message).doesNotContain("expense\"").doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName("when the port throws an exception nothing else maps - then the response is 500, naming no "
                + "session or cause")
        void whenPortThrowsUnmappedException_thenResponseIs500NamingRequestNotSession() throws Exception {
            String secretMessage = "connection pool exhausted on host db-primary-7";
            when(browseExpensesPort.browse(any())).thenThrow(new RuntimeException(secretMessage));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isInternalServerError())
                    .andReturn();

            String message = assertSingleJsonMessage(result);
            assertThat(message).doesNotContainIgnoringCase("session").doesNotContain(secretMessage);
        }
    }

    private String assertSingleJsonMessage(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = JsonUtils.readJson(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.has("message")).isTrue();
        return body.get("message").asText();
    }

    private static Cookie sessionCookie() {
        return BrowserSessions.cookieFor(EXTERNAL_ID);
    }
}
