package bot.finance.ai.adapter.ai;

import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ExampleSectionRenderer {

    public String render(Optional<List<MessageExample>> examples) {
        // the empty string where no retrieval ran, the section's heading followed by "none" where one found
        // nothing, and otherwise one block per example: the message quoted, then a line per decided expense —
        // description, amount, currency, category, grouping and accepted/discarded
        return "";
    }
}
