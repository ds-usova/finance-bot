package bot.finance.ai.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.adapter.scheduling.MemoryProperties;
import bot.finance.ai.adapter.scheduling.MemoryPropertiesConfiguration;
import bot.finance.ai.common.boot.AiAdapterTest;
import bot.finance.ai.common.boot.WireMockUrlConfiguration;
import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.EmbeddingFixtures;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.WireMockStubs;
import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.value.Embedding;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.context.TestPropertySource;

@AiAdapterTest
class AiMessageEmbeddingAdapterTest {

    private static final String TEXT = "spent 15 euros on lunch";

    @Autowired
    private AiMessageEmbeddingAdapter adapter;

    @Autowired
    private MemoryProperties properties;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String configuredModel;

    @BeforeEach
    void setUp() {
        WireMockSupport.SERVER.resetAll();
    }

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    /** The texts an embeddings request body's {@code input} field carries, whether serialized as one or many. */
    private static List<String> inputTexts(JsonNode body) {
        JsonNode input = body.get("input");
        List<String> texts = new ArrayList<>();
        if (input.isArray()) {
            input.forEach(node -> texts.add(node.asText()));
        } else {
            texts.add(input.asText());
        }
        return texts;
    }

    @Nested
    @DisplayName("embed()")
    class Embed {

        @Test
        @DisplayName("when the provider answers one vector - then the answer matches it and the request names "
                + "the model and text")
        void whenProviderAnswersOneVector_thenAnswerMatchesItAndRequestNamesModelAndText() {
            List<Float> vector = EmbeddingFixtures.unitVector(0);
            WireMockStubs.stubEmbeddings(EmbeddingFixtures.embeddingsResponse(vector));

            Embedding result = adapter.embed(TEXT);
            assertThat(result.values()).containsExactlyElementsOf(vector);

            List<LoggedRequest> requests = CapturedRequestUtils.embeddingsRequests();
            assertThat(requests).hasSize(1);
            JsonNode body = CapturedRequestUtils.body(requests.get(0));
            assertThat(body.get("model").asText()).isEqualTo(configuredModel);
            assertThat(inputTexts(body)).containsExactly(TEXT);
        }

        @Test
        @DisplayName("when the provider answers a server error - then it throws MessageEmbeddingFailedException")
        void whenProviderAnswersServerError_thenThrowsMessageEmbeddingFailedException() {
            WireMockStubs.stubEmbeddingsServerError();

            assertThatThrownBy(() -> adapter.embed(TEXT)).isInstanceOf(MessageEmbeddingFailedException.class);
        }

        @Test
        @DisplayName("when the provider answers after the embedding timeout - then it throws within a bound of it")
        void whenProviderAnswersAfterTimeout_thenThrowsWithinBoundOfTimeout() {
            WireMockStubs.stubEmbeddingsDelayed(
                    EmbeddingFixtures.embeddingsResponse(EmbeddingFixtures.unitVector(0)),
                    properties.embeddingTimeout().plusSeconds(4));

            long start = System.nanoTime();
            assertThatThrownBy(() -> adapter.embed(TEXT)).isInstanceOf(MessageEmbeddingFailedException.class);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isLessThan(properties.embeddingTimeout().plusSeconds(3));
        }
    }

    @Nested
    @DisplayName("embedAll()")
    class EmbedAll {

        @Test
        @DisplayName("when the provider answers three vectors for three texts - then one request carries all "
                + "three, in order")
        void whenProviderAnswersThreeVectors_thenOneRequestCarriesAllThreeAndAnswerHoldsThemInOrder() {
            List<String> texts = List.of("spent 15 euros on lunch", "20 dollars for a cab", "coffee 3.50");
            List<List<Float>> vectors = List.of(
                    EmbeddingFixtures.unitVector(0), EmbeddingFixtures.unitVector(1), EmbeddingFixtures.unitVector(2));
            WireMockStubs.stubEmbeddings(EmbeddingFixtures.embeddingsResponseForAll(vectors));

            List<Embedding> result = adapter.embedAll(texts);

            List<LoggedRequest> requests = CapturedRequestUtils.embeddingsRequests();
            assertThat(requests).hasSize(1);
            assertThat(inputTexts(CapturedRequestUtils.body(requests.get(0)))).containsExactlyElementsOf(texts);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).values()).containsExactlyElementsOf(vectors.get(0));
            assertThat(result.get(1).values()).containsExactlyElementsOf(vectors.get(1));
            assertThat(result.get(2).values()).containsExactlyElementsOf(vectors.get(2));
        }

        @Test
        @DisplayName("when the provider returns too few vectors or a server error - then it throws "
                + "MessageEmbeddingFailedException")
        void whenProviderReturnsTooFewVectorsOrServerError_thenThrowsMessageEmbeddingFailedException() {
            List<String> texts = List.of("spent 15 euros on lunch", "20 dollars for a cab", "coffee 3.50");
            WireMockStubs.stubEmbeddings(EmbeddingFixtures.embeddingsResponseForAll(
                    List.of(EmbeddingFixtures.unitVector(0), EmbeddingFixtures.unitVector(1))));

            assertThatThrownBy(() -> adapter.embedAll(texts)).isInstanceOf(MessageEmbeddingFailedException.class);

            WireMockSupport.SERVER.resetAll();
            WireMockStubs.stubEmbeddingsServerError();

            assertThatThrownBy(() -> adapter.embedAll(texts)).isInstanceOf(MessageEmbeddingFailedException.class);
        }

        /**
         * A full, self-contained context rather than a layer over the enclosing one, so {@code
         * memory.backfill-timeout} can be set short for this scenario alone without racing every other test's
         * context, which stays on the production default.
         */
        @Nested
        @DisplayName("when the backfill timeout is set short for the test")
        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        @ActiveProfiles("test")
        @TestPropertySource(properties = "memory.backfill-timeout=1s")
        @SpringBootTest(
                classes = {
                    AiMessageEmbeddingAdapter.class,
                    MemoryPropertiesConfiguration.class,
                    Slf4jLoggerFactory.class
                })
        @ImportAutoConfiguration(OpenAiEmbeddingAutoConfiguration.class)
        @Import(WireMockUrlConfiguration.class)
        class WhenBackfillTimeoutIsShort {

            @Autowired
            private AiMessageEmbeddingAdapter adapterWithShortTimeout;

            @Autowired
            private MemoryProperties propertiesWithShortTimeout;

            @Test
            @DisplayName(
                    "when the provider answers after the backfill timeout - then it throws within a bound " + "of it")
            void whenProviderAnswersAfterTimeout_thenThrowsWithinBoundOfTimeout() {
                WireMockStubs.stubEmbeddingsDelayed(
                        EmbeddingFixtures.embeddingsResponse(EmbeddingFixtures.unitVector(0)),
                        propertiesWithShortTimeout.backfillTimeout().plusSeconds(4));

                long start = System.nanoTime();
                assertThatThrownBy(() -> adapterWithShortTimeout.embedAll(List.of(TEXT)))
                        .isInstanceOf(MessageEmbeddingFailedException.class);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

                assertThat(elapsed)
                        .isLessThan(propertiesWithShortTimeout.backfillTimeout().plusSeconds(3));
            }
        }
    }
}
