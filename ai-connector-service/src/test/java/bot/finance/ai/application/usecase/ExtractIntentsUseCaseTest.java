package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractIntentsUseCaseTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    private ExpenseRecordingPort expenseRecordingPort;
    private MessageStorePort messageStorePort;
    private Logger log;
    private ExtractIntentsUseCase useCase;

    @BeforeEach
    void setUp() {
        expenseRecordingPort = mock(ExpenseRecordingPort.class);
        messageStorePort = mock(MessageStorePort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        useCase = new ExtractIntentsUseCase(expenseRecordingPort, messageStorePort, loggerFactory);
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

    /**
     * The lines {@code log} received at info, message and placeholders flattened into one string each, in call
     * order.
     */
    private List<String> loggedInfoLines() {
        return mockingDetails(log).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("info"))
                .map(invocation -> {
                    Object[] arguments = invocation.getArguments();
                    Object[] placeholders = Arrays.copyOfRange(arguments, 1, arguments.length);
                    return arguments[0] + " " + Arrays.toString(placeholders);
                })
                .toList();
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
                    .record(eq(TEXT), eq(categoryGroupings), eq("Other"), eq(Optional.empty()), eq(CURRENT_DATE));
        }

        @Test
        @DisplayName("when a command carries a current date - then the port receives that same date, unchanged")
        void whenCommandCarriesCurrentDate_thenPortReceivesThatSameDateUnchanged() {
            LocalDate currentDate = LocalDate.of(2026, 1, 15);
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command = command(TEXT, categoryGroupings, "Food", currentDate);

            useCase.extractIntents(command);

            verify(expenseRecordingPort).record(any(), any(), any(), any(), eq(currentDate));
        }

        @Test
        @DisplayName("when a command carries an assumed currency - then the port receives that currency code")
        void whenCommandCarriesAssumedCurrency_thenPortReceivesThatCurrencyCode() {
            List<String> categoryGroupings = List.of("Food");
            ExtractIntentsCommand command =
                    command(TEXT, categoryGroupings, "Food", CurrencyCode.of("EUR"), CURRENT_DATE);

            useCase.extractIntents(command);

            verify(expenseRecordingPort).record(any(), any(), any(), eq(Optional.of(CurrencyCode.of("EUR"))), any());
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
            doThrow(failure).when(expenseRecordingPort).record(any(), any(), any(), any(), any());

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
    }
}
