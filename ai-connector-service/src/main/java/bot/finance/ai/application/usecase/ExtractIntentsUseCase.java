package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.dto.ProposedExpense;
import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.ExpenseProposalPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryIntent;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExpenseIntent;
import bot.finance.ai.domain.value.Intent;
import bot.finance.ai.domain.value.IntentTarget;
import bot.finance.ai.domain.value.Money;
import bot.finance.ai.domain.value.Operation;
import bot.finance.ai.domain.value.UnknownIntent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExtractIntentsUseCase implements ExtractIntentsPort {

    private final IntentInferencePort intentInferencePort;
    private final ExpenseProposalPort expenseProposalPort;
    private final Logger log;

    public ExtractIntentsUseCase(
            IntentInferencePort intentInferencePort, ExpenseProposalPort expenseProposalPort,
            LoggerFactory loggerFactory) {
        this.intentInferencePort = intentInferencePort;
        this.expenseProposalPort = expenseProposalPort;
        this.log = loggerFactory.getLogger(ExtractIntentsUseCase.class);
    }

    @Override
    public void extractIntents(ExtractIntentsCommand command) {
        if (command == null) {
            throw new InvalidValueException("Command must not be null");
        }

        List<String> knownCategoryLabels =
                command.knownCategories().stream().map(KnownCategory::label).toList();
        List<RawIntent> rawIntents = intentInferencePort.infer(command.text(), knownCategoryLabels);

        List<Intent> intents;
        if (rawIntents == null || rawIntents.isEmpty()) {
            intents = List.of(new UnknownIntent("No usable intents were extracted from the message"));
        } else {
            List<CategoryOption> availableCategories = availableCategories(rawIntents, command.knownCategories());
            intents = new ArrayList<>(rawIntents.size());
            for (RawIntent raw : rawIntents) {
                intents.add(assemble(raw, command, availableCategories));
            }
        }

        for (Intent intent : intents) {
            if (intent instanceof ExpenseIntent expenseIntent && expenseIntent.operation() == Operation.CREATE) {
                expenseProposalPort.propose(toProposedExpense(expenseIntent));
            } else {
                logSkipped(intent);
            }
        }
    }

    private void logSkipped(Intent intent) {
        switch (intent) {
            case CategoryIntent category -> log.info(
                    "Skipping intent: target={} operation={} name={}",
                    IntentTarget.CATEGORY, category.operation(), category.name());
            case ExpenseIntent expense -> log.info(
                    "Skipping intent: target={} operation={}", IntentTarget.EXPENSE, expense.operation());
            case UnknownIntent unknown -> log.info("Skipping intent: reason={}", unknown.reason());
        }
    }

    /**
     * Reads the three fields unconditionally: {@code ExpenseIntent}'s compact constructor already rejects a
     * CREATE missing any of them, and only a CREATE reaches here.
     */
    private ProposedExpense toProposedExpense(ExpenseIntent expenseIntent) {
        return new ProposedExpense(
                expenseIntent.categoryName().orElseThrow(),
                expenseIntent.parentCategoryName(),
                expenseIntent.description().orElseThrow(),
                expenseIntent.amount().orElseThrow());
    }

    private List<CategoryOption> availableCategories(List<RawIntent> rawIntents, List<KnownCategory> knownCategories) {
        List<CategoryOption> categories = knownCategories.stream()
                .map(known -> new CategoryOption(known.name(), Optional.of(known.parentName())))
                .collect(Collectors.toCollection(ArrayList::new));
        for (RawIntent raw : rawIntents) {
            usableCategoryName(raw)
                    .filter(name -> categories.stream().noneMatch(category -> category.name().equalsIgnoreCase(name)))
                    .ifPresent(name -> categories.add(new CategoryOption(name, Optional.empty())));
        }
        return categories;
    }

    private Optional<String> usableCategoryName(RawIntent raw) {
        if (raw == null || raw.categoryName() == null || raw.categoryName().isBlank()) {
            return Optional.empty();
        }

        boolean isCategoryTarget = IntentTarget.fromLabel(raw.target())
                .filter(target -> target == IntentTarget.CATEGORY)
                .isPresent();

        if (!isCategoryTarget || Operation.fromLabel(raw.operation()).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(raw.categoryName());
    }

    private Intent assemble(RawIntent raw, ExtractIntentsCommand command, List<CategoryOption> availableCategories) {
        try {
            if (raw == null) {
                throw new InvalidValueException("Raw intent must not be null");
            }

            IntentTarget target = IntentTarget.fromLabel(raw.target())
                    .orElseThrow(() -> new InvalidValueException("Unrecognized target: " + raw.target()));
            Operation operation = Operation.fromLabel(raw.operation())
                    .orElseThrow(() -> new InvalidValueException("Unrecognized operation: " + raw.operation()));

            return switch (target) {
                case CATEGORY -> new CategoryIntent(operation, raw.categoryName(), Optional.ofNullable(raw.newCategoryName()));
                case EXPENSE -> buildExpenseIntent(raw, operation, command, availableCategories);
            };
        } catch (InvalidValueException e) {
            return new UnknownIntent(e.getMessage());
        }
    }

    private ExpenseIntent buildExpenseIntent(
            RawIntent raw, Operation operation, ExtractIntentsCommand command, List<CategoryOption> availableCategories) {
        Optional<CategoryOption> category = matchCategory(raw.categoryName(), availableCategories);
        Optional<Money> amount = resolveAmount(raw, command);
        Optional<String> description = Optional.ofNullable(raw.description());
        return new ExpenseIntent(
                operation,
                category.map(CategoryOption::name),
                amount,
                description,
                category.flatMap(CategoryOption::parentName));
    }

    private Optional<CategoryOption> matchCategory(String rawCategoryName, List<CategoryOption> availableCategories) {
        if (rawCategoryName == null || rawCategoryName.isBlank()) {
            return Optional.empty();
        }

        Optional<CategoryOption> byLabel = availableCategories.stream()
                .filter(category -> category.label().map(rawCategoryName::equalsIgnoreCase).orElse(false))
                .findFirst();
        if (byLabel.isPresent()) {
            return byLabel;
        }

        List<CategoryOption> byName = availableCategories.stream()
                .filter(category -> category.name().equalsIgnoreCase(rawCategoryName))
                .toList();
        if (byName.size() == 1) {
            return Optional.of(byName.get(0));
        }
        if (byName.isEmpty()) {
            throw new InvalidValueException("Unrecognized category: " + rawCategoryName);
        }

        String matches = byName.stream().map(CategoryOption::describe).collect(Collectors.joining(", "));
        throw new InvalidValueException("Ambiguous category name: " + rawCategoryName + " (matches " + matches + ")");
    }

    private Optional<Money> resolveAmount(RawIntent raw, ExtractIntentsCommand command) {
        if (raw.amount() == null || raw.amount().isBlank()) {
            return Optional.empty();
        }
        String currencyCode = raw.currency() != null && !raw.currency().isBlank()
                ? raw.currency()
                : command.defaultCurrency()
                        .map(CurrencyCode::code)
                        .orElseThrow(() -> new InvalidValueException(
                                "No currency specified and no default currency configured"));
        return Optional.of(Money.of(raw.amount(), currencyCode));
    }

    /**
     * A category available for matching: a known category with its grouping, or a category the same message
     * asks to create, which carries no grouping (D28).
     */
    private record CategoryOption(String name, Optional<String> parentName) {

        private Optional<String> label() {
            return parentName.map(parent -> KnownCategory.label(parent, name));
        }

        private String describe() {
            return label().orElse(name);
        }
    }

}
