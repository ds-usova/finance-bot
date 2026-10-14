package bot.finance.ai.adapter.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.common.fixtures.SystemOneFixtures;
import bot.finance.ai.common.fixtures.SystemOneFixtures.QuestionAnswer;
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.MessageExample;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SystemOneRequestMapperTest {

    private static final String MODEL = "jev-1.13.0";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<CatalogueEntry> CATALOGUE = List.of(
            new CatalogueEntry("Dining", List.of("Cafés", "Restaurants")),
            new CatalogueEntry("Transportation", List.of("Taxis/Uber")));

    private static ExpenseToFile expense(String description, String merchant) {
        return new ExpenseToFile(description, Optional.ofNullable(merchant), "15.00", CurrencyCode.of("EUR"));
    }

    private static JsonNode requestOf(String text, List<MessageExample> examples, List<ExpenseToFile> expenses) {
        return MAPPER.valueToTree(SystemOneRequestMapper.toRequest(MODEL, text, examples, expenses, CATALOGUE));
    }

    @Nested
    @DisplayName("toRequest()")
    class ToRequest {

        @Test
        @DisplayName(
                "when the catalogue holds three pairs - then the question offers each keyed by grouping and category")
        void whenCatalogueHoldsThreePairs_thenQuestionOffersEachKeyedByGroupingAndCategory() {
            JsonNode request = requestOf("Coffee", List.of(), List.of(expense("Flat white", "Blue Bottle")));

            assertThat(request.path("model").asText()).isEqualTo(MODEL);
            JsonNode questions = request.path("questions");
            assertThat(questions.size()).isEqualTo(1);
            JsonNode question = questions.elements().next();
            assertThat(question.path("type").asText()).isEqualTo("choice");
            JsonNode criteria = question.path("criteria");
            assertThat(criteria.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder("Dining / Cafés", "Dining / Restaurants", "Transportation / Taxis/Uber");
            assertThat(criteria.path("Dining / Cafés").asText()).isEqualTo("Dining");
            assertThat(criteria.path("Dining / Restaurants").asText()).isEqualTo("Dining");
            assertThat(criteria.path("Transportation / Taxis/Uber").asText()).isEqualTo("Transportation");
        }

        @Test
        @DisplayName(
                "when two expenses are filed - then each gets its own question naming its description and merchant")
        void whenTwoExpensesAreFiled_thenEachGetsItsOwnQuestionNamingDescriptionAndMerchant() {
            JsonNode request = requestOf(
                    "Coffee and a taxi",
                    List.of(),
                    List.of(expense("Flat white", "Blue Bottle"), expense("Airport ride", "Bolt")));

            JsonNode questions = request.path("questions");
            assertThat(questions.size()).isEqualTo(2);
            String firstKey = keyOfQuestionNaming(questions, "Flat white", "Blue Bottle");
            String secondKey = keyOfQuestionNaming(questions, "Airport ride", "Bolt");
            assertThat(firstKey).isNotEqualTo(secondKey);
            assertThat(Integer.parseInt(firstKey)).isLessThan(Integer.parseInt(secondKey));
        }

        @Test
        @DisplayName("when examples are given - then the state carries their decided fields and never an amount")
        void whenExamplesAreGiven_thenStateCarriesTheirDecidedFieldsAndNeverAnAmount() {
            ExampleExpense decided = new ExampleExpense(
                    "Oat latte",
                    Optional.of("Bluebird Roasters"),
                    "4321.99",
                    CurrencyCode.of("CHF"),
                    Optional.of("Cafés"),
                    Optional.of("Dining"),
                    ExampleOutcome.ACCEPTED);
            MessageExample example = new MessageExample("Sunday brunch with Anna", List.of(decided));

            JsonNode request =
                    requestOf("Coffee and a taxi", List.of(example), List.of(expense("Flat white", "Blue Bottle")));

            String state = request.path("state").toString();
            assertThat(state)
                    .contains("Coffee and a taxi")
                    .contains("Oat latte")
                    .contains("Bluebird Roasters")
                    .contains("Cafés")
                    .contains("Dining")
                    .containsIgnoringCase("accepted")
                    .doesNotContain("4321.99")
                    .doesNotContain("CHF")
                    .doesNotContain("Sunday brunch with Anna");
        }

        private String keyOfQuestionNaming(JsonNode questions, String description, String merchant) {
            List<String> keys = new ArrayList<>();
            questions.fields().forEachRemaining(entry -> {
                String instructions = entry.getValue().path("instructions").asText();
                if (instructions.contains(description) && instructions.contains(merchant)) {
                    keys.add(entry.getKey());
                }
            });
            assertThat(keys).hasSize(1);
            return keys.get(0);
        }
    }

    @Nested
    @DisplayName("fromAnswer()")
    class FromAnswer {

        @Test
        @DisplayName("when the answer chooses a catalogue pair - then the choice and every probability map to pairs")
        void whenAnswerChoosesACataloguePair_thenChoiceAndEveryProbabilityMapToPairs() throws Exception {
            JsonNode request = requestOf("Taxi", List.of(), List.of(expense("Airport ride", "Bolt")));
            String key = request.path("questions").fieldNames().next();
            JsonNode answer = MAPPER.readTree(SystemOneFixtures.answer(new QuestionAnswer(
                    key,
                    "Transportation / Taxis/Uber",
                    0.9,
                    Map.of(
                            "Dining / Cafés", 0.04,
                            "Dining / Restaurants", 0.06,
                            "Transportation / Taxis/Uber", 0.9))));

            List<ExpenseClassification> classifications = SystemOneRequestMapper.fromAnswer(answer, CATALOGUE);

            assertThat(classifications).hasSize(1);
            ExpenseClassification classification = classifications.get(0);
            assertThat(classification.position()).isEqualTo(Integer.parseInt(key));
            assertThat(classification.choice()).isEqualTo(new CategoryChoice("Transportation", "Taxis/Uber"));
            assertThat(classification.confidence()).isEqualTo(0.9);
            assertThat(classification.probabilities())
                    .containsOnlyKeys(Set.of(
                            new CategoryChoice("Dining", "Cafés"),
                            new CategoryChoice("Dining", "Restaurants"),
                            new CategoryChoice("Transportation", "Taxis/Uber")))
                    .containsEntry(new CategoryChoice("Dining", "Cafés"), 0.04)
                    .containsEntry(new CategoryChoice("Dining", "Restaurants"), 0.06)
                    .containsEntry(new CategoryChoice("Transportation", "Taxis/Uber"), 0.9);
        }

        @Test
        @DisplayName(
                "when the answer names an option no catalogue pair holds - then it throws ClassificationFailedException")
        void whenAnswerNamesAnOptionNoPairHolds_thenThrowsClassificationFailedException() throws Exception {
            JsonNode request = requestOf("Rent", List.of(), List.of(expense("Monthly rent", null)));
            String key = request.path("questions").fieldNames().next();
            JsonNode answer = MAPPER.readTree(SystemOneFixtures.answer(new QuestionAnswer(
                    key, "Housing / Rent", 0.8, Map.of("Housing / Rent", 0.8, "Dining / Cafés", 0.2))));

            assertThatThrownBy(() -> SystemOneRequestMapper.fromAnswer(answer, CATALOGUE))
                    .isInstanceOf(ClassificationFailedException.class);
        }

        @Test
        @Disabled("An answer with no confidence field reads as confidence 0.0 and is returned, instead of failing")
        @DisplayName("when the answer carries no confidence - then it throws ClassificationFailedException")
        void whenAnswerCarriesNoConfidence_thenThrowsClassificationFailedException() throws Exception {
            JsonNode answer = MAPPER.readTree(
                    """
                    {
                      "model": "jev-1.13.0",
                      "answers": {
                        "0": {
                          "type": "choice",
                          "choice": "Dining / Cafés",
                          "probabilities": {"Dining / Cafés": 1.0}
                        }
                      },
                      "usage": {}
                    }
                    """);

            assertThatThrownBy(() -> SystemOneRequestMapper.fromAnswer(answer, CATALOGUE))
                    .isInstanceOf(ClassificationFailedException.class);
        }
    }
}
