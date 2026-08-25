package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.application.dto.AcceptExpensesCommand;
import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ChangeExpenseCategoryCommand;
import bot.finance.application.dto.ExpenseAcceptance;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ExpensePage;
import bot.finance.application.port.AcceptExpensesPort;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.application.port.ChangeExpenseCategoryPort;
import bot.finance.common.boot.WebAdapterTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.ExpenseEntryNotFoundException;
import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.ProposalIds;
import bot.finance.domain.value.SpendingPeriod;
import io.restassured.path.json.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the inbound HTTP adapter. Enters through the protocol - MockMvc requests against
 * {@code /api/v1/expenses} and {@code /api/v1/expenses/acceptances} - never by calling {@link ExpensesController}'s
 * methods directly, since binding lives in the generated {@link bot.finance.api.ExpensesApi} interface. Both
 * {@link BrowseExpensesPort} and {@link AcceptExpensesPort} are mocked. It owns what these endpoints accept and
 * refuse, including the 400s only their own filter and {@code ids} raise; {@link WebExceptionHandlerTest} owns only
 * the mappings every controller shares.
 */
@WebAdapterTest
@WebMvcTest(ExpensesController.class)
class ExpensesControllerTest {

    private static final String PATH = "/api/v1/expenses";
    private static final String ACCEPT_PATH = "/api/v1/expenses/acceptances";
    private static final String CHANGE_CATEGORY_PATH = "/api/v1/expenses/{id}";
    private static final long USER_ID = 778899001L;
    private static final String VALID_DOCUMENT = """
            [{"op":"replace","path":"/categoryId","value":42}]""";
    private static final MediaType JSON_PATCH = MediaType.parseMediaType("application/json-patch+json");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowseExpensesPort browseExpensesPort;

    @MockitoBean
    private AcceptExpensesPort acceptExpensesPort;

    @MockitoBean
    private ChangeExpenseCategoryPort changeExpenseCategoryPort;

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
            ExpensePage page = ExpensePage.of(List.of(firstEntry(), secondEntry()), 50, 0, 2L);
            when(browseExpensesPort.browse(any())).thenReturn(page);

            return mockMvc.perform(get(PATH).cookie(sessionCookie()))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        @Test
        @DisplayName("when the request carries every query parameter - then the port is called with a filter "
                + "carrying all of them")
        void whenEveryParameterIsGiven_thenPortIsCalledWithFilterCarryingAllOfThem() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(ExpensePage.of(List.of(), 30, 5, 0));

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

