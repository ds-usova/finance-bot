package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BackfillEmbeddingsUseCaseTest {

    private static final int BATCH = 2;
    private static final int BATCHES = 3;
    private static final int EMBEDDING_ATTEMPTS = 3;
    private static final Duration STALE_CLAIM = Duration.ofMinutes(5);

    private static final UnembeddedMessage ROW_1 = new UnembeddedMessage(101L, "buy milk");
    private static final UnembeddedMessage ROW_2 = new UnembeddedMessage(102L, "get coffee");
    private static final UnembeddedMessage ROW_3 = new UnembeddedMessage(103L, "pay rent");

    private MessageMemoryPort messageMemoryPort;
    private MessageEmbeddingPort messageEmbeddingPort;
    private Logger log;
    private BackfillEmbeddingsUseCase useCase;

    @BeforeEach
    void setUp() {
        messageMemoryPort = mock(MessageMemoryPort.class);
        messageEmbeddingPort = mock(MessageEmbeddingPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        useCase = new BackfillEmbeddingsUseCase(
                messageMemoryPort, messageEmbeddingPort, BATCH, BATCHES, EMBEDDING_ATTEMPTS, STALE_CLAIM, loggerFactory);
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    private List<String> loggedErrorLines() {
        return MockedLoggerUtils.linesAt(log, "error");
    }

    @Nested
    @DisplayName("constructing a BackfillEmbeddingsUseCase")
    class Constructor {

        static Stream<Arguments> invalidBounds() {
            return Stream.of(
                    Arguments.of(0, BATCHES, EMBEDDING_ATTEMPTS, STALE_CLAIM),
                    Arguments.of(-1, BATCHES, EMBEDDING_ATTEMPTS, STALE_CLAIM),
                    Arguments.of(BATCH, 0, EMBEDDING_ATTEMPTS, STALE_CLAIM),
                    Arguments.of(BATCH, -1, EMBEDDING_ATTEMPTS, STALE_CLAIM),
                    Arguments.of(BATCH, BATCHES, 0, STALE_CLAIM),
                    Arguments.of(BATCH, BATCHES, -1, STALE_CLAIM),
                    Arguments.of(BATCH, BATCHES, EMBEDDING_ATTEMPTS, Duration.ZERO),
                    Arguments.of(BATCH, BATCHES, EMBEDDING_ATTEMPTS, Duration.ofMinutes(-1)));
        }

        @ParameterizedTest
        @MethodSource("invalidBounds")
        @DisplayName("when batch, batches, embeddingAttempts or staleClaim is non-positive - then throws "
                + "InvalidValueException")
        void whenBatchOrBatchesOrEmbeddingAttemptsOrStaleClaimIsNonPositive_thenThrowsInvalidValueException(
                int batch, int batches, int embeddingAttempts, Duration staleClaim) {
            LoggerFactory loggerFactory = mock(LoggerFactory.class);
            when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));

            assertThatThrownBy(() -> new BackfillEmbeddingsUseCase(
                            messageMemoryPort,
                            messageEmbeddingPort,
                            batch,
                            batches,
                            embeddingAttempts,
                            staleClaim,
                            loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("backfilling embeddings")
    class Backfill {

        @Test
        @DisplayName("when a batch of three rows is claimed and embedded - then each row's vector is stored")
        void whenOneBatchClaimedAndPortAnswersVectors_thenClaimEmbedAllAndStoreEmbeddingReceiveExpectedArgs() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2, ROW_3);
            Embedding v1 = new Embedding(List.of(0.1f));
            Embedding v2 = new Embedding(List.of(0.2f));
            Embedding v3 = new Embedding(List.of(0.3f));
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(claim, List.of());
            when(messageEmbeddingPort.embedAll(List.of(ROW_1.text(), ROW_2.text(), ROW_3.text())))
                    .thenReturn(List.of(v1, v2, v3));

            useCase.backfill();

            verify(messageMemoryPort).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
            verify(messageEmbeddingPort).embedAll(eq(List.of(ROW_1.text(), ROW_2.text(), ROW_3.text())));
            verify(messageMemoryPort).storeEmbedding(eq(ROW_1.messageId()), eq(v1));
            verify(messageMemoryPort).storeEmbedding(eq(ROW_2.messageId()), eq(v2));
            verify(messageMemoryPort).storeEmbedding(eq(ROW_3.messageId()), eq(v3));
        }

        @Test
        @DisplayName("when a claim answers nothing - then the embedding port is never touched and no second "
                + "claim is made")
        void whenClaimAnswersNothing_thenEmbeddingPortUntouchedAndNoSecondClaim() {
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(List.of());

            useCase.backfill();

            verifyNoInteractions(messageEmbeddingPort);
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }

        @Test
        @DisplayName("when every claim answers a full batch - then exactly the configured number of batches "
                + "run")
        void whenEveryClaimAnswersFullBatch_thenExactlyConfiguredBatchesClaimedAndEmbedded() {
            List<UnembeddedMessage> full = List.of(ROW_1, ROW_2);
            Embedding v = new Embedding(List.of(0.1f));
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(full);
            when(messageEmbeddingPort.embedAll(any())).thenReturn(List.of(v, v));

            useCase.backfill();

            verify(messageMemoryPort, times(BATCHES)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
            verify(messageEmbeddingPort, times(BATCHES)).embedAll(any());
        }

        @Test
        @DisplayName("when embedAll() fails on the first batch - then every claimed row is counted and one "
                + "WARN logs")
        void whenEmbedAllFailsOnFirstBatch_thenEveryRowCountedOneWarnNoSecondClaimNothingPropagates() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(claim);
            when(messageEmbeddingPort.embedAll(any())).thenThrow(new MessageEmbeddingFailedException("refused"));

            assertThatCode(() -> useCase.backfill()).doesNotThrowAnyException();

            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_1.messageId()));
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_2.messageId()));
            assertThat(loggedWarnLines()).hasSize(1);
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }

        @Test
        @DisplayName("when one row's attempt count reaches the bound - then exactly one ERROR names that row")
        void whenOneRowReachesAttemptBound_thenExactlyOneErrorNamesThatRowAndCarriesNoText() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(claim);
            when(messageEmbeddingPort.embedAll(any())).thenThrow(new MessageEmbeddingFailedException("refused"));
            when(messageMemoryPort.countEmbeddingAttempt(ROW_1.messageId())).thenReturn(EMBEDDING_ATTEMPTS);
            when(messageMemoryPort.countEmbeddingAttempt(ROW_2.messageId())).thenReturn(EMBEDDING_ATTEMPTS - 1);

            useCase.backfill();

            assertThat(loggedErrorLines()).hasSize(1);
            assertThat(loggedErrorLines().get(0))
                    .contains(String.valueOf(ROW_1.messageId()))
                    .doesNotContain(ROW_1.text());
        }

        @Test
        @DisplayName("when the embedding port answers fewer vectors than the batch held - then nothing is "
                + "stored")
        void whenEmbeddingPortAnswersFewerVectorsThanBatch_thenNothingStoredEveryRowCountedOneWarnLogged() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            Embedding v1 = new Embedding(List.of(0.1f));
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(claim);
            when(messageEmbeddingPort.embedAll(any())).thenReturn(List.of(v1));

            useCase.backfill();

            verify(messageMemoryPort, never()).storeEmbedding(anyLong(), any());
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_1.messageId()));
            verify(messageMemoryPort).countEmbeddingAttempt(eq(ROW_2.messageId()));
            assertThat(loggedWarnLines()).hasSize(1);
        }

        @Test
        @DisplayName("when storeEmbedding() throws MessageStoreFailedException - then one WARN logs")
        void whenStoreEmbeddingThrows_thenOneWarnLogsNoSecondBatchAndNothingPropagates() {
            List<UnembeddedMessage> claim = List.of(ROW_1, ROW_2);
            Embedding v1 = new Embedding(List.of(0.1f));
            Embedding v2 = new Embedding(List.of(0.2f));
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(claim);
            when(messageEmbeddingPort.embedAll(any())).thenReturn(List.of(v1, v2));
            doThrow(new MessageStoreFailedException("failed"))
                    .when(messageMemoryPort)
                    .storeEmbedding(anyLong(), any());

            assertThatCode(() -> useCase.backfill()).doesNotThrowAnyException();

            assertThat(loggedWarnLines()).hasSize(1);
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }

        @Test
        @DisplayName("when claimUnembedded() throws MessageStoreFailedException - then one WARN logs and "
                + "nothing propagates")
        void whenClaimUnembeddedThrows_thenOneWarnLogsEmbeddingPortUntouchedNoSecondClaimNothingPropagates() {
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenThrow(new MessageStoreFailedException("failed"));

            assertThatCode(() -> useCase.backfill()).doesNotThrowAnyException();

            assertThat(loggedWarnLines()).hasSize(1);
            verifyNoInteractions(messageEmbeddingPort);
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }
    }
}
