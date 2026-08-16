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
import bot.finance.ai.domain.value.SpendingRowChange;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
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
@RedisAdapterTest
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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearStream() {
        LedgerChangeStreamStubs.deleteStream(properties.key());
    }

    private static Map<String, String> expenseCreatedFixture(long expenseId, String txId) {
        return ChangeStreamEntryFixtures.expenseCreated(
                expenseId, 10L, "msg-" + expenseId, "Coffee", "Roastery", 550L, "USD", 3L, "Dining", "Food", txId);
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("when the context starts and an entry is published - then the group exists, the port sees "
                + "it, and pending reads zero")
        void whenContextStartsAndEntryIsPublished_thenGroupExistsPortSeesItAndPendingReadsZero() {
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.APPLIED);

            String entryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(1L, "tx-1"));

            await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
                assertThat(LedgerChangeStreamStubs.groupExists(properties.key(), GROUP))
                        .isTrue();
                ArgumentCaptor<LearnMessageOutcomeCommand> captor =
                        ArgumentCaptor.forClass(LearnMessageOutcomeCommand.class);
                verify(learnMessageOutcomePort).learn(captor.capture());
                assertThat(captor.getValue().deliveryId()).isEqualTo(entryId);
                assertThat(captor.getValue().change()).isInstanceOf(SpendingRowChange.class);
                assertThat(LedgerChangeStreamStubs.pending(properties.key(), GROUP))
                        .isZero();
            });
        }

        @Test
        @DisplayName("when the first entry retries and the second is applied - then the second never precedes "
                + "the first's last offer")
        void whenFirstEntryRetriesAndSecondApplied_thenSecondNeverPrecedesFirstsLastOffer() {
            String firstEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(2L, "tx-2"));
            String secondEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(3L, "tx-3"));
            when(learnMessageOutcomePort.learn(
                            argThat(command -> command.deliveryId().equals(firstEntryId))))
                    .thenReturn(LearnOutcome.RETRY_LATER);
            when(learnMessageOutcomePort.learn(
                            argThat(command -> command.deliveryId().equals(secondEntryId))))
                    .thenReturn(LearnOutcome.APPLIED);

            ArgumentCaptor<LearnMessageOutcomeCommand> captor =
                    ArgumentCaptor.forClass(LearnMessageOutcomeCommand.class);
            await().atMost(RETRY_TIMEOUT).untilAsserted(() -> verify(learnMessageOutcomePort, atLeast(3))
                    .learn(captor.capture()));

            List<String> offeredIds = captor.getAllValues().stream()
                    .map(LearnMessageOutcomeCommand::deliveryId)
                    .toList();
            long firstOfferCount =
                    offeredIds.stream().filter(firstEntryId::equals).count();
            int lastFirstOfferIndex = offeredIds.lastIndexOf(firstEntryId);
            int secondOfferIndex = offeredIds.indexOf(secondEntryId);

            assertThat(firstOfferCount).isGreaterThanOrEqualTo(2);
            assertThat(secondOfferIndex).isNotNegative();
            assertThat(secondOfferIndex).isLessThan(lastFirstOfferIndex);
        }

        @Test
        @DisplayName("when the port answers DROPPED - then the entry is acknowledged and the next entry is offered")
        void whenPortAnswersDropped_thenEntryIsAcknowledgedAndNextEntryIsOffered() {
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.DROPPED);

            String firstEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(4L, "tx-4"));
            String secondEntryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(5L, "tx-5"));

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
            try (LogCapture logCapture = LogCapture.attachedTo(ChangeStreamConsumer.class)) {
                // ChangeStreamEntryFixtures.withNoPayload() answers an empty map, which XADD refuses outright
                // (it requires at least one field); a body carrying no "payload" key is published directly here
                // instead.
                String entryId = LedgerChangeStreamStubs.publish(properties.key(), Map.of("enrichment", "{}"));

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
            redisTemplate.opsForStream().createGroup(properties.key(), ReadOffset.from("0"), GROUP);
            when(learnMessageOutcomePort.learn(any())).thenReturn(LearnOutcome.APPLIED);

            String entryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(6L, "tx-6"));
            LedgerChangeStreamStubs.readAsOther(properties.key(), GROUP, "other-consumer");

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
            try (LogCapture logCapture = LogCapture.attachedTo(ChangeStreamConsumer.class)) {
                when(learnMessageOutcomePort.learn(any())).thenThrow(new RuntimeException("port failed"));

                String entryId = LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(7L, "tx-7"));

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
                    entryId = LedgerChangeStreamStubs.publish(
                            propertiesOverProxy.key(), expenseCreatedFixture(8L, "tx-8"));
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

            LedgerChangeStreamStubs.publish(properties.key(), expenseCreatedFixture(9L, "tx-9"));

            await().pollDelay(Duration.ofSeconds(2))
                    .atMost(Duration.ofSeconds(4))
                    .untilAsserted(
                            () -> verify(learnMessageOutcomePort, never()).learn(any()));
        }
    }
}
