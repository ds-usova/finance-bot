package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.RSAKey;
import io.restassured.path.json.JsonPath;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(JwksController.class)
@Import(SecurityConfiguration.class)
class JwksControllerTest {

    private static final String KEY_ID = "test-signing-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessTokenMinter accessTokenMinter;

    @Nested
    @DisplayName("GET /.well-known/jwks.json")
    class HappyPath {

        @Test
        @DisplayName(
                "when the endpoint is requested with no token - then it returns 200 with a JWK Set carrying the minter's public key and key id")
        void whenRequestedWithNoToken_thenReturnsJwkSetWithMinterPublicKeyAndKeyId() throws Exception {
            RSAPublicKey publicKey = generateRsaPublicKey();
            RSAKey expectedJwk = new RSAKey.Builder(publicKey).build();
            when(accessTokenMinter.publicKey()).thenReturn(publicKey);
            when(accessTokenMinter.keyId()).thenReturn(KEY_ID);

            MvcResult result =
                    mockMvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()).andReturn();

            JsonPath json = JsonPath.from(result.getResponse().getContentAsString());
            assertThat(json.getList("keys")).hasSize(1);
            assertThat(json.getString("keys[0].kty")).isEqualTo("RSA");
            assertThat(json.getString("keys[0].alg")).isEqualTo("RS256");
            assertThat(json.getString("keys[0].use")).isEqualTo("sig");
            assertThat(json.getString("keys[0].kid")).isEqualTo(KEY_ID);
            assertThat(json.getString("keys[0].n")).isEqualTo(expectedJwk.getModulus().toString());
            assertThat(json.getString("keys[0].e")).isEqualTo(expectedJwk.getPublicExponent().toString());
            Map<String, Object> jwk = json.getMap("keys[0]");
            assertThat(jwk).doesNotContainKey("d");
        }

        private static RSAPublicKey generateRsaPublicKey() throws Exception {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return (RSAPublicKey) keyPairGenerator.generateKeyPair().getPublic();
        }
    }
}
