package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.ExpenseProposalPort;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.IntentInferenceException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExtractIntentsUseCaseTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<KnownCategory> KNOWN_CATEGORIES =
            List.of(known("Food"), known("Travel"), known("Other"));

    private IntentInferencePort intentInferencePort;
    private ExpenseProposalPort expenseProposalPort;
    private ExtractIntentsUseCase useCase;

    @BeforeEach
    void setUp() {
        intentInferencePort = mock(IntentInferencePort.class);
        expenseProposalPort = mock(ExpenseProposalPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));
        useCase = new ExtractIntentsUseCase(intentInferencePort, expenseProposalPort, loggerFactory);
    }

    private static KnownCategory known(String name) {
        return new KnownCategory(name, "Everyday");
    }

    private static ExtractIntentsCommand command(String text, List<KnownCategory> knownCategories) {
        return new ExtractIntentsCommand(text, knownCategories, Optional.empty());
    }

    private static ExtractIntentsCommand command(
            String text, List<KnownCategory> knownCategories, CurrencyCode defaultCurrency) {
        return new ExtractIntentsCommand(text, knownCategories, Optional.of(defaultCurrency));
    }

    @Nested
    @DisplayName("extracting intents")
    class ExtractIntents {

        @Test
        @DisplayName("when the port returns one raw expense create answer - then returns a single ExpenseIntent "
                + "and the port was called with the command's text and known categories")
        void whenPortReturnsOneRawExpenseAnswer_thenReturnsSingleExpenseIntentAndPortCalledWithTextAndCategories() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15.00", "EUR", "lunch");
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(eq(TEXT), any())).thenReturn(List.of(raw));

            // TODO RU08: assert the port was called with the rendered labels, and that the CREATE expense
            //  was proposed carrying Food, its grouping, the amount and the description.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();

            verify(intentInferencePort).infer(eq(TEXT), any());
        }

        @Test
        @DisplayName("when an expense answer names a category not in the known categories - then that position "
                + "holds an UnknownIntent whose reason names the rejected category")
        void whenExpenseAnswerNamesCategoryNotInKnownCategories_thenUnknownIntentReasonNamesRejectedCategory() {
            RawIntent raw = rawIntent("expense", "create", "Shopping", null, "15.00", "EUR", "shoes");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the unmatched entry was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when an expense answer names a known category in different case - then the ExpenseIntent "
                + "carries the known category's own spelling")
        void whenExpenseAnswerNamesKnownCategoryInDifferentCase_thenExpenseIntentCarriesKnownCategorysSpelling() {
            RawIntent raw = rawIntent("expense", "create", "food", null, "15.00", "EUR", "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert the proposal carries the known category's own spelling, "Food".
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when a category-creation answer names something absent from the known categories - then a "
                + "CategoryIntent is returned")
        void whenCategoryCreationAnswerNamesCategoryAbsentFromKnownCategories_thenReturnsCategoryIntent() {
            RawIntent raw = rawIntent("category", "create", "Travel", null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the category intent was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port returns one raw category delete answer - then returns a single CategoryIntent "
                + "with operation DELETE and the given name")
        void whenPortReturnsOneRawCategoryDeleteAnswer_thenReturnsSingleCategoryIntentWithDeleteOperationAndName() {
            RawIntent raw = rawIntent("category", "delete", "Food", null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the DELETE category intent was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port returns three raw answers targeting expense, category, expense - then the "
                + "returned list has the same size and order, position by position")
        void whenPortReturnsThreeRawAnswersExpenseCategoryExpense_thenResultMatchesSizeAndOrder() {
            RawIntent firstExpense = rawIntent("expense", "create", "Food", null, "10", "EUR", "breakfast");
            RawIntent category = rawIntent("category", "delete", "Travel", null, null, null, null);
            RawIntent secondExpense = rawIntent("expense", "create", "Food", null, "20", "EUR", "dinner");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(firstExpense, category, secondExpense));

            // TODO RU08: assert both CREATE expenses were proposed, in the user's order, and the category
            //  intent between them was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port returns a valid category answer, an expense answer with an unknown currency, "
                + "and a valid expense answer - then returns three intents in that order, unaffected by the "
                + "unusable answer between them")
        void whenPortReturnsCategoryThenUnknownCurrencyExpenseThenValidExpense_thenReturnsThreeIntentsInOrder() {
            RawIntent category = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent badExpense = rawIntent("expense", "create", "Food", null, "10", "XYZ", "lunch");
            RawIntent goodExpense = rawIntent("expense", "create", "Food", null, "20", "EUR", "dinner");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(category, badExpense, goodExpense));

            // TODO RU08: assert only the last entry was proposed, and the unusable one between them did not
            //  stop the walk.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = "bogus-target")
        @DisplayName("when the raw answer's target is null or unrecognized - then that position holds an "
                + "UnknownIntent whose reason names the target")
        void whenAnswerTargetIsNullOrUnrecognized_thenUnknownIntentReasonNamesTarget(String target) {
            RawIntent raw = rawIntent(target, "read", null, null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the unrecognized entry was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the raw answer's operation is unrecognized - then that position holds an UnknownIntent "
                + "whose reason names the operation")
        void whenAnswerOperationIsUnrecognized_thenUnknownIntentReasonNamesOperation() {
            RawIntent raw = rawIntent("expense", "invalid-operation", null, null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the unrecognized operation was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when an expense answer's amount is not a decimal number - then that position holds an "
                + "UnknownIntent whose reason is the rejected value's exception message")
        void whenExpenseAnswerAmountIsNotDecimal_thenUnknownIntentReasonIsRejectedValueExceptionMessage() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "twelve", "EUR", "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the rejected amount was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when an expense answer has operation create and no amount - then that position holds an "
                + "UnknownIntent whose reason names the missing amount")
        void whenExpenseAnswerCreateHasNoAmount_thenUnknownIntentReasonNamesMissingAmount() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, null, null, "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed and the missing amount was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the command carries a default currency and the expense answer has an amount but no "
                + "currency - then the ExpenseIntent carries money in the default currency")
        void whenCommandHasDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenExpenseIntentUsesDefaultCurrency() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", null, "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")), CurrencyCode.of("EUR"));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert the proposal carries 1500 minor units in EUR, the command's default.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the command has no default currency and the expense answer has an amount but no "
                + "currency - then that position holds an UnknownIntent")
        void whenCommandHasNoDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenUnknownIntent() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", null, "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert nothing was proposed - an amount with no currency and no default is unknown.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the command carries a default currency and the expense answer names USD explicitly - "
                + "then the ExpenseIntent carries USD")
        void whenCommandHasDefaultCurrencyAndExpenseAnswerNamesUsdExplicitly_thenExpenseIntentCarriesUsd() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", "USD", "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")), CurrencyCode.of("EUR"));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            // TODO RU08: assert the proposal carries USD, the currency the answer named, not the default.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port returns an empty list - then returns exactly one UnknownIntent with a "
                + "non-blank reason")
        void whenPortReturnsEmptyList_thenReturnsExactlyOneUnknownIntentWithNonBlankReason() {
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of());

            // TODO RU08: assert nothing was proposed and the empty answer was logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port returns null - then returns exactly one UnknownIntent")
        void whenPortReturnsNull_thenReturnsExactlyOneUnknownIntent() {
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any())).thenReturn(null);

            // TODO RU08: assert nothing was proposed.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port returns a list containing a null element - then that position holds an "
                + "UnknownIntent and the surrounding entries are unaffected")
        void whenPortReturnsListContainingNullElement_thenThatPositionHoldsUnknownIntentAndOthersUnaffected() {
            RawIntent category = rawIntent("category", "delete", "Food", null, null, null, null);
            RawIntent expense = rawIntent("expense", "create", "Food", null, "20", "EUR", "dinner");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(Arrays.asList(category, null, expense));

            // TODO RU08: assert the trailing expense was still proposed - a null element does not stop the walk.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the port throws IntentInferenceException - then the exception propagates")
        void whenPortThrowsIntentInferenceException_thenExceptionPropagates() {
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any()))
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
            verifyNoInteractions(expenseProposalPort);
        }

        @Test
        @DisplayName("when a category-creation answer names Travel, followed by an expense answer filed under "
                + "Travel, with Travel absent from the known categories - then returns a CategoryIntent then an "
                + "ExpenseIntent carrying Travel")
        void whenCategoryCreationOfTravelPrecedesExpenseFiledUnderTravel_thenReturnsCategoryIntentThenExpenseIntentCarryingTravel() {
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "Travel", null, "15.00", "EUR", "taxi");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            // TODO RU08: assert the proposal carries Travel with an empty parent - a category the same message
            //  created has no grouping (D28).
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when an expense answer is filed under Travel before a category-creation answer names "
                + "Travel, with Travel absent from the known categories - then returns an ExpenseIntent "
                + "carrying Travel then a CategoryIntent")
        void whenExpenseFiledUnderTravelPrecedesCategoryCreationOfTravel_thenReturnsExpenseIntentCarryingTravelThenCategoryIntent() {
            RawIntent expenseRaw = rawIntent("expense", "create", "Travel", null, "15.00", "EUR", "taxi");
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(expenseRaw, categoryRaw));

            // TODO RU08: assert the proposal carries Travel even though the creation answer follows it.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when a category-creation answer names Travel, followed by an expense answer naming travel "
                + "in a different case - then the ExpenseIntent carries Travel")
        void whenCategoryCreationOfTravelPrecedesExpenseNamingTravelInDifferentCase_thenExpenseIntentCarriesTravel() {
            RawIntent categoryRaw = rawIntent("category", "create", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "travel", null, "15.00", "EUR", "taxi");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            // TODO RU08: assert the proposal carries the creation answer's spelling, "Travel".
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when a category-deletion answer names Travel, followed by an expense answer filed under "
                + "Travel, with Travel absent from the known categories - then returns a CategoryIntent then an "
                + "ExpenseIntent carrying Travel")
        void whenCategoryDeletionOfTravelPrecedesExpenseFiledUnderTravel_thenReturnsCategoryIntentThenExpenseIntentCarryingTravel() {
            RawIntent categoryRaw = rawIntent("category", "delete", "Travel", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "Travel", null, "15.00", "EUR", "taxi");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            // TODO RU08: assert the proposal carries Travel, added to the available set by the deletion answer.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when a category-creation answer fails to assemble because its name is blank, followed by "
                + "an expense answer filed under that same name - then both positions hold an UnknownIntent")
        void whenCategoryCreationWithBlankNameFailsAssembly_thenBothItAndFollowingExpenseHoldUnknownIntent() {
            RawIntent categoryRaw = rawIntent("category", "create", "", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "", null, "15.00", "EUR", "taxi");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            // TODO RU08: assert nothing was proposed and both entries were logged as skipped.
            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();
        }

    }

}
