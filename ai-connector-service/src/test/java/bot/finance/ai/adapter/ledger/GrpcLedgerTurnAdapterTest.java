package bot.finance.ai.adapter.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.grpc.CallerTokenTestSupport;
import bot.finance.ai.common.boot.LedgerTurnAdapterTest;
import bot.finance.ai.common.stubs.LedgerTurnStubs;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.LedgerCallRefusedException;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodRequest;
import io.grpc.Context;
import io.grpc.Status;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

@LedgerTurnAdapterTest
class GrpcLedgerTurnAdapterTest {

    private static final String CALLER_TOKEN = "caller-token-1";
    private static final CategoryChoice CHOICE = new CategoryChoice("Dining", "Restaurants");
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 11);

    @Autowired
    private GrpcLedgerTurnAdapter adapter;

    @BeforeEach
    void setUp() {
        LedgerTurnStubs.reset();
    }

    @AfterEach
    void tearDown() {
        LedgerTurnStubs.reset();
    }

    private static ExpenseToFile expense(Optional<String> merchant) {
        return new ExpenseToFile("lunch", merchant, "15.00", CurrencyCode.of("EUR"));
    }

    private void fileExpense(Optional<String> merchant) {
        CallerTokenTestSupport.withCallerToken(CALLER_TOKEN, () -> adapter.fileExpense(CHOICE, expense(merchant)));
    }

    private void recordPeriod() {
        CallerTokenTestSupport.withCallerToken(CALLER_TOKEN, () -> adapter.recordPeriod(FROM, TO));
    }

    @Nested
    @DisplayName("fileExpense()")
    class FileExpense {

        @Test
        @DisplayName("when an expense with a merchant is filed - then the ledger receives every field and the token")
        void whenExpenseWithMerchantIsFiled_thenLedgerReceivesEveryFieldAndTheToken() {
            LedgerTurnStubs.scriptStored();

            fileExpense(Optional.of("Deli Co"));

            assertThat(LedgerTurnStubs.recordedCalls()).singleElement().satisfies(call -> {
                assertThat(call.method()).isEqualTo("FileExpenseProposal");
                assertThat(call.authorization()).isEqualTo("Bearer " + CALLER_TOKEN);
                FileExpenseProposalRequest request = (FileExpenseProposalRequest) call.request();
                assertThat(request.getGrouping()).isEqualTo("Dining");
                assertThat(request.getCategory()).isEqualTo("Restaurants");
                assertThat(request.getDescription()).isEqualTo("lunch");
                assertThat(request.hasMerchant()).isTrue();
                assertThat(request.getMerchant()).isEqualTo("Deli Co");
                assertThat(request.getAmount()).isEqualTo("15.00");
                assertThat(request.getCurrencyCode()).isEqualTo("EUR");
            });
        }

        @Test
        @DisplayName("when an expense with no merchant is filed - then the ledger receives no merchant")
        void whenExpenseWithNoMerchantIsFiled_thenLedgerReceivesNoMerchant() {
            LedgerTurnStubs.scriptStored();

            fileExpense(Optional.empty());

            assertThat(LedgerTurnStubs.recordedCalls()).singleElement().satisfies(call -> {
                FileExpenseProposalRequest request = (FileExpenseProposalRequest) call.request();
                assertThat(request.getDescription()).isEqualTo("lunch");
                assertThat(request.hasMerchant()).isFalse();
            });
        }

        @Test
        @DisplayName("when the ledger answers INVALID_ARGUMENT - then it throws LedgerCallRefusedException")
        void whenLedgerAnswersInvalidArgument_thenThrowsLedgerCallRefusedException() {
            LedgerTurnStubs.scriptRefused(Status.INVALID_ARGUMENT);

            assertThatThrownBy(() -> fileExpense(Optional.of("Deli Co")))
                    .isInstanceOf(LedgerCallRefusedException.class);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = Status.Code.class,
                names = {"NOT_FOUND", "UNAVAILABLE", "PERMISSION_DENIED"})
        @DisplayName("when the ledger answers any other failure - then it throws ExpenseRecordingFailedException")
        void whenLedgerAnswersAnyOtherFailure_thenThrowsExpenseRecordingFailedException(Status.Code code) {
            LedgerTurnStubs.scriptRefused(code.toStatus());

            assertThatThrownBy(() -> fileExpense(Optional.of("Deli Co")))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when the ledger does not answer inside the timeout - then it throws "
                + "ExpenseRecordingFailedException")
        void whenLedgerDoesNotAnswerInsideTimeout_thenThrowsExpenseRecordingFailedException() {
            LedgerTurnStubs.scriptNeverAnswering();

            assertThatThrownBy(() -> fileExpense(Optional.of("Deli Co")))
                    .isInstanceOf(ExpenseRecordingFailedException.class);
        }

        @Test
        @DisplayName("when the inbound deadline is nearer than the timeout - then it fails once that deadline passes")
        void whenInboundDeadlineIsNearerThanTimeout_thenFailsOnceThatDeadlinePasses() {
            LedgerTurnStubs.scriptNeverAnswering();
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            Duration deadline = Duration.ofMillis(200);
            long startedAt = System.nanoTime();

            try {
                assertThatThrownBy(() -> Context.current()
                                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS, scheduler)
                                .run(() -> fileExpense(Optional.of("Deli Co"))))
                        .isInstanceOf(ExpenseRecordingFailedException.class);
            } finally {
                scheduler.shutdownNow();
            }

            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(900));
        }
    }

    @Nested
    @DisplayName("recordPeriod()")
    class RecordPeriod {

        @Test
        @DisplayName("when a period is recorded - then the ledger receives both days written YYYY-MM-DD")
        void whenPeriodIsRecorded_thenLedgerReceivesBothDaysWrittenAsIsoDates() {
            LedgerTurnStubs.scriptStored();

            recordPeriod();

            assertThat(LedgerTurnStubs.recordedCalls()).singleElement().satisfies(call -> {
                assertThat(call.method()).isEqualTo("RecordSpendingPeriod");
                assertThat(call.authorization()).isEqualTo("Bearer " + CALLER_TOKEN);
                RecordSpendingPeriodRequest request = (RecordSpendingPeriodRequest) call.request();
                assertThat(request.getFrom()).isEqualTo("2026-10-05");
                assertThat(request.getTo()).isEqualTo("2026-10-11");
            });
        }

        @Test
        @DisplayName("when the ledger answers INVALID_ARGUMENT - then it throws LedgerCallRefusedException")
        void whenLedgerAnswersInvalidArgument_thenThrowsLedgerCallRefusedException() {
            LedgerTurnStubs.scriptRefused(Status.INVALID_ARGUMENT);

            assertThatThrownBy(GrpcLedgerTurnAdapterTest.this::recordPeriod)
                    .isInstanceOf(LedgerCallRefusedException.class);
        }
    }
}
