package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;

public interface CategoryClassifierPort {

    /** @throws ClassificationFailedException if the classifier fails, times out or names an unknown option */
    List<ExpenseClassification> classify(
            String text, List<MessageExample> examples, List<ExpenseToFile> expenses, List<CatalogueEntry> catalogue);
}
