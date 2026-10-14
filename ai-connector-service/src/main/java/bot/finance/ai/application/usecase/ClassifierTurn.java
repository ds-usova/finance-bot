package bot.finance.ai.application.usecase;

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
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.LedgerCallRefusedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.ExtractionMode;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClassifierTurn implements ExtractionTurn {

    private final SpendingReadOutPort spendingReadOutPort;
    private final CategoryClassifierPort categoryClassifierPort;
    private final LedgerTurnPort ledgerTurnPort;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final double minConfidence;
    private final Logger log;

    public ClassifierTurn(
            SpendingReadOutPort spendingReadOutPort,
            CategoryClassifierPort categoryClassifierPort,
            LedgerTurnPort ledgerTurnPort,
            TurnMeters turnMeters,
            Clock clock,
            double minConfidence,
            LoggerFactory loggerFactory) {
        this.spendingReadOutPort = spendingReadOutPort;
        this.categoryClassifierPort = categoryClassifierPort;
        this.ledgerTurnPort = ledgerTurnPort;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.minConfidence = minConfidence;
        this.log = loggerFactory.getLogger(ClassifierTurn.class);
    }

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.CLASSIFIER;
    }

    @Override
    public void run(ExtractIntentsCommand command, Optional<List<MessageExample>> examples) {
        SpendingReadOut readOut =
                spendingReadOutPort.readOut(command.text(), command.currentDate(), command.defaultCurrency());

        List<ExpenseToFile> usable = usableExpenses(readOut.expenses(), command);
        if (!usable.isEmpty()) {
            List<CategoryChoice> choices = chooseCategories(command, examples.orElse(List.of()), usable);
            for (int position = 0; position < usable.size(); position++) {
                fileExpense(position, choices.get(position), usable.get(position));
            }
        }
        for (ReadOutPeriod period : readOut.periods()) {
            recordPeriod(period);
        }
    }

    private List<ExpenseToFile> usableExpenses(List<ReadOutExpense> readOutExpenses, ExtractIntentsCommand command) {
        List<ExpenseToFile> usable = new ArrayList<>();
        for (int position = 0; position < readOutExpenses.size(); position++) {
            try {
                usable.add(toExpense(readOutExpenses.get(position), command));
            } catch (InvalidValueException e) {
                log.warn("Dropped read-out expense {}: {}", position, withoutValue(e.getMessage()));
                log.debug("Dropped read-out expense {}: {}", position, e.getMessage());
            }
        }
        return usable;
    }

    /** A domain refusal names the offending value after a colon. */
    private static String withoutValue(String message) {
        int colon = message.indexOf(':');
        return colon < 0 ? message : message.substring(0, colon);
    }

    private ExpenseToFile toExpense(ReadOutExpense readOut, ExtractIntentsCommand command) {
        CurrencyCode currency;
        if (readOut.currency() == null || readOut.currency().isBlank()) {
            currency = command.defaultCurrency()
                    .orElseThrow(() -> new InvalidValueException("Currency not read and none assumed"));
        } else {
            currency = CurrencyCode.of(readOut.currency());
        }
        return new ExpenseToFile(
                readOut.description(), Optional.ofNullable(readOut.merchant()), readOut.amount(), currency);
    }

    private List<CategoryChoice> chooseCategories(
            ExtractIntentsCommand command, List<MessageExample> examples, List<ExpenseToFile> expenses) {
        Instant start = clock.instant();
        try {
            List<ExpenseClassification> answer =
                    categoryClassifierPort.classify(command.text(), examples, expenses, command.catalogue());
            Duration duration = Duration.between(start, clock.instant());
            List<ExpenseClassification> ordered = orderedAnswer(answer, expenses.size(), command.catalogue());
            turnMeters.recordClassifierCall(duration, true);
            return ordered.stream()
                    .map(classification -> chooseCategory(classification, command))
                    .toList();
        } catch (ClassificationFailedException e) {
            turnMeters.recordClassifierCall(Duration.between(start, clock.instant()), false);
            return fallBack(command, expenses.size());
        }
    }

    private List<ExpenseClassification> orderedAnswer(
            List<ExpenseClassification> answer, int expectedCount, List<CatalogueEntry> catalogue) {
        if (answer == null || answer.size() != expectedCount) {
            throw new ClassificationFailedException("Classifier did not answer every expense once");
        }
        List<CategoryChoice> pairs =
                catalogue.stream().flatMap(entry -> entry.choices().stream()).toList();
        ExpenseClassification[] ordered = new ExpenseClassification[expectedCount];
        for (ExpenseClassification classification : answer) {
            Integer position = classification.position();
            if (position == null || position < 0 || position >= expectedCount || ordered[position] != null) {
                throw new ClassificationFailedException("Classifier answered an unusable position");
            }
            Double confidence = classification.confidence();
            if (confidence == null || confidence.isNaN() || confidence < 0 || confidence > 1) {
                throw new ClassificationFailedException("Classifier answered an unusable confidence");
            }
            Map<CategoryChoice, Double> probabilities = classification.probabilities();
            if (classification.choice() == null
                    || probabilities == null
                    || !probabilities.keySet().containsAll(pairs)) {
                throw new ClassificationFailedException("Classifier left a catalogue category without a probability");
            }
            ordered[position] = classification;
        }
        return Arrays.asList(ordered);
    }

    private CategoryChoice chooseCategory(ExpenseClassification classification, ExtractIntentsCommand command) {
        double confidence = classification.confidence();
        turnMeters.recordConfidence(confidence);
        if (confidence >= minConfidence) {
            return classification.choice();
        }
        turnMeters.countLowConfidence();
        return catchAll(command).choices().stream()
                .max(Comparator.comparingDouble(
                        choice -> classification.probabilities().get(choice)))
                .orElseThrow();
    }

    private List<CategoryChoice> fallBack(ExtractIntentsCommand command, int count) {
        Optional<MessageIdentity> identity = command.messageIdentity();
        if (identity.isPresent()) {
            log.warn(
                    "Classifier failed for message {} of user {}; filing under the catch-all",
                    identity.get().incomingMessageId(),
                    identity.get().userId());
        } else {
            log.warn("Classifier failed; filing under the catch-all");
        }
        CategoryChoice choice = catchAll(command).choices().getFirst();
        return Collections.nCopies(count, choice);
    }

    private CatalogueEntry catchAll(ExtractIntentsCommand command) {
        return command.catalogue().stream()
                .filter(entry -> entry.grouping().equals(command.catchAllGrouping()))
                .findFirst()
                .orElseThrow();
    }

    private void fileExpense(int position, CategoryChoice choice, ExpenseToFile expense) {
        try {
            ledgerTurnPort.fileExpense(choice, expense);
        } catch (LedgerCallRefusedException e) {
            log.warn("Ledger refused expense {}", position);
            log.debug("Ledger refused expense {}: '{}'", position, expense.description());
        }
    }

    private void recordPeriod(ReadOutPeriod period) {
        try {
            ledgerTurnPort.recordPeriod(period.from(), period.to());
        } catch (LedgerCallRefusedException e) {
            log.warn("Ledger refused period {} to {}", period.from(), period.to());
        }
    }
}
