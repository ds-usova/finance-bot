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

        // TODO: render command.knownCategories() as labels (KnownCategory.label()) for the prompt, so the model
        // sees "Insurance > Travel" beside "Travel".
        List<String> knownCategoryLabels =
                command.knownCategories().stream().map(KnownCategory::label).toList();
        List<RawIntent> rawIntents = intentInferencePort.infer(command.text(), knownCategoryLabels);
        if (rawIntents == null || rawIntents.isEmpty()) {
            // TODO: nothing usable was extracted; the turn still completes normally (D22).
            return;
        }

        List<String> availableCategories = availableCategories(rawIntents, knownCategoryLabels);
        List<Intent> intents = new ArrayList<>(rawIntents.size());
        for (RawIntent raw : rawIntents) {
            intents.add(assemble(raw, command, availableCategories));
        }

        // TODO: walk `intents` in order and call expenseProposalPort.propose for every ExpenseIntent whose
        // operation is CREATE, matching a raw category name against the closed set by label first, then by
        // bare name (D27), and log every other intent at info by target and operation (D22). Let
        // ExpenseProposalFailedException propagate on the first failure (D24).
    }

    private List<String> availableCategories(List<RawIntent> rawIntents, List<String> knownCategories) {
        List<String> categories = new ArrayList<>(knownCategories);
        for (RawIntent raw : rawIntents) {
            usableCategoryName(raw).ifPresent(categories::add);
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

    private Intent assemble(RawIntent raw, ExtractIntentsCommand command, List<String> availableCategories) {
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
            RawIntent raw, Operation operation, ExtractIntentsCommand command, List<String> availableCategories) {
        Optional<String> categoryName = matchCategory(raw.categoryName(), availableCategories);
        Optional<Money> amount = resolveAmount(raw, command);
        Optional<String> description = Optional.ofNullable(raw.description());
        // TODO: carry the matched category's parent name (D27); empty when the category was created by this
        // same message (D28). Passing Optional.empty() for now.
        return new ExpenseIntent(operation, categoryName, amount, description, Optional.empty());
    }

    private Optional<String> matchCategory(String rawCategoryName, List<String> availableCategories) {
        if (rawCategoryName == null || rawCategoryName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(availableCategories.stream()
                .filter(category -> category.equalsIgnoreCase(rawCategoryName))
                .findFirst()
                .orElseThrow(() -> new InvalidValueException("Unrecognized category: " + rawCategoryName)));
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

}
