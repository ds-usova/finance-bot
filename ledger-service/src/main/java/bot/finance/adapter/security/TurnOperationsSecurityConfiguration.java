package bot.finance.adapter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

@Configuration
public class TurnOperationsSecurityConfiguration {

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor turnOperationsAuthenticationInterceptor(GrpcSecurity grpcSecurity)
            throws Exception {
        // stub-intent: requires the turn's credential on every LedgerTurnService call, verified against the
        // ledger's own public key with the tools' validators and a message reference required, and leaves the
        // health and reflection services open
        return grpcSecurity
                .authorizeRequests(requests -> requests.allRequests().permitAll())
                .build();
    }
}
