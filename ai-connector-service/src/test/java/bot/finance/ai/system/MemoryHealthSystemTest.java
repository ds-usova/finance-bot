package bot.finance.ai.system;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered over the actuator port {@link AbstractMemorySystemTest} exposes, with the memory on against the real,
 * containerized database.
 */
class MemoryHealthSystemTest extends AbstractMemorySystemTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when GET /actuator/health is called with the memory on - then it answers 200, UP, and db UP")
        void whenActuatorHealthCalledWithMemoryOn_thenAnswers200UpAndDbUp() {
            Response response = given().port(actuatorPort).when().get("/actuator/health");
            log.info("response: {}", response.getBody().asString());

            response.then().statusCode(200);
            assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
            assertThat(response.jsonPath().getString("components.db.status")).isEqualTo("UP");
        }
    }
}
