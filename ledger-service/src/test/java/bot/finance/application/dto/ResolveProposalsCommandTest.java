package bot.finance.application.dto;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.value.IncomingMessageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ResolveProposalsCommandTest {

    @Nested
    @DisplayName("constructing a resolve-proposals command")
    class ResolveProposalsCommandConstructor {

        @Test
        @DisplayName("when every component is present and non-blank - then each reads back what was passed")
        void whenAllComponentsArePresent_thenEachComponentReadsBackWhatWasPassed() {
            IncomingMessageId reference = newIncomingMessageId();

            ResolveProposalsCommand command =
                    new ResolveProposalsCommand("42", "555", "1", "abc", reference, ProposalResolution.ACCEPT);

            assertThat(command.userExternalId()).isEqualTo("42");
            assertThat(command.conversationId()).isEqualTo("555");
            assertThat(command.reportMessageId()).isEqualTo("1");
            assertThat(command.interactionId()).isEqualTo("abc");
            assertThat(command.reference()).isEqualTo(reference);
            assertThat(command.resolution()).isEqualTo(ProposalResolution.ACCEPT);
        }

        @ParameterizedTest(name = "userExternalId={0}")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when userExternalId is null or blank - then throws InvalidIncomingMessageException")
        void whenUserExternalIdIsNullOrBlank_thenThrowsInvalidIncomingMessageException(String userExternalId) {
            assertThatThrownBy(() -> new ResolveProposalsCommand(
                            userExternalId, "555", "1", "abc", newIncomingMessageId(), ProposalResolution.ACCEPT))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @ParameterizedTest(name = "conversationId={0}")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when conversationId is null or blank - then throws InvalidIncomingMessageException")
        void whenConversationIdIsNullOrBlank_thenThrowsInvalidIncomingMessageException(String conversationId) {
            assertThatThrownBy(() -> new ResolveProposalsCommand(
                            "42", conversationId, "1", "abc", newIncomingMessageId(), ProposalResolution.ACCEPT))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @ParameterizedTest(name = "reportMessageId={0}")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when reportMessageId is null or blank - then throws InvalidIncomingMessageException")
        void whenReportMessageIdIsNullOrBlank_thenThrowsInvalidIncomingMessageException(String reportMessageId) {
            assertThatThrownBy(() -> new ResolveProposalsCommand(
                            "42", "555", reportMessageId, "abc", newIncomingMessageId(), ProposalResolution.ACCEPT))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @ParameterizedTest(name = "interactionId={0}")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when interactionId is null or blank - then throws InvalidIncomingMessageException")
        void whenInteractionIdIsNullOrBlank_thenThrowsInvalidIncomingMessageException(String interactionId) {
            assertThatThrownBy(() -> new ResolveProposalsCommand(
                            "42", "555", "1", interactionId, newIncomingMessageId(), ProposalResolution.ACCEPT))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @Test
        @DisplayName("when reference is null - then throws InvalidIncomingMessageException")
        void whenReferenceIsNull_thenThrowsInvalidIncomingMessageException() {
            assertThatThrownBy(
                            () -> new ResolveProposalsCommand("42", "555", "1", "abc", null, ProposalResolution.ACCEPT))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        @Test
        @DisplayName("when resolution is null - then throws InvalidIncomingMessageException")
        void whenResolutionIsNull_thenThrowsInvalidIncomingMessageException() {
            assertThatThrownBy(() -> new ResolveProposalsCommand("42", "555", "1", "abc", newIncomingMessageId(), null))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }
    }
}
