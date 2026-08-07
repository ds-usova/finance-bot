package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.port.BrowseCategoriesPort;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.PersistenceFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for {@link WebExceptionHandler}, the advice shared by every read in {@code bot.finance.adapter.web}.
 * Driven through {@link ExpensesController} and {@link CategoriesController} - never by calling the advice's own
 * methods directly - with {@link BrowseExpensesPort} and {@link BrowseCategoriesPort} mocked. The advice's two
 * existing 401 handlers are {@code SessionControllerTest}'s, not this class's.
 */
@WebAdapterTest
@WebMvcTest(controllers = {ExpensesController.class, CategoriesController.class})
class WebExceptionHandlerTest {

    private static final String EXPENSES_PATH = "/api/v1/expenses";
    private static final String CATEGORIES_PATH = "/api/v1/categories";
    private static final String EXTERNAL_ID = "445566778";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseExpensesPort browseExpensesPort;

    @MockitoBean
    private BrowseCategoriesPort browseCategoriesPort;

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port throws EntityNotFoundException - then the response is 404 with a Problem body")
        void whenPortThrowsEntityNotFoundException_thenResponseIs404WithProblemBody() throws Exception {
            when(browseExpensesPort.browse(any()))
                    .thenThrow(new EntityNotFoundException("User", "no user stored for external id " + EXTERNAL_ID));

            MvcResult result = mockMvc.perform(get(EXPENSES_PATH).cookie(sessionCookie()))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertSingleJsonMessage(result);
        }

        @Test
        @DisplayName("when the port throws PersistenceFailedException - then the response is 503, naming the "
                + "request rather than the session and naming no table, statement or stack frame")
        void whenPortThrowsPersistenceFailedException_thenResponseIs503NamingRequestNotSessionOrInternals()
                throws Exception {
            when(browseExpensesPort.browse(any()))
                    .thenThrow(new PersistenceFailedException(
                            "duplicate key value violates unique constraint \"pk_expense\" on table \"expense\"",
                            new RuntimeException("cause")));

            MvcResult result = mockMvc.perform(get(EXPENSES_PATH).cookie(sessionCookie()))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            String message = assertSingleJsonMessage(result);
            assertThat(message).doesNotContainIgnoringCase("session");
            assertThat(message).doesNotContain("expense\"").doesNotContainIgnoringCase("constraint");
        }

        @Test
        @DisplayName("when the port throws an exception nothing else maps - then the response is 500, naming the "
                + "request rather than the session")
        void whenPortThrowsUnmappedException_thenResponseIs500NamingRequestNotSession() throws Exception {
            String secretMessage = "connection pool exhausted on host db-primary-7";
            when(browseExpensesPort.browse(any())).thenThrow(new RuntimeException(secretMessage));

            MvcResult result = mockMvc.perform(get(EXPENSES_PATH).cookie(sessionCookie()))
                    .andExpect(status().isInternalServerError())
                    .andReturn();

            String message = assertSingleJsonMessage(result);
            assertThat(message).doesNotContainIgnoringCase("session").doesNotContain(secretMessage);
        }

