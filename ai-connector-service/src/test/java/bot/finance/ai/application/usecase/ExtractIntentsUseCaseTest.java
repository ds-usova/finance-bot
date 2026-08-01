package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.dto.ProposedExpense;
import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.ExpenseProposalPort;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.ExpenseProposalFailedException;
import bot.finance.ai.domain.exception.IntentInferenceException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static bot.finance.ai.common.IntentFixtures.rawIntent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExtractIntentsUseCaseTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<KnownCategory> KNOWN_CATEGORIES =
            List.of(known("Food"), known("Travel"), known("Other"));

    private IntentInferencePort intentInferencePort;
    private ExpenseProposalPort expenseProposalPort;
    private Logger log;
    private ExtractIntentsUseCase useCase;

    @BeforeEach
    void setUp() {
        intentInferencePort = mock(IntentInferencePort.class);
        expenseProposalPort = mock(ExpenseProposalPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
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
        @DisplayName("when the port returns one raw expense create answer - then returns a single ExpenseIntent "
                + "and the port was called with the command's text and known categories")
        void whenPortReturnsOneRawExpenseAnswer_thenReturnsSingleExpenseIntentAndPortCalledWithTextAndCategories() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15.00", "EUR", "lunch");
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(eq(TEXT), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(intentInferencePort).infer(
                    eq(TEXT), eq(List.of("Everyday > Food", "Everyday > Travel", "Everyday > Other")));
            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            ProposedExpense proposed = captor.getValue();
            assertThat(proposed.categoryName()).isEqualTo("Food");
            assertThat(proposed.parentCategoryName()).contains("Everyday");
            assertThat(proposed.description()).isEqualTo("lunch");
            assertThat(proposed.amount()).isEqualTo(Money.of("15.00", "EUR"));
        }

        @Test
        @DisplayName("when an expense answer names a category not in the known categories - then that position "
                + "holds an UnknownIntent whose reason names the rejected category")
        void whenExpenseAnswerNamesCategoryNotInKnownCategories_thenUnknownIntentReasonNamesRejectedCategory() {
            RawIntent raw = rawIntent("expense", "create", "Shopping", null, "15.00", "EUR", "shoes");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
            assertThat(loggedInfoLines().get(0)).contains("Shopping");
        }

        @Test
        @DisplayName("when an expense answer names a known category in different case - then the ExpenseIntent "
                + "carries the known category's own spelling")
        void whenExpenseAnswerNamesKnownCategoryInDifferentCase_thenExpenseIntentCarriesKnownCategorysSpelling() {
            RawIntent raw = rawIntent("expense", "create", "food", null, "15.00", "EUR", "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Food");
        }

        @Test
        @DisplayName("when a category-creation answer names something absent from the known categories - then a "
                + "CategoryIntent is returned")
        void whenCategoryCreationAnswerNamesCategoryAbsentFromKnownCategories_thenReturnsCategoryIntent() {
            RawIntent raw = rawIntent("category", "create", "Travel", null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food"), known("Other")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
            assertThat(loggedInfoLines().get(0)).contains("Travel");
        }

        @Test
        @DisplayName("when the port returns one raw category delete answer - then returns a single CategoryIntent "
                + "with operation DELETE and the given name")
        void whenPortReturnsOneRawCategoryDeleteAnswer_thenReturnsSingleCategoryIntentWithDeleteOperationAndName() {
            RawIntent raw = rawIntent("category", "delete", "Food", null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
            assertThat(loggedInfoLines().get(0)).contains("Food");
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort, times(2)).propose(captor.capture());
            assertThat(captor.getAllValues()).extracting(ProposedExpense::description)
                    .containsExactly("breakfast", "dinner");
            assertThat(loggedInfoLines()).hasSize(1);
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort, times(1)).propose(captor.capture());
            assertThat(captor.getValue().description()).isEqualTo("dinner");
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

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName("when the raw answer's operation is unrecognized - then that position holds an UnknownIntent "
                + "whose reason names the operation")
        void whenAnswerOperationIsUnrecognized_thenUnknownIntentReasonNamesOperation() {
            RawIntent raw = rawIntent("expense", "invalid-operation", null, null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName("when an expense answer's amount is not a decimal number - then that position holds an "
                + "UnknownIntent whose reason is the rejected value's exception message")
        void whenExpenseAnswerAmountIsNotDecimal_thenUnknownIntentReasonIsRejectedValueExceptionMessage() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "twelve", "EUR", "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName("when an expense answer has operation create and no amount - then that position holds an "
                + "UnknownIntent whose reason names the missing amount")
        void whenExpenseAnswerCreateHasNoAmount_thenUnknownIntentReasonNamesMissingAmount() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, null, null, "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName("when the command carries a default currency and the expense answer has an amount but no "
                + "currency - then the ExpenseIntent carries money in the default currency")
        void whenCommandHasDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenExpenseIntentUsesDefaultCurrency() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", null, "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")), CurrencyCode.of("EUR"));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            Money amount = captor.getValue().amount();
            assertThat(amount.minorUnits()).isEqualTo(1500);
            assertThat(amount.currencyCode().code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the command has no default currency and the expense answer has an amount but no "
                + "currency - then that position holds an UnknownIntent")
        void whenCommandHasNoDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenUnknownIntent() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", null, "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
        }

        @Test
        @DisplayName("when the command carries a default currency and the expense answer names USD explicitly - "
                + "then the ExpenseIntent carries USD")
        void whenCommandHasDefaultCurrencyAndExpenseAnswerNamesUsdExplicitly_thenExpenseIntentCarriesUsd() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "15", "USD", "lunch");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")), CurrencyCode.of("EUR"));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().amount().currencyCode().code()).isEqualTo("USD");
        }

        @Test
        @DisplayName("when the port returns an empty list - then returns exactly one UnknownIntent with a "
                + "non-blank reason")
        void whenPortReturnsEmptyList_thenReturnsExactlyOneUnknownIntentWithNonBlankReason() {
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of());

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName("when the port returns null - then returns exactly one UnknownIntent")
        void whenPortReturnsNull_thenReturnsExactlyOneUnknownIntent() {
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any())).thenReturn(null);

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().description()).isEqualTo("dinner");
        }

        @Test
        @DisplayName("when the port throws IntentInferenceException - then the exception propagates")
        void whenPortThrowsIntentInferenceException_thenExceptionPropagates() {
            ExtractIntentsCommand command = command(TEXT, KNOWN_CATEGORIES);
            when(intentInferencePort.infer(any(), any()))
                    .thenThrow(new IntentInferenceException("provider unreachable"));

            assertThatThrownBy(() -> useCase.extractIntents(command))
                    .isInstanceOf(IntentInferenceException.class);

            verifyNoInteractions(expenseProposalPort);
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Travel");
            assertThat(captor.getValue().parentCategoryName()).isEmpty();
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Travel");
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Travel");
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

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Travel");
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

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(2);
        }

        @Test
        @DisplayName("when known categories render to distinct labels - then the port is called with the "
                + "rendered labels in the command's order")
        void whenKnownCategoriesRenderToDistinctLabels_thenPortCalledWithLabelsInCommandsOrder() {
            List<KnownCategory> knownCategories =
                    List.of(new KnownCategory("Travel", "Insurance"), new KnownCategory("Lunch", "Food"));
            ExtractIntentsCommand command = command(TEXT, knownCategories);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of());

            useCase.extractIntents(command);

            verify(intentInferencePort).infer(eq(TEXT), eq(List.of("Insurance > Travel", "Food > Lunch")));
        }

        @Test
        @DisplayName("when an expense answer names a category by its full label - then the proposal carries the "
                + "category name and its parent")
        void whenExpenseAnswerNamesCategoryByItsFullLabel_thenProposalCarriesNameAndParent() {
            List<KnownCategory> knownCategories = List.of(new KnownCategory("Travel", "Insurance"));
            RawIntent raw = rawIntent("expense", "create", "Insurance > Travel", null, "12.00", "EUR", "flight");
            ExtractIntentsCommand command = command(TEXT, knownCategories);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Travel");
            assertThat(captor.getValue().parentCategoryName()).contains("Insurance");
            assertThat(captor.getValue().description()).isEqualTo("flight");
            assertThat(captor.getValue().amount()).isEqualTo(Money.of("12.00", "EUR"));
        }

        @Test
        @DisplayName("when a bare category name matches multiple known categories - then nothing is proposed "
                + "and the ambiguity is logged")
        void whenBareNameMatchesMultipleKnownCategories_thenNothingProposedAndAmbiguityLogged() {
            List<KnownCategory> knownCategories =
                    List.of(new KnownCategory("Travel", "Insurance"), new KnownCategory("Travel", "Trips"));
            RawIntent raw = rawIntent("expense", "create", "Travel", null, "12.00", "EUR", "cab");
            ExtractIntentsCommand command = command(TEXT, knownCategories);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
            assertThat(loggedInfoLines().get(0)).contains("Travel");
        }

        @Test
        @DisplayName("when a bare category name matches exactly one known category - then the proposal carries "
                + "the category name and its parent")
        void whenBareNameMatchesExactlyOneKnownCategory_thenProposalCarriesNameAndParent() {
            List<KnownCategory> knownCategories =
                    List.of(new KnownCategory("Travel", "Insurance"), new KnownCategory("Lunch", "Food"));
            RawIntent raw = rawIntent("expense", "create", "Lunch", null, "12.00", "EUR", "noodles");
            ExtractIntentsCommand command = command(TEXT, knownCategories);
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Lunch");
            assertThat(captor.getValue().parentCategoryName()).contains("Food");
        }

        @Test
        @DisplayName("when an expense is filed under a category the same message creates - then the proposal "
                + "carries an empty parent")
        void whenExpenseIsFiledUnderCategoryCreatedByTheSameMessage_thenProposalCarriesEmptyParent() {
            RawIntent categoryRaw = rawIntent("category", "create", "Trips", null, null, null, null);
            RawIntent expenseRaw = rawIntent("expense", "create", "Trips", null, "12.00", "EUR", "gear");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(categoryRaw, expenseRaw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort).propose(captor.capture());
            assertThat(captor.getValue().categoryName()).isEqualTo("Trips");
            assertThat(captor.getValue().parentCategoryName()).isEmpty();
        }

        @Test
        @DisplayName("when the answers mix a CREATE expense with a READ expense, a category intent and an "
                + "unusable entry - then only the CREATE expense is proposed and the others are logged")
        void whenAnswersMixCreateExpenseWithReadCategoryAndUnusable_thenOnlyCreateExpenseIsProposedAndOthersLogged() {
            RawIntent createExpense = rawIntent("expense", "create", "Food", null, "10", "EUR", "lunch");
            RawIntent readExpense = rawIntent("expense", "read", null, null, null, null, null);
            RawIntent categoryIntentRaw = rawIntent("category", "delete", "Food", null, null, null, null);
            RawIntent unusableRaw = rawIntent(null, "read", null, null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any()))
                    .thenReturn(List.of(createExpense, readExpense, categoryIntentRaw, unusableRaw));

            useCase.extractIntents(command);

            ArgumentCaptor<ProposedExpense> captor = ArgumentCaptor.forClass(ProposedExpense.class);
            verify(expenseProposalPort, times(1)).propose(captor.capture());
            assertThat(captor.getValue().description()).isEqualTo("lunch");
            assertThat(loggedInfoLines()).hasSize(3);
        }

        @Test
        @DisplayName("when propose fails on the second of three CREATE expenses - then the exception propagates "
                + "and the third is never proposed")
        void whenProposeFailsOnSecondOfThreeCreateExpenses_thenExceptionPropagatesAndThirdIsNeverProposed() {
            RawIntent first = rawIntent("expense", "create", "Food", null, "10", "EUR", "e1");
            RawIntent second = rawIntent("expense", "create", "Food", null, "20", "EUR", "e2");
            RawIntent third = rawIntent("expense", "create", "Food", null, "30", "EUR", "e3");
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(first, second, third));
            doNothing()
                    .doThrow(new ExpenseProposalFailedException(
                            "refused", ExpenseProposalFailedException.Reason.REFUSED))
                    .when(expenseProposalPort).propose(any());

            assertThatThrownBy(() -> useCase.extractIntents(command))
                    .isInstanceOf(ExpenseProposalFailedException.class);

            verify(expenseProposalPort, times(2)).propose(any());
        }

        @Test
        @DisplayName("when a CREATE expense answer has no description - then nothing is proposed and the "
                + "skipped entry is logged")
        void whenCreateExpenseAnswerHasNoDescription_thenNothingProposedAndSkippedEntryLogged() {
            RawIntent raw = rawIntent("expense", "create", "Food", null, "10", "EUR", null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(raw));

            useCase.extractIntents(command);

            verify(expenseProposalPort, never()).propose(any());
            assertThat(loggedInfoLines()).hasSize(1);
        }

        @Test
        @DisplayName("when no answer is usable - then no proposal is made and the call returns normally")
        void whenNoAnswerIsUsable_thenNoProposalIsMadeAndCallReturnsNormally() {
            RawIntent categoryRaw = rawIntent("category", "delete", "Food", null, null, null, null);
            RawIntent unusableRaw = rawIntent(null, "read", null, null, null, null, null);
            ExtractIntentsCommand command = command(TEXT, List.of(known("Food")));
            when(intentInferencePort.infer(any(), any())).thenReturn(List.of(categoryRaw, unusableRaw));

            assertThatCode(() -> useCase.extractIntents(command)).doesNotThrowAnyException();

            verify(expenseProposalPort, never()).propose(any());
        }

    }

}
