package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
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

    private MessageMemoryPort messageMemoryPort;
    private MessageEmbedder messageEmbedder;
    private Logger log;
    private BackfillEmbeddingsUseCase useCase;

    @BeforeEach
    void setUp() {
        messageMemoryPort = mock(MessageMemoryPort.class);
        messageEmbedder = mock(MessageEmbedder.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        when(messageEmbedder.embeddingAttempts()).thenReturn(EMBEDDING_ATTEMPTS);
        useCase = new BackfillEmbeddingsUseCase(
                messageMemoryPort, messageEmbedder, BATCH, BATCHES, STALE_CLAIM, loggerFactory);
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    @Nested
    @DisplayName("constructing a BackfillEmbeddingsUseCase")
    class Constructor {

        static Stream<Arguments> invalidBounds() {
            return Stream.of(
                    Arguments.of(0, BATCHES, STALE_CLAIM),
                    Arguments.of(-1, BATCHES, STALE_CLAIM),
                    Arguments.of(BATCH, 0, STALE_CLAIM),
                    Arguments.of(BATCH, -1, STALE_CLAIM),
                    Arguments.of(BATCH, BATCHES, Duration.ZERO),
                    Arguments.of(BATCH, BATCHES, Duration.ofMinutes(-1)));
        }

        @ParameterizedTest
        @MethodSource("invalidBounds")
        @DisplayName("when batch, batches or staleClaim is non-positive - then throws InvalidValueException")
        void whenBatchOrBatchesOrStaleClaimIsNonPositive_thenThrowsInvalidValueException(
                int batch, int batches, Duration staleClaim) {
            LoggerFactory loggerFactory = mock(LoggerFactory.class);
            when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));

            assertThatThrownBy(() -> new BackfillEmbeddingsUseCase(
                            messageMemoryPort, mock(MessageEmbedder.class), batch, batches, staleClaim, loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("backfilling embeddings")
    class Backfill {

        @Test
        @DisplayName("when a claim answers fewer rows than the batch - then the tick ends after the embedder " + "runs")
        void whenClaimAnswersFewerThanBatch_thenTickEndsAfterEmbedderRuns() {
            List<UnembeddedMessage> partial = List.of(ROW_1);
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(partial);
            when(messageEmbedder.ensureEmbedded(partial)).thenReturn(true);

            useCase.backfill();

            verify(messageEmbedder).ensureEmbedded(eq(partial));
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }

        @Test
        @DisplayName(
                "when a claim answers nothing - then the embedder is never touched and no second claim " + "is made")
        void whenClaimAnswersNothing_thenEmbedderUntouchedAndNoSecondClaim() {
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(List.of());

            useCase.backfill();

            verify(messageEmbedder, never()).ensureEmbedded(anyList());
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }

        @Test
        @DisplayName("when every claim answers a full batch - then exactly the configured number of batches " + "run")
        void whenEveryClaimAnswersFullBatch_thenExactlyConfiguredBatchesClaimedAndEmbedded() {
            List<UnembeddedMessage> full = List.of(ROW_1, ROW_2);
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(full);
            when(messageEmbedder.ensureEmbedded(full)).thenReturn(true);

            useCase.backfill();

            verify(messageMemoryPort, times(BATCHES))
                    .claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
            verify(messageEmbedder, times(BATCHES)).ensureEmbedded(eq(full));
        }

        @Test
        @DisplayName("when the embedder answers false - then the tick ends after one claim")
        void whenEmbedderAnswersFalse_thenTickEndsAfterOneClaim() {
            List<UnembeddedMessage> full = List.of(ROW_1, ROW_2);
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenReturn(full);
            when(messageEmbedder.ensureEmbedded(full)).thenReturn(false);

            useCase.backfill();

            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }

        @Test
        @DisplayName("when claimUnembedded() throws MessageStoreFailedException - then one WARN logs and "
                + "nothing propagates")
        void whenClaimUnembeddedThrows_thenOneWarnLogsEmbedderUntouchedNoSecondClaimNothingPropagates() {
            when(messageMemoryPort.claimUnembedded(BATCH, EMBEDDING_ATTEMPTS, STALE_CLAIM))
                    .thenThrow(new MessageStoreFailedException("failed"));

            assertThatCode(() -> useCase.backfill()).doesNotThrowAnyException();

            assertThat(loggedWarnLines()).hasSize(1);
            verify(messageEmbedder, never()).ensureEmbedded(anyList());
            verify(messageMemoryPort, times(1)).claimUnembedded(eq(BATCH), eq(EMBEDDING_ATTEMPTS), eq(STALE_CLAIM));
        }
    }
}
