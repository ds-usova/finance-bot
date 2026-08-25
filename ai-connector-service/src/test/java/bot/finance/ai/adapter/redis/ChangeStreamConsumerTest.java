package bot.finance.ai.adapter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.common.LogCapture;
import bot.finance.ai.common.boot.RedisAdapterTest;
import bot.finance.ai.common.containers.ToxiproxyContainers;
import bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures;
import bot.finance.ai.common.stubs.LedgerChangeStreamStubs;
import bot.finance.ai.domain.value.RecordedStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test wiring only the consumer against the real, containerized Redis, with
 * {@link LearnMessageOutcomePort} mocked. Every entry is published directly onto the stream through
 * {@link LedgerChangeStreamStubs}, standing in for the ledger's own writes, since driving the consumer is the only
 * thing under test here.
 */
// PER_CLASS: JUnit builds a fresh enclosing-instance chain, and re-prepares it, for every nested test method by
// default - including this class's own @MockitoBean field - and that re-preparation is what races against
// Start.WhenConnectionCut's own independently-booted context below, since it is re-run while that context is
// current. One instance for the whole class means the enclosing instance is prepared once, against this class's
// own context, before any nested context exists to race with.
@RedisAdapterTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChangeStreamConsumerTest {

    private static final String GROUP = "ai-connector";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RETRY_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration CLAIM_TIMEOUT = Duration.ofSeconds(10);

    @MockitoBean
    private LearnMessageOutcomePort learnMessageOutcomePort;

    @Autowired
    private ChangeStreamConsumer changeStreamConsumer;

    @Autowired
    private ChangeStreamProperties properties;

    @AfterEach
    void clearStream() {
        // Draining rather than deleting: deleting the stream destroys its consumer group too, and the consumer
        // re-creates that group lazily at "$" - a cursor the next test's entry, published before the group
        // exists again, would never be visible under. Draining acknowledges what is pending and trims the stream
        // to empty, leaving the group and its cursor exactly where the design puts them: created once, in start().
        LedgerChangeStreamStubs.drain(properties.key(), GROUP);
    }

    private static Map<String, String> expenseCreatedFixture(long expenseId) {
        return ChangeStreamEntryFixtures.expenseRecorded(
                expenseId,
                10L,
                "msg-" + expenseId,
                expenseId,
                "Coffee",
                "Roastery",
                "5.50",
                "USD",
                3L,
                "Dining",
                1L,
                "Food");
    }

    private static Map<String, String> proposalCreatedFixture(long expenseId) {
        return ChangeStreamEntryFixtures.proposalCreated(
                expenseId,
                10L,
                "msg-" + expenseId,
                expenseId,
                "Coffee",
                "Roastery",
                "5.50",
                "USD",
                3L,
                "Dining",
                1L,
                "Food");
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName(
                "when a ProposalCreated entry is applied - then it is offered as PROPOSED and pending " + "reads zero")
        void whenProposalCreatedEntryIsPublished_thenGroupExistsCommandIsProposedAndPendingReadsZero() {
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.APPLIED);

            long expenseId = 1L;
            String entryId = LedgerChangeStreamStubs.publish(properties.key(), proposalCreatedFixture(expenseId));

            await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
                assertThat(LedgerChangeStreamStubs.groupExists(properties.key(), GROUP))
                        .isTrue();
                ArgumentCaptor<LearnMessageOutcomeCommand> captor =
                        ArgumentCaptor.forClass(LearnMessageOutcomeCommand.class);
                verify(learnMessageOutcomePort).learn(captor.capture());
                assertThat(captor.getValue().deliveryId()).isEqualTo(entryId);
                assertThat(captor.getValue().status()).isEqualTo(RecordedStatus.PROPOSED);
                assertThat(captor.getValue().entry().expenseId()).isEqualTo(expenseId);
                assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP))
                        .isZero();
            });
        }

        @Test
        @DisplayName("when the first entry retries and the second is applied - then the second never precedes "
                + "the first's last offer")
        void whenFirstEntryRetriesAndSecondApplied_thenSecondNeverPrecedesFirstsLastOffer() {
            // One answer keyed on the delivery id, rather than two argThat-matched stubs: an entry's id is only
            // known once publish() returns it, so a per-id stub can only be registered after publishing, and the
            // consumer can read and offer that entry first. An unstubbed answer would be null, and offer()'s
            // switch on it would NPE on the consumer's background thread, killing it for the rest of the class.
            // The first entry answers RETRY_LATER twice and then APPLIED, so it has a last offer for the
            // assertions below to key on.
            AtomicReference<String> secondEntryIdRef = new AtomicReference<>();
            AtomicInteger firstEntryOfferCount = new AtomicInteger();
            when(learnMessageOutcomePort.learn(any())).thenAnswer(invocation -> {
                LearnMessageOutcomeCommand command = invocation.getArgument(0);
                if (command.deliveryId().equals(secondEntryIdRef.get())) {
                    return LearnOutcome.APPLIED;
                }
                return firstEntryOfferCount.incrementAndGet() <= 2 ? LearnOutcome.RETRY_LATER : LearnOutcome.APPLIED;
            });

            String firstEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(2L));
            String secondEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(3L));
            secondEntryIdRef.set(secondEntryId);

            ArgumentCaptor<LearnMessageOutcomeCommand> captor =
                    ArgumentCaptor.forClass(LearnMessageOutcomeCommand.class);
            await().atMost(RETRY_TIMEOUT).untilAsserted(() -> {
                verify(learnMessageOutcomePort)
                        .learn(argThat(command ->
                                command != null && command.deliveryId().equals(secondEntryIdRef.get())));
                verify(learnMessageOutcomePort, atLeast(4)).learn(captor.capture());
            });

            List<String> offeredIds = captor.getAllValues().stream()
                    .map(LearnMessageOutcomeCommand::deliveryId)
                    .toList();
            long firstOfferCount =
                    offeredIds.stream().filter(firstEntryId::equals).count();
            int lastFirstOfferIndex = offeredIds.lastIndexOf(firstEntryId);
            int secondOfferIndex = offeredIds.indexOf(secondEntryId);

            assertThat(firstOfferCount).isGreaterThanOrEqualTo(2);
            assertThat(secondOfferIndex).isNotNegative();
            assertThat(secondOfferIndex).isGreaterThan(lastFirstOfferIndex);
        }

        @Test
        @DisplayName("when the port answers DROPPED - then the entry is acknowledged and the next entry is offered")
        void whenPortAnswersDropped_thenEntryIsAcknowledgedAndNextEntryIsOffered() {
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.DROPPED);

            String firstEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(4L));
            String secondEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(5L));

            await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
                verify(learnMessageOutcomePort)
                        .learn(argThat(command -> command.deliveryId().equals(firstEntryId)));
                verify(learnMessageOutcomePort)
                        .learn(argThat(command -> command.deliveryId().equals(secondEntryId)));
                assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP))
                        .isZero();
            });
        }

        @Test
        @DisplayName("when a body with no payload is published - then it is WARN-logged by entry id, "
                + "acknowledged, and skipped")
        void whenBodyWithNoPayloadIsPublished_thenItIsWarnLoggedAcknowledgedAndSkipped() {
            try (LogCapture logCapture = LogCapture.attachedTo(ChangeStreamEntryHandler.class)) {
                String entryId =
                        LedgerChangeStreamStubs.publish(properties.key(), ChangeStreamEntryFixtures.withNoPayload());

                await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
                    assertThat(logCapture.messages()).anyMatch(message -> message.contains(entryId));
                    assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP))
                            .isZero();
                });
                verify(learnMessageOutcomePort, never()).learn(any());
            }
        }

        @Test
        @DisplayName("when an entry idles past claimIdle under another consumer - then it is claimed, offered, "
                + "and acknowledged")
        void whenEntryIdlesPastClaimIdleUnderAnotherConsumer_thenItIsClaimedOfferedAndAcknowledged() {
            // No explicit createGroup: start() created it and drain() leaves it standing between tests, so
            // creating it again here would only race the consumer's own re-affirmation for a BUSYGROUP error.
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.APPLIED);

            // The consumer under test is taken out of the group while the entry is published and read by the
            // other consumer: a blocking read already waiting would otherwise be served the entry first, and the
            // claim path would never run. Its loop may still be inside a one-second blocking read after stop(),
            // so the pause outlasts that.
            changeStreamConsumer.stop();
            await().pollDelay(Duration.ofSeconds(2))
                    .atMost(Duration.ofSeconds(4))
                    .untilAsserted(
                            () -> assertThat(changeStreamConsumer.isRunning()).isFalse());

            String entryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(6L));
            LedgerChangeStreamStubs.readAsOther(properties.key(), GROUP, "other-consumer");
            assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP)).isEqualTo(1);
            verify(learnMessageOutcomePort, never()).learn(any());

            changeStreamConsumer.start();

            await().atMost(CLAIM_TIMEOUT).untilAsserted(() -> {
                verify(learnMessageOutcomePort)
                        .learn(argThat(command -> command.deliveryId().equals(entryId)));
                assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP))
                        .isZero();
            });
        }

        @Test
        @DisplayName("when the port throws a RuntimeException - then it is ERROR-logged, stays pending, and "
                + "the consumer runs on")
        void whenPortThrowsRuntimeException_thenItIsErrorLoggedStaysPendingAndConsumerRunsOn() {
            try (LogCapture logCapture = LogCapture.attachedTo(ChangeStreamEntryHandler.class)) {
                when(learnMessageOutcomePort.learn(any())).thenThrow(new RuntimeException("port failed"));

                String entryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(7L));

                await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
                    assertThat(logCapture.messages()).anyMatch(message -> message.contains(entryId));
                    assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP))
                            .isEqualTo(1);
                    assertThat(changeStreamConsumer.isRunning()).isTrue();
                });
            }
        }

        /**
         * A full, self-contained context rather than a layer over the enclosing one: two {@code
         * DynamicPropertyRegistrar} beans both targeting {@code spring.data.redis.url} would leave which one
         * wins to registration order nothing here defines, so this scenario declares its own {@link RedisAdapterTest}
         * -shaped boot from scratch, pointed at {@link ToxiproxyContainers#proxiedRedisUrl()} instead of the
         * container's own address, with its own stream key.
         */
        @Nested
        @DisplayName("when the connection to Redis is cut before the first entry")
        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        @ActiveProfiles("test")
        @TestPropertySource(properties = {"memory.enabled=true", "ledger.change-stream.claim-idle=2s"})
        @SpringBootTest(
                classes = {
                    ChangeStreamConfiguration.class,
                    ChangeStreamConsumer.class,
                    ChangeStreamEntryHandler.class,
                    ChangeStreamEntryReader.class,
                    Slf4jLoggerFactory.class
                })
        @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
        @Import(WhenConnectionCut.ProxyRedisPropertiesConfiguration.class)
        @Testcontainers(disabledWithoutDocker = true)
        class WhenConnectionCut {

            @MockitoBean
            private LearnMessageOutcomePort learnMessageOutcomePortOverProxy;

            @Autowired
            private ChangeStreamConsumer changeStreamConsumerOverProxy;

            @Autowired
            private ChangeStreamProperties propertiesOverProxy;

            @Test
            @DisplayName("when the proxy is restored - then entries published while cut are offered, "
                    + "acknowledged, and it never stopped")
            void whenProxyRestored_thenEntriesPublishedWhileCutAreOfferedAcknowledgedAndItNeverStopped() {
                when(learnMessageOutcomePortOverProxy.learn(any())).thenReturn(LearnOutcome.APPLIED);

                ToxiproxyContainers.REDIS_PROXY.setConnectionCut(true);
                String entryId;
                try {
                    entryId = LedgerChangeStreamStubs.publish(propertiesOverProxy.key(), expenseCreatedFixture(8L));
                } finally {
                    ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
                }

                await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
                    assertThat(changeStreamConsumerOverProxy.isRunning()).isTrue();
                    verify(learnMessageOutcomePortOverProxy)
                            .learn(argThat(command -> command.deliveryId().equals(entryId)));
                    assertThat(LedgerChangeStreamStubs.pending(propertiesOverProxy.key(), GROUP))
                            .isZero();
                });
            }

            @TestConfiguration(proxyBeanMethods = false)
            static class ProxyRedisPropertiesConfiguration {

                @Bean
                DynamicPropertyRegistrar proxiedRedisProperties() {
                    String key = "ledger.cdc-" + UUID.randomUUID();
                    return registry -> {
                        registry.add("spring.data.redis.url", ToxiproxyContainers::proxiedRedisUrl);
                        registry.add("ledger.change-stream.key", () -> key);
                    };
                }
            }
        }
    }

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        @DisplayName("when stop() is called - then isRunning() reads false within a bound and a later entry is "
                + "never offered")
        @DirtiesContext
        void whenStopIsCalled_thenIsRunningReadsFalseAndLaterEntryIsNeverOffered() {
            changeStreamConsumer.stop();

            await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> assertThat(changeStreamConsumer.isRunning())
                    .isFalse());

            LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(9L));

            await().pollDelay(Duration.ofSeconds(2))
                    .atMost(Duration.ofSeconds(4))
                    .untilAsserted(
                            () -> verify(learnMessageOutcomePort, never()).learn(any()));
        }
    }
}
