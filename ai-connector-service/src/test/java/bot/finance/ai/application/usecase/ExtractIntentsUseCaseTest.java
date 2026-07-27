package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.IntentExtractionCommand;
import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.domain.exception.IntentInferenceException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryIntent;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExpenseIntent;
import bot.finance.ai.domain.value.Intent;
import bot.finance.ai.domain.value.Money;
import bot.finance.ai.domain.value.Operation;
import bot.finance.ai.domain.value.UnknownIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static bot.finance.ai.common.IntentFixtures.rawIntent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExtractIntentsUseCaseTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<String> KNOWN_CATEGORIES = List.of("Food", "Travel", "Other");

    private IntentInferencePort intentInferencePort;
    private ExtractIntentsUseCase useCase;

    @BeforeEach
    void setUp() {
        intentInferencePort = mock(IntentInferencePort.class);
        useCase = new ExtractIntentsUseCase(intentInferencePort);
    }

    private static IntentExtractionCommand command(String text, List<String> knownCategories) {
        return new IntentExtractionCommand(text, knownCategories, Optional.empty());
    }

    private static IntentExtractionCommand command(
            String text, List<String> knownCategories, CurrencyCode defaultCurrency) {
        return new IntentExtractionCommand(text, knownCategories, Optional.of(defaultCurrency));
    }

    @Nested
    @DisplayName("extracting intents")
    class ExtractIntents {

        @Test
        @DisplayName("when the port returns one raw expense create answer - then returns a single ExpenseIntent "
                + "and the port was called with the command's text and known categories")
        void whenPortReturnsOneRawExpenseAnswer_thenReturnsSingleExpenseIntentAndPortCalledWithTextAndCategories() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(TEXT, KNOWN_CATEGORIES)).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(ExpenseIntent.class, expense -> {
                assertThat(expense.operation()).isEqualTo(Operation.CREATE);
                assertThat(expense.amount()).isPresent();
                assertThat(expense.amount().get().minorUnits()).isEqualTo(1500);
                assertThat(expense.amount().get().currencyCode().code()).isEqualTo("EUR");
            });
            verify(intentInferencePort).infer(TEXT, KNOWN_CATEGORIES);
        }

        @Test
        @DisplayName("when an expense answer names a category not in the known categories - then that position "
                + "holds an UnknownIntent whose reason names the rejected category")
        void whenExpenseAnswerNamesCategoryNotInKnownCategories_thenUnknownIntentReasonNamesRejectedCategory() {
            RawIntent raw = rawIntent("expense", "create", "Shopping", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food", "Other"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(UnknownIntent.class,
                    unknown -> assertThat(unknown.reason()).contains("Shopping"));
        }

        @Test
        @DisplayName("when an expense answer names a known category in different case - then the ExpenseIntent "
                + "carries the known category's own spelling")
        void whenExpenseAnswerNamesKnownCategoryInDifferentCase_thenExpenseIntentCarriesKnownCategorysSpelling() {
            RawIntent raw = rawIntent("expense", "create", "food", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.categoryName()).contains("Food"));
        }

        @Test
        @DisplayName("when a category-creation answer names something absent from the known categories - then a "
                + "CategoryIntent is returned")
        void whenCategoryCreationAnswerNamesCategoryAbsentFromKnownCategories_thenReturnsCategoryIntent() {
            RawIntent raw = rawIntent("category", "create", "Travel", null, null, null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food", "Other"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(CategoryIntent.class, category -> {
                assertThat(category.operation()).isEqualTo(Operation.CREATE);
                assertThat(category.name()).isEqualTo("Travel");
            });
        }

        @Test
        @DisplayName("when the port returns one raw category delete answer - then returns a single CategoryIntent "
                + "with operation DELETE and the given name")
        void whenPortReturnsOneRawCategoryDeleteAnswer_thenReturnsSingleCategoryIntentWithDeleteOperationAndName() {
            RawIntent raw = rawIntent("category", "delete", "Food", null, null, null, null);
            IntentExtractionCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(CategoryIntent.class, category -> {
                assertThat(category.operation()).isEqualTo(Operation.DELETE);
                assertThat(category.name()).isEqualTo("Food");
            });
        }

        @Test
        @DisplayName("when the port returns a raw category creation followed by a raw expense creation - then "
                + "returns both, a CategoryIntent then an ExpenseIntent, in that order")
        void whenPortReturnsCategoryCreationFollowedByExpenseCreation_thenReturnsCategoryIntentThenExpenseIntent() {
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "Food", null, "50", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food", "Other"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(CategoryIntent.class);
            assertThat(result.get(1)).isInstanceOf(ExpenseIntent.class);
        }

        @Test
        @DisplayName("when the port returns three raw answers targeting expense, category, expense - then the "
                + "returned list has the same size and order, position by position")
        void whenPortReturnsThreeRawAnswersExpenseCategoryExpense_thenResultMatchesSizeAndOrder() {
            RawIntent firstExpense = rawIntent("expense", "create", "Food", null, "10", "EUR", null);
            RawIntent category = rawIntent("category", "delete", "Travel", null, null, null, null);
            RawIntent secondExpense = rawIntent("expense", "create", "Food", null, "20", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(firstExpense, category, secondExpense));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.amount().get().minorUnits()).isEqualTo(1000));
            assertThat(result.get(1)).isInstanceOf(CategoryIntent.class);
            assertThat(result.get(2)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.amount().get().minorUnits()).isEqualTo(2000));
        }

        @Test
        @DisplayName("when the port returns a valid category answer, an expense answer with an unknown currency, "
                + "and a valid expense answer - then returns three intents in that order, unaffected by the "
                + "unusable answer between them")
        void whenPortReturnsCategoryThenUnknownCurrencyExpenseThenValidExpense_thenReturnsThreeIntentsInOrder() {
            RawIntent category = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent badExpense = rawIntent("expense", "create", "Food", null, "10", "XYZ", null);
            RawIntent goodExpense = rawIntent("expense", "create", "Food", null, "20", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(category, badExpense, goodExpense));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isInstanceOf(CategoryIntent.class);
            assertThat(result.get(1)).isInstanceOf(UnknownIntent.class);
            assertThat(result.get(2)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.amount().get().minorUnits()).isEqualTo(2000));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = "bogus-target")
        @DisplayName("when the raw answer's target is null or unrecognized - then that position holds an "
                + "UnknownIntent whose reason names the target")
        void whenAnswerTargetIsNullOrUnrecognized_thenUnknownIntentReasonNamesTarget(String target) {
            RawIntent raw = rawIntent(target, "read", null, null, null, null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(UnknownIntent.class,
                    unknown -> assertThat(unknown.reason()).contains(String.valueOf(target)));
        }

        @Test
        @DisplayName("when the raw answer's operation is unrecognized - then that position holds an UnknownIntent "
                + "whose reason names the operation")
        void whenAnswerOperationIsUnrecognized_thenUnknownIntentReasonNamesOperation() {
            RawIntent raw = rawIntent("expense", "invalid-operation", null, null, null, null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(UnknownIntent.class,
                    unknown -> assertThat(unknown.reason()).contains("invalid-operation"));
        }

        @Test
        @DisplayName("when an expense answer's amount is not a decimal number - then that position holds an "
                + "UnknownIntent whose reason is the rejected value's exception message")
        void whenExpenseAnswerAmountIsNotDecimal_thenUnknownIntentReasonIsRejectedValueExceptionMessage() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "twelve", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            Throwable rejection = catchThrowable(() -> Money.of("twelve", "EUR"));
            String expectedReason = rejection == null ? null : rejection.getMessage();
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(UnknownIntent.class,
                    unknown -> assertThat(unknown.reason()).isEqualTo(expectedReason));
        }

        @Test
        @DisplayName("when an expense answer has operation create and no amount - then that position holds an "
                + "UnknownIntent whose reason names the missing amount")
        void whenExpenseAnswerCreateHasNoAmount_thenUnknownIntentReasonNamesMissingAmount() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, null, null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(UnknownIntent.class,
                    unknown -> assertThat(unknown.reason()).containsIgnoringCase("amount"));
        }

        @Test
        @DisplayName("when the command carries a default currency and the expense answer has an amount but no "
                + "currency - then the ExpenseIntent carries money in the default currency")
        void whenCommandHasDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenExpenseIntentUsesDefaultCurrency() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"), CurrencyCode.of("EUR"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(ExpenseIntent.class, expense -> {
                assertThat(expense.amount().get().minorUnits()).isEqualTo(1500);
                assertThat(expense.amount().get().currencyCode().code()).isEqualTo("EUR");
            });
        }

        @Test
        @DisplayName("when the command has no default currency and the expense answer has an amount but no "
                + "currency - then that position holds an UnknownIntent")
        void whenCommandHasNoDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenUnknownIntent() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(UnknownIntent.class);
        }

        @Test
        @DisplayName("when the command carries a default currency and the expense answer names USD explicitly - "
                + "then the ExpenseIntent carries USD")
        void whenCommandHasDefaultCurrencyAndExpenseAnswerNamesUsdExplicitly_thenExpenseIntentCarriesUsd() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", "USD", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"), CurrencyCode.of("EUR"));
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of(raw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.amount().get().currencyCode().code()).isEqualTo("USD"));
        }

        @Test
        @DisplayName("when the port returns an empty list - then returns exactly one UnknownIntent with a "
                + "non-blank reason")
        void whenPortReturnsEmptyList_thenReturnsExactlyOneUnknownIntentWithNonBlankReason() {
            IntentExtractionCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(List.of());

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOfSatisfying(UnknownIntent.class,
                    unknown -> assertThat(unknown.reason()).isNotBlank());
        }

        @Test
        @DisplayName("when the port returns null - then returns exactly one UnknownIntent")
        void whenPortReturnsNull_thenReturnsExactlyOneUnknownIntent() {
            IntentExtractionCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(command.text(), command.knownCategories())).thenReturn(null);

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(UnknownIntent.class);
        }

        @Test
        @DisplayName("when the port returns a list containing a null element - then that position holds an "
                + "UnknownIntent and the surrounding entries are unaffected")
        void whenPortReturnsListContainingNullElement_thenThatPositionHoldsUnknownIntentAndOthersUnaffected() {
            RawIntent category = rawIntent("category", "delete", "Food", null, null, null, null);
            RawIntent expense = rawIntent("expense", "create", "Food", null, "20", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(Arrays.asList(category, null, expense));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isInstanceOf(CategoryIntent.class);
            assertThat(result.get(1)).isInstanceOf(UnknownIntent.class);
            assertThat(result.get(2)).isInstanceOf(ExpenseIntent.class);
        }

        @Test
        @DisplayName("when the port throws IntentInferenceException - then the exception propagates")
        void whenPortThrowsIntentInferenceException_thenExceptionPropagates() {
            IntentExtractionCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenThrow(new IntentInferenceException("provider unreachable"));

            assertThatThrownBy(() -> useCase.extractIntents(command))
                    .isInstanceOf(IntentInferenceException.class);
        }

        @Test
        @DisplayName("when the command is null - then throws InvalidValueException and the port is never called")
        void whenCommandIsNull_thenThrowsInvalidValueExceptionAndPortNeverCalled() {
            assertThatThrownBy(() -> useCase.extractIntents(null))
                    .isInstanceOf(InvalidValueException.class);

            verifyNoInteractions(intentInferencePort);
        }

        @Test
        @DisplayName("when a category-creation answer names Travel, followed by an expense answer filed under "
                + "Travel, with Travel absent from the known categories - then returns a CategoryIntent then an "
                + "ExpenseIntent carrying Travel")
        void whenCategoryCreationOfTravelPrecedesExpenseFiledUnderTravel_thenReturnsCategoryIntentThenExpenseIntentCarryingTravel() {
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "Travel", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food", "Other"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(CategoryIntent.class);
            assertThat(result.get(1)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.categoryName()).contains("Travel"));
        }

        @Test
        @DisplayName("when an expense answer is filed under Travel before a category-creation answer names "
                + "Travel, with Travel absent from the known categories - then returns an ExpenseIntent "
                + "carrying Travel then a CategoryIntent")
        void whenExpenseFiledUnderTravelPrecedesCategoryCreationOfTravel_thenReturnsExpenseIntentCarryingTravelThenCategoryIntent() {
            RawIntent expenseRaw = rawIntent("expense", "create", "Travel", null, "15.00", "EUR", null);
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            IntentExtractionCommand command = command(TEXT, List.of("Food", "Other"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(expenseRaw, categoryRaw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.categoryName()).contains("Travel"));
            assertThat(result.get(1)).isInstanceOfSatisfying(CategoryIntent.class,
                    category -> assertThat(category.name()).isEqualTo("Travel"));
        }

        @Test
        @DisplayName("when a category-creation answer names Travel, followed by an expense answer naming travel "
                + "in a different case - then the ExpenseIntent carries Travel")
        void whenCategoryCreationOfTravelPrecedesExpenseNamingTravelInDifferentCase_thenExpenseIntentCarriesTravel() {
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "travel", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(2);
            assertThat(result.get(1)).isInstanceOfSatisfying(ExpenseIntent.class,
                    expense -> assertThat(expense.categoryName()).contains("Travel"));
        }

        @Test
        @DisplayName("when a category-deletion answer names Travel, followed by an expense answer filed under "
                + "Travel, with Travel absent from the known categories - then the expense position holds an "
                + "UnknownIntent")
        void whenCategoryDeletionOfTravelPrecedesExpenseFiledUnderTravel_thenExpensePositionHoldsUnknownIntent() {
            RawIntent categoryRaw = rawIntent("category", "delete", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "Travel", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food", "Other"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(2);
            assertThat(result.get(1)).isInstanceOf(UnknownIntent.class);
        }

        @Test
        @DisplayName("when a category-creation answer fails to assemble because its name is blank, followed by "
                + "an expense answer filed under that same name - then both positions hold an UnknownIntent")
        void whenCategoryCreationWithBlankNameFailsAssembly_thenBothItAndFollowingExpenseHoldUnknownIntent() {
            RawIntent categoryRaw = rawIntent("category", "create", "", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "", null, "15.00", "EUR", null);
            IntentExtractionCommand command = command(TEXT, List.of("Food"));
            when(intentInferencePort.infer(command.text(), command.knownCategories()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            List<Intent> result = useCase.extractIntents(command);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(result.get(1)).isInstanceOf(UnknownIntent.class);
        }

    }

}
