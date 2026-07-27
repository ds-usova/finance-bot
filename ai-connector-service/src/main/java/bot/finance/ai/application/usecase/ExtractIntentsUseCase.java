package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.IntentExtractionCommand;
import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.IntentInferencePort;
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

    public ExtractIntentsUseCase(IntentInferencePort intentInferencePort) {
        this.intentInferencePort = intentInferencePort;
    }

    @Override
    public List<Intent> extractIntents(IntentExtractionCommand command) {
        if (command == null) {
            throw new InvalidValueException("Command must not be null");
        }

        List<RawIntent> rawIntents = intentInferencePort.infer(command.text(), command.knownCategories());
        if (rawIntents == null || rawIntents.isEmpty()) {
            return List.of(new UnknownIntent("The provider returned no intents"));
        }

        List<String> availableCategories = availableCategories(rawIntents, command.knownCategories());
        List<Intent> intents = new ArrayList<>(rawIntents.size());
        for (RawIntent raw : rawIntents) {
            intents.add(assemble(raw, command, availableCategories));
        }

        return intents;
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

    private Intent assemble(RawIntent raw, IntentExtractionCommand command, List<String> availableCategories) {
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
            RawIntent raw, Operation operation, IntentExtractionCommand command, List<String> availableCategories) {
        Optional<String> categoryName = matchCategory(raw.categoryName(), availableCategories);
        Optional<Money> amount = resolveAmount(raw, command);
        Optional<String> description = Optional.ofNullable(raw.description());
        return new ExpenseIntent(operation, categoryName, amount, description);
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

    private Optional<Money> resolveAmount(RawIntent raw, IntentExtractionCommand command) {
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
