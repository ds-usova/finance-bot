package bot.finance.ai.adapter.typesafe;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.MessageExample;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

final class SystemOneRequestMapper {

    private SystemOneRequestMapper() {}

    static Map<String, Object> toRequest(
            String model,
            String text,
            List<MessageExample> examples,
            List<ExpenseToFile> expenses,
            List<CatalogueEntry> catalogue) {
        // stub-intent: builds the classifier's body: the model, the state holding the message text and each
        // example's decided expenses without amounts, currencies or earlier text, and one choice question per
        // expense keyed by its position, offering one option per catalogue pair keyed "<grouping> / <category>"
        return Map.of();
    }

    static List<ExpenseClassification> fromAnswer(JsonNode answer, List<CatalogueEntry> catalogue) {
        // stub-intent: reads each question's choice, confidence and probabilities back, mapping each option key to
        // its pair through the catalogue the request was built from; an option no pair holds is a failure
        return List.of();
    }
}
