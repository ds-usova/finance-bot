package bot.finance.adapter.aiconnector;

import static bot.finance.common.IntentFixtures.categoryEntry;
import static bot.finance.common.IntentFixtures.expenseEntry;
import static bot.finance.common.IntentFixtures.payloadlessEntry;
import static bot.finance.common.IntentFixtures.rawOperationValueEntry;
import static bot.finance.common.IntentFixtures.response;
import static bot.finance.common.IntentFixtures.unknownEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.value.CategoryIntent;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseIntent;
import bot.finance.domain.value.Intent;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.Operation;
import bot.finance.domain.value.UnknownIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntentProtoUtilsTest {

    @Nested
    @DisplayName("mapping an intent-extraction request to the generated proto request")
    class ToProtoRequest {

        @Test
        @DisplayName("when the request carries text, three categories, and default currency EUR - then the "
                + "generated request carries the text, the categories in order, and default_currency EUR")
        void whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro", List.of("Groceries", "Transport", "Other"), Optional.of(CurrencyCode.of("EUR")));

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.getText()).isEqualTo("lunch 12 euro");
            assertThat(protoRequest.getKnownCategoriesList()).containsExactly("Groceries", "Transport", "Other");
            assertThat(protoRequest.hasDefaultCurrency()).isTrue();
            assertThat(protoRequest.getDefaultCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the request's default currency is empty - then the generated request reports "
                + "hasDefaultCurrency() as false")
        void whenRequestDefaultCurrencyIsEmpty_thenGeneratedRequestReportsHasDefaultCurrencyAsFalse() {
            IntentExtractionRequest request =
                    new IntentExtractionRequest("lunch 12 euro", List.of("Groceries", "Other"), Optional.empty());

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.hasDefaultCurrency()).isFalse();
        }
    }

    @Nested
    @DisplayName("mapping the generated response to domain intents")
    class ToIntents {

        @Test
        @DisplayName("when the response holds one OPERATION_CREATE entry with a category payload - then a "
                + "CategoryIntent with operation CREATE, that name, and an empty new name comes back")
        void whenCreateCategoryEntry_thenCategoryIntentWithCreateNameAndEmptyNewNameComesBack() {
            ExtractIntentsResponse response = response(
                    categoryEntry(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE, "Groceries", null));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).containsExactly(new CategoryIntent(Operation.CREATE, "Groceries", Optional.empty()));
        }

        @Test
        @DisplayName("when the response holds one OPERATION_UPDATE category entry carrying new_name - then the "
                + "CategoryIntent holds that new name")
        void whenUpdateCategoryEntryCarriesNewName_thenCategoryIntentHoldsThatNewName() {
            ExtractIntentsResponse response = response(
                    categoryEntry(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UPDATE, "Groceries", "Food"));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).containsExactly(new CategoryIntent(Operation.UPDATE, "Groceries", Optional.of("Food")));
        }

        @Test
        @DisplayName("when the response holds one OPERATION_CREATE expense entry with a category, 1250 minor "
                + "units of EUR, and a description - then an ExpenseIntent comes back whose amount is "
                + "Money(1250, EUR) and whose other fields match")
        void whenCreateExpenseEntryWithCategoryMoneyAndDescription_thenExpenseIntentMatchesAllFields() {
            ExtractIntentsResponse response = response(expenseEntry(
                    bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE,
                    "Groceries",
                    1250L,
                    "EUR",
                    "weekly shopping"));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents)
                    .containsExactly(new ExpenseIntent(
                            Operation.CREATE,
                            Optional.of("Groceries"),
                            Optional.of(new Money(1250L, new CurrencyCode("EUR"))),
                            Optional.of("weekly shopping")));
        }

        @Test
        @DisplayName("when the response holds one OPERATION_READ expense entry with no amount and no "
                + "description - then an ExpenseIntent with operation READ and empty optionals comes back")
        void whenReadExpenseEntryWithNoAmountAndNoDescription_thenExpenseIntentWithReadAndEmptyOptionalsComesBack() {
            ExtractIntentsResponse response = response(
                    expenseEntry(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_READ, null, null, null, null));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents)
                    .containsExactly(
                            new ExpenseIntent(Operation.READ, Optional.empty(), Optional.empty(), Optional.empty()));
        }

        @Test
        @DisplayName("when the response holds one OPERATION_DELETE expense entry - then an ExpenseIntent with "
                + "operation DELETE comes back")
        void whenDeleteExpenseEntry_thenExpenseIntentWithDeleteComesBack() {
            ExtractIntentsResponse response = response(expenseEntry(
                    bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_DELETE, "Groceries", null, null, null));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents)
                    .containsExactly(new ExpenseIntent(
                            Operation.DELETE, Optional.of("Groceries"), Optional.empty(), Optional.empty()));
        }

        @Test
        @DisplayName("when the response holds one OPERATION_UNKNOWN entry carrying a reason - then an "
                + "UnknownIntent with that reason comes back")
        void whenUnknownEntryCarriesReason_thenUnknownIntentWithThatReasonComesBack() {
            ExtractIntentsResponse response = response(unknownEntry("could not classify the message"));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).containsExactly(new UnknownIntent("could not classify the message"));
        }

        @Test
        @DisplayName("when the response holds one OPERATION_UNKNOWN entry whose reason is empty - then an "
                + "UnknownIntent comes back carrying a stand-in reason rather than throwing")
        void whenUnknownEntryReasonIsEmpty_thenUnknownIntentCarriesStandInReasonRatherThanThrowing() {
            ExtractIntentsResponse response = response(unknownEntry(""));

            List<Intent> intents = new ArrayList<>();
            assertThatCode(() -> intents.addAll(IntentProtoUtils.toIntents(response)))
                    .doesNotThrowAnyException();

            assertThat(intents).hasSize(1);
            assertThat(intents.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(((UnknownIntent) intents.get(0)).reason()).isNotBlank();
        }

        @Test
        @DisplayName("when the response holds one OPERATION_UNSPECIFIED entry - then an UnknownIntent comes "
                + "back whose reason names the unrecognized operation")
        void whenUnspecifiedEntry_thenUnknownIntentReasonNamesTheUnrecognizedOperation() {
            ExtractIntentsResponse response =
                    response(payloadlessEntry(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNSPECIFIED));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).hasSize(1);
            assertThat(intents.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(((UnknownIntent) intents.get(0)).reason()).containsIgnoringCase("UNSPECIFIED");
        }

        @Test
        @DisplayName("when the response holds one entry built with setOperationValue of a number the schema "
                + "does not define - then an UnknownIntent comes back whose reason names the unrecognized "
                + "operation, and nothing is thrown")
        void whenEntryHasUndefinedOperationValue_thenUnknownIntentReasonNamesItAndNothingIsThrown() {
            bot.finance.ai.adapter.grpc.v1.Intent entry = rawOperationValueEntry(99);
            ExtractIntentsResponse response = response(entry);
            assertThat(entry.getOperation()).isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.UNRECOGNIZED);

            List<Intent> intents = new ArrayList<>();
            assertThatCode(() -> intents.addAll(IntentProtoUtils.toIntents(response)))
                    .doesNotThrowAnyException();

            assertThat(intents).hasSize(1);
            assertThat(intents.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(((UnknownIntent) intents.get(0)).reason()).contains("99");
        }

        @Test
        @DisplayName("when the response holds one OPERATION_CREATE entry with neither payload set - then an "
                + "UnknownIntent comes back whose reason says the entry carried no payload")
        void whenCreateEntryWithNeitherPayloadSet_thenUnknownIntentReasonSaysNoPayload() {
            ExtractIntentsResponse response =
                    response(payloadlessEntry(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).hasSize(1);
            assertThat(intents.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(((UnknownIntent) intents.get(0)).reason()).containsIgnoringCase("payload");
        }

        @Test
        @DisplayName("when the response holds an expense entry whose money carries a currency ISO 4217 does "
                + "not know - then an UnknownIntent comes back naming that currency")
        void whenExpenseEntryMoneyCarriesUnknownCurrency_thenUnknownIntentNamesThatCurrency() {
            ExtractIntentsResponse response = response(expenseEntry(
                    bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE, "Groceries", 1250L, "ZZZ", null));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).hasSize(1);
            assertThat(intents.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(((UnknownIntent) intents.get(0)).reason()).contains("ZZZ");
        }

        @Test
        @DisplayName("when the response holds an OPERATION_CREATE expense entry with no amount, a shape "
                + "ExpenseIntent rejects - then an UnknownIntent comes back carrying the domain's reason")
        void whenCreateExpenseEntryWithNoAmount_thenUnknownIntentCarriesDomainsReason() {
            ExtractIntentsResponse response = response(expenseEntry(
                    bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE, "Groceries", null, null, null));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents).hasSize(1);
            assertThat(intents.get(0)).isInstanceOf(UnknownIntent.class);
            assertThat(((UnknownIntent) intents.get(0)).reason()).isNotBlank();
        }

        @Test
        @DisplayName("when the response holds a category entry, an expense entry, and an unknown entry in "
                + "that order - then the three intents come back in the same order")
        void whenCategoryExpenseAndUnknownEntriesInOrder_thenThreeIntentsComeBackInSameOrder() {
            ExtractIntentsResponse response = response(
                    categoryEntry(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE, "Groceries", null),
                    expenseEntry(
                            bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE,
                            "Groceries",
                            1250L,
                            "EUR",
                            "weekly shopping"),
                    unknownEntry("could not classify the message"));

            List<Intent> intents = IntentProtoUtils.toIntents(response);

            assertThat(intents)
                    .containsExactly(
                            new CategoryIntent(Operation.CREATE, "Groceries", Optional.empty()),
                            new ExpenseIntent(
                                    Operation.CREATE,
                                    Optional.of("Groceries"),
                                    Optional.of(new Money(1250L, new CurrencyCode("EUR"))),
                                    Optional.of("weekly shopping")),
                            new UnknownIntent("could not classify the message"));
        }
    }
}
