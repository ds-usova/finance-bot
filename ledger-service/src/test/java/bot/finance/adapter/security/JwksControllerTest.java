package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.common.boot.SigningKeysConfiguration;
import bot.finance.common.fixtures.SigningKeys;
import com.nimbusds.jose.jwk.RSAKey;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(JwksController.class)
@Import({SecurityConfiguration.class, SigningKeysConfiguration.class, Slf4jLoggerFactory.class})
class JwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("GET /.well-known/jwks.json")
    class HappyPath {

        @Test
        @DisplayName(
                "when the endpoint is requested with no token - then it returns 200 with a JWK Set carrying the signing key's public half and key id")
        void whenRequestedWithNoToken_thenReturnsJwkSetWithSigningPublicKeyAndKeyId() throws Exception {
            RSAKey expectedJwk = new RSAKey.Builder(SigningKeys.keys().publicKey()).build();

            MvcResult result = mockMvc.perform(get("/.well-known/jwks.json"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getList("keys")).hasSize(1);
            assertThat(json.getString("keys[0].kty")).isEqualTo("RSA");
            assertThat(json.getString("keys[0].alg")).isEqualTo("RS256");
            assertThat(json.getString("keys[0].use")).isEqualTo("sig");
            assertThat(json.getString("keys[0].kid")).isEqualTo(SigningKeys.KEY_ALIAS);
            assertThat(json.getString("keys[0].n"))
                    .isEqualTo(expectedJwk.getModulus().toString());
            assertThat(json.getString("keys[0].e"))
                    .isEqualTo(expectedJwk.getPublicExponent().toString());
            Map<String, Object> jwk = json.getMap("keys[0]");
            assertThat(jwk).doesNotContainKey("d");
        }
    }
}
