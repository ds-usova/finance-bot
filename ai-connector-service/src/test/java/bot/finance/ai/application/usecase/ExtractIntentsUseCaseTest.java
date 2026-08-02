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
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractIntentsUseCaseTest {

    private static final String TEXT = "spent 15 euros on lunch";

    private ExpenseRecordingPort expenseRecordingPort;
    private Logger log;
    private ExtractIntentsUseCase useCase;

    @BeforeEach
    void setUp() {
        expenseRecordingPort = mock(ExpenseRecordingPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        useCase = new ExtractIntentsUseCase(expenseRecordingPort, loggerFactory);
    }

    private static ExtractIntentsCommand command(String text, List<KnownCategory> knownCategories) {
        return new ExtractIntentsCommand(text, knownCategories, Optional.empty());
    }

    private static ExtractIntentsCommand command(
            String text, List<KnownCategory> knownCategories, CurrencyCode defaultCurrency) {
        return new ExtractIntentsCommand(text, knownCategories, Optional.of(defaultCurrency));
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
        @DisplayName("when a command carries a text, three known categories and no assumed currency - then the "
                + "port is called once with that text, the three categories rendered as labels in the command's "
                + "order, and an empty assumed currency")
        void whenTextThreeKnownCategoriesAndNoAssumedCurrency_thenPortCalledOnceWithTextLabelsAndEmptyCurrency() {
            List<KnownCategory> knownCategories = List.of(
                    new KnownCategory("Lunch", "Food"),
                    new KnownCategory("Flight", "Travel"),
                    new KnownCategory("Misc", "Other"));
            ExtractIntentsCommand command = command(TEXT, knownCategories);

            useCase.extractIntents(command);

            verify(expenseRecordingPort)
                    .record(
                            eq(TEXT),
                            eq(List.of("Food > Lunch", "Travel > Flight", "Other > Misc")),
                            eq(Optional.empty()));
        }

        @Test
        @DisplayName("when a command carries an assumed currency - then the port receives that currency code")
        void whenCommandCarriesAssumedCurrency_thenPortReceivesThatCurrencyCode() {
            List<KnownCategory> knownCategories = List.of(new KnownCategory("Lunch", "Food"));
            ExtractIntentsCommand command = command(TEXT, knownCategories, CurrencyCode.of("EUR"));

            useCase.extractIntents(command);

            verify(expenseRecordingPort).record(any(), any(), eq(Optional.of(CurrencyCode.of("EUR"))));
        }

        @Test
        @DisplayName("when the known categories are two entries sharing a name under different groupings - then "
                + "both labels reach the port, distinct and in order")
        void whenKnownCategoriesShareNameUnderDifferentGroupings_thenBothLabelsReachPortDistinctAndInOrder() {
            List<KnownCategory> knownCategories =
                    List.of(new KnownCategory("Travel", "Insurance"), new KnownCategory("Travel", "Trips"));
            ExtractIntentsCommand command = command(TEXT, knownCategories);

            useCase.extractIntents(command);

            verify(expenseRecordingPort).record(any(), eq(List.of("Insurance > Travel", "Trips > Travel")), any());
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
            List<KnownCategory> knownCategories = List.of(new KnownCategory("Lunch", "Food"));
            ExtractIntentsCommand command = command(TEXT, knownCategories);
            ExpenseRecordingFailedException failure = new ExpenseRecordingFailedException("provider unreachable");
            doThrow(failure).when(expenseRecordingPort).record(any(), any(), any());

            assertThatThrownBy(() -> useCase.extractIntents(command)).isSameAs(failure);
        }

        @Test
        @DisplayName("when the port returns normally - then one INFO line is logged, naming how many categories "
                + "were offered and carrying nothing from the message text")
        void whenPortReturnsNormally_thenOneInfoLineLoggedNamingCategoryCountAndCarryingNothingFromText() {
            List<KnownCategory> knownCategories =
                    List.of(new KnownCategory("Lunch", "Food"), new KnownCategory("Flight", "Travel"));
            ExtractIntentsCommand command = command(TEXT, knownCategories);

            useCase.extractIntents(command);

            assertThat(loggedInfoLines()).hasSize(1);
            assertThat(loggedInfoLines().get(0)).contains("2").doesNotContain(TEXT);
        }
    }
}
