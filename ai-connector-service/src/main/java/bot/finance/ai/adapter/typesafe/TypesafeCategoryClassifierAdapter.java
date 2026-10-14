package bot.finance.ai.adapter.typesafe;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.application.port.CategoryClassifierPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.MessageExample;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "extraction.mode", havingValue = "classifier")
public class TypesafeCategoryClassifierAdapter implements CategoryClassifierPort {

    private static final ObjectMapper ANSWER_READER = new ObjectMapper();

    private final RestClient restClient;
    private final TypesafeClassifierProperties properties;
    private final Logger logger;

    public TypesafeCategoryClassifierAdapter(
            @Qualifier("classifierRestClient") RestClient restClient,
            TypesafeClassifierProperties properties,
            LoggerFactory loggerFactory) {
        this.restClient = restClient;
        this.properties = properties;
        this.logger = loggerFactory.getLogger(TypesafeCategoryClassifierAdapter.class);
    }

    @Override
    public List<ExpenseClassification> classify(
            String text, List<MessageExample> examples, List<ExpenseToFile> expenses, List<CatalogueEntry> catalogue) {
        Map<String, Object> request =
                SystemOneRequestMapper.toRequest(properties.model(), text, examples, expenses, catalogue);
        JsonNode answer;
        try {
            String body = restClient
                    .post()
                    .uri("/v1/systemone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            answer = body == null ? null : ANSWER_READER.readTree(body);
        } catch (RestClientResponseException e) {
            logger.warn("The classifier answered status {}", e.getStatusCode().value());
            throw new ClassificationFailedException(
                    "The classifier answered status " + e.getStatusCode().value(), e);
        } catch (RuntimeException | JsonProcessingException e) {
            throw new ClassificationFailedException("The classifier could not be reached or understood", e);
        }
        if (answer == null) {
            throw new ClassificationFailedException("The classifier answered with an empty body");
        }
        return SystemOneRequestMapper.fromAnswer(answer, catalogue);
    }
}
