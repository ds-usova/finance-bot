package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.ReadOutExpense;
import bot.finance.ai.application.dto.ReadOutPeriod;
import bot.finance.ai.application.dto.SpendingReadOut;
import bot.finance.ai.application.port.CategoryClassifierPort;
import bot.finance.ai.application.port.LedgerTurnPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.SpendingReadOutPort;
import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.LedgerCallRefusedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.ExtractionMode;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ClassifierTurnTest {

    private static final String TEXT = "confidential message about my money";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 10, 13);
    private static final double MIN_CONFIDENCE = 0.7;
    private static final CurrencyCode EUR = CurrencyCode.of("EUR");
    private static final MessageIdentity IDENTITY = new MessageIdentity(42L, "msg-123");

    private static final CategoryChoice CAFES = new CategoryChoice("Dining", "Cafés");
    private static final CategoryChoice RESTAURANTS = new CategoryChoice("Dining", "Restaurants");
    private static final CategoryChoice TAXIS = new CategoryChoice("Transport", "Taxis");
    private static final CategoryChoice GIFTS = new CategoryChoice("Miscellaneous", "Gifts");
    private static final CategoryChoice UNCATEGORIZED = new CategoryChoice("Miscellaneous", "Uncategorized Expenses");

    private SpendingReadOutPort spendingReadOutPort;
    private CategoryClassifierPort categoryClassifierPort;
    private LedgerTurnPort ledgerTurnPort;
    private TurnMeters turnMeters;
    private AdvancingClock clock;
    private Logger log;
    private ClassifierTurn turn;

    @BeforeEach
    void setUp() {
        spendingReadOutPort = mock(SpendingReadOutPort.class);
        categoryClassifierPort = mock(CategoryClassifierPort.class);
        ledgerTurnPort = mock(LedgerTurnPort.class);
        turnMeters = mock(TurnMeters.class);
        clock = new AdvancingClock();
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        turn = new ClassifierTurn(
                spendingReadOutPort,
                categoryClassifierPort,
                ledgerTurnPort,
                turnMeters,
                clock,
                MIN_CONFIDENCE,
                loggerFactory);
    }

    private static final class AdvancingClock extends Clock {

        private Instant now = Instant.parse("2026-10-13T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static List<CatalogueEntry> catalogue() {
        return List.of(
                new CatalogueEntry("Dining", List.of("Cafés", "Restaurants")),
                new CatalogueEntry("Transport", List.of("Taxis")),
                new CatalogueEntry("Miscellaneous", List.of("Gifts", "Uncategorized Expenses")));
    }

    private static ExtractIntentsCommand command(Optional<CurrencyCode> assumedCurrency, Optional<MessageIdentity> id) {
        return new ExtractIntentsCommand(
                TEXT,
                List.of("Dining", "Transport", "Miscellaneous"),
                "Miscellaneous",
                assumedCurrency,
                CURRENT_DATE,
                id,
                catalogue());
    }

    private static ExtractIntentsCommand command() {
        return command(Optional.empty(), Optional.empty());
    }

    private static ReadOutExpense readOutExpense(String description, String merchant, String amount, String currency) {
        return new ReadOutExpense(description, merchant, amount, currency);
    }

    private static ReadOutPeriod period(LocalDate from, LocalDate to) {
        return new ReadOutPeriod(from, to);
    }

    private static Map<CategoryChoice, Double> probabilities(Map<CategoryChoice, Double> overrides) {
        Map<CategoryChoice, Double> all = new HashMap<>();
        for (CategoryChoice pair : List.of(CAFES, RESTAURANTS, TAXIS, GIFTS, UNCATEGORIZED)) {
            all.put(pair, 0.1);
        }
        all.putAll(overrides);
        return all;
    }

    private static ExpenseClassification classification(int position, CategoryChoice choice, double confidence) {
        return new ExpenseClassification(position, choice, confidence, probabilities(Map.of(choice, confidence)));
    }

    private void readOutIs(List<ReadOutExpense> expenses, List<ReadOutPeriod> periods) {
        when(spendingReadOutPort.readOut(any(), any(), any())).thenReturn(new SpendingReadOut(expenses, periods));
    }

    private void twoExpensesRead() {
        readOutIs(
                List.of(readOutExpense("taxi", null, "12", "EUR"), readOutExpense("lunch", null, "15", "EUR")),
                List.of());
    }

    private void classifierAnswers(ExpenseClassification... classifications) {
        when(categoryClassifierPort.classify(any(), any(), any(), any())).thenReturn(List.of(classifications));
    }

    @SuppressWarnings("unchecked")
    private List<ExpenseToFile> classifiedExpenses() {
        ArgumentCaptor<List<ExpenseToFile>> captor =
                (ArgumentCaptor<List<ExpenseToFile>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(categoryClassifierPort).classify(any(), any(), captor.capture(), any());
        return captor.getValue();
    }

    private List<CategoryChoice> filedChoices(int expectedFilings) {
        ArgumentCaptor<CategoryChoice> choices = ArgumentCaptor.forClass(CategoryChoice.class);
        verify(ledgerTurnPort, times(expectedFilings)).fileExpense(choices.capture(), any());
        return choices.getAllValues();
    }

    private List<ExpenseToFile> filedExpenses(int expectedFilings) {
        ArgumentCaptor<ExpenseToFile> expenses = ArgumentCaptor.forClass(ExpenseToFile.class);
        verify(ledgerTurnPort, times(expectedFilings)).fileExpense(any(), expenses.capture());
        return expenses.getAllValues();
    }

    private List<String> loggedLines() {
        List<String> lines = new ArrayList<>();
        lines.addAll(MockedLoggerUtils.infoLines(log));
        lines.addAll(MockedLoggerUtils.warnLines(log));
        lines.addAll(MockedLoggerUtils.linesAt(log, "error"));
        return lines;
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    private static MessageExample exampleFixture() {
        ExampleExpense expense = new ExampleExpense(
                "latte",
                Optional.of("Blue Bottle"),
                "5.00",
                EUR,
                Optional.of("Cafés"),
                Optional.of("Dining"),
                ExampleOutcome.ACCEPTED);
        return new MessageExample("latte earlier", List.of(expense));
    }

    @Nested
    @DisplayName("run()")
    class Run {

        @Nested
        @DisplayName("filing what the classifier is sure of")
        class Filing {

            @Test
            @DisplayName("when the classifier answers at the minimum confidence exactly - then the expense is "
                    + "filed under its choice")
            void whenClassifierAnswersAtMinimumConfidence_thenExpenseFiledUnderItsChoice() {
                readOutIs(List.of(readOutExpense("coffee", "Blue Bottle", "4.50", "EUR")), List.of());
                classifierAnswers(classification(0, CAFES, MIN_CONFIDENCE));

                turn.run(command(), Optional.empty());

                assertThat(filedChoices(1)).containsExactly(CAFES);
                ExpenseToFile filed = filedExpenses(1).get(0);
                assertThat(filed.description()).isEqualTo("coffee");
                assertThat(filed.merchant()).contains("Blue Bottle");
                assertThat(filed.amount()).isEqualTo("4.50");
                assertThat(filed.currency()).isEqualTo(EUR);
            }

            @Test
            @DisplayName("when the read-out holds two expenses - then the classifier is asked once and they are "
                    + "filed in the user's order")
            void whenReadOutHoldsTwoExpenses_thenClassifierAskedOnceAndExpensesFiledInOrder() {
                twoExpensesRead();
                classifierAnswers(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, 0.9));

                turn.run(command(), Optional.empty());

                assertThat(classifiedExpenses())
                        .extracting(ExpenseToFile::description)
                        .containsExactly("taxi", "lunch");
                assertThat(filedChoices(2)).containsExactly(TAXIS, RESTAURANTS);
                assertThat(filedExpenses(2))
                        .extracting(ExpenseToFile::description)
                        .containsExactly("taxi", "lunch");
                InOrder inOrder = inOrder(ledgerTurnPort);
                inOrder.verify(ledgerTurnPort).fileExpense(eq(TAXIS), any());
                inOrder.verify(ledgerTurnPort).fileExpense(eq(RESTAURANTS), any());
            }

            @Test
            @DisplayName("when the read-out expense has no merchant - then it is filed with no merchant")
            void whenReadOutExpenseHasNoMerchant_thenItIsFiledWithNoMerchant() {
                readOutIs(List.of(readOutExpense("coffee", null, "4.50", "EUR")), List.of());
                classifierAnswers(classification(0, CAFES, 0.9));

                turn.run(command(), Optional.empty());

                assertThat(filedExpenses(1).get(0).merchant()).isEmpty();
            }

            @Test
            @DisplayName("when the command carries an assumed currency - then an expense without one is filed in it")
            void whenCommandCarriesAssumedCurrency_thenExpenseWithoutCurrencyFiledInIt() {
                readOutIs(List.of(readOutExpense("lunch", null, "15", null)), List.of());
                classifierAnswers(classification(0, RESTAURANTS, 0.9));

                turn.run(command(Optional.of(EUR), Optional.empty()), Optional.empty());

                assertThat(filedExpenses(1).get(0).currency()).isEqualTo(EUR);
                verify(spendingReadOutPort).readOut(eq(TEXT), eq(CURRENT_DATE), eq(Optional.of(EUR)));
            }

            @Test
            @DisplayName("when recalled examples are given - then the classifier receives the message text and "
                    + "those examples")
            void whenRecalledExamplesGiven_thenClassifierReceivesMessageTextAndExamples() {
                List<MessageExample> examples = List.of(exampleFixture());
                readOutIs(List.of(readOutExpense("coffee", null, "4.50", "EUR")), List.of());
                classifierAnswers(classification(0, CAFES, 0.9));

                turn.run(command(), Optional.of(examples));

                verify(categoryClassifierPort).classify(eq(TEXT), eq(examples), any(), any());
            }
        }

        @Nested
        @DisplayName("when the classifier is not confident")
        class LowConfidence {

            @Test
            @DisplayName("when the confidence is below the minimum - then the expense is filed under the "
                    + "catch-all's most probable category")
            void whenConfidenceBelowMinimum_thenFiledUnderCatchAllMostProbableCategory() {
                readOutIs(List.of(readOutExpense("the thing", null, "4000", "HUF")), List.of());
                classifierAnswers(new ExpenseClassification(
                        0, CAFES, 0.4, probabilities(Map.of(CAFES, 0.4, GIFTS, 0.2, UNCATEGORIZED, 0.5))));

                turn.run(command(), Optional.empty());

                assertThat(filedChoices(1)).containsExactly(UNCATEGORIZED);
                verify(turnMeters, times(1)).countLowConfidence();
            }
        }

        @Nested
        @DisplayName("recording what happened")
        class Metering {

            @Test
            @DisplayName("when the classifier answers several expenses - then each confidence and the call "
                    + "are recorded")
            void whenClassifierAnswersSeveralExpenses_thenEachConfidenceAndTheAnsweredCallRecorded() {
                twoExpensesRead();
                when(categoryClassifierPort.classify(any(), any(), any(), any()))
                        .thenAnswer(invocation -> {
                            clock.advance(Duration.ofMillis(250));
                            return List.of(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, 0.8));
                        });

                turn.run(command(), Optional.empty());

                verify(turnMeters).recordConfidence(0.9);
                verify(turnMeters).recordConfidence(0.8);
                verify(turnMeters).recordClassifierCall(Duration.ofMillis(250), true);
                verify(turnMeters, never()).countLowConfidence();
            }
        }

        @Nested
        @DisplayName("when the read-out holds nothing to classify")
        class NothingToClassify {

            @Test
            @DisplayName("when only a period is read - then it is recorded and the classifier is never called")
            void whenOnlyPeriodRead_thenPeriodRecordedAndClassifierNeverCalled() {
                readOutIs(List.of(), List.of(period(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12))));

                turn.run(command(), Optional.empty());

                verify(ledgerTurnPort).recordPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
                verify(ledgerTurnPort, never()).fileExpense(any(), any());
                verifyNoInteractions(categoryClassifierPort);
            }

            @Test
            @DisplayName("when neither an expense nor a period is read - then neither the classifier nor the "
                    + "ledger is called")
            void whenNothingIsRead_thenNeitherClassifierNorLedgerCalled() {
                readOutIs(List.of(), List.of());

                turn.run(command(), Optional.empty());

                verifyNoInteractions(categoryClassifierPort, ledgerTurnPort);
            }
        }

        @Nested
        @DisplayName("when a read-out expense is unusable")
        class UnusableExpense {

            static Stream<Arguments> unusableExpenses() {
                return Stream.of(
                        Arguments.of("blank description", readOutExpense("  ", null, "9", "EUR"), "description"),
                        Arguments.of("amount not a decimal", readOutExpense("gift", null, "lots", "EUR"), "amount"),
                        Arguments.of("amount not positive", readOutExpense("gift", null, "-3", "EUR"), "amount"),
                        Arguments.of("currency not ISO 4217", readOutExpense("gift", null, "9", "EURO"), "currency"));
            }

            @Test
            @DisplayName("when an expense has no currency and none is assumed - then only the usable one is "
                    + "filed and the other is logged")
            void whenNoCurrencyAndNoneAssumed_thenOnlyUsableFiledAndOtherLoggedWithReason() {
                readOutIs(
                        List.of(
                                readOutExpense("coffee", null, "4.50", "EUR"),
                                readOutExpense("lunch", null, "15", null)),
                        List.of());
                classifierAnswers(classification(0, CAFES, 0.9));

                turn.run(command(), Optional.empty());

                assertThat(classifiedExpenses())
                        .extracting(ExpenseToFile::description)
                        .containsExactly("coffee");
                assertThat(filedExpenses(1))
                        .extracting(ExpenseToFile::description)
                        .containsExactly("coffee");
                assertThat(loggedLines())
                        .anyMatch(line -> line.toLowerCase().contains("currency"))
                        .noneMatch(line -> line.contains(TEXT));
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource("unusableExpenses")
            @DisplayName("when an expense is unusable - then only the usable one is filed and the other is "
                    + "logged without the text")
            void whenExpenseIsUnusable_thenOnlyUsableFiledAndOtherLoggedWithReasonAndNoText(
                    String name, ReadOutExpense unusable, String reason) {
                readOutIs(List.of(readOutExpense("coffee", null, "4.50", "EUR"), unusable), List.of());
                classifierAnswers(classification(0, CAFES, 0.9));

                turn.run(command(), Optional.empty());

                assertThat(classifiedExpenses())
                        .extracting(ExpenseToFile::description)
                        .containsExactly("coffee");
                assertThat(filedExpenses(1))
                        .extracting(ExpenseToFile::description)
                        .containsExactly("coffee");
                assertThat(loggedLines())
                        .anyMatch(line -> line.toLowerCase().contains(reason))
                        .noneMatch(line -> line.contains(TEXT));
            }
        }

        @Nested
        @DisplayName("when the classifier fails")
        class ClassifierFailure {

            static Stream<Arguments> unusableAnswers() {
                return Stream.of(
                        Arguments.of("no classification for one expense", List.of(classification(0, TAXIS, 0.9))),
                        Arguments.of(
                                "repeated position",
                                List.of(classification(0, TAXIS, 0.9), classification(0, RESTAURANTS, 0.9))),
                        Arguments.of(
                                "missing position",
                                List.of(
                                        classification(0, TAXIS, 0.9),
                                        new ExpenseClassification(null, RESTAURANTS, 0.9, probabilities(Map.of())))),
                        Arguments.of(
                                "position out of range",
                                List.of(classification(0, TAXIS, 0.9), classification(7, RESTAURANTS, 0.9))),
                        Arguments.of(
                                "null confidence",
                                List.of(
                                        classification(0, TAXIS, 0.9),
                                        new ExpenseClassification(1, RESTAURANTS, null, probabilities(Map.of())))),
                        Arguments.of(
                                "confidence above one",
                                List.of(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, 1.5))),
                        Arguments.of(
                                "confidence below zero",
                                List.of(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, -0.1))),
                        Arguments.of(
                                "no probability for one catalogue pair",
                                List.of(
                                        classification(0, TAXIS, 0.9),
                                        new ExpenseClassification(1, RESTAURANTS, 0.9, withoutGifts()))));
            }

            private static Map<CategoryChoice, Double> withoutGifts() {
                Map<CategoryChoice, Double> probabilities = probabilities(Map.of());
                probabilities.remove(GIFTS);
                return probabilities;
            }

            @Test
            @DisplayName("when the classifier throws - then every expense is filed under the catch-all's first "
                    + "category")
            void whenClassifierThrows_thenEveryExpenseFiledUnderCatchAllFirstCategoryAndCallRecordedFailed() {
                twoExpensesRead();
                when(categoryClassifierPort.classify(any(), any(), any(), any()))
                        .thenThrow(new ClassificationFailedException("classifier unreachable"));

                turn.run(command(Optional.empty(), Optional.of(IDENTITY)), Optional.empty());

                assertThat(filedChoices(2)).containsExactly(GIFTS, GIFTS);
                verify(turnMeters).recordClassifierCall(any(Duration.class), eq(false));
                verify(turnMeters, never()).recordConfidence(anyDouble());
            }

            @Test
            @DisplayName("when the classifier throws - then one WARN names the person and the message and no text")
            void whenClassifierThrows_thenOneWarnNamesPersonAndMessageAndNoText() {
                twoExpensesRead();
                when(categoryClassifierPort.classify(any(), any(), any(), any()))
                        .thenThrow(new ClassificationFailedException("classifier unreachable"));

                turn.run(command(Optional.empty(), Optional.of(IDENTITY)), Optional.empty());

                assertThat(loggedWarnLines()).hasSize(1);
                assertThat(loggedWarnLines().get(0))
                        .contains("42")
                        .contains("msg-123")
                        .doesNotContain(TEXT);
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource("unusableAnswers")
            @DisplayName("when the classifier's answer is unusable - then every expense is filed as for a failure "
                    + "and one WARN is logged")
            void whenAnswerIsUnusable_thenEveryExpenseFiledAsForFailureAndOneWarnLogged(
                    String name, List<ExpenseClassification> answer) {
                twoExpensesRead();
                when(categoryClassifierPort.classify(any(), any(), any(), any()))
                        .thenReturn(answer);

                turn.run(command(Optional.empty(), Optional.of(IDENTITY)), Optional.empty());

                assertThat(filedChoices(2)).containsExactly(GIFTS, GIFTS);
                verify(turnMeters).recordClassifierCall(any(Duration.class), eq(false));
                assertThat(loggedWarnLines()).hasSize(1);
            }

            @Test
            @DisplayName("when the command carries no message identity - then the WARN names neither person nor "
                    + "message")
            void whenCommandCarriesNoIdentity_thenWarnNamesNeitherPersonNorMessage() {
                twoExpensesRead();
                when(categoryClassifierPort.classify(any(), any(), any(), any()))
                        .thenThrow(new ClassificationFailedException("classifier unreachable"));

                turn.run(command(), Optional.empty());

                assertThat(loggedWarnLines()).hasSize(1);
                assertThat(loggedWarnLines().get(0)).doesNotContain(TEXT).doesNotContain("null");
            }
        }

        @Nested
        @DisplayName("when the read-out fails")
        class ReadOutFailure {

            @Test
            @DisplayName("when the read-out port throws ExpenseRecordingFailedException - then it propagates and "
                    + "nothing else is called")
            void whenReadOutPortThrows_thenExceptionPropagatesAndNothingElseCalled() {
                ExpenseRecordingFailedException failure = new ExpenseRecordingFailedException("provider down");
                when(spendingReadOutPort.readOut(any(), any(), any())).thenThrow(failure);

                assertThatThrownBy(() -> turn.run(command(), Optional.empty())).isSameAs(failure);

                verifyNoInteractions(categoryClassifierPort, ledgerTurnPort);
            }
        }

        @Nested
        @DisplayName("when the ledger refuses or fails")
        class LedgerFailure {

            @Test
            @DisplayName("when the ledger refuses the first of two expenses - then the second is still filed")
            void whenLedgerRefusesFirstExpense_thenSecondFiledFirstLoggedAndTurnReturns() {
                twoExpensesRead();
                classifierAnswers(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, 0.9));
                doThrow(new LedgerCallRefusedException("invalid argument"))
                        .doNothing()
                        .when(ledgerTurnPort)
                        .fileExpense(any(), any());

                assertThatCode(() -> turn.run(command(), Optional.empty())).doesNotThrowAnyException();

                assertThat(filedExpenses(2))
                        .extracting(ExpenseToFile::description)
                        .containsExactly("taxi", "lunch");
                assertThat(loggedLines()).anyMatch(line -> line.equals("Ledger refused expense {} [0]"));
            }

            @Test
            @DisplayName("when the ledger refuses an expense - then no line above debug carries its description")
            void whenLedgerRefusesExpense_thenNoLineAboveDebugCarriesItsDescription() {
                readOutIs(List.of(readOutExpense("coffee at Anna's", null, "4.50", "EUR")), List.of());
                classifierAnswers(classification(0, CAFES, 0.9));
                doThrow(new LedgerCallRefusedException("invalid argument"))
                        .when(ledgerTurnPort)
                        .fileExpense(any(), any());

                turn.run(command(), Optional.empty());

                assertThat(loggedLines()).isNotEmpty().noneMatch(line -> line.contains("coffee at Anna's"));
            }

            @Test
            @DisplayName("when the ledger refuses a period - then the turn returns and the refusal is logged")
            void whenLedgerRefusesPeriod_thenTurnReturnsAndRefusalLogged() {
                readOutIs(List.of(), List.of(period(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12))));
                doThrow(new LedgerCallRefusedException("invalid argument"))
                        .when(ledgerTurnPort)
                        .recordPeriod(any(), any());

                assertThatCode(() -> turn.run(command(), Optional.empty())).doesNotThrowAnyException();

                verify(ledgerTurnPort).recordPeriod(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
                assertThat(loggedLines()).anyMatch(line -> line.contains("2026-10-12"));
            }

            @Test
            @DisplayName("when the ledger fails the first of two expenses - then the exception propagates and "
                    + "nothing more is tried")
            void whenLedgerFailsFirstExpense_thenExceptionPropagatesAndNothingMoreTried() {
                readOutIs(
                        List.of(readOutExpense("taxi", null, "12", "EUR"), readOutExpense("lunch", null, "15", "EUR")),
                        List.of(period(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12))));
                classifierAnswers(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, 0.9));
                ExpenseRecordingFailedException failure = new ExpenseRecordingFailedException("ledger unavailable");
                doThrow(failure).doNothing().when(ledgerTurnPort).fileExpense(any(), any());

                assertThatThrownBy(() -> turn.run(command(), Optional.empty())).isSameAs(failure);

                verify(ledgerTurnPort, times(1)).fileExpense(any(), any());
                verify(ledgerTurnPort, never()).recordPeriod(any(), any());
            }
        }

        @Nested
        @DisplayName("when the turn's thread is interrupted")
        class Interruption {

            @Test
            @DisplayName("when the thread is interrupted once the first of two expenses is filed - then the second "
                    + "is not filed and the turn fails")
            void whenThreadInterruptedAfterFirstFiling_thenSecondNotFiledAndTurnFails() {
                twoExpensesRead();
                classifierAnswers(classification(0, TAXIS, 0.9), classification(1, RESTAURANTS, 0.9));
                doAnswer(invocation -> {
                            Thread.currentThread().interrupt();
                            return null;
                        })
                        .doNothing()
                        .when(ledgerTurnPort)
                        .fileExpense(any(), any());

                try {
                    assertThatThrownBy(() -> turn.run(command(), Optional.empty()))
                            .isInstanceOf(ExpenseRecordingFailedException.class);
                } finally {
                    Thread.interrupted();
                }

                assertThat(filedExpenses(1))
                        .extracting(ExpenseToFile::description)
                        .containsExactly("taxi");
            }
        }
    }

    @Nested
    @DisplayName("mode()")
    class Mode {

        @Test
        @DisplayName("when the mode is read - then it is the classifier mode")
        void whenModeRead_thenItIsClassifierMode() {
            assertThat(turn.mode()).isEqualTo(ExtractionMode.CLASSIFIER);
        }
    }
}
