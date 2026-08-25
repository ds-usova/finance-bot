package bot.finance.ai.adapter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryRef;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;

class ChangeStreamEntryHandlerTest {

    private static final String STREAM_KEY = "ledger.cdc";
    private static final String ENTRY_ID = "1700000000000-1";

    private ChangeStreamEntryReader reader;
    private LearnMessageOutcomePort learnMessageOutcomePort;
    private Logger log;
    private StreamOperations<String, String, String> streamOperations;
    private ChangeStreamEntryHandler handler;

    @BeforeEach
    void setUp() {
        reader = mock(ChangeStreamEntryReader.class);
        learnMessageOutcomePort = mock(LearnMessageOutcomePort.class);
        streamOperations = mock(StreamOperations.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        ChangeStreamProperties properties = new ChangeStreamProperties(STREAM_KEY, Duration.ofSeconds(30));
        handler = new ChangeStreamEntryHandler(reader, learnMessageOutcomePort, properties, loggerFactory);
    }

    private static MapRecord<String, String, String> entry() {
        return MapRecord.<String, String, String>create(STREAM_KEY, Map.of("type", "ProposalCreated"))
                .withId(RecordId.of(ENTRY_ID));
    }

    private static LearnMessageOutcomeCommand command() {
        SpendingRow row = new SpendingRow(
                1L,
                10L,
                Optional.of("message-1"),
                "lunch",
                Optional.of("Deli Co"),
                "15.00",
                CurrencyCode.of("EUR"),
                new CategoryRef(42L, "Lunch"),
                new CategoryRef(7L, "Food"));
        return new LearnMessageOutcomeCommand(
                ENTRY_ID, new StreamPosition(1_700_000_000_000L, 1L), RecordedStatus.PROPOSED, row);
    }

    @Nested
    @DisplayName("handle()")
    class Handle {

        @Test
        @DisplayName("when the body cannot be read - then WARN-logged by entry id, acknowledged, and no retry")
        void whenBodyCannotBeRead_thenWarnLoggedAcknowledgedAndNoRetry() {
            when(reader.read(eq(ENTRY_ID), any())).thenThrow(new InvalidValueException("Type must not be null"));

            boolean retryNeeded = handler.handle(streamOperations, entry());

            assertThat(retryNeeded).isFalse();
            List<String> warnLines = MockedLoggerUtils.warnLines(log);
            assertThat(warnLines).hasSize(1);
            assertThat(warnLines.get(0)).contains(ENTRY_ID);
            verify(streamOperations).acknowledge(STREAM_KEY, ChangeStreamProperties.GROUP, ENTRY_ID);
        }

        @Test
        @DisplayName("when the reader maps the body to nothing - then acknowledged and no retry")
        void whenReaderMapsBodyToNothing_thenAcknowledgedAndNoRetry() {
            when(reader.read(eq(ENTRY_ID), any())).thenReturn(Optional.empty());

            boolean retryNeeded = handler.handle(streamOperations, entry());

            assertThat(retryNeeded).isFalse();
            verify(streamOperations).acknowledge(STREAM_KEY, ChangeStreamProperties.GROUP, ENTRY_ID);
        }

        @Test
        @DisplayName("when the port answers APPLIED - then acknowledged and no retry")
        void whenPortAnswersApplied_thenAcknowledgedAndNoRetry() {
            when(reader.read(eq(ENTRY_ID), any())).thenReturn(Optional.of(command()));
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.APPLIED);

            boolean retryNeeded = handler.handle(streamOperations, entry());

            assertThat(retryNeeded).isFalse();
            verify(streamOperations).acknowledge(STREAM_KEY, ChangeStreamProperties.GROUP, ENTRY_ID);
        }

        @Test
        @DisplayName("when the port answers DROPPED - then acknowledged and no retry")
        void whenPortAnswersDropped_thenAcknowledgedAndNoRetry() {
            when(reader.read(eq(ENTRY_ID), any())).thenReturn(Optional.of(command()));
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.DROPPED);

            boolean retryNeeded = handler.handle(streamOperations, entry());

            assertThat(retryNeeded).isFalse();
            verify(streamOperations).acknowledge(STREAM_KEY, ChangeStreamProperties.GROUP, ENTRY_ID);
        }

        @Test
        @DisplayName("when the port answers RETRY_LATER - then not acknowledged and retry is needed")
        void whenPortAnswersRetryLater_thenNotAcknowledgedAndRetryNeeded() {
            when(reader.read(eq(ENTRY_ID), any())).thenReturn(Optional.of(command()));
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.RETRY_LATER);

            boolean retryNeeded = handler.handle(streamOperations, entry());

            assertThat(retryNeeded).isTrue();
            verifyNoInteractions(streamOperations);
        }

        @Test
        @DisplayName("when the port throws a RuntimeException - then ERROR-logged, not acknowledged, and retry "
                + "is needed")
        void whenPortThrowsRuntimeException_thenErrorLoggedNotAcknowledgedAndRetryNeeded() {
            when(reader.read(eq(ENTRY_ID), any())).thenReturn(Optional.of(command()));
            when(learnMessageOutcomePort.learn(any())).thenThrow(new RuntimeException("port failed"));

            boolean retryNeeded = handler.handle(streamOperations, entry());

            assertThat(retryNeeded).isTrue();
            List<String> errorLines = MockedLoggerUtils.linesAt(log, "error");
            assertThat(errorLines).hasSize(1);
            assertThat(errorLines.get(0)).contains(ENTRY_ID);
            verifyNoInteractions(streamOperations);
        }
    }
}
