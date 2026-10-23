package bot.finance.ai.adapter.typesafe;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.application.dto.ExpenseClassification;
import bot.finance.ai.common.boot.ClassifierAdapterTest;
import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.SystemOneFixtures;
import bot.finance.ai.common.fixtures.SystemOneFixtures.QuestionAnswer;
import bot.finance.ai.common.stubs.CapturedRequestUtils;
import bot.finance.ai.common.stubs.ClassifierStubs;
import bot.finance.ai.domain.exception.ClassificationFailedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExpenseToFile;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@ClassifierAdapterTest
class TypesafeCategoryClassifierAdapterTest {

    private static final String TEXT = "a flat white at Blue Bottle and a ride to the airport";

    private static final String CAFES = "Dining / Cafés";
    private static final String RESTAURANTS = "Dining / Restaurants";
    private static final String TAXIS = "Transportation / Taxis/Uber";

    private static final List<CatalogueEntry> CATALOGUE = List.of(
            new CatalogueEntry("Dining", List.of("Cafés", "Restaurants")),
            new CatalogueEntry("Transportation", List.of("Taxis/Uber")));

    private static final List<ExpenseToFile> EXPENSES = List.of(
            new ExpenseToFile("Flat white", Optional.of("Blue Bottle"), "4.50", CurrencyCode.of("EUR")),
            new ExpenseToFile("Airport ride", Optional.empty(), "32.00", CurrencyCode.of("EUR")));

    @Autowired
    private TypesafeCategoryClassifierAdapter adapter;

    @BeforeEach
    void setUp() {
        WireMockSupport.SERVER.resetAll();
    }

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    private List<ExpenseClassification> classifyBoth() {
        return adapter.classify(TEXT, List.of(), EXPENSES, CATALOGUE);
    }

    private static String answerForBoth() {
        return SystemOneFixtures.answer(
                new QuestionAnswer("0", CAFES, 0.9, Map.of(CAFES, 0.9, RESTAURANTS, 0.06, TAXIS, 0.04)),
                new QuestionAnswer("1", TAXIS, 0.7, Map.of(CAFES, 0.1, RESTAURANTS, 0.2, TAXIS, 0.7)));
    }

    @Nested
    @DisplayName("classify()")
    class Classify {

        @Test
        @DisplayName("when the classifier answers both questions - then each classification comes back in "
                + "position order")
        void whenClassifierAnswersBothQuestions_thenClassificationsComeBackInPositionOrder() {
            ClassifierStubs.stubAnswered(answerForBoth());

            List<ExpenseClassification> classifications = classifyBoth();

            assertThat(classifications).hasSize(2);
            ExpenseClassification first = classifications.get(0);
            assertThat(first.position()).isEqualTo(0);
            assertThat(first.choice()).isEqualTo(new CategoryChoice("Dining", "Cafés"));
            assertThat(first.confidence()).isEqualTo(0.9);
            assertThat(first.probabilities())
                    .containsOnlyKeys(
                            new CategoryChoice("Dining", "Cafés"),
                            new CategoryChoice("Dining", "Restaurants"),
                            new CategoryChoice("Transportation", "Taxis/Uber"))
                    .containsEntry(new CategoryChoice("Dining", "Restaurants"), 0.06);
            ExpenseClassification second = classifications.get(1);
            assertThat(second.position()).isEqualTo(1);
            assertThat(second.choice()).isEqualTo(new CategoryChoice("Transportation", "Taxis/Uber"));
            assertThat(second.confidence()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("when two expenses are classified - then one request reaches the classifier carrying the model "
                + "and the key")
        void whenTwoExpensesAreClassified_thenOneRequestCarriesTheModelAndTheKey() {
            ClassifierStubs.stubAnswered(answerForBoth());

            classifyBoth();

            List<LoggedRequest> requests =
                    WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(ClassifierStubs.SYSTEM_ONE_PATH)));
            assertThat(requests).hasSize(1);
            assertThat(requests.get(0).getHeader("Authorization")).isEqualTo("Bearer test-key");
            assertThat(CapturedRequestUtils.body(requests.get(0)).path("model").asText())
                    .isEqualTo("test-model");
            assertThat(CapturedRequestUtils.body(requests.get(0))
                            .path("questions")
                            .size())
                    .isEqualTo(2);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {400, 401, 429, 500, 503})
        @DisplayName("when the classifier answers an error status - then it throws ClassificationFailedException")
        void whenClassifierAnswersErrorStatus_thenThrowsClassificationFailedException(int status) {
            ClassifierStubs.stubStatus(status);

            assertThatThrownBy(TypesafeCategoryClassifierAdapterTest.this::classifyBoth)
                    .isInstanceOf(ClassificationFailedException.class);
        }

        @Test
        @DisplayName("when the classifier answers later than the timeout - then it throws "
                + "ClassificationFailedException")
        void whenClassifierAnswersLaterThanTimeout_thenThrowsClassificationFailedException() {
            ClassifierStubs.stubAnsweredAfter(answerForBoth(), Duration.ofSeconds(3));

            assertThatThrownBy(TypesafeCategoryClassifierAdapterTest.this::classifyBoth)
                    .isInstanceOf(ClassificationFailedException.class);
        }

        @Test
        @DisplayName("when the classifier answers an unparseable body - then it throws ClassificationFailedException")
        void whenClassifierAnswersUnparseableBody_thenThrowsClassificationFailedException() {
            ClassifierStubs.stubUnparseableBody();

            assertThatThrownBy(TypesafeCategoryClassifierAdapterTest.this::classifyBoth)
                    .isInstanceOf(ClassificationFailedException.class);
        }
    }
}
