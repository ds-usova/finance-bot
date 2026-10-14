package bot.finance.ai.adapter.ledger;

import bot.finance.ai.adapter.grpc.CallerTokenContext;
import bot.finance.ai.adapter.grpc.UnverifiedCallerClaims;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.TurnEndPort;
import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnEndServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GrpcTurnEndAdapter implements TurnEndPort {

    private static final long RETRY_PAUSE_MILLIS = 500;

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
        Optional<String> token = CallerTokenContext.callerToken();
        if (token.isEmpty()) {
            logger.error("Turn end {} not reported, no caller token held, turn {}", end, turnKey());
            return;
        }
        Optional<Instant> expiry = expiryOf(token.get());
        EndTurnRequest request = EndTurnRequest.newBuilder().setEnd(toWire(end)).build();

        while (true) {
            StatusRuntimeException failure;
            try {
                stub.withDeadlineAfter(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)
                        .endTurn(request);
                return;
            } catch (StatusRuntimeException e) {
                failure = e;
            }
            if (!isTransient(failure) || !beforeExpiry(expiry) || !pause()) {
                logger.error("Turn end {} not reported, turn {}: {}", end, turnKey(), failure.getStatus());
                return;
            }
        }
    }

    private static boolean isTransient(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    private boolean beforeExpiry(Optional<Instant> expiry) {
        return expiry.filter(at -> clock.instant().isBefore(at)).isPresent();
    }

    private static boolean pause() {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Optional<Instant> expiryOf(String token) {
        try {
            return UnverifiedCallerClaims.read(token).expiry();
        } catch (CallerNotIdentifiedException e) {
            return Optional.empty();
        }
    }

    private static Optional<MessageIdentity> turnKey() {
        return CallerTokenContext.turnKey();
    }

    private static bot.finance.ledger.adapter.grpc.v1.TurnEnd toWire(TurnEnd end) {
        return switch (end) {
            case ACTED_ON -> bot.finance.ledger.adapter.grpc.v1.TurnEnd.TURN_END_ACTED_ON;
            case UNAVAILABLE -> bot.finance.ledger.adapter.grpc.v1.TurnEnd.TURN_END_UNAVAILABLE;
            case FAILED -> bot.finance.ledger.adapter.grpc.v1.TurnEnd.TURN_END_FAILED;
        };
    }
}
