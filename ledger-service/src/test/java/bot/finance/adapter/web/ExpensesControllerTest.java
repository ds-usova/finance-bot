package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ExpensePage;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import io.restassured.path.json.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the inbound HTTP adapter. Enters through the protocol - a MockMvc GET against
 * {@code /api/v1/expenses} - never by calling {@link ExpensesController}'s method directly, since binding lives in
 * the generated {@link bot.finance.api.ExpensesApi} interface. Only {@link BrowseExpensesPort} is mocked. It owns
 * what this endpoint accepts and refuses, including the two 400s only its own filter raises;
 * {@link WebExceptionHandlerTest} owns only the mappings every controller shares.
 */
@WebAdapterTest
@WebMvcTest(ExpensesController.class)
class ExpensesControllerTest {

    private static final String PATH = "/api/v1/expenses";
    private static final String EXTERNAL_ID = "778899001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseExpensesPort browseExpensesPort;

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when the request carries no query parameters - then the port is called with a filter "
                + "carrying the defaults")
        void whenNoQueryParameters_thenPortIsCalledWithDefaultFilter() throws Exception {
            browseWithNoParameters();

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            ExpenseFilter filter = command.getValue().filter();
            assertThat(filter.limit()).isEqualTo(ExpenseFilter.DEFAULT_LIMIT);
            assertThat(filter.offset()).isZero();
            assertThat(filter.status()).isNull();
            assertThat(filter.categoryId()).isNull();
            assertThat(filter.period()).isNull();
        }

        @Test
        @DisplayName("when the port answers a page - then the response is 200 with the page's items, limit, "
                + "offset and total")
        void whenPortAnswersAPage_thenResponseIs200WithThePage() throws Exception {
            MvcResult result = browseWithNoParameters();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getList("items")).hasSize(2);
            assertThat(json.getInt("limit")).isEqualTo(50);
            assertThat(json.getInt("offset")).isZero();
            assertThat(json.getLong("total")).isEqualTo(2L);
        }

        /** A GET carrying a valid session cookie and no query parameters, the port answering a page of two. */
        private MvcResult browseWithNoParameters() throws Exception {
            ExpensePage page = new ExpensePage(List.of(firstEntry(), secondEntry()), 50, 0, 2L);
            when(browseExpensesPort.browse(any())).thenReturn(page);

            return mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        @Test
        @DisplayName("when the request carries every query parameter - then the port is called with a filter "
                + "carrying all of them")
        void whenEveryParameterIsGiven_thenPortIsCalledWithFilterCarryingAllOfThem() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(new ExpensePage(List.of(), 30, 5, 0));

            mockMvc.perform(get(PATH)
                    .cookie(sessionCookie())
                    .param("status", "RECORDED")
                    .param("categoryId", "7")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .param("limit", "30")
                    .param("offset", "5"));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            ExpenseFilter filter = command.getValue().filter();
            assertThat(filter.status()).isEqualTo(ExpenseStatus.RECORDED);
            assertThat(filter.categoryId()).isEqualTo(7L);
            assertThat(filter.period())
                    .isEqualTo(new SpendingPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
            assertThat(filter.limit()).isEqualTo(30);
            assertThat(filter.offset()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#boundaryLimits")
        @DisplayName("when limit is at either bound - then it binds and reaches the port")
        void whenLimitIsAtEitherBound_thenItBindsAndReachesThePort(String description, int limit) throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(new ExpensePage(List.of(), limit, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("limit", String.valueOf(limit)));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().limit()).isEqualTo(limit);
        }

        @Test
        @DisplayName("when offset is zero - then it binds and reaches the port")
        void whenOffsetIsZero_thenItBindsAndReachesThePort() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(new ExpensePage(List.of(), 50, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("offset", "0"));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().offset()).isZero();
        }

        @ParameterizedTest
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#statusValues")
        @DisplayName("when status is each enum constant - then it binds to its own enum constant")
        void whenStatusIsEachEnumConstant_thenItBindsToItsOwnEnumConstant(ExpenseStatus status) throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(new ExpensePage(List.of(), 50, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("status", status.name()));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().status()).isEqualTo(status);
        }

        @Test
        @DisplayName("when from and to are a well-formed pair - then they bind to a period carrying both days")
        void whenFromAndToAreAWellFormedPair_thenTheyBindToAPeriodCarryingBothDays() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(new ExpensePage(List.of(), 50, 0, 0));

            mockMvc.perform(get(PATH)
                    .cookie(sessionCookie())
                    .param("from", "2026-02-01")
                    .param("to", "2026-02-14"));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().period())
                    .isEqualTo(new SpendingPeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 14)));
        }

        @Test
        @DisplayName("when categoryId is a number - then it binds and reaches the port")
        void whenCategoryIdIsANumber_thenItBindsAndReachesThePort() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(new ExpensePage(List.of(), 50, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("categoryId", "42"));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().categoryId()).isEqualTo(42L);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#invalidLimits")
        @DisplayName("when limit is refused - then the response is 400 naming limit, and the port is never called")
        void whenLimitIsRefused_thenResponseIs400NamingLimitAndPortNeverCalled(String description, String limit)
                throws Exception {
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("limit");
            verify(browseExpensesPort, never()).browse(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#invalidOffsets")
        @DisplayName("when offset is refused - then the response is 400 naming offset, and the port is never called")
        void whenOffsetIsRefused_thenResponseIs400NamingOffsetAndPortNeverCalled(String description, String offset)
                throws Exception {
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("offset", offset))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("offset");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when status is neither PENDING nor RECORDED - then the response is 400 naming status, and "
                + "the port is never called")
        void whenStatusIsNeitherPendingNorRecorded_thenResponseIs400NamingStatusAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("status", "FOO"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("status");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when from is not a YYYY-MM-DD day - then the response is 400 naming from, and the port is "
                + "never called")
        void whenFromIsNotAYyyyMmDdDay_thenResponseIs400NamingFromAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("from", "not-a-date"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("from");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when to is not a YYYY-MM-DD day - then the response is 400 naming to, and the port is never "
                + "called")
        void whenToIsNotAYyyyMmDdDay_thenResponseIs400NamingToAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("to", "not-a-date"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("to");
            verify(browseExpensesPort, never()).browse(any());
        }

        @Test
        @DisplayName("when from is given with no to - then the response is 400 naming the period, and the port is "
                + "never called")
        void whenFromIsGivenWithNoTo_thenResponseIs400NamingPeriodAndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("from", "2026-01-01"))
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
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("to", "2026-01-31"))
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
            MvcResult result = mockMvc.perform(get(PATH)
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
            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()).param("categoryId", "abc"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("categoryId");
            verify(browseExpensesPort, never()).browse(any());
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port throws InvalidExpenseFilterException - then the response is 400 carrying the "
                + "exception's message")
        void whenPortThrowsInvalidExpenseFilterException_thenResponseIs400NamingParameterAndBound() throws Exception {
            String exceptionMessage = "limit must be between 1 and 100";
            when(browseExpensesPort.browse(any())).thenThrow(new InvalidExpenseFilterException(exceptionMessage));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws InvalidSpendingPeriodException - then the response is 400 naming from "
                + "and to")
        void whenPortThrowsInvalidSpendingPeriodException_thenResponseIs400NamingFromAndTo() throws Exception {
            when(browseExpensesPort.browse(any()))
                    .thenThrow(new InvalidSpendingPeriodException("Period ends before it starts"));

            MvcResult result = mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String message = messageOf(result);
            assertThat(message).containsIgnoringCase("from").containsIgnoringCase("to");
        }
    }

    static Stream<Arguments> boundaryLimits() {
        return Stream.of(arguments("the minimum", 1), arguments("the maximum", ExpenseFilter.MAX_LIMIT));
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

    private static String messageOf(MvcResult result) throws Exception {
        return JsonUtils.readJson(result.getResponse().getContentAsString())
                .get("message")
                .asText();
    }

    static Stream<ExpenseStatus> statusValues() {
        return Stream.of(ExpenseStatus.PENDING, ExpenseStatus.RECORDED);
    }

    private static ExpenseEntry firstEntry() {
        return new ExpenseEntry(
                ExpenseStatus.PENDING,
                1L,
                10L,
                "Coffee",
                Optional.of("Corner Cafe"),
                new Money(500L, CurrencyCode.of("EUR")),
                Instant.parse("2026-01-01T10:00:00Z"));
    }

    private static ExpenseEntry secondEntry() {
        return new ExpenseEntry(
                ExpenseStatus.RECORDED,
                2L,
                20L,
                "Groceries",
                Optional.empty(),
                new Money(3000L, CurrencyCode.of("EUR")),
                Instant.parse("2026-01-02T08:30:00Z"));
    }

    private static Cookie sessionCookie() {
        return BrowserSessions.cookieFor(EXTERNAL_ID);
    }
}
