package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.RecallExamplesCommand;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ExtractIntentsUseCaseTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    private ExpenseRecordingPort expenseRecordingPort;
    private MessageStorePort messageStorePort;
    private RecallExamplesPort recallExamplesPort;
    private Logger log;
    private ExtractIntentsUseCase useCase;

    @BeforeEach
    void setUp() {
        expenseRecordingPort = mock(ExpenseRecordingPort.class);
        messageStorePort = mock(MessageStorePort.class);
        recallExamplesPort = mock(RecallExamplesPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        useCase = new ExtractIntentsUseCase(expenseRecordingPort, messageStorePort, recallExamplesPort, loggerFactory);
    }

    private static ExtractIntentsCommand command(
            String text, List<String> categoryGroupings, String catchAllGrouping, LocalDate currentDate) {
        return new ExtractIntentsCommand(
                text, categoryGroupings, catchAllGrouping, Optional.empty(), currentDate, Optional.empty());
    }

    private static ExtractIntentsCommand command(
            String text,
            List<String> categoryGroupings,
            String catchAllGrouping,
            CurrencyCode defaultCurrency,
            LocalDate currentDate) {
        return new ExtractIntentsCommand(
                text, categoryGroupings, catchAllGrouping, Optional.of(defaultCurrency), currentDate, Optional.empty());
    }

    private static ExtractIntentsCommand commandWithIdentity(
            String text, List<String> categoryGroupings, String catchAllGrouping, MessageIdentity messageIdentity) {
        return new ExtractIntentsCommand(
                text,
                categoryGroupings,
                catchAllGrouping,
                Optional.empty(),
                CURRENT_DATE,
                Optional.of(messageIdentity));
    }

    private static MessageExample exampleFixture() {
        ExampleExpense expense = new ExampleExpense(
                "lunch",
                "15.00",
                CurrencyCode.of("EUR"),
                Optional.of("Restaurants"),
                Optional.of("Dining"),
                ExampleOutcome.ACCEPTED);
        return new MessageExample("spent 15 euros on lunch last week", List.of(expense));
    }

    private List<String> loggedInfoLines() {
        return MockedLoggerUtils.infoLines(log);
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    @Nested
    @DisplayName("extracting intents")
    class ExtractIntents {

        @Test
        @DisplayName("when a command carries grouping names and a catch-all - then the port receives the "
                + "command's values unchanged")
        void whenCommandCarriesThreeGroupingNamesAndACatchAll_thenPortReceivesThemPassedThrough() {
            List<String> categoryGroupings = List.of("Food", "Travel", "Other");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Other", CURRENT_DATE);

            useCase.extractIntents(command);

            verify(expenseRecordingPort)
                    .record(
                            eq(TEXT),
                            eq(categoryGroupings),
                            eq("Other"),
                            eq(Optional.empty()),
                            eq(CURRENT_DATE),
                            eq(Optional.empty()));
        }

        @Test
        @DisplayName("when a command carries a current date - then the port receives that same date, unchanged")
        void whenCommandCarriesCurrentDate_thenPortReceivesThatSameDateUnchanged() {
            LocalDate currentDate = LocalDate.of(2026, 1, 15);
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Food", currentDate);

            useCase.extractIntents(command);

            verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), eq(currentDate), eq(Optional.empty()));
        }

        @Test
        @DisplayName("when a command carries an assumed currency - then the port receives that currency code")
        void whenCommandCarriesAssumedCurrency_thenPortReceivesThatCurrencyCode() {
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command =
                    command(TEXT, categoryGroupings, "Food", CurrencyCode.of("EUR"), CURRENT_DATE);

            useCase.extractIntents(command);

            verify(expenseRecordingPort)
                    .record(
                            any(),
                            any(),
                            any(),
                            eq(Optional.of(CurrencyCode.of("EUR"))),
                            any(),
                            eq(Optional.empty()));
        }

        @Test
        @DisplayName("when the command is null - then throws InvalidValueException and the port is never called")
        void whenCommandIsNull_thenThrowsInvalidValueExceptionAndPortNeverCalled() {
            assertThatThrownBy(() -> useCase.extractIntents(null)).isInstanceOf(InvalidValueException.class);

            verifyNoInteractions(expenseRecordingPort);
        }

        @Test
        @DisplayName(
                "when the port throws ExpenseRecordingFailedException - then the exception propagates " + "unchanged")
        void whenPortThrowsExpenseRecordingFailedException_thenExceptionPropagatesUnchanged() {
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Food", CURRENT_DATE);
            ExpenseRecordingFailedException failure = new ExpenseRecordingFailedException("provider unreachable");
            doThrow(failure)
                    .when(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.empty()));

            assertThatThrownBy(() -> useCase.extractIntents(command)).isSameAs(failure);
        }

        @Test
        @DisplayName("when the port returns normally - then one INFO line is logged, naming the grouping count "
                + "and no message text")
        void whenPortReturnsNormally_thenOneInfoLineLoggedNamingCategoryCountAndCarryingNothingFromText() {
            List<String> categoryGroupings = List.of("Food", "Travel");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Food", CURRENT_DATE);

            useCase.extractIntents(command);

            assertThat(loggedInfoLines()).hasSize(1);
            assertThat(loggedInfoLines().get(0)).contains("2").doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when a command carries a message identity - then the store registers it before the "
                + "expense is recorded")
        void whenCommandCarriesMessageIdentity_thenStoreRegistersItWithTextBeforeExpenseIsRecorded() {
            MessageIdentity identity = new MessageIdentity(42L, "msg-123");
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = commandWithIdentity(TEXT, categoryGroupings, "Food", identity);

            useCase.extractIntents(command);

            InOrder inOrder = inOrder(messageStorePort, expenseRecordingPort);
            inOrder.verify(messageStorePort).register(eq(identity), eq(TEXT));
            inOrder.verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.empty()));
        }

        @Test
        @DisplayName("when a command carries no message identity - then the store is never touched and the "
                + "expense is still recorded")
        void whenCommandCarriesNoMessageIdentity_thenStoreNeverTouchedAndExpenseStillRecorded() {
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Food", CURRENT_DATE);

            useCase.extractIntents(command);

            verifyNoInteractions(messageStorePort);
            verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.empty()));
        }

        @Test
        @DisplayName("when register() throws MessageStoreFailedException - then WARN names the identity and "
                + "no text, expense still recorded")
        void whenRegisterThrowsMessageStoreFailedException_thenOneWarnLineNamesIdentityAndExpenseStillRecorded() {
            MessageIdentity identity = new MessageIdentity(42L, "msg-123");
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = commandWithIdentity(TEXT, categoryGroupings, "Food", identity);
            doThrow(new MessageStoreFailedException("store unreachable"))
                    .when(messageStorePort)
                    .register(any(), any());
            when(recallExamplesPort.recall(any())).thenReturn(Optional.empty());

            useCase.extractIntents(command);

            assertThat(loggedWarnLines()).hasSize(1);
            assertThat(loggedWarnLines().get(0))
                    .contains("42")
                    .contains("msg-123")
                    .doesNotContain(TEXT);
            verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.empty()));
        }

        @Test
        @DisplayName("when the recall port answers examples - then the recording port receives those examples")
        void whenCommandCarriesIdentityAndRecallAnswersExamples_thenRecallReceivesIdentityAndPortReceivesExamples() {
            MessageIdentity identity = new MessageIdentity(42L, "msg-123");
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = commandWithIdentity(TEXT, categoryGroupings, "Food", identity);
            List<MessageExample> examples = List.of(exampleFixture());
            when(recallExamplesPort.recall(any())).thenReturn(Optional.of(examples));

            useCase.extractIntents(command);

            InOrder inOrder = inOrder(messageStorePort, recallExamplesPort);
            inOrder.verify(messageStorePort).register(eq(identity), eq(TEXT));
            inOrder.verify(recallExamplesPort).recall(eq(new RecallExamplesCommand(identity, TEXT)));
            verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.of(examples)));
        }

        @Test
        @DisplayName("when the command carries no message identity - then the recall port is never touched")
        void whenCommandCarriesNoMessageIdentity_thenRecallPortUntouchedAndPortReceivesAbsentRetrieval() {
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Food", CURRENT_DATE);

            useCase.extractIntents(command);

            verifyNoInteractions(recallExamplesPort);
            verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.empty()));
        }

        @Test
        @DisplayName("when the recall port answers an absent retrieval - then the recording port receives it "
                + "absent and the turn still runs")
        void whenRecallPortAnswersAbsentRetrieval_thenPortReceivesItAbsentAndTurnStillRuns() {
            MessageIdentity identity = new MessageIdentity(42L, "msg-123");
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = commandWithIdentity(TEXT, categoryGroupings, "Food", identity);
            when(recallExamplesPort.recall(any())).thenReturn(Optional.empty());

            useCase.extractIntents(command);

            verify(expenseRecordingPort)
                    .record(any(), any(), any(), any(), any(), eq(Optional.empty()));
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName(
                "when register() throws MessageStoreFailedException - then the INFO line is still logged " + "once")
        void whenRegisterThrowsMessageStoreFailedException_thenInfoLineStillLoggedOnce() {
            MessageIdentity identity = new MessageIdentity(42L, "msg-123");
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = commandWithIdentity(TEXT, categoryGroupings, "Food", identity);
            doThrow(new MessageStoreFailedException("store unreachable"))
                    .when(messageStorePort)
                    .register(any(), any());

            useCase.extractIntents(command);

            assertThat(loggedInfoLines()).hasSize(1);
        }
    }
}
