package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RecallExamplesCommand;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RecallExamplesUseCaseTest {

    private static final int EXAMPLES = 3;
    private static final double MIN_SIMILARITY = 0.6;
    private static final Duration RECENT_WINDOW = Duration.ofDays(30);
    private static final Duration MAX_AGE = Duration.ofDays(365);
    private static final int EXAMPLE_LINES = 10;
    private static final int EMBEDDING_ATTEMPTS = 3;

    private static final String TEXT = "spent 15 euros on lunch";
    private static final MessageIdentity IDENTITY = new MessageIdentity(7L, "msg-1");
    private static final long MESSAGE_ID = 99L;

    private MessageMemoryPort messageMemoryPort;
    private MessageEmbeddingPort messageEmbeddingPort;
    private Logger log;
    private RecallExamplesUseCase useCase;

    @BeforeEach
    void setUp() {
        messageMemoryPort = mock(MessageMemoryPort.class);
        messageEmbeddingPort = mock(MessageEmbeddingPort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        useCase = new RecallExamplesUseCase(
                messageMemoryPort,
                messageEmbeddingPort,
                EXAMPLES,
                MIN_SIMILARITY,
                RECENT_WINDOW,
                MAX_AGE,
                EXAMPLE_LINES,
                EMBEDDING_ATTEMPTS,
                loggerFactory);
    }

    private static RecallExamplesCommand command() {
        return new RecallExamplesCommand(IDENTITY, TEXT);
    }

    private static MessageExample exampleFixture() {
        ExampleExpense expense = new ExampleExpense(
                "lunch",
                "15.00",
                CurrencyCode.of("EUR"),
                Optional.of("Restaurants"),
                Optional.of("Dining"),
                ExampleOutcome.ACCEPTED);
        return new MessageExample("spent 15 euros on lunch last week", List.of(expense));
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    private List<String> loggedErrorLines() {
        return MockedLoggerUtils.linesAt(log, "error");
    }

    @Nested
    @DisplayName("constructing a RecallExamplesUseCase")
    class Constructor {

        static Stream<Arguments> nonPositiveCounts() {
            return Stream.of(
                    Arguments.of(0, EXAMPLE_LINES, EMBEDDING_ATTEMPTS),
                    Arguments.of(-1, EXAMPLE_LINES, EMBEDDING_ATTEMPTS),
                    Arguments.of(EXAMPLES, 0, EMBEDDING_ATTEMPTS),
                    Arguments.of(EXAMPLES, -1, EMBEDDING_ATTEMPTS),
                    Arguments.of(EXAMPLES, EXAMPLE_LINES, 0),
                    Arguments.of(EXAMPLES, EXAMPLE_LINES, -1));
        }

        @ParameterizedTest
        @MethodSource("nonPositiveCounts")
        @DisplayName("when examples, exampleLines or embeddingAttempts is non-positive - then throws "
                + "InvalidValueException")
        void whenExamplesOrExampleLinesOrEmbeddingAttemptsIsNonPositive_thenThrowsInvalidValueException(
                int examples, int exampleLines, int embeddingAttempts) {
            LoggerFactory loggerFactory = mock(LoggerFactory.class);
            when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));

            assertThatThrownBy(() -> new RecallExamplesUseCase(
                            messageMemoryPort,
                            messageEmbeddingPort,
                            examples,
                            MIN_SIMILARITY,
                            RECENT_WINDOW,
                            MAX_AGE,
                            exampleLines,
                            embeddingAttempts,
                            loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }

        static Stream<Arguments> invalidWindowsAndSimilarity() {
            return Stream.of(
                    Arguments.of(Duration.ZERO, MAX_AGE, MIN_SIMILARITY),
                    Arguments.of(Duration.ofDays(-1), MAX_AGE, MIN_SIMILARITY),
                    Arguments.of(RECENT_WINDOW, Duration.ZERO, MIN_SIMILARITY),
                    Arguments.of(RECENT_WINDOW, Duration.ofDays(-1), MIN_SIMILARITY),
                    Arguments.of(RECENT_WINDOW, MAX_AGE, -0.01),
                    Arguments.of(RECENT_WINDOW, MAX_AGE, 1.01));
        }

        @ParameterizedTest
        @MethodSource("invalidWindowsAndSimilarity")
        @DisplayName("when recentWindow or maxAge is zero/negative, or minSimilarity is out of [0,1] - then "
                + "throws InvalidValueException")
        void whenWindowOrMaxAgeInvalidOrSimilarityOutOfRange_thenThrowsInvalidValueException(
                Duration recentWindow, Duration maxAge, double minSimilarity) {
            LoggerFactory loggerFactory = mock(LoggerFactory.class);
            when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));

            assertThatThrownBy(() -> new RecallExamplesUseCase(
                            messageMemoryPort,
                            messageEmbeddingPort,
                            EXAMPLES,
                            minSimilarity,
                            recentWindow,
                            maxAge,
                            EXAMPLE_LINES,
                            EMBEDDING_ATTEMPTS,
                            loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("recalling examples")
    class Recall {

        @Test
        @DisplayName("when the row already holds a vector - then the embedding port is untouched and "
                + "findExamples() receives it as the query")
        void whenRowHoldsVector_thenEmbeddingPortUntouchedAndFindExamplesReceivesStoredVectorAsQuery() {
            Embedding vector = new Embedding(List.of(0.1f, 0.2f, 0.3f));
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.of(vector));
            List<MessageExample> examples = List.of(exampleFixture());
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageMemoryPort.findExamples(any())).thenReturn(examples);

            Optional<List<MessageExample>> result = useCase.recall(command());

            verifyNoInteractions(messageEmbeddingPort);
            ExampleQuery expectedQuery = new ExampleQuery(
                    IDENTITY.userId(), MESSAGE_ID, vector, EXAMPLES, MIN_SIMILARITY, RECENT_WINDOW, MAX_AGE, EXAMPLE_LINES);
            verify(messageMemoryPort).findExamples(eq(expectedQuery));
            assertThat(result).contains(examples);
        }

        @Test
        @DisplayName("when the row holds no vector and embedding answers one - then the vector is stored")
        void whenRowHoldsNoVectorAndEmbeddingAnswers_thenStoreEmbeddingAndFindExamplesReceiveComputedVector() {
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.empty());
            Embedding computed = new Embedding(List.of(0.4f, 0.5f));
            List<MessageExample> examples = List.of(exampleFixture());
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageEmbeddingPort.embed(TEXT)).thenReturn(computed);
            when(messageMemoryPort.findExamples(any())).thenReturn(examples);

            Optional<List<MessageExample>> result = useCase.recall(command());

            verify(messageMemoryPort).storeEmbedding(eq(MESSAGE_ID), eq(computed));
            ExampleQuery expectedQuery = new ExampleQuery(
                    IDENTITY.userId(), MESSAGE_ID, computed, EXAMPLES, MIN_SIMILARITY, RECENT_WINDOW, MAX_AGE, EXAMPLE_LINES);
            verify(messageMemoryPort).findExamples(eq(expectedQuery));
            assertThat(result).contains(examples);
        }

        @Test
        @DisplayName("when the store holds no row for the identity - then the answer is empty")
        void whenStoreHoldsNoRow_thenAnswerEmptyAndEmbeddingPortAndFindExamplesUntouched() {
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.empty());

            Optional<List<MessageExample>> result = useCase.recall(command());

            verify(messageMemoryPort).find(eq(IDENTITY));
            assertThat(result).isEmpty();
            verifyNoInteractions(messageEmbeddingPort);
            verify(messageMemoryPort, never()).findExamples(any());
        }

        @Test
        @DisplayName("when find() throws MessageStoreFailedException - then the answer is empty")
        void whenFindThrowsMessageStoreFailedException_thenAnswerEmptyOneWarnNamesIdentityNothingPropagates() {
            when(messageMemoryPort.find(IDENTITY)).thenThrow(new MessageStoreFailedException("store unreachable"));

            AtomicReference<Optional<List<MessageExample>>> result = new AtomicReference<>();
            assertThatCode(() -> result.set(useCase.recall(command()))).doesNotThrowAnyException();

            assertThat(result.get()).isEmpty();
            assertThat(loggedWarnLines()).hasSize(1);
            assertThat(loggedWarnLines().get(0))
                    .contains(String.valueOf(IDENTITY.userId()))
                    .contains(IDENTITY.incomingMessageId())
                    .doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when findExamples() throws MessageStoreFailedException - then the answer is empty")
        void whenFindExamplesThrowsMessageStoreFailedException_thenAnswerEmptyOneWarnNamesIdentityNothingPropagates() {
            Embedding vector = new Embedding(List.of(0.1f, 0.2f));
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.of(vector));
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageMemoryPort.findExamples(any())).thenThrow(new MessageStoreFailedException("failed"));

            AtomicReference<Optional<List<MessageExample>>> result = new AtomicReference<>();
            assertThatCode(() -> result.set(useCase.recall(command()))).doesNotThrowAnyException();

            assertThat(result.get()).isEmpty();
            assertThat(loggedWarnLines()).hasSize(1);
            assertThat(loggedWarnLines().get(0))
                    .contains(String.valueOf(IDENTITY.userId()))
                    .contains(IDENTITY.incomingMessageId())
                    .doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when embed() fails and the attempt count stays below the bound - then one WARN logs")
        void whenEmbedFailsAndAttemptCountBelowBound_thenAnswerEmptyOneWarnNoErrorFindExamplesUntouched() {
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.empty());
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageEmbeddingPort.embed(TEXT)).thenThrow(new MessageEmbeddingFailedException("provider refused"));
            when(messageMemoryPort.countEmbeddingAttempt(MESSAGE_ID)).thenReturn(EMBEDDING_ATTEMPTS - 1);

            Optional<List<MessageExample>> result = useCase.recall(command());

            assertThat(result).isEmpty();
            verify(messageMemoryPort).countEmbeddingAttempt(eq(MESSAGE_ID));
            assertThat(loggedWarnLines()).hasSize(1);
            assertThat(loggedErrorLines()).isEmpty();
            verify(messageMemoryPort, never()).findExamples(any());
        }

        @Test
        @DisplayName("when embed() fails and the attempt count reaches the bound - then one ERROR names the "
                + "row")
        void whenEmbedFailsAndAttemptCountReachesBound_thenOneErrorNamesRowAndCarriesNoText() {
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.empty());
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageEmbeddingPort.embed(TEXT)).thenThrow(new MessageEmbeddingFailedException("provider refused"));
            when(messageMemoryPort.countEmbeddingAttempt(MESSAGE_ID)).thenReturn(EMBEDDING_ATTEMPTS);

            useCase.recall(command());

            assertThat(loggedErrorLines()).hasSize(1);
            assertThat(loggedErrorLines().get(0)).contains(String.valueOf(MESSAGE_ID)).doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when embed() fails and countEmbeddingAttempt() answers past the attempt bound - then "
                + "nothing is logged at ERROR")
        void whenEmbedFailsAndAttemptCountPastBound_thenNothingLoggedAtError() {
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.empty());
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageEmbeddingPort.embed(TEXT)).thenThrow(new MessageEmbeddingFailedException("provider refused"));
            when(messageMemoryPort.countEmbeddingAttempt(MESSAGE_ID)).thenReturn(EMBEDDING_ATTEMPTS + 1);

            useCase.recall(command());

            assertThat(loggedErrorLines()).isEmpty();
        }

        @Test
        @DisplayName("when the retrieval answers no neighbour - then the answer is present and empty")
        void whenRetrievalAnswersNoNeighbour_thenAnswerIsPresentAndEmpty() {
            Embedding vector = new Embedding(List.of(0.1f, 0.2f));
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.of(vector));
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageMemoryPort.findExamples(any())).thenReturn(List.of());

            Optional<List<MessageExample>> result = useCase.recall(command());

            assertThat(result).isPresent();
            assertThat(result.get()).isEmpty();
        }

        @Test
        @DisplayName("when countEmbeddingAttempt() throws after a failed embedding - then the answer is empty")
        void whenCountEmbeddingAttemptThrowsAfterFailedEmbedding_thenAnswerEmptyAndNothingPropagates() {
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.empty());
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageEmbeddingPort.embed(TEXT)).thenThrow(new MessageEmbeddingFailedException("provider refused"));
            when(messageMemoryPort.countEmbeddingAttempt(MESSAGE_ID))
                    .thenThrow(new MessageStoreFailedException("failed"));

            AtomicReference<Optional<List<MessageExample>>> result = new AtomicReference<>();
            assertThatCode(() -> result.set(useCase.recall(command()))).doesNotThrowAnyException();

            assertThat(result.get()).isEmpty();
            verify(messageMemoryPort).countEmbeddingAttempt(eq(MESSAGE_ID));
        }

        @Test
        @DisplayName("when storeEmbedding() throws MessageStoreFailedException - then the answer is empty")
        void whenStoreEmbeddingThrows_thenAnswerEmptyFindExamplesUntouchedAndOneWarnLogged() {
            RegisteredMessage registered = new RegisteredMessage(MESSAGE_ID, Optional.empty());
            Embedding computed = new Embedding(List.of(0.4f, 0.5f));
            when(messageMemoryPort.find(IDENTITY)).thenReturn(Optional.of(registered));
            when(messageEmbeddingPort.embed(TEXT)).thenReturn(computed);
            doThrow(new MessageStoreFailedException("failed"))
                    .when(messageMemoryPort)
                    .storeEmbedding(eq(MESSAGE_ID), eq(computed));

            AtomicReference<Optional<List<MessageExample>>> result = new AtomicReference<>();
            assertThatCode(() -> result.set(useCase.recall(command()))).doesNotThrowAnyException();

            assertThat(result.get()).isEmpty();
            verify(messageMemoryPort, never()).findExamples(any());
            assertThat(loggedWarnLines()).hasSize(1);
        }
    }
}
