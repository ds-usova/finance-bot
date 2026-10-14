package bot.finance.ai.adapter.ledger;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.TurnEndPort;
import bot.finance.ai.domain.value.TurnEnd;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnEndServiceGrpc;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class GrpcTurnEndAdapter implements TurnEndPort {

    private final LedgerTurnEndServiceGrpc.LedgerTurnEndServiceBlockingStub stub;
    private final LedgerTurnProperties properties;
    private final Clock clock;
    private final Logger logger;

    public GrpcTurnEndAdapter(
            LedgerTurnEndServiceGrpc.LedgerTurnEndServiceBlockingStub stub,
            LedgerTurnProperties properties,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.stub = stub;
        this.properties = properties;
        this.clock = clock;
        this.logger = loggerFactory.getLogger(GrpcTurnEndAdapter.class);
    }

    @Override
    public void report(TurnEnd end) {
        // stub-intent: maps the end onto the generated TurnEnd and sends it, each attempt bounded by the timeout,
        // again after a 500 ms pause only while the ledger answers unavailable or not in time and the clock is before
        // the token's expiry read through UnverifiedCallerClaims; any other status, unauthenticated included, and a
        // token with no expiry are tried once; giving up is logged at ERROR with the turn key when held
    }
}
