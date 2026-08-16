package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.adapter.security.CallerTokenVerifier;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.common.boot.GrpcAdapterTest;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.MessageIdentity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
        @DisplayName("when the request carries a default currency in any casing - then the command holds it "
                + "upper-cased")
        void whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode(
                String defaultCurrency) {
            authenticatedStub()
                    .extractIntents(RequestFixtures.request(
                            TEXT,
                            RequestFixtures.DEFAULT_CATEGORY_GROUPINGS,
                            RequestFixtures.DEFAULT_CATCH_ALL,
                            defaultCurrency));

            ArgumentCaptor<ExtractIntentsCommand> commandCaptor = ArgumentCaptor.forClass(ExtractIntentsCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().defaultCurrency()).isPresent();
            assertThat(commandCaptor.getValue().defaultCurrency().get().code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the request carries two groupings and a catch-all - then the port receives them in "
                + "order with that catch-all")
        void whenRequestCarriesTextAndTwoCategoryGroupings_thenPortReceivesOrderedGroupingsAndCatchAll() {
            List<String> categoryGroupings = List.of("Food", "Insurance");
            String catchAllGrouping = "Insurance";

            ExtractIntentsResponse response = authenticatedStub()
                    .extractIntents(RequestFixtures.request(TEXT, categoryGroupings, catchAllGrouping));

            ArgumentCaptor<ExtractIntentsCommand> commandCaptor = ArgumentCaptor.forClass(ExtractIntentsCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().categoryGroupings()).containsExactly("Food", "Insurance");
            assertThat(commandCaptor.getValue().catchAllGrouping()).isEqualTo(catchAllGrouping);
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
        }

        @Test
        @DisplayName("when the request carries a valid current_date - then the port receives it as a LocalDate")
        void whenRequestCarriesValidCurrentDate_thenCommandHoldsItAsLocalDate() {
            String currentDate = "2026-01-15";

            ExtractIntentsResponse response =
                    authenticatedStub().extractIntents(RequestFixtures.requestWithCurrentDate(currentDate));

            ArgumentCaptor<ExtractIntentsCommand> commandCaptor = ArgumentCaptor.forClass(ExtractIntentsCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().currentDate()).isEqualTo(LocalDate.parse(currentDate));
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
        }

        @Test
        @DisplayName("when memory is disabled under an opaque token - then the command carries no identity and "
                + "the port is called")
        void whenValidRequestMadeUnderOpaqueTokenWithMemoryDisabled_thenCommandCarriesNoIdentityAndPortCalled() {
            authenticatedStub().extractIntents(RequestFixtures.request());

            ArgumentCaptor<ExtractIntentsCommand> commandCaptor = ArgumentCaptor.forClass(ExtractIntentsCommand.class);
            verify(extractIntentsPort).extractIntents(commandCaptor.capture());
            assertThat(commandCaptor.getValue().messageIdentity()).isEmpty();
        }

        @Nested
        @DisplayName("with the caller identified")
        class CallerIdentified {

            @MockitoBean
            private CallerTokenVerifier callerTokenVerifier;

            @Test
            @DisplayName("when a valid request is made under any authorization header - then the command carries "
                    + "the verified identity")
            void whenValidRequestMadeUnderAnyAuthorizationHeader_thenCommandCarriesVerifiedIdentity() {
                MessageIdentity identity = new MessageIdentity(7L, "incoming-message-42");
                when(callerTokenVerifier.verify(any())).thenReturn(identity);

                authenticatedStub().extractIntents(RequestFixtures.request());

                ArgumentCaptor<ExtractIntentsCommand> commandCaptor =
                        ArgumentCaptor.forClass(ExtractIntentsCommand.class);
                verify(extractIntentsPort).extractIntents(commandCaptor.capture());
                assertThat(commandCaptor.getValue().messageIdentity()).contains(identity);
            }
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
        @DisplayName("when the port throws an unrecognized RuntimeException - then the RPC fails with UNKNOWN and "
                + "leaks no message")
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
                    Arguments.of(
                            "category_groupings empty",
                            RequestFixtures.request(TEXT, List.of(), RequestFixtures.DEFAULT_CATCH_ALL)),
                    Arguments.of(
                            "default_currency not a known ISO 4217 code",
                            RequestFixtures.request(
                                    TEXT,
                                    RequestFixtures.DEFAULT_CATEGORY_GROUPINGS,
                                    RequestFixtures.DEFAULT_CATCH_ALL,
                                    "ZZZ")),
                    Arguments.of(
                            "category_groupings entry blank",
                            RequestFixtures.request(TEXT, List.of("Food", ""), "Food")),
                    Arguments.of(
                            "catch_all_grouping blank",
                            RequestFixtures.request(TEXT, RequestFixtures.DEFAULT_CATEGORY_GROUPINGS, "")),
                    Arguments.of(
                            "catch_all_grouping not among category_groupings",
                            RequestFixtures.request(TEXT, RequestFixtures.DEFAULT_CATEGORY_GROUPINGS, "NotInList")),
                    Arguments.of("current_date blank", RequestFixtures.requestWithCurrentDate("")),
                    Arguments.of(
                            "current_date not written YYYY-MM-DD (wrong format)",
                            RequestFixtures.requestWithCurrentDate("27/07/2026")),
                    Arguments.of(
                            "current_date not written YYYY-MM-DD (invalid calendar date)",
                            RequestFixtures.requestWithCurrentDate("2026-13-01")));
        }
    }
}
