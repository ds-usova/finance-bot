package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.common.LogCapture;
import bot.finance.common.boot.TurnOperationsAdapterTest;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TurnOperationCalls;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Integration test for the inbound gRPC adapter. Enters through the protocol - a {@code LedgerTurnService} call on
 * the server's port under a bearer token - never by calling {@link LedgerTurnGrpcService}'s methods directly. Only
 * {@link CreateExpenseProposalPort} and {@link SummarizeSpendingPort} are mocked.
 */
@TurnOperationsAdapterTest
class LedgerTurnGrpcServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-12T09:30:00Z");
    private static final String INTERNAL_DETAIL = "jdbc:postgresql://db-internal:5432 refused the connection";

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private CreateExpenseProposalPort createExpenseProposalPort;

    @Autowired
    private SummarizeSpendingPort summarizeSpendingPort;

    private String token(long userId) {
        return McpTokens.tokenFor(accessTokenMinter, userId);
    }

    private FileExpenseProposalResponse fileExpense(String token, FileExpenseProposalRequest request) {
        return TurnOperationCalls.call(grpcServerLifecycle.getPort(), token, stub -> stub.fileExpenseProposal(request));
    }

    private RecordSpendingPeriodResponse recordPeriod(String token, String from, String to) {
        return TurnOperationCalls.call(
                grpcServerLifecycle.getPort(),
                token,
                stub -> stub.recordSpendingPeriod(TurnOperationCalls.recordPeriod(from, to)));
    }

    private FileExpenseProposalRequest dining() {
        return TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", null, "4.50", "EUR");
    }

    private static Expense stored(Optional<String> merchant, Money money, Optional<IncomingMessageId> reference) {
        return Expense.stored(
                4242L, 42L, 3L, "coffee", merchant, money, ExpenseStatus.PENDING, reference, CREATED_AT, CREATED_AT);
    }

    private static Expense storedEuros() {
        return stored(Optional.empty(), new Money(450L, CurrencyCode.of("EUR")), Optional.empty());
    }

    private CreateExpenseProposalCommand receivedCommand() {
        ArgumentCaptor<CreateExpenseProposalCommand> command =
                ArgumentCaptor.forClass(CreateExpenseProposalCommand.class);
        verify(createExpenseProposalPort).create(command.capture());
        return command.getValue();
    }

    private static StatusRuntimeException failureOf(Runnable call) {
        StatusRuntimeException failure = catchThrowableOfType(call::run, StatusRuntimeException.class);
        assertThat(failure).as("the call's failure").isNotNull();
        return failure;
    }

    private static List<String> turnCallLines(LogCapture logCapture) {
        return logCapture.messages().stream()
                .filter(message -> message.startsWith("turn_call"))
                .toList();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when FileExpenseProposal is called - then the port receives the caller, the message and the "
                + "entry's fields")
        void whenFileExpenseProposalIsCalled_thenPortReceivesCallerMessageAndFields() {
            IncomingMessageId reference = IncomingMessageId.of("m-42");
            when(createExpenseProposalPort.create(any())).thenReturn(storedEuros());

            fileExpense(McpTokens.tokenFor(accessTokenMinter, 42L, reference), dining());

            CreateExpenseProposalCommand command = receivedCommand();
            assertThat(command.userId()).isEqualTo(new AuthenticatedUserId(42L));
            assertThat(command.incomingMessageId()).isEqualTo(reference);
            assertThat(command.groupingName()).isEqualTo("Dining");
            assertThat(command.categoryName()).isEqualTo("Cafés");
            assertThat(command.description()).isEqualTo("coffee");
            assertThat(command.merchant()).isEmpty();
            assertThat(command.money()).isEqualTo(new Money(450L, CurrencyCode.of("EUR")));
        }

        @Test
        @DisplayName("when FileExpenseProposal is called - then the call answers the stored entry")
        void whenFileExpenseProposalIsCalled_thenCallAnswersStoredEntry() {
            when(createExpenseProposalPort.create(any())).thenReturn(storedEuros());

            FileExpenseProposalResponse response = fileExpense(token(42L), dining());

            assertThat(response.getId()).isEqualTo(4242L);
            assertThat(response.getGrouping()).isEqualTo("Dining");
            assertThat(response.getCategory()).isEqualTo("Cafés");
            assertThat(response.getDescription()).isEqualTo("coffee");
            assertThat(response.hasMerchant()).isFalse();
            assertThat(response.getAmount()).isEqualTo("4.50");
            assertThat(response.getCurrencyCode()).isEqualTo("EUR");
            assertThat(response.getCreatedAt()).isEqualTo("2026-10-12T09:30:00Z");
        }

        @Test
        @DisplayName("when the entry has a merchant - then the call answers it")
        void whenEntryHasMerchant_thenCallAnswersIt() {
            when(createExpenseProposalPort.create(any()))
                    .thenReturn(stored(Optional.of("Trattoria Roma"), new Money(450L, CurrencyCode.of("EUR")), Optional.empty()));

            FileExpenseProposalResponse response = fileExpense(
                    token(42L),
                    TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", "Trattoria Roma", "4.50", "EUR"));

            assertThat(response.hasMerchant()).isTrue();
            assertThat(response.getMerchant()).isEqualTo("Trattoria Roma");
            assertThat(receivedCommand().merchant()).contains("Trattoria Roma");
        }

        @Test
        @DisplayName("when the amount is written with surrounding whitespace - then the port receives it trimmed")
        void whenAmountHasSurroundingWhitespace_thenPortReceivesItTrimmed() {
            when(createExpenseProposalPort.create(any())).thenReturn(storedEuros());

            fileExpense(
                    token(42L), TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", null, " 4.50 ", "EUR"));

            assertThat(receivedCommand().money()).isEqualTo(new Money(450L, CurrencyCode.of("EUR")));
        }

        @Test
        @DisplayName("when the merchant is blank - then the port receives no merchant")
        void whenMerchantIsBlank_thenPortReceivesNoMerchant() {
            when(createExpenseProposalPort.create(any())).thenReturn(storedEuros());

            fileExpense(token(42L), TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", "   ", "4.50", "EUR"));

            assertThat(receivedCommand().merchant()).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("acceptedExtremes")
        @DisplayName("when the amount is at the edge of what is accepted - then the port receives it")
        void whenAmountIsAtEdgeOfWhatIsAccepted_thenPortReceivesIt(
                String amount, String currencyCode, long minorUnits) {
            Money money = new Money(minorUnits, CurrencyCode.of(currencyCode));
            when(createExpenseProposalPort.create(any())).thenReturn(stored(Optional.empty(), money, Optional.empty()));

            fileExpense(
                    token(42L),
                    TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", null, amount, currencyCode));

            assertThat(receivedCommand().money()).isEqualTo(money);
        }

        static Stream<Arguments> acceptedExtremes() {
            return Stream.of(
                    arguments("999999999999999999", "JPY", 999999999999999999L), arguments("1.2345", "CLF", 12345L));
        }

        @Test
        @DisplayName("when RecordSpendingPeriod is called - then the port receives the caller, the message and both "
                + "days")
        void whenRecordSpendingPeriodIsCalled_thenPortReceivesCallerMessageAndBothDays() {
            IncomingMessageId reference = IncomingMessageId.of("m-42");
            when(summarizeSpendingPort.summarize(any()))
                    .thenReturn(new SpendingPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)));

            recordPeriod(McpTokens.tokenFor(accessTokenMinter, 42L, reference), "2026-10-12", "2026-10-12");

            ArgumentCaptor<SummarizeSpendingCommand> command = ArgumentCaptor.forClass(SummarizeSpendingCommand.class);
            verify(summarizeSpendingPort).summarize(command.capture());
            assertThat(command.getValue().userId()).isEqualTo(new AuthenticatedUserId(42L));
            assertThat(command.getValue().reference()).isEqualTo(reference);
            assertThat(command.getValue().from()).isEqualTo("2026-10-12");
            assertThat(command.getValue().to()).isEqualTo("2026-10-12");
        }

        @Test
        @DisplayName("when the port answers a period - then RecordSpendingPeriod answers its two days")
        void whenPortAnswersPeriod_thenRecordSpendingPeriodAnswersItsTwoDays() {
            when(summarizeSpendingPort.summarize(any()))
                    .thenReturn(new SpendingPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)));

            RecordSpendingPeriodResponse response = recordPeriod(token(42L), "2026-10-12", "2026-10-12");

            assertThat(response.getFrom()).isEqualTo("2026-10-12");
            assertThat(response.getTo()).isEqualTo("2026-10-12");
        }

        @Test
        @DisplayName("when FileExpenseProposal succeeds - then one ok turn_call line carries the caller, the message "
                + "and the arguments")
        void whenFileExpenseProposalSucceeds_thenOneOkTurnCallLineCarriesCallerMessageAndArguments() {
            when(createExpenseProposalPort.create(any())).thenReturn(storedEuros());

            try (LogCapture logCapture = LogCapture.attachedTo(TurnCallLog.class)) {
                fileExpense(
                        McpTokens.tokenFor(accessTokenMinter, 42L, IncomingMessageId.of("m")),
                        TurnOperationCalls.fileExpense("Dining", "Restaurants", "lunch", "Cafe", "4.50", "EUR"));

                assertThat(turnCallLines(logCapture))
                        .singleElement()
                        .asString()
                        .startsWith("turn_call operation=create_expense_proposal via=grpc outcome=ok userId=42 "
                                + "message=m arguments=")
                        .contains("\"category\":\"Restaurants\"")
                        .contains("\"grouping\":\"Dining\"")
                        .contains("\"description\":\"lunch\"")
                        .contains("\"merchant\":\"Cafe\"")
                        .contains("\"amount\":\"4.50\"")
                        .contains("\"currencyCode\":\"EUR\"");
            }
        }

        @Test
        @DisplayName("when RecordSpendingPeriod succeeds - then one ok turn_call line carries both days")
        void whenRecordSpendingPeriodSucceeds_thenOneOkTurnCallLineCarriesBothDays() {
            when(summarizeSpendingPort.summarize(any()))
                    .thenReturn(new SpendingPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)));

            try (LogCapture logCapture = LogCapture.attachedTo(TurnCallLog.class)) {
                recordPeriod(token(42L), "2026-10-12", "2026-10-12");

                assertThat(turnCallLines(logCapture))
                        .singleElement()
                        .asString()
                        .startsWith("turn_call operation=summarize_spending via=grpc outcome=ok userId=42 ")
                        .contains("\"from\":\"2026-10-12\"")
                        .contains("\"to\":\"2026-10-12\"");
            }
        }
    }

    /** One operation of the service, with how to make its port throw and how to call it. */
    private enum Operation {
        FILE_EXPENSE_PROPOSAL("the proposal could not be stored", "the proposal could not be created"),
        RECORD_SPENDING_PERIOD("the summary could not be recorded", "the spending could not be summarized");

        final String persistenceFailureDescription;
        final String unexpectedFailureDescription;

        Operation(String persistenceFailureDescription, String unexpectedFailureDescription) {
            this.persistenceFailureDescription = persistenceFailureDescription;
            this.unexpectedFailureDescription = unexpectedFailureDescription;
        }
    }

    private void arrange(Operation operation, RuntimeException failure) {
        switch (operation) {
            case FILE_EXPENSE_PROPOSAL -> when(createExpenseProposalPort.create(any()))
                    .thenThrow(failure);
            case RECORD_SPENDING_PERIOD -> when(summarizeSpendingPort.summarize(any()))
                    .thenThrow(failure);
        }
    }

    private StatusRuntimeException call(Operation operation) {
        Runnable call =
                switch (operation) {
                    case FILE_EXPENSE_PROPOSAL -> () -> fileExpense(token(42L), dining());
                    case RECORD_SPENDING_PERIOD -> () -> recordPeriod(token(42L), "2026-10-12", "2026-10-13");
                };
        return failureOf(call);
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port throws InvalidGroupingException - then INVALID_ARGUMENT repeats the grouping")
        void whenPortThrowsInvalidGroupingException_thenInvalidArgumentRepeatsTheGrouping() {
            arrange(Operation.FILE_EXPENSE_PROPOSAL, new InvalidGroupingException("no grouping named Snacks"));

            StatusRuntimeException failure = call(Operation.FILE_EXPENSE_PROPOSAL);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(failure.getStatus().getDescription()).contains("Snacks");
        }

        @Test
        @DisplayName("when the port throws InvalidCategoryException - then INVALID_ARGUMENT names both categories")
        void whenPortThrowsInvalidCategoryException_thenInvalidArgumentNamesBothCategories() {
            arrange(
                    Operation.FILE_EXPENSE_PROPOSAL,
                    new InvalidCategoryException("Fuel is not filed under Dining"));

            StatusRuntimeException failure = call(Operation.FILE_EXPENSE_PROPOSAL);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(failure.getStatus().getDescription()).contains("Fuel").contains("Dining");
        }

        @Test
        @DisplayName("when the port throws InvalidExpenseException - then INVALID_ARGUMENT names the invalid request "
                + "and the field")
        void whenPortThrowsInvalidExpenseException_thenInvalidArgumentNamesInvalidRequestAndField() {
            arrange(Operation.FILE_EXPENSE_PROPOSAL, new InvalidExpenseException("description must be present"));

            StatusRuntimeException failure = call(Operation.FILE_EXPENSE_PROPOSAL);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(failure.getStatus().getDescription())
                    .contains("invalid request")
                    .contains("description");
        }

        @Test
        @DisplayName("when the port throws InvalidSpendingPeriodException - then INVALID_ARGUMENT carries its own "
                + "message")
        void whenPortThrowsInvalidSpendingPeriodException_thenInvalidArgumentCarriesItsOwnMessage() {
            arrange(
                    Operation.RECORD_SPENDING_PERIOD,
                    new InvalidSpendingPeriodException("the period ends before it starts"));

            StatusRuntimeException failure = call(Operation.RECORD_SPENDING_PERIOD);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(failure.getStatus().getDescription()).isEqualTo("the period ends before it starts");
        }

        @Test
        @DisplayName("when the summarizing port throws InvalidUserException - then INVALID_ARGUMENT names an "
                + "invalid request")
        void whenSummarizingPortThrowsInvalidUserException_thenInvalidArgumentNamesInvalidRequest() {
            arrange(Operation.RECORD_SPENDING_PERIOD, new InvalidUserException("authenticated user id is blank"));

            StatusRuntimeException failure = call(Operation.RECORD_SPENDING_PERIOD);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(failure.getStatus().getDescription()).contains("invalid request");
        }

        @ParameterizedTest
        @EnumSource(Operation.class)
        @DisplayName("when the port throws EntityNotFoundException - then NOT_FOUND says the user is unknown")
        void whenPortThrowsEntityNotFoundException_thenNotFoundSaysUserIsUnknown(Operation operation) {
            arrange(operation, new EntityNotFoundException("User", "no user stored for id 42"));

            StatusRuntimeException failure = call(operation);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
            assertThat(failure.getStatus().getDescription()).isEqualTo("the user is unknown");
        }

        @ParameterizedTest
        @EnumSource(Operation.class)
        @DisplayName("when the port throws PersistenceFailedException - then UNAVAILABLE carries the tool's reason "
                + "and never the detail")
        void whenPortThrowsPersistenceFailedException_thenUnavailableCarriesToolsReasonAndNeverTheDetail(
                Operation operation) {
            arrange(operation, new PersistenceFailedException(INTERNAL_DETAIL, new RuntimeException(INTERNAL_DETAIL)));

            StatusRuntimeException failure = call(operation);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(failure.getStatus().getDescription())
                    .isEqualTo(operation.persistenceFailureDescription)
                    .doesNotContain(INTERNAL_DETAIL);
        }

        @ParameterizedTest
        @EnumSource(Operation.class)
        @DisplayName("when the port throws an unrecognised RuntimeException - then INTERNAL carries the tool's "
                + "reason and never the detail")
        void whenPortThrowsUnrecognisedRuntimeException_thenInternalCarriesToolsReasonAndNeverTheDetail(
                Operation operation) {
            arrange(operation, new IllegalStateException(INTERNAL_DETAIL));

            StatusRuntimeException failure = call(operation);

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
            assertThat(failure.getStatus().getDescription())
                    .isEqualTo(operation.unexpectedFailureDescription)
                    .doesNotContain(INTERNAL_DETAIL);
        }

        @Test
        @DisplayName("when the port refuses the call - then the turn_call line reads outcome=rejected")
        void whenPortRefusesTheCall_thenTurnCallLineReadsRejected() {
            arrange(Operation.FILE_EXPENSE_PROPOSAL, new InvalidGroupingException("no grouping named Snacks"));

            try (LogCapture logCapture = LogCapture.attachedTo(TurnCallLog.class)) {
                call(Operation.FILE_EXPENSE_PROPOSAL);

                assertThat(turnCallLines(logCapture))
                        .singleElement()
                        .asString()
                        .startsWith("turn_call operation=create_expense_proposal via=grpc outcome=rejected "
                                + "userId=42 ");
            }
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidRequests")
        @DisplayName("when a field is malformed - then INVALID_ARGUMENT names the invalid request and the field, and "
                + "no port is called")
        void whenFieldIsMalformed_thenInvalidArgumentNamesInvalidRequestAndFieldAndNoPortIsCalled(
                String label, String field, FileExpenseProposalRequest request) {
            StatusRuntimeException failure = failureOf(() -> fileExpense(token(42L), request));

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(failure.getStatus().getDescription())
                    .contains("invalid request")
                    .containsIgnoringCase(field);
            verifyNoInteractions(createExpenseProposalPort, summarizeSpendingPort);
        }

        static Stream<Arguments> invalidRequests() {
            return Stream.of(
                    amount("a blank amount", "", "EUR"),
                    amount("a whitespace-only amount", "   ", "EUR"),
                    amount("a minus sign", "-4.50", "EUR"),
                    amount("a plus sign", "+4.50", "EUR"),
                    amount("an exponent", "1e3", "EUR"),
                    amount("digit grouping", "1,000.00", "EUR"),
                    amount("five decimal places", "4.12345", "EUR"),
                    amount("nineteen integer digits", "1234567890123456789", "EUR"),
                    amount("more decimals than the currency records", "12.505", "EUR"),
                    currency("two letters", "EU"),
                    currency("four letters", "EURO"),
                    currency("a digit among the letters", "E1R"),
                    arguments(
                            "an empty grouping",
                            "grouping",
                            TurnOperationCalls.fileExpense("", "Cafés", "coffee", null, "4.50", "EUR")),
                    arguments(
                            "a blank grouping",
                            "grouping",
                            TurnOperationCalls.fileExpense("   ", "Cafés", "coffee", null, "4.50", "EUR")));
        }

        private static Arguments amount(String label, String amount, String currencyCode) {
            return arguments(
                    label,
                    "amount",
                    TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", null, amount, currencyCode));
        }

        private static Arguments currency(String label, String currencyCode) {
            return arguments(
                    label,
                    "currency_code",
                    TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", null, "4.50", currencyCode));
        }
    }
}
