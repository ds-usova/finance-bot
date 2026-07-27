package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.application.dto.IntentExtractionCommand;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.common.GrpcAdapterTest;
import bot.finance.ai.common.IntentFixtures;
import bot.finance.ai.common.RequestFixtures;
import bot.finance.ai.domain.exception.IntentInferenceException;
import bot.finance.ai.domain.value.Operation;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GrpcAdapterTest
class IntentExtractionGrpcServiceTest {

    private static final String TEXT = "spent 15 euros on lunch";

    @Autowired
    private IntentExtractionServiceBlockingStub intentExtractionStub;

    @MockitoBean
    private ExtractIntentsPort extractIntentsPort;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("when the port returns a CREATE expense intent - then the response maps it and the command carries the text and both categories in order")
        void whenPortReturnsCreateExpenseIntent_thenResponseMapsItAndCommandCarriesTextAndCategories() {
            List<String> knownCategories = List.of("Food", "Travel");
            when(extractIntentsPort.extractIntents(any()))
                    .thenReturn(List.of(IntentFixtures.expenseIntent(Operation.CREATE)));

            ExtractIntentsResponse response =
                    intentExtractionStub.extractIntents(RequestFixtures.request(TEXT, knownCategories));

            ArgumentCaptor<IntentExtractionCommand> commandCaptor =
                    ArgumentCaptor.forClass(IntentExtractionCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().text()).isEqualTo(TEXT);
            assertThat(commandCaptor.getValue().knownCategories()).containsExactly("Food", "Travel");

            assertThat(response.getIntentsList()).hasSize(1);
            var intent = response.getIntents(0);
            assertThat(intent.getOperation()).isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE);
            assertThat(intent.getExpense().getAmount().getMinorUnits()).isEqualTo(1500L);
            assertThat(intent.getExpense().getAmount().getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the port returns a category intent followed by an expense intent - then the response holds both in that order")
        void whenPortReturnsCategoryThenExpenseIntent_thenResponseHoldsBothInOrder() {
            when(extractIntentsPort.extractIntents(any()))
                    .thenReturn(List.of(
                            IntentFixtures.categoryIntent(Operation.READ),
                            IntentFixtures.expenseIntent(Operation.READ)));

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(RequestFixtures.request());

            assertThat(response.getIntentsList()).hasSize(2);
            var first = response.getIntents(0);
            var second = response.getIntents(1);
            assertThat(first.getPayloadCase()).isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.CATEGORY);
            assertThat(first.getCategory().getName()).isEqualTo("Food");
            assertThat(second.getPayloadCase()).isEqualTo(bot.finance.ai.adapter.grpc.v1.Intent.PayloadCase.EXPENSE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"EUR", "eur"})
        @DisplayName("when the request carries a default currency in any casing - then the command holds it as a present, upper-cased currency code")
        void whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode(
                String defaultCurrency) {
            when(extractIntentsPort.extractIntents(any())).thenReturn(List.of(IntentFixtures.unknownIntent()));

            intentExtractionStub.extractIntents(
                    RequestFixtures.request(TEXT, RequestFixtures.DEFAULT_KNOWN_CATEGORIES, defaultCurrency));

            ArgumentCaptor<IntentExtractionCommand> commandCaptor =
                    ArgumentCaptor.forClass(IntentExtractionCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().defaultCurrency()).isPresent();
            assertThat(commandCaptor.getValue().defaultCurrency().get().code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the port returns a single unknown intent - then the RPC completes OK with OPERATION_UNKNOWN and the reason")
        void whenPortReturnsUnknownIntent_thenRpcCompletesOkWithOperationUnknownAndReason() {
            String reason = "could not classify the message";
            when(extractIntentsPort.extractIntents(any())).thenReturn(List.of(IntentFixtures.unknownIntent(reason)));

            ExtractIntentsResponse response = intentExtractionStub.extractIntents(RequestFixtures.request());

            assertThat(response.getIntentsList()).hasSize(1);
            var intent = response.getIntents(0);
            assertThat(intent.getOperation()).isEqualTo(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNKNOWN);
            assertThat(intent.getReason()).isEqualTo(reason);
        }

    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port throws IntentInferenceException - then the RPC fails with status UNAVAILABLE")
        void whenPortThrowsIntentInferenceException_thenFailsWithUnavailable() {
            when(extractIntentsPort.extractIntents(any()))
                    .thenThrow(new IntentInferenceException("provider unreachable"));

            assertThatThrownBy(() -> intentExtractionStub.extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAVAILABLE);
        }

        @Test
        @DisplayName("when the port throws an unrecognized RuntimeException - then the RPC fails with status UNKNOWN and its message does not appear in the description")
        void whenPortThrowsUnrecognizedRuntimeException_thenFailsWithUnknownAndMessageAbsent() {
            String secretMessage = "sensitive internal detail";
            when(extractIntentsPort.extractIntents(any())).thenThrow(new RuntimeException(secretMessage));

            assertThatThrownBy(() -> intentExtractionStub.extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(ex -> {
                        Status status = ((StatusRuntimeException) ex).getStatus();
                        assertThat(status.getCode()).isEqualTo(Status.Code.UNKNOWN);
                        assertThat(Optional.ofNullable(status.getDescription()).orElse("")).doesNotContain(secretMessage);
                    });
        }

    }

    /**
     * Also covers the plan's "empty known_categories" Happy Path scenario, which describes the same
     * INVALID_ARGUMENT/port-never-called outcome as this matrix's "known_categories empty" case.
     */
    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidRequests")
        @DisplayName("when the request violates a validation constraint - then it fails with INVALID_ARGUMENT and the port is never called")
        void whenRequestViolatesConstraint_thenFailsWithInvalidArgumentAndPortNeverCalled(
                String caseName, ExtractIntentsRequest request) {
            assertThatThrownBy(() -> intentExtractionStub.extractIntents(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.INVALID_ARGUMENT);
            verify(extractIntentsPort, never()).extractIntents(any());
        }

        private static Stream<Arguments> invalidRequests() {
            return Stream.of(
                    Arguments.of("text absent", RequestFixtures.request("")),
                    Arguments.of("text whitespace-only", RequestFixtures.request("   ")),
                    Arguments.of("known_categories empty", RequestFixtures.request(TEXT, List.of())),
                    Arguments.of("known_categories containing a blank entry",
                            RequestFixtures.request(TEXT, List.of("Food", "  "))),
                    Arguments.of("default_currency not a known ISO 4217 code",
                            RequestFixtures.request(TEXT, RequestFixtures.DEFAULT_KNOWN_CATEGORIES, "ZZZ")));
        }

    }

}
