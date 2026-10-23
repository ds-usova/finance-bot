package bot.finance.ai.adapter.typesafe;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.MessageExample;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SystemOneRequestMapper {

    private static final String OPTION_SEPARATOR = " / ";

    private SystemOneRequestMapper() {}

    static Map<String, Object> toRequest(
            String model,
            String text,
            List<MessageExample> examples,
            List<ExpenseToFile> expenses,
            List<CatalogueEntry> catalogue) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("state", state(text, examples));
        request.put("questions", questions(expenses, catalogue));
        return request;
    }

    static List<ExpenseClassification> fromAnswer(JsonNode answer, List<CatalogueEntry> catalogue) {
        Map<String, CategoryChoice> choicesByOption = choicesByOption(catalogue);
        List<ExpenseClassification> classifications = new ArrayList<>();
        answer.path("answers").fields().forEachRemaining(entry -> {
            JsonNode question = entry.getValue();
            Map<CategoryChoice, Double> probabilities = new LinkedHashMap<>();
            question.path("probabilities")
                    .fields()
                    .forEachRemaining(probability -> probabilities.put(
                            choiceOf(choicesByOption, probability.getKey()),
                            number(probability.getValue(), "probability")));
            classifications.add(new ExpenseClassification(
                    position(entry.getKey()),
                    choiceOf(choicesByOption, question.path("choice").asText()),
                    number(question.path("confidence"), "confidence"),
                    probabilities));
        });
        return classifications;
    }

    private static Map<String, Object> state(String text, List<MessageExample> examples) {
        List<Map<String, Object>> decided = new ArrayList<>();
        for (MessageExample example : examples) {
            for (ExampleExpense expense : example.expenses()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("description", expense.description());
                expense.merchant().ifPresent(merchant -> item.put("merchant", merchant));
                expense.groupingName().ifPresent(grouping -> item.put("grouping", grouping));
                expense.categoryName().ifPresent(category -> item.put("category", category));
                item.put("outcome", expense.outcome().name().toLowerCase());
                decided.add(item);
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", text);
        state.put("examples", decided);
        return state;
    }

    private static Map<String, Object> questions(List<ExpenseToFile> expenses, List<CatalogueEntry> catalogue) {
        Map<String, String> criteria = new LinkedHashMap<>();
        choicesByOption(catalogue).forEach((option, choice) -> criteria.put(option, choice.grouping()));
        Map<String, Object> questions = new LinkedHashMap<>();
        for (int position = 0; position < expenses.size(); position++) {
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("type", "choice");
            question.put("instructions", instructions(expenses.get(position)));
            question.put("criteria", criteria);
            questions.put(String.valueOf(position), question);
        }
        return questions;
    }

    private static String instructions(ExpenseToFile expense) {
        String subject = "Pick the category for the expense \"" + expense.description() + "\"";
        return expense.merchant()
                .map(merchant -> subject + " paid to \"" + merchant + "\".")
                .orElse(subject + ".");
    }

    private static Map<String, CategoryChoice> choicesByOption(List<CatalogueEntry> catalogue) {
        Map<String, CategoryChoice> choices = new LinkedHashMap<>();
        for (CatalogueEntry entry : catalogue) {
            for (CategoryChoice choice : entry.choices()) {
                choices.put(choice.grouping() + OPTION_SEPARATOR + choice.category(), choice);
            }
        }
        return choices;
    }

    private static CategoryChoice choiceOf(Map<String, CategoryChoice> choicesByOption, String option) {
        CategoryChoice choice = choicesByOption.get(option);
        if (choice == null) {
            throw new ClassificationFailedException("The classifier answered with an unknown option: " + option);
        }
        return choice;
    }

    private static double number(JsonNode value, String field) {
        if (!value.isNumber()) {
            throw new ClassificationFailedException("The classifier answered without a numeric " + field);
        }
        return value.asDouble();
    }

    private static int position(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            throw new ClassificationFailedException("The classifier answered with an unknown question: " + key, e);
        }
    }
}
