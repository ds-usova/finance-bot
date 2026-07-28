package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.common.AiConnectorAdapterTest;
import bot.finance.common.IntentFixtures;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CategoryIntent;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseIntent;
import bot.finance.domain.value.Intent;
import bot.finance.domain.value.Operation;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

@AiConnectorAdapterTest
class AiConnectorIntentExtractionAdapterTest {

    @Autowired
    private AiConnectorIntentExtractionAdapter adapter;

    @AfterEach
    void resetStubServer() {
        GrpcStubServer.reset();
    }

    @Nested
    @DisplayName("extracting intents")
    class Extract {

        @Test
        @DisplayName(
                "when the stub server answers a two-entry response - then the two domain intents come back in order, and the request the server received carries that text, those two categories in order, and that default currency")
        void whenStubServerAnswersTwoEntryResponse_thenDomainIntentsComeBackInOrderAndServerReceivedRequestFields() {
            GrpcStubServer.answerExtractionWith(IntentFixtures.response(
                    IntentFixtures.categoryEntry(
                            bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE, "Groceries", null),
                    IntentFixtures.expenseEntry(
                            bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE,
                            "Groceries",
                            1500L,
                            "USD",
                            "milk")));
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "spent 15 on milk", List.of("Groceries", "Other"), Optional.of(CurrencyCode.of("USD")));

            List<Intent> intents = adapter.extract(request);

            CategoryIntent expectedCategoryIntent =
                    IntentFixtures.categoryIntent(Operation.CREATE, "Groceries", Optional.empty());
            ExpenseIntent expectedExpenseIntent = IntentFixtures.expenseIntent(
                    Operation.CREATE,
                    Optional.of("Groceries"),
                    Optional.of(IntentFixtures.money(1500L, "USD")),
                    Optional.of("milk"));
            assertThat(intents).containsExactly(expectedCategoryIntent, expectedExpenseIntent);

            ExtractIntentsRequest receivedRequest = GrpcStubServer.lastExtractionRequest();
            assertThat(receivedRequest.getText()).isEqualTo("spent 15 on milk");
            assertThat(receivedRequest.getKnownCategoriesList()).containsExactly("Groceries", "Other");
            assertThat(receivedRequest.getDefaultCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName(
                "when the stub server answers a response with no entries - then throws IntentExtractionFailedException")
        void whenStubServerAnswersResponseWithNoEntries_thenThrowsIntentExtractionFailedException() {
            GrpcStubServer.answerExtractionWith(IntentFixtures.response());
            IntentExtractionRequest request =
                    new IntentExtractionRequest("no intents here", List.of("Other"), Optional.empty());

            assertThatThrownBy(() -> adapter.extract(request)).isInstanceOf(IntentExtractionFailedException.class);
        }

        @ParameterizedTest
        @EnumSource(
                value = Status.Code.class,
                names = {"INVALID_ARGUMENT", "UNAVAILABLE"})
        @DisplayName(
                "when the stub server fails the call - then throws IntentExtractionFailedException carrying the StatusRuntimeException as its cause and naming the status")
        void
                whenStubServerFailsCall_thenThrowsIntentExtractionFailedExceptionCarryingStatusRuntimeExceptionAsCauseAndNamingStatus(
                        Status.Code code) {
            GrpcStubServer.failExtractionWith(Status.fromCode(code).withDescription("stub failure"));
            IntentExtractionRequest request =
                    new IntentExtractionRequest("connector unavailable", List.of("Other"), Optional.empty());

            IntentExtractionFailedException thrown =
                    catchThrowableOfType(() -> adapter.extract(request), IntentExtractionFailedException.class);

            assertThat(thrown).isNotNull();
            assertThat(thrown.getMessage()).contains(code.name());
            assertThat(thrown.getCause()).isInstanceOf(StatusRuntimeException.class);
            StatusRuntimeException cause = (StatusRuntimeException) thrown.getCause();
            assertThat(cause.getStatus().getCode()).isEqualTo(code);
        }

        @Test
        @DisplayName(
                "when extract is called with null - then throws InvalidExtractionRequestException and the server is never called")
        void whenCalledWithNull_thenThrowsInvalidExtractionRequestExceptionAndServerIsNeverCalled() {
            assertThatThrownBy(() -> adapter.extract(null)).isInstanceOf(InvalidExtractionRequestException.class);

            assertThat(GrpcStubServer.lastExtractionRequest()).isNull();
        }
    }
}
