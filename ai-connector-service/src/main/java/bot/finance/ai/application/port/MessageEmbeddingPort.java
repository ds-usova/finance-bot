package bot.finance.ai.application.port;

import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.value.Embedding;
import java.util.List;

public interface MessageEmbeddingPort {

    /**
     * @throws MessageEmbeddingFailedException if the provider refuses, answers unreadably, or does not answer
     *                                          inside its own timeout
     */
    Embedding embed(String text);

    /**
     * @throws MessageEmbeddingFailedException if the provider refuses, answers unreadably, or does not answer
     *                                          inside its own timeout
     */
    List<Embedding> embedAll(List<String> texts);
}
