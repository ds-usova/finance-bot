package bot.finance.ai.common.containers;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.stubs.LedgerTurnStubs;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LedgerTurnServerTest {

    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        LedgerTurnStubs.reset();
        channel = ManagedChannelBuilder.forAddress("localhost", LedgerTurnServer.port())
                .usePlaintext()
                .build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        LedgerTurnStubs.reset();
    }

    @Nested
    @DisplayName("one exchange")
    class OneExchange {

        @Test
        @DisplayName("when a call carries a credential - then the server records it and answers stored")
        void whenCallCarriesCredential_thenServerRecordsItAndAnswersStored() {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer token-1");
            LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub stub = LedgerTurnServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            FileExpenseProposalResponse response = stub.fileExpenseProposal(FileExpenseProposalRequest.newBuilder()
                    .setGrouping("Dining")
                    .setCategory("Cafés")
                    .setDescription("coffee")
                    .setAmount("4.50")
                    .setCurrencyCode("EUR")
                    .build());

            assertThat(response.getCategory()).isEqualTo("Cafés");
            assertThat(LedgerTurnStubs.recordedCalls()).singleElement().satisfies(call -> {
                assertThat(call.method()).isEqualTo("FileExpenseProposal");
                assertThat(call.authorization()).isEqualTo("Bearer token-1");
            });
        }
    }
}
