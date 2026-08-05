package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Context;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CallerTokenContextTest {

    @Nested
    @DisplayName("callerToken()")
    class CallerToken {

        @Test
        @DisplayName("when the context key holds a token - then answers a present token")
        void whenContextKeyHoldsToken_thenAnswersPresentToken() throws Exception {
            Optional<String> token = Context.current()
                    .withValue(CallerTokenContext.CALLER_TOKEN, "Bearer abc")
                    .call(CallerTokenContext::callerToken);

            assertThat(token).contains("Bearer abc");
        }

        @Test
        @DisplayName("when no context key is set - then answers an empty Optional")
        void whenNoContextKeyIsSet_thenAnswersEmptyOptional() {
            Optional<String> token = CallerTokenContext.callerToken();

            assertThat(token).isEmpty();
        }
    }
}