        @Test
        @DisplayName("when the request posts two ids - then the port is called with the caller's ids, and the "
                + "response is 200")
        void whenTheRequestPostsTwoIds_thenPortIsCalledWithCallerAndIdsAndResponseIs200() throws Exception {
            when(acceptExpensesPort.accept(any())).thenReturn(new ExpenseAcceptance(2, 0));

            MvcResult result = mockMvc.perform(post(ACCEPT_PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"ids":[5,7]}"""))
                    .andExpect(status().isOk())
                    .andReturn();

            ArgumentCaptor<AcceptExpensesCommand> command = ArgumentCaptor.forClass(AcceptExpensesCommand.class);
            verify(acceptExpensesPort).accept(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
            assertThat(command.getValue().ids().ids()).containsExactly(5L, 7L);

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getInt("accepted")).isEqualTo(2);
            assertThat(json.getInt("missing")).isZero();
        }

        @Test
        @DisplayName("when an entry is patched to a new categoryId - then the port receives it and the response "
                + "is 200 with the entry")
        void whenAnEntryIsPatchedToANewCategoryId_thenPortIsCalledAndResponseIs200WithTheEntry() throws Exception {
            ExpenseEntry answeredEntry = new ExpenseEntry(
                    ExpenseStatus.RECORDED,
                    7L,
                    42L,
                    "Coffee",
                    Optional.of("Corner Cafe"),
                    new Money(500L, CurrencyCode.of("EUR")),
                    Instant.parse("2026-01-01T10:00:00Z"));
            when(changeExpenseCategoryPort.change(any())).thenReturn(answeredEntry);

            MvcResult result = mockMvc.perform(patch(CHANGE_CATEGORY_PATH, "7")
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content(VALID_DOCUMENT))
                    .andExpect(status().isOk())
                    .andReturn();

            ArgumentCaptor<ChangeExpenseCategoryCommand> command =
                    ArgumentCaptor.forClass(ChangeExpenseCategoryCommand.class);
            verify(changeExpenseCategoryPort).change(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(USER_ID));
            assertThat(command.getValue().entryId()).isEqualTo(7L);
            assertThat(command.getValue().categoryId()).isEqualTo(42L);

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getLong("id")).isEqualTo(7L);
            assertThat(json.getString("status")).isEqualTo("RECORDED");
            assertThat(json.getLong("categoryId")).isEqualTo(42L);
            assertThat(json.getString("description")).isEqualTo("Coffee");
            assertThat(json.getString("merchant")).isEqualTo("Corner Cafe");
            assertThat(json.getString("money.amount")).isEqualTo("5.00");
            assertThat(json.getString("money.currency")).isEqualTo("€");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#boundaryLimits")
        @DisplayName("when limit is at either bound - then it binds and reaches the port")
        void whenLimitIsAtEitherBound_thenItBindsAndReachesThePort(String description, int limit) throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(ExpensePage.of(List.of(), limit, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("limit", String.valueOf(limit)));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().limit()).isEqualTo(limit);
        }

        @Test
        @DisplayName("when offset is zero - then it binds and reaches the port")
        void whenOffsetIsZero_thenItBindsAndReachesThePort() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(ExpensePage.of(List.of(), 50, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("offset", "0"));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().offset()).isZero();
        }

        @ParameterizedTest
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#statusValues")
        @DisplayName("when status is each enum constant - then it binds to its own enum constant")
        void whenStatusIsEachEnumConstant_thenItBindsToItsOwnEnumConstant(ExpenseStatus status) throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(ExpensePage.of(List.of(), 50, 0, 0));

            mockMvc.perform(get(PATH).cookie(sessionCookie()).param("status", status.name()));

            ArgumentCaptor<BrowseExpensesCommand> command = ArgumentCaptor.forClass(BrowseExpensesCommand.class);
            verify(browseExpensesPort).browse(command.capture());
            assertThat(command.getValue().filter().status()).isEqualTo(status);
        }

        @Test
        @DisplayName("when from and to are a well-formed pair - then they bind to a period carrying both days")
        void whenFromAndToAreAWellFormedPair_thenTheyBindToAPeriodCarryingBothDays() throws Exception {
            when(browseExpensesPort.browse(any())).thenReturn(ExpensePage.of(List.of(), 50, 0, 0));

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
            when(browseExpensesPort.browse(any())).thenReturn(ExpensePage.of(List.of(), 50, 0, 0));

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

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#idsBoundViolations")
        @DisplayName("when ids breaks its own bound - then the response is 400 naming ids, and the port is never "
                + "called")
        void whenIdsBreaksItsOwnBound_thenResponseIs400NamingIdsAndPortNeverCalled(String description, String body)
                throws Exception {
            MvcResult result = mockMvc.perform(post(ACCEPT_PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).containsIgnoringCase("ids");
            verify(acceptExpensesPort, never()).accept(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#idsUnreadableBodies")
        @DisplayName(
                "when the body naming ids cannot be read - then the response is 400, and the port is never " + "called")
        void whenTheBodyNamingIdsCannotBeRead_thenResponseIs400AndPortNeverCalled(String description, String body)
                throws Exception {
            MvcResult result = mockMvc.perform(post(ACCEPT_PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo("the request body could not be read");
            verify(acceptExpensesPort, never()).accept(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#changeCategoryPathViolations")
        @DisplayName("when the id path segment is refused - then the response is 400, and the port is never called")
        void whenIdPathSegmentIsRefused_thenResponseIs400AndPortNeverCalled(String description, String id)
                throws Exception {
            mockMvc.perform(patch(CHANGE_CATEGORY_PATH, id)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content(VALID_DOCUMENT))
                    .andExpect(status().isBadRequest());

            verify(changeExpenseCategoryPort, never()).change(any());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.adapter.web.ExpensesControllerTest#changeCategoryDocumentViolations")
        @DisplayName("when the document is refused - then the response is 400, and the port is never called")
        void whenTheDocumentIsRefused_thenResponseIs400AndPortNeverCalled(String description, String body)
                throws Exception {
            mockMvc.perform(patch(CHANGE_CATEGORY_PATH, "7")
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content(body))
                    .andExpect(status().isBadRequest());

            verify(changeExpenseCategoryPort, never()).change(any());
        }

        @Test
        @DisplayName("when the document is not JSON at all - then the response is 400 and the port is never called")
        void whenTheDocumentIsNotJsonAtAll_thenResponseIs400AndPortNeverCalled() throws Exception {
            MvcResult result = mockMvc.perform(patch(CHANGE_CATEGORY_PATH, "7")
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content("not json at all"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo("the request body could not be read");
            verify(changeExpenseCategoryPort, never()).change(any());
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

        @Test
        @DisplayName("when the port throws InvalidExpenseAcceptanceException - then the response is 400 carrying "
                + "the exception's own message")
        void whenPortThrowsInvalidExpenseAcceptanceException_thenResponseIs400CarryingExceptionsMessage()
                throws Exception {
            String exceptionMessage = "ids must not repeat a value";
            when(acceptExpensesPort.accept(any())).thenThrow(new InvalidExpenseAcceptanceException(exceptionMessage));

            MvcResult result = mockMvc.perform(post(ACCEPT_PATH)
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"ids":[5,7]}"""))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws InvalidExpenseCategoryChangeException - then the response is 400 "
                + "carrying its message")
        void whenPortThrowsInvalidExpenseCategoryChangeException_thenResponseIs400CarryingExceptionsMessage()
                throws Exception {
            String exceptionMessage = "categoryId names no category of the caller's";
            when(changeExpenseCategoryPort.change(any()))
                    .thenThrow(new InvalidExpenseCategoryChangeException(exceptionMessage));

            MvcResult result = mockMvc.perform(patch(CHANGE_CATEGORY_PATH, "7")
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content(VALID_DOCUMENT))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo(exceptionMessage);
        }

        @Test
        @DisplayName("when the port throws ExpenseEntryNotFoundException - then the response is 404 carrying the "
                + "exception's own message")
        void whenPortThrowsExpenseEntryNotFoundException_thenResponseIs404CarryingExceptionsOwnMessage()
                throws Exception {
            String exceptionMessage = "no entry of the caller's carries id 7 under RECORDED";
            when(changeExpenseCategoryPort.change(any()))
                    .thenThrow(new ExpenseEntryNotFoundException(exceptionMessage));

            MvcResult result = mockMvc.perform(patch(CHANGE_CATEGORY_PATH, "7")
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content(VALID_DOCUMENT))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(messageOf(result)).isEqualTo(exceptionMessage).isNotEqualTo("the caller is unknown");
        }

        @Test
        @DisplayName("when the port throws PersistenceFailedException - then the response is 503 and its message "
                + "names no table or statement")
        void whenPortThrowsPersistenceFailedException_thenResponseIs503NamingNoTableOrStatement() throws Exception {
            when(changeExpenseCategoryPort.change(any()))
                    .thenThrow(new PersistenceFailedException("the refile statement failed", new RuntimeException()));

            MvcResult result = mockMvc.perform(patch(CHANGE_CATEGORY_PATH, "7")
                            .with(csrf())
                            .cookie(sessionCookie())
                            .contentType(JSON_PATCH)
                            .content(VALID_DOCUMENT))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn();

            String message = messageOf(result);
            assertThat(message).doesNotContainIgnoringCase("table").doesNotContainIgnoringCase("statement");
        }
    }

    static Stream<Arguments> idsBoundViolations() {
        return Stream.of(
                arguments("absent", "{}"),
                arguments("null", """
                        {"ids":null}"""),
                arguments("an empty array", """
                        {"ids":[]}"""),
                arguments("101 ids", tooManyIds()),
                arguments("an id of 0", """
                        {"ids":[0]}"""),
                arguments("an id below 0", """
                        {"ids":[-1]}"""),
                arguments("a repeated id", """
                        {"ids":[5,5]}"""));
    }

    static Stream<Arguments> idsUnreadableBodies() {
        return Stream.of(
                arguments("a value that is not a number", """
                        {"ids":[1,"abc"]}"""),
                arguments("a body that is not JSON at all", "not json at all"));
    }

    private static String tooManyIds() {
        return IntStream.rangeClosed(1, ProposalIds.MAX_IDS + 1)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "{\"ids\":[", "]}"));
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

    static Stream<Arguments> changeCategoryPathViolations() {
        return Stream.of(arguments("id of 0", "0"), arguments("id that is not a number", "abc"));
    }

    static Stream<Arguments> changeCategoryDocumentViolations() {
        return Stream.of(
                arguments("an empty document", "[]"),
                arguments(
                        "a document of two operations",
                        """
                        [{"op":"replace","path":"/categoryId","value":42},\
                        {"op":"replace","path":"/categoryId","value":43}]"""),
                arguments(
                        "an op other than replace",
                        """
                        [{"op":"add","path":"/categoryId","value":42}]"""),
                arguments(
                        "a path other than /categoryId",
                        """
                        [{"op":"replace","path":"/other","value":42}]"""),
                arguments(
                        "a value of 0",
                        """
                        [{"op":"replace","path":"/categoryId","value":0}]"""),
                arguments(
                        "a value below 0",
                        """
                        [{"op":"replace","path":"/categoryId","value":-1}]"""),
                arguments("an absent value", """
                        [{"op":"replace","path":"/categoryId"}]"""));
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
        return BrowserSessions.cookieFor(USER_ID);
    }
}