        @Test
        @DisplayName("when the port throws InvalidExpenseFilterException - then the response is 400, and the "
                + "message names the parameter and the bound it broke")
        void whenPortThrowsInvalidExpenseFilterException_thenResponseIs400NamingParameterAndBound() throws Exception {
            String exceptionMessage = "limit must be between 1 and 100";
            when(browseExpensesPort.browse(any())).thenThrow(new InvalidExpenseFilterException(exceptionMessage));

            MvcResult result = mockMvc.perform(get(EXPENSES_PATH).cookie(sessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(assertSingleJsonMessage(result)).isEqualTo(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws InvalidSpendingPeriodException - then the response is 400, and the "
                + "message names the period as from and to")
        void whenPortThrowsInvalidSpendingPeriodException_thenResponseIs400NamingFromAndTo() throws Exception {
            when(browseExpensesPort.browse(any()))
                    .thenThrow(new InvalidSpendingPeriodException("Period ends before it starts"));

            MvcResult result = mockMvc.perform(get(EXPENSES_PATH).cookie(sessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String message = assertSingleJsonMessage(result);
            assertThat(message).containsIgnoringCase("from").containsIgnoringCase("to");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.WebExceptionHandlerTest#invalidLimits")
        @DisplayName("when limit is refused - then the response is 400 naming limit, and the port is never called")
        void whenLimitIsRefused_thenResponseIs400NamingLimitAndPortNeverCalled(String description, String limit)
                throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("limit");
            verify(browseExpensesPort, never()).browse(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.WebExceptionHandlerTest#invalidOffsets")
        @DisplayName("when offset is refused - then the response is 400 naming offset, and the port is never called")
        void whenOffsetIsRefused_thenResponseIs400NamingOffsetAndPortNeverCalled(String description, String offset)
                throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("offset", offset))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("offset");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when status is neither PENDING nor RECORDED - then the response is 400 naming status, and "
                + "the port is never called")
        void whenStatusIsNeitherPendingNorRecorded_thenResponseIs400NamingStatusAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("status", "FOO"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("status");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when from is not a YYYY-MM-DD day - then the response is 400 naming from, and the port is "
                + "never called")
        void whenFromIsNotAYyyyMmDdDay_thenResponseIs400NamingFromAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("from", "not-a-date"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("from");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when to is not a YYYY-MM-DD day - then the response is 400 naming to, and the port is never "
                + "called")
        void whenToIsNotAYyyyMmDdDay_thenResponseIs400NamingToAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("to", "not-a-date"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("to");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when from is given with no to - then the response is 400 naming the period, and the port is "
                + "never called")
        void whenFromIsGivenWithNoTo_thenResponseIs400NamingPeriodAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("from", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String message = messageOf(result);
            assertThat(message).containsIgnoringCase("from").containsIgnoringCase("to");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when to is given with no from - then the response is 400 naming the period, and the port is "
                + "never called")
        void whenToIsGivenWithNoFrom_thenResponseIs400NamingPeriodAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("to", "2026-01-31"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String message = messageOf(result);
            assertThat(message).containsIgnoringCase("from").containsIgnoringCase("to");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when to falls before from - then the response is 400 naming the period, and the port is "
                + "never called")
        void whenToFallsBeforeFrom_thenResponseIs400NamingPeriodAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(get(EXPENSES_PATH)
                            .cookie(sessionCookie())
                            .param("from", "2026-02-01")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String message = messageOf(result);
            assertThat(message).containsIgnoringCase("from").containsIgnoringCase("to");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when categoryId is not a number - then the response is 400 naming categoryId, and the port "
                + "is never called")
        void whenCategoryIdIsNotANumber_thenResponseIs400NamingCategoryIdAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(EXPENSES_PATH).cookie(sessionCookie()).param("categoryId", "abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("categoryId");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when groupingId is not a number - then the response is 400 naming groupingId, and the port "
                + "is never called")
        void whenGroupingIdIsNotANumber_thenResponseIs400NamingGroupingIdAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(
                            get(CATEGORIES_PATH).cookie(sessionCookie()).param("groupingId", "abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("groupingId");
            verify(browseCategoriesPort, never()).browse(any());
        }
    }

    static Stream<Arguments> invalidLimits() {
        return Stream.of(
                arguments("zero", "0"),
                arguments("negative", "-1"),
                arguments("above the maximum", "101"),
                arguments("not a number", "abc"));
    }

    static Stream<Arguments> invalidOffsets() {
        return Stream.of(arguments("negative", "-1"), arguments("not a number", "xyz"));
    }

    private String assertSingleJsonMessage(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = JsonUtils.readJson(result.getResponse().getContentAsString());
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.has("message")).isTrue();
        return body.get("message").asText();
    }

    private String messageOf(MvcResult result) throws Exception {
        return JsonUtils.readJson(result.getResponse().getContentAsString())
                .get("message")
                .asText();
    }

    private static Cookie sessionCookie() {
        return BrowserSessions.cookieFor(EXTERNAL_ID);
    }
}
