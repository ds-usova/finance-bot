package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

        // TODO RI05: replace with the happy-path scenario — a tokened request carrying a text, two grouping
        // names and a catch-all that is one of them; the port receives a command holding those names in order
        // and that catch-all, and the RPC answers an empty response. Also covers
        // whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode() with
        // the fixture swap to DEFAULT_CATEGORY_GROUPINGS / DEFAULT_CATCH_ALL.
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

        // TODO RI05: replace the two known_categories entries with the four grouping and catch-all cases (empty
        // category_groupings, a blank entry, a blank catch_all_grouping, and a catch_all_grouping not among
        // category_groupings); the text and currency cases stay, reading DEFAULT_CATEGORY_GROUPINGS and
        // DEFAULT_CATCH_ALL in place of the dropped DEFAULT_KNOWN_CATEGORIES.
        private static Stream<Arguments> invalidRequests() {
            return Stream.of(
                    Arguments.of("text absent", RequestFixtures.request("")),
                    Arguments.of("text whitespace-only", RequestFixtures.request("   ")),
                    Arguments.of(
                            "category_groupings empty",
                            RequestFixtures.request(TEXT, List.of(), RequestFixtures.DEFAULT_CATCH_ALL)));
        }
    }
}
