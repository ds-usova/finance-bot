package bot.finance.application.dto;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class SummarizeSpendingCommandTest {

    @Nested
    @DisplayName("constructing a summarize-spending command")
    class SummarizeSpendingCommandConstructor {

        @Test
        @DisplayName("when every component is present - then every component reads back unchanged")
        void whenUserIdReferenceAndDatesArePresent_thenEveryComponentReadsBackUnchanged() {
            AuthenticatedUserId userId = new AuthenticatedUserId(555L);
            IncomingMessageId reference = newIncomingMessageId();

            SummarizeSpendingCommand command =
                    new SummarizeSpendingCommand(userId, reference, "2026-08-01", "2026-08-05");

            assertThat(command.userId()).isEqualTo(userId);
            assertThat(command.reference()).isEqualTo(reference);
            assertThat(command.from()).isEqualTo("2026-08-01");
            assertThat(command.to()).isEqualTo("2026-08-05");
        }

        @Test
        @DisplayName("when the authenticated user id is null - then throws InvalidSpendingQueryException")
        void whenUserIdIsNull_thenThrowsInvalidSpendingQueryException() {
            assertThatThrownBy(() ->
                            new SummarizeSpendingCommand(null, newIncomingMessageId(), "2026-08-01", "2026-08-05"))
                    .isInstanceOf(InvalidSpendingQueryException.class);
        }

        @Test
        @DisplayName("when the message reference is null - then throws InvalidSpendingQueryException")
        void whenReferenceIsNull_thenThrowsInvalidSpendingQueryException() {
            assertThatThrownBy(() -> new SummarizeSpendingCommand(
                            new AuthenticatedUserId(555L), null, "2026-08-01", "2026-08-05"))
                    .isInstanceOf(InvalidSpendingQueryException.class);
        }

        @ParameterizedTest(name = "from={0}")
        @NullAndEmptySource
        @DisplayName("when from is null or blank - then the record is built and from is carried through unchanged")
        void whenFromIsNullOrBlank_thenTheRecordIsBuiltAndFromIsCarriedThroughUnchanged(String from) {
            SummarizeSpendingCommand command = new SummarizeSpendingCommand(
                    new AuthenticatedUserId(555L), newIncomingMessageId(), from, "2026-08-05");

            assertThat(command.from()).isEqualTo(from);
        }

        @ParameterizedTest(name = "to={0}")
        @NullAndEmptySource
        @DisplayName("when to is null or blank - then the record is built and to is carried through unchanged")
        void whenToIsNullOrBlank_thenTheRecordIsBuiltAndToIsCarriedThroughUnchanged(String to) {
            SummarizeSpendingCommand command = new SummarizeSpendingCommand(
                    new AuthenticatedUserId(555L), newIncomingMessageId(), "2026-08-01", to);

            assertThat(command.to()).isEqualTo(to);
        }
    }
}
