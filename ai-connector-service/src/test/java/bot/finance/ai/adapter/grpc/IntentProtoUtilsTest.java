package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.domain.value.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static bot.finance.ai.common.IntentFixtures.categoryIntent;
import static bot.finance.ai.common.IntentFixtures.expenseIntent;
import static bot.finance.ai.common.IntentFixtures.unknownIntent;
import static org.assertj.core.api.Assertions.assertThat;

class IntentProtoUtilsTest {

    @Nested
    @DisplayName("mapping domain intents to the proto response")
    class ToResponse {

        @Test
        @DisplayName("when a single ExpenseIntent has operation CREATE and money of 1500 minor units in EUR - "
                + "then the response holds one intent carrying OPERATION_CREATE, the expense payload, "
                + "minor_units 1500 and currency EUR")
        void whenSingleCreateExpenseIntentWithMoney_thenResponseHoldsCreateOperationExpensePayloadAndMoney() {
            ExtractIntentsResponse response = IntentProtoUtils.toResponse(List.of(expenseIntent(Operation.CREATE)));

            assertThat(response.getIntentsCount()).isEqualTo(1);
            bot.finance.ai.adapter.grpc.v1.Intent protoIntent = response.getIntents(0);
            assertThat(protoIntent.getOperation())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE);
            assertThat(protoIntent.getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.EXPENSE);
            assertThat(protoIntent.getExpense().getAmount().getMinorUnits()).isEqualTo(1500L);
            assertThat(protoIntent.getExpense().getAmount().getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when a READ ExpenseIntent has all optional fields empty - then the expense payload is "
                + "selected and no optional field reports presence")
        void whenReadExpenseIntentWithEmptyOptionalFields_thenExpensePayloadSelectedWithNoOptionalFieldPresent() {
            ExtractIntentsResponse response = IntentProtoUtils.toResponse(List.of(expenseIntent(Operation.READ)));

            bot.finance.ai.adapter.grpc.v1.Intent protoIntent = response.getIntents(0);
            assertThat(protoIntent.getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.EXPENSE);
            assertThat(protoIntent.getExpense().hasCategoryName()).isFalse();
            assertThat(protoIntent.getExpense().hasAmount()).isFalse();
            assertThat(protoIntent.getExpense().hasDescription()).isFalse();
        }

        @Test
        @DisplayName("when a single CategoryIntent has operation UPDATE and a new name - then the response holds "
                + "one intent carrying OPERATION_UPDATE, the category payload, and the new name")
        void whenSingleUpdateCategoryIntentWithNewName_thenResponseHoldsUpdateOperationCategoryPayloadAndNewName() {
            ExtractIntentsResponse response = IntentProtoUtils.toResponse(List.of(categoryIntent(Operation.UPDATE)));

            bot.finance.ai.adapter.grpc.v1.Intent protoIntent = response.getIntents(0);
            assertThat(protoIntent.getOperation())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UPDATE);
            assertThat(protoIntent.getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.CATEGORY);
            assertThat(protoIntent.getCategory().getNewName()).isEqualTo("Groceries");
        }

        @Test
        @DisplayName("when a single UnknownIntent is mapped - then the response holds one intent carrying "
                + "OPERATION_UNKNOWN, the reason, and no payload set in the oneof")
        void whenSingleUnknownIntent_thenResponseHoldsUnknownOperationReasonAndNoPayload() {
            ExtractIntentsResponse response = IntentProtoUtils.toResponse(
                    List.of(unknownIntent("could not classify the message")));

            bot.finance.ai.adapter.grpc.v1.Intent protoIntent = response.getIntents(0);
            assertThat(protoIntent.getOperation())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNKNOWN);
            assertThat(protoIntent.getReason()).isEqualTo("could not classify the message");
            assertThat(protoIntent.getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.PAYLOAD_NOT_SET);
        }

        @Test
        @DisplayName("when a list of CategoryIntent, UnknownIntent, ExpenseIntent is mapped - then the response "
                + "holds three intents in that same order, each with its own operation and payload")
        void whenListOfCategoryUnknownExpenseIntents_thenResponseHoldsThreeIntentsInOrderWithOwnOperationAndPayload() {
            ExtractIntentsResponse response = IntentProtoUtils.toResponse(List.of(
                    categoryIntent(Operation.CREATE),
                    unknownIntent("could not classify the message"),
                    expenseIntent(Operation.CREATE)));

            assertThat(response.getIntentsCount()).isEqualTo(3);
            assertThat(response.getIntents(0).getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.CATEGORY);
            assertThat(response.getIntents(0).getOperation())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE);
            assertThat(response.getIntents(1).getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.PAYLOAD_NOT_SET);
            assertThat(response.getIntents(1).getOperation())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNKNOWN);
            assertThat(response.getIntents(2).getPayloadCase())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.EXPENSE);
            assertThat(response.getIntents(2).getOperation())
                    .isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE);
        }

        @ParameterizedTest(name = "operation {0}")
        @EnumSource(Operation.class)
        @DisplayName("when each Operation is paired with an intent valid for it - then the matching proto "
                + "Operation is set, and OPERATION_UNSPECIFIED is never produced")
        void whenEachOperationPairedWithValidIntent_thenMatchingProtoOperationSetAndNeverUnspecified(
                Operation operation) {
            ExtractIntentsResponse response = IntentProtoUtils.toResponse(
                    List.of(categoryIntent(operation), expenseIntent(operation)));

            bot.finance.ai.adapter.grpc.v1.Operation expected =
                    bot.finance.ai.adapter.grpc.v1.Operation.valueOf("OPERATION_" + operation.name());

            assertThat(response.getIntentsList())
                    .extracting(bot.finance.ai.adapter.grpc.v1.Intent::getOperation)
                    .containsOnly(expected)
                    .doesNotContain(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNSPECIFIED);
        }

    }

}
