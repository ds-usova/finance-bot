package bot.finance.ai.adapter.grpc;

import static bot.finance.ai.common.fixtures.RequestFixtures.catalogueEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.GroupingCategories;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub;
import bot.finance.ai.adapter.security.CallerTokenVerifier;
import bot.finance.ai.application.dto.AcceptTurnCommand;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.TurnAcceptance;
import bot.finance.ai.application.port.AcceptTurnPort;
import bot.finance.ai.common.boot.GrpcAdapterTest;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.fixtures.RequestFixtures;
import bot.finance.ai.common.stubs.AuthorizedStubs;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.MessageIdentity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
    private AcceptTurnPort acceptTurnPort;

    @BeforeEach
    void setUp() {
        when(acceptTurnPort.accept(any())).thenReturn(TurnAcceptance.ACCEPTED);
    }

    private IntentExtractionServiceBlockingStub authenticatedStub() {
        return AuthorizedStubs.withCallerToken(intentExtractionStub, CallerTokens.bearer(42L, "incoming-message-42"));
    }

    private ExtractIntentsCommand acceptedTurn() {
        ArgumentCaptor<AcceptTurnCommand> commandCaptor = ArgumentCaptor.forClass(AcceptTurnCommand.class);
        verify(acceptTurnPort).accept(commandCaptor.capture());
        return commandCaptor.getValue().turn();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("when a token names person 42 and message m-1 - then the call answers "
                + "empty and the port receives that turn key")
        void whenRequestMadeUnderTokenNamingPersonAndMessage_thenCallAnswersEmptyAndPortReceivesTurnKey() {
            ExtractIntentsResponse response = AuthorizedStubs.withCallerToken(
                            intentExtractionStub, CallerTokens.bearer(42L, "m-1"))
                    .extractIntents(RequestFixtures.request());

            ArgumentCaptor<AcceptTurnCommand> commandCaptor = ArgumentCaptor.forClass(AcceptTurnCommand.class);
            verify(acceptTurnPort).accept(commandCaptor.capture());
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
            assertThat(commandCaptor.getValue().turnKey()).isEqualTo(new MessageIdentity(42L, "m-1"));
        }

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

            ExtractIntentsCommand turn = acceptedTurn();
            assertThat(turn.defaultCurrency()).isPresent();
            assertThat(turn.defaultCurrency().get().code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the request carries two groupings and a catch-all - then the port receives them in "
                + "order with that catch-all")
        void whenRequestCarriesTextAndTwoCategoryGroupings_thenPortReceivesOrderedGroupingsAndCatchAll() {
            List<String> categoryGroupings = List.of("Food", "Insurance");
            String catchAllGrouping = "Insurance";

            ExtractIntentsResponse response = authenticatedStub()
                    .extractIntents(RequestFixtures.request(TEXT, categoryGroupings, catchAllGrouping));

            ExtractIntentsCommand turn = acceptedTurn();
            assertThat(turn.categoryGroupings()).containsExactly("Food", "Insurance");
            assertThat(turn.catchAllGrouping()).isEqualTo(catchAllGrouping);
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
        }

        @Test
        @DisplayName("when the request carries a valid current_date - then the port receives it as a LocalDate")
        void whenRequestCarriesValidCurrentDate_thenCommandHoldsItAsLocalDate() {
            String currentDate = "2026-01-15";

            ExtractIntentsResponse response =
                    authenticatedStub().extractIntents(RequestFixtures.requestWithCurrentDate(currentDate));

            assertThat(acceptedTurn().currentDate()).isEqualTo(LocalDate.parse(currentDate));
            assertThat(response).isEqualTo(ExtractIntentsResponse.getDefaultInstance());
        }

        @Test
        @DisplayName("when the request carries a catalogue of two groupings - then the command holds both "
                + "entries in order with categories")
        void whenRequestCarriesCatalogueOfTwoGroupings_thenCommandHoldsEntriesInOrderWithCategories() {
            ExtractIntentsRequest request = RequestFixtures.requestWithCatalogue(
                    TEXT,
                    List.of(catalogueEntry("Food", "Cafe", "Groceries"), catalogueEntry("Other", "Gifts")),
                    "Other");

            authenticatedStub().extractIntents(request);

            assertThat(acceptedTurn().catalogue())
                    .containsExactly(
                            new CatalogueEntry("Food", List.of("Cafe", "Groceries")),
                            new CatalogueEntry("Other", List.of("Gifts")));
        }

        @Test
        @DisplayName("when memory is disabled under a caller token - then the command carries no identity and "
                + "the port is called")
        void whenValidRequestMadeUnderCallerTokenWithMemoryDisabled_thenCommandCarriesNoIdentityAndPortCalled() {
            authenticatedStub().extractIntents(RequestFixtures.request());

            assertThat(acceptedTurn().messageIdentity()).isEmpty();
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

                assertThat(acceptedTurn().messageIdentity()).contains(identity);
            }
        }
    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port answers BUSY - then the RPC fails with status RESOURCE_EXHAUSTED")
        void whenPortAnswersBusy_thenFailsWithResourceExhausted() {
            when(acceptTurnPort.accept(any())).thenReturn(TurnAcceptance.BUSY);

            assertThatThrownBy(() -> authenticatedStub().extractIntents(RequestFixtures.request()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                    .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        }

        @Test
        @DisplayName("when the port throws an unrecognized RuntimeException - then the RPC fails with UNKNOWN and "
                + "leaks no message")
        void whenPortThrowsUnrecognizedRuntimeException_thenFailsWithUnknownAndMessageAbsent() {
            String secretMessage = "sensitive internal detail";
            doThrow(new RuntimeException(secretMessage)).when(acceptTurnPort).accept(any());

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
            verify(acceptTurnPort, never()).accept(any());
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
                            RequestFixtures.requestWithCurrentDate("2026-13-01")),
                    Arguments.of(
                            "catalogue has no entry for the catch-all grouping",
                            catalogueRequest(
                                    List.of("Food", "Other"), "Other", List.of(catalogueEntry("Food", "Cafe")))),
                    Arguments.of(
                            "catalogue entry has no category",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(catalogueEntry("Food", "Cafe"), catalogueEntry("Other")))),
                    Arguments.of(
                            "catalogue entry has a blank grouping",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(
                                            catalogueEntry("Food", "Cafe"),
                                            catalogueEntry("Other", "Gifts"),
                                            catalogueEntry(" ", "Misc")))),
                    Arguments.of(
                            "catalogue entry has a blank category name",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(catalogueEntry("Food", "Cafe", " "), catalogueEntry("Other", "Gifts")))),
                    Arguments.of(
                            "catalogue names a grouping twice",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(
                                            catalogueEntry("Food", "Cafe"),
                                            catalogueEntry("Food", "Groceries"),
                                            catalogueEntry("Other", "Gifts")))),
                    Arguments.of(
                            "catalogue entry names a category twice",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(catalogueEntry("Food", "Cafe", "Cafe"), catalogueEntry("Other", "Gifts")))),
                    Arguments.of(
                            "catalogue groupings differ from category_groupings",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(
                                            catalogueEntry("Food", "Cafe"),
                                            catalogueEntry("Other", "Gifts"),
                                            catalogueEntry("Travel", "Taxi")))),
                    Arguments.of(
                            "catalogue groupings are in another order than category_groupings",
                            catalogueRequest(
                                    List.of("Food", "Other"),
                                    "Other",
                                    List.of(catalogueEntry("Other", "Gifts"), catalogueEntry("Food", "Cafe")))));
        }

        private static ExtractIntentsRequest catalogueRequest(
                List<String> categoryGroupings, String catchAllGrouping, List<GroupingCategories> catalogue) {
            return RequestFixtures.request(TEXT, categoryGroupings, catchAllGrouping).toBuilder()
                    .clearCatalogue()
                    .addAllCatalogue(catalogue)
                    .build();
        }
    }
}
