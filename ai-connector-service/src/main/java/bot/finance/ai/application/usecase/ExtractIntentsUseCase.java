package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.IntentExtractionCommand;
import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.domain.value.Intent;

import java.util.List;

public class ExtractIntentsUseCase implements ExtractIntentsPort {

    private final IntentInferencePort intentInferencePort;

    public ExtractIntentsUseCase(IntentInferencePort intentInferencePort) {
        this.intentInferencePort = intentInferencePort;
    }

    @Override
    public List<Intent> extractIntents(IntentExtractionCommand command) {
        // rejects a null command with InvalidValueException before calling the port; otherwise calls
        // IntentInferencePort.infer() with the command's text and known categories, assembles each raw
        // answer independently inside its own try (via assemble()) so one bad or null entry becomes an
        // UnknownIntent in its position without discarding its neighbours, preserves the port's order
        // throughout, and returns a single UnknownIntent when the port returns null or an empty list —
        // the contract's response is never empty.
        // Assembles in two passes: the first collects the name of every raw answer that is a usable
        // category creation, the second assembles each entry against the command's categories plus those,
        // so a category the message creates is filable by every entry regardless of position — the user
        // may name the expense before the category it belongs to
        return null;
    }

    private Intent assemble(RawIntent raw, IntentExtractionCommand command, List<String> availableCategories) {
        // resolves raw.target()/raw.operation() via IntentTarget.fromLabel()/Operation.fromLabel(),
        // then builds a CategoryIntent or ExpenseIntent for the resolved target: for an expense, parses
        // amount/currency through Money.of(), falling back to command.defaultCurrency() when the raw
        // answer names no currency, and matches raw.categoryName() against availableCategories
        // case-insensitively, keeping the matched spelling in the result; throws
        // InvalidValueException — caught by extractIntents() and turned into an UnknownIntent whose
        // reason is the exception's message — when the target/operation is unrecognized, the category is
        // not among the available ones, or a value fails validation
        return null;
    }

}
