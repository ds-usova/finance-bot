package bot.finance.common.boot;

import bot.finance.adapter.aiconnector.LedgerTurnGrpcService;
import bot.finance.adapter.security.AccessTokenMinter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Proves {@link TurnOperationsAdapterTest} itself boots a working context - compiling says nothing about whether
 * the gRPC server, its security builder and the turn service come up together without the rest of the
 * application. Throwaway: it asserts nothing beyond the autowiring succeeding.
 */
@TurnOperationsAdapterTest
class TurnOperationsAdapterContextTest {

    @Autowired
    private LedgerTurnGrpcService ledgerTurnGrpcService;

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Test
    void contextLoads() {}
}
