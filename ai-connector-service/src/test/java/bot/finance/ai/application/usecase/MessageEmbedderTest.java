package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.Embedding;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageEmbedderTest {

    private static final int EMBEDDING_ATTEMPTS = 3;
    private static final long MESSAGE_ID = 99L;
    private static final String TEXT = "spent 15 euros on lunch";

    private static final UnembeddedMessage ROW_1 = new UnembeddedMessage(101L, "buy milk");
    private static final UnembeddedMessage ROW_2 = new UnembeddedMessage(102L, "get coffee");

    private MessageMemoryPort messageMemoryPort;
    private MessageEmbeddingPort messageEmbeddingPort;
    private Logger log;
    private MessageEmbedder messageEmbedder;

    @BeforeEach
    void setUp() {
        messageMemoryPort = mock(MessageMemoryPort.class);
        messageEmbeddingPort = mock(MessageEmbeddingPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        messageEmbedder =
                new MessageEmbedder(messageMemoryPort, messageEmbeddingPort, EMBEDDING_ATTEMPTS, loggerFactory);
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    private List<String> loggedErrorLines() {
        return MockedLoggerUtils.linesAt(log, "error");
    }

    @Nested
    @DisplayName("constructing a MessageEmbedder")
    class Constructor {

        @Test
        @DisplayName("when embeddingAttempts is non-positive - then throws InvalidValueException")
        void whenEmbeddingAttemptsIsNonPositive_thenThrowsInvalidValueException() {
            LoggerFactory loggerFactory = mock(LoggerFactory.class);
            when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));

            assertThatThrownBy(() -> new MessageEmbedder(messageMemoryPort, messageEmbeddingPort, 0, loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("embedding and storing a message")
    class EnsureEmbeddedSingle {

        @Test
        @DisplayName("when the provider answers a vector - then it is stored and answered")
        void whenProviderAnswersVector_thenStoredAndAnswered() {
            Embedding computed = new Embedding(List.of(0.1f, 0.2f));
            when(messageEmbeddingPort.embed(TEXT)).thenReturn(computed);

            Optional<Embedding> result = messageEmbedder.ensureEmbedded(MESSAGE_ID, TEXT);

            assertThat(result).contains(computed);
            verify(messageMemoryPort).storeEmbedding(eq(MESSAGE_ID), eq(computed));
        }

        @Test
        @DisplayName("when the provider refuses and the attempt count stays below the bound - then one WARN "
                + "logs and no ERROR")
        void whenProviderRefusesAndAttemptCountBelowBound_thenOneWarnLoggedAndNoError() {
            when(messageEmbeddingPort.embed(TEXT)).thenThrow(new MessageEmbeddingFailedException("refused"));
            when(messageMemoryPort.countEmbeddingAttempt(MESSAGE_ID)).thenReturn(EMBEDDING_ATTEMPTS - 1);

            Optional<Embedding> result = messageEmbedder.ensureEmbedded(MESSAGE_ID, TEXT);

            assertThat(result).isEmpty();
            verify(messageMemoryPort).countEmbeddingAttempt(eq(MESSAGE_ID));
            assertThat(loggedWarnLines()).hasSize(1);
            assertThat(loggedErrorLines()).isEmpty();
        }

        @Test
        @DisplayName(
                "when the provider refuses and the attempt count reaches the bound - then one ERROR names " + "the row")
        void whenProviderRefusesAndAttemptCountReachesBound_thenOneErrorNamesRow() {
            when(messageEmbeddingPort.embed(TEXT)).thenThrow(new MessageEmbeddingFailedException("refused"));
            when(messageMemoryPort.countEmbeddingAttempt(MESSAGE_ID)).thenReturn(EMBEDDING_ATTEMPTS);

            Optional<Embedding> result = messageEmbedder.ensureEmbedded(MESSAGE_ID, TEXT);

            assertThat(result).isEmpty();
            assertThat(loggedErrorLines()).hasSize(1);
            assertThat(loggedErrorLines().get(0))
                    .contains(String.valueOf(MESSAGE_ID))
                    .doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when storeEmbedding() throws MessageStoreFailedException - then it propagates")
        void whenStoreEmbeddingThrows_thenItPropagates() {
            Embedding computed = new Embedding(List.of(0.1f, 0.2f));
            when(messageEmbeddingPort.embed(TEXT)).thenReturn(computed);
            doThrow(new MessageStoreFailedException("failed"))
                    .when(messageMemoryPort)
                    .storeEmbedding(eq(MESSAGE_ID), eq(computed));

            assertThatThrownBy(() -> messageEmbedder.ensureEmbedded(MESSAGE_ID, TEXT))
                    .isInstanceOf(MessageStoreFailedException.class);
        }
    }

    @Nested
    @DisplayName("embedding and storing a claimed batch")
    class EnsureEmbeddedBatch {

        @Test
        @DisplayName("when the provider answers a vector for every row - then each is stored and true is answered")
        void whenProviderAnswersVectorForEveryRow_thenEachStoredAndTrueAnswered() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            Embedding v1 = new Embedding(List.of(0.1f));
            Embedding v2 = new Embedding(List.of(0.2f));
            when(messageEmbeddingPort.embedAll(List.of(ROW_1.text(), ROW_2.text())))
                    .thenReturn(List.of(v1, v2));

            boolean result = messageEmbedder.ensureEmbedded(claim);

            assertThat(result).isTrue();
            verify(messageMemoryPort).storeEmbedding(eq(ROW_1.messageId()), eq(v1));
            verify(messageMemoryPort).storeEmbedding(eq(ROW_2.messageId()), eq(v2));
        }

        @Test
        @DisplayName("when the provider refuses - then every claimed row is counted, one WARN logs and false "
                + "is answered")
        void whenProviderRefuses_thenEveryRowCountedOneWarnLoggedAndFalseAnswered() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            when(messageEmbeddingPort.embedAll(any())).thenThrow(new MessageEmbeddingFailedException("refused"));

            boolean result = messageEmbedder.ensureEmbedded(claim);

            assertThat(result).isFalse();
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_1.messageId()));
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_2.messageId()));
            assertThat(loggedWarnLines()).hasSize(1);
        }

        @Test
        @DisplayName("when the provider answers fewer vectors than the batch held - then nothing is stored, "
                + "every row is counted")
        void whenProviderAnswersFewerVectorsThanBatch_thenNothingStoredEveryRowCountedOneWarnLogged() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            Embedding v1 = new Embedding(List.of(0.1f));
            when(messageEmbeddingPort.embedAll(any())).thenReturn(List.of(v1));

            boolean result = messageEmbedder.ensureEmbedded(claim);

            assertThat(result).isFalse();
            verify(messageMemoryPort, never()).storeEmbedding(anyLong(), any());
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_1.messageId()));
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_2.messageId()));
            assertThat(loggedWarnLines()).hasSize(1);
        }

        @Test
        @DisplayName("when a store write fails mid-batch - then rows already written stay written, one WARN "
                + "logs and false is answered")
        void whenStoreWriteFailsMidBatch_thenRowsAlreadyWrittenStayWrittenOneWarnLoggedAndFalseAnswered() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            Embedding v1 = new Embedding(List.of(0.1f));
            Embedding v2 = new Embedding(List.of(0.2f));
            when(messageEmbeddingPort.embedAll(any())).thenReturn(List.of(v1, v2));
            doThrow(new MessageStoreFailedException("failed"))
                    .when(messageMemoryPort)
                    .storeEmbedding(eq(ROW_2.messageId()), eq(v2));

            boolean result = messageEmbedder.ensureEmbedded(claim);

            assertThat(result).isFalse();
            verify(messageMemoryPort).storeEmbedding(eq(ROW_1.messageId()), eq(v1));
            assertThat(loggedWarnLines()).hasSize(1);
        }
    }
}
