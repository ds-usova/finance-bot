package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.common.AuthorizedStubs;
import bot.finance.ai.common.GrpcAdapterTest;
import bot.finance.ai.common.RequestFixtures;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
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

@GrpcAdapterTest
class IntentExtractionGrpcServiceTest {

    private static final String TEXT = "spent 15 euros on lunch";

    @Autowired
    private IntentExtractionServiceBlockingStub intentExtractionStub;

    @MockitoBean
    private ExtractIntentsPort extractIntentsPort;

    private IntentExtractionServiceBlockingStub authenticatedStub() {
        return AuthorizedStubs.withCallerToken(intentExtractionStub, "Bearer opaque-caller-token");
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @ParameterizedTest
        @ValueSource(strings = {"EUR", "eur"})
        @DisplayName(
                "when the request carries a default currency in any casing - then the command holds it as a present, upper-cased currency code")
        void whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode(
                String defaultCurrency) {
            authenticatedStub()
                    .extractIntents(
                            RequestFixtures.request(TEXT, RequestFixtures.DEFAULT_KNOWN_CATEGORIES, defaultCurrency));

            ArgumentCaptor<ExtractIntentsCommand> commandCaptor = ArgumentCaptor.forClass(ExtractIntentsCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().defaultCurrency()).isPresent();
            assertThat(commandCaptor.getValue().defaultCurrency().get().code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName(
                "when a request carrying a text and two known categories arrives - then the port is called with a command whose known categories hold both names and parent names in order, and the RPC answers an empty response")
        void whenRequestCarriesTextAndTwoKnownCategories_thenPortReceivesOrderedCategoriesAndResponseIsEmpty() {
            List<bot.finance.ai.adapter.grpc.v1.KnownCategory> knownCategories = List.of(
                    RequestFixtures.knownCategory("Lunch", "Food"),
                    RequestFixtures.knownCategory("Travel", "Insurance"));

            ExtractIntentsResponse response =
                    authenticatedStub().extractIntents(RequestFixtures.request(TEXT, knownCategories));

            ArgumentCaptor<ExtractIntentsCommand> commandCaptor = ArgumentCaptor.forClass(ExtractIntentsCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().knownCategories())
                    .extracting(KnownCategory::name, KnownCategory::parentName)
                    .containsExactly(Tuple.tuple("Lunch", "Food"), Tuple.tuple("Travel", "Insurance"));
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
        }
    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        @Test
        @DisplayName(
                "when the port throws ExpenseRecordingFailedException - then the RPC fails with status UNAVAILABLE")
        void whenPortThrowsExpenseRecordingFailedException_thenFailsWithUnavailable() {
            doThrow(new ExpenseRecordingFailedException("provider unreachable"))
                    .when(extractIntentsPort)
                    .extractIntents(any());

            assertThatThrownBy(() -> authenticatedStub().extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.UNAVAILABLE);
        }

        @Test
        @DisplayName(
                "when the port throws an unrecognized RuntimeException - then the RPC fails with status UNKNOWN and its message does not appear in the description")
        void whenPortThrowsUnrecognizedRuntimeException_thenFailsWithUnknownAndMessageAbsent() {
            String secretMessage = "sensitive internal detail";
            doThrow(new RuntimeException(secretMessage))
                    .when(extractIntentsPort)
                    .extractIntents(any());

            assertThatThrownBy(() -> authenticatedStub().extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(ex -> {
                        Status status = ((StatusRuntimeException) ex).getStatus();
                        assertThat(status.getCode()).isEqualTo(Status.Code.UNKNOWN);
                        assertThat(Optional.ofNullable(status.getDescription()).orElse(""))
                                .doesNotContain(secretMessage);
                    });
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidRequests")
        @DisplayName(
                "when the request violates a validation constraint - then it fails with INVALID_ARGUMENT and the port is never called")
        void whenRequestViolatesConstraint_thenFailsWithInvalidArgumentAndPortNeverCalled(
                String caseName, ExtractIntentsRequest request) {
            assertThatThrownBy(() -> authenticatedStub().extractIntents(request))
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
                    Arguments.of(
                            "default_currency not a known ISO 4217 code",
                            RequestFixtures.request(TEXT, RequestFixtures.DEFAULT_KNOWN_CATEGORIES, "ZZZ")),
                    Arguments.of(
                            "known_categories entry with a blank name",
                            RequestFixtures.request(TEXT, List.of(RequestFixtures.knownCategory("", "Food")))),
                    Arguments.of(
                            "known_categories entry with a blank parent_name",
                            RequestFixtures.request(TEXT, List.of(RequestFixtures.knownCategory("Lunch", "")))));
        }
    }
}
