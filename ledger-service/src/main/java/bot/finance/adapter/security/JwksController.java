package bot.finance.adapter.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

    private final AccessTokenMinter accessTokenMinter;

    public JwksController(AccessTokenMinter accessTokenMinter) {
        this.accessTokenMinter = accessTokenMinter;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAKey jwk = new RSAKey.Builder(accessTokenMinter.publicKey())
                .keyID(accessTokenMinter.keyId())
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return new JWKSet(jwk).toJSONObject();
    }
}
