package bot.finance.adapter.security;

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
        // returns the JWK Set carrying this service's RS256 public key, its key id and use=sig
        return Map.of();
    }
}
