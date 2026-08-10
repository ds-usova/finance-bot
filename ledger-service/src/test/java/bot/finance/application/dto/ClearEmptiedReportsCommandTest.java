package bot.finance.application.dto;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidProposalReportException;
import bot.finance.domain.value.IncomingMessageId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClearEmptiedReportsCommandTest {

    private static final long USER_ID = 7L;

    @Nested
    @DisplayName("constructing a clear-emptied-reports command")
    class ClearEmptiedReportsCommandConstructor {

        @Test
        @DisplayName("when the ids are given - then the command carries them in order")
        void whenIdsAreGiven_thenCommandCarriesThemInOrder() {
            IncomingMessageId first = newIncomingMessageId();
            IncomingMessageId second = newIncomingMessageId();

            ClearEmptiedReportsCommand command = new ClearEmptiedReportsCommand(USER_ID, List.of(first, second));

            assertThat(command.incomingMessageIds()).containsExactly(first, second);
        }

        @Test
        @DisplayName("when no ids are given - then the command is accepted and carries none")
        void whenNoIdsAreGiven_thenCommandIsAcceptedAndCarriesNone() {
            assertThat(new ClearEmptiedReportsCommand(USER_ID, List.of()).incomingMessageIds())
                    .isEmpty();
        }

        @Test
        @DisplayName("when the id list is absent - then throws InvalidProposalReportException")
        void whenIdListIsAbsent_thenThrowsInvalidProposalReportException() {
            assertThatThrownBy(() -> new ClearEmptiedReportsCommand(USER_ID, null))
                    .isInstanceOf(InvalidProposalReportException.class);
        }

        @Test
        @DisplayName("when the id list holds a null - then throws InvalidProposalReportException")
        void whenIdListHoldsANull_thenThrowsInvalidProposalReportException() {
            List<IncomingMessageId> withNull = Arrays.asList(newIncomingMessageId(), null);

            assertThatThrownBy(() -> new ClearEmptiedReportsCommand(USER_ID, withNull))
                    .isInstanceOf(InvalidProposalReportException.class);
        }

        @Test
        @DisplayName("when the list given is mutated afterwards - then the command still carries what it was given")
        void whenListGivenIsMutatedAfterwards_thenCommandStillCarriesWhatItWasGiven() {
            List<IncomingMessageId> mutable = new ArrayList<>(List.of(newIncomingMessageId()));
            ClearEmptiedReportsCommand command = new ClearEmptiedReportsCommand(USER_ID, mutable);

            mutable.add(newIncomingMessageId());

            assertThat(command.incomingMessageIds()).hasSize(1);
        }
    }
}
