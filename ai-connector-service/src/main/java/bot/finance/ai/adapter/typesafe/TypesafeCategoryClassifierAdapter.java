package bot.finance.ai.adapter.typesafe;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.application.port.CategoryClassifierPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "extraction.mode", havingValue = "classifier")
public class TypesafeCategoryClassifierAdapter implements CategoryClassifierPort {

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
        // stub-intent: posts one request built by SystemOneRequestMapper to /v1/systemone with the model and the
        // key, reads the answer back through the same mapper, and throws ClassificationFailedException on a 4xx
        // or 5xx answer, an answer later than the timeout, or an unparseable body
        return List.of();
    }
}
