package bot.finance.ai.adapter.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import bot.finance.ai.common.boot.LedgerTurnAdapterTest;
import bot.finance.ai.common.containers.LedgerTurnServer.RecordedCall;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.stubs.LedgerTurnStubs;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@LedgerTurnAdapterTest
class GrpcTurnEndAdapterTest {

    private static final MessageIdentity TURN_KEY = new MessageIdentity(42L, "m-1");

    @Autowired
    private GrpcTurnEndAdapter adapter;

    private ListAppender<ILoggingEvent> appender;
    private Logger adapterLogger;

    @BeforeEach
    void setUp() {
        LedgerTurnStubs.reset();
        adapterLogger = (Logger) LoggerFactory.getLogger(GrpcTurnEndAdapter.class);
        appender = new ListAppender<>();
        appender.setContext(adapterLogger.getLoggerContext());
        appender.start();
        adapterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        adapterLogger.detachAppender(appender);
        appender.stop();
        LedgerTurnStubs.reset();
    }

    private void report(String token, TurnEnd end) {
        CallerTokenTestSupport.withTurn(token, TURN_KEY, () -> adapter.report(end));
    }

    private List<String> errors() {
        return List.copyOf(appender.list).stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void scriptUnavailable(int times) {
        for (int i = 0; i < times; i++) {
            LedgerTurnStubs.scriptEndRefused(Status.UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("report()")
    class Report {

        static Stream<Arguments> ends() {
            return Stream.of(
                    Arguments.of(TurnEnd.ACTED_ON, bot.finance.ledger.adapter.grpc.v1.TurnEnd.TURN_END_ACTED_ON),
                    Arguments.of(TurnEnd.UNAVAILABLE, bot.finance.ledger.adapter.grpc.v1.TurnEnd.TURN_END_UNAVAILABLE),
                    Arguments.of(TurnEnd.FAILED, bot.finance.ledger.adapter.grpc.v1.TurnEnd.TURN_END_FAILED));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ends")
        @DisplayName("when an end is reported and the ledger takes it - then it receives one EndTurn with the "
                + "matching value and the token")
        void whenEndReportedAndLedgerTakesIt_thenLedgerReceivesOneEndTurnWithMatchingValueAndToken(
                TurnEnd end, bot.finance.ledger.adapter.grpc.v1.TurnEnd wireValue) {
            String token = CallerTokens.bearer(42L, "m-1");
            LedgerTurnStubs.scriptEndTaken();

            report(token, end);

            assertThat(LedgerTurnStubs.recordedEnds()).singleElement().satisfies(call -> {
                assertThat(call.method()).isEqualTo("EndTurn");
                assertThat(call.authorization()).isEqualTo(token);
                assertThat(((EndTurnRequest) call.request()).getEnd()).isEqualTo(wireValue);
            });
            assertThat(errors()).isEmpty();
        }

        @Test
        @DisplayName("when the ledger answers UNAVAILABLE twice - then the end is sent three times and nothing is "
                + "logged at ERROR")
        void whenLedgerAnswersUnavailableTwice_thenEndSentThreeTimesAndNothingLoggedAtError() {
            scriptUnavailable(2);
            LedgerTurnStubs.scriptEndTaken();

            report(CallerTokens.bearer(42L, "m-1"), TurnEnd.ACTED_ON);

            assertThat(LedgerTurnStubs.recordedEnds()).hasSize(3);
            assertThat(errors()).isEmpty();
        }

        @Test
        @DisplayName("when the ledger does not answer inside the timeout once - then the end is sent twice")
        void whenLedgerDoesNotAnswerInsideTimeoutOnce_thenEndSentTwice() {
            LedgerTurnStubs.scriptEndNeverAnswering();
            LedgerTurnStubs.scriptEndTaken();

            report(CallerTokens.bearer(42L, "m-1"), TurnEnd.ACTED_ON);

            assertThat(LedgerTurnStubs.recordedEnds()).hasSize(2);
        }

        @Test
        @DisplayName("when the ledger answers UNAUTHENTICATED - then the end is sent once and the turn key is logged "
                + "at ERROR")
        void whenLedgerAnswersUnauthenticated_thenEndSentOnceAndTurnKeyLoggedAtError() {
            LedgerTurnStubs.scriptEndRefused(Status.UNAUTHENTICATED);

            report(CallerTokens.bearer(42L, "m-1"), TurnEnd.ACTED_ON);

            assertThat(LedgerTurnStubs.recordedEnds()).hasSize(1);
            assertThat(errors())
                    .singleElement()
                    .satisfies(message -> assertThat(message).contains("42").contains("m-1"));
        }

        @Test
        @DisplayName("when the ledger answers INVALID_ARGUMENT - then the end is sent once and logged at ERROR")
        void whenLedgerAnswersInvalidArgument_thenEndSentOnceAndLoggedAtError() {
            LedgerTurnStubs.scriptEndRefused(Status.INVALID_ARGUMENT);

            report(CallerTokens.bearer(42L, "m-1"), TurnEnd.FAILED);

            assertThat(LedgerTurnStubs.recordedEnds()).hasSize(1);
            assertThat(errors()).hasSize(1);
        }

        @Test
        @DisplayName("when the ledger is unavailable past token expiry - then it gives up without retrying "
                + "and logs the turn key at ERROR")
        void whenLedgerStaysUnavailablePastTokenExpiry_thenGivesUpWithoutLaterAttemptAndLogsTurnKeyAtError()
                throws InterruptedException {
            int scripted = 40;
            scriptUnavailable(scripted);
            String token = CallerTokens.bearerExpiringIn(42L, "m-1", Duration.ofSeconds(2));
            Instant startedAt = Instant.now();

            report(token, TurnEnd.ACTED_ON);

            Instant returnedAt = Instant.now();
            List<RecordedCall> atReturn = LedgerTurnStubs.recordedEnds();
            Thread.sleep(1500);
            assertThat(Duration.between(startedAt, returnedAt)).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
            assertThat(atReturn).hasSizeGreaterThan(1).hasSizeLessThan(scripted);
            assertThat(LedgerTurnStubs.recordedEnds()).hasSameSizeAs(atReturn);
            assertThat(errors())
                    .singleElement()
                    .satisfies(message -> assertThat(message).contains("42").contains("m-1"));
        }

        @Test
        @DisplayName("when the ledger is unavailable and the token has already expired - then the end is sent once "
                + "and logged at ERROR")
        void whenLedgerUnavailableAndTokenAlreadyExpired_thenEndSentOnceAndLoggedAtError() {
            scriptUnavailable(5);

            report(CallerTokens.bearerExpiringIn(42L, "m-1", Duration.ofMinutes(-1)), TurnEnd.ACTED_ON);

            assertThat(LedgerTurnStubs.recordedEnds()).hasSize(1);
            assertThat(errors()).hasSize(1);
        }

        @Test
        @DisplayName("when the ledger is unavailable and the token has no expiry - then the end is sent once and "
                + "logged at ERROR")
        void whenLedgerUnavailableAndTokenHasNoExpiry_thenEndSentOnceAndLoggedAtError() {
            scriptUnavailable(5);

            report(CallerTokens.bearerNoExpiry(42L, "m-1"), TurnEnd.ACTED_ON);

            assertThat(LedgerTurnStubs.recordedEnds()).hasSize(1);
            assertThat(errors()).hasSize(1);
        }

        @Test
        @DisplayName("when the turn holds no token - then nothing is sent and the end is logged at ERROR")
        void whenTurnHoldsNoToken_thenNothingSentAndEndLoggedAtError() {
            report(null, TurnEnd.ACTED_ON);

            assertThat(LedgerTurnStubs.recordedEnds()).isEmpty();
            assertThat(errors()).hasSize(1);
        }
    }
}
