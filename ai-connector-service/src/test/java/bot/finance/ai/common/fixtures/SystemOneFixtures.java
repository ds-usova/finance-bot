package bot.finance.ai.common.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/** Builders for the answer body of the classifier's {@code POST /v1/systemone}. */
public final class SystemOneFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SystemOneFixtures() {}

    /** One question's answer: the option key chosen, how confident, and the probability of every option. */
    public record QuestionAnswer(
            String question, String choice, double confidence, Map<String, Double> probabilities) {}

    public static String answer(QuestionAnswer... questions) {
        return answer(List.of(questions));
    }

    public static String answer(List<QuestionAnswer> questions) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", "jev-1.13.0");
        ObjectNode answered = root.putObject("answers");
        for (QuestionAnswer question : questions) {
            ObjectNode node = answered.putObject(question.question());
            node.put("type", "choice");
            node.put("choice", question.choice());
            node.put("confidence", question.confidence());
            ObjectNode probabilities = node.putObject("probabilities");
            question.probabilities().forEach(probabilities::put);
        }
        root.putObject("usage");
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to render the classifier answer", e);
        }
    }
}
