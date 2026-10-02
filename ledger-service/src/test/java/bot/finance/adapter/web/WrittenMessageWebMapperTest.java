package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.api.model.ListWrittenMessages200ResponseInner;
import bot.finance.api.model.WrittenMessageOutcome;
import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WrittenMessageWebMapperTest {

    private static final AuthenticatedUserId USER = new AuthenticatedUserId(7L);
    private static final String TEXT = "coffee 3.40, taxi home 14";

    private static WrittenMessageView view(
            long id, WrittenMessageStatus status, Optional<ReportOutcome> outcome) {
        return new WrittenMessageView(id, USER.userId(), TEXT, status, outcome);
    }

    @Nested
    @DisplayName("toResponse()")
    class ToResponse {

        @ParameterizedTest
        @EnumSource(
                value = WrittenMessageStatus.class,
                names = {"WAITING", "READING"})
        @DisplayName("when an unsettled message is mapped - then it is reading, without outcome and without days")
        void whenUnsettledMessageIsMapped_thenItIsReadingWithoutOutcomeAndDays(WrittenMessageStatus status) {
            ListWrittenMessages200ResponseInner response =
                    WrittenMessageWebMapper.toResponse(view(42L, status, Optional.empty()));

            assertThat(response.getId()).isEqualTo(42L);
            assertThat(String.valueOf(response.getState())).isEqualTo("READING");
            assertThat(response.getText()).isEqualTo(TEXT);
            assertThat(response.getOutcome().orElse(null)).isNull();
            assertThat(response.getDays()).isEmpty();
        }

        @Test
        @DisplayName("when an answer settled as recorded is mapped - then it is done with its days, oldest first")
        void whenAnswerSettledAsRecordedIsMapped_thenItIsDoneWithItsDaysOldestFirst() {
            LocalDate first = LocalDate.of(2026, 9, 30);
            LocalDate second = LocalDate.of(2026, 10, 1);
            WrittenMessageAnswer answer = new WrittenMessageAnswer(
                    view(42L, WrittenMessageStatus.DONE, Optional.of(ReportOutcome.RECORDED)),
                    List.of(first, second));

            ListWrittenMessages200ResponseInner response = WrittenMessageWebMapper.toResponse(answer);

            assertThat(response.getId()).isEqualTo(42L);
            assertThat(String.valueOf(response.getState())).isEqualTo("DONE");
            assertThat(response.getOutcome().orElse(null)).isEqualTo(WrittenMessageOutcome.RECORDED);
            assertThat(response.getDays()).containsExactly(first, second);
        }

        @Test
        @DisplayName("when an answer settled with nothing identified is mapped - then its days are present and empty")
        void whenAnswerSettledWithNothingIdentifiedIsMapped_thenItsDaysArePresentAndEmpty() {
            WrittenMessageAnswer answer = new WrittenMessageAnswer(
                    view(42L, WrittenMessageStatus.DONE, Optional.of(ReportOutcome.NOTHING_IDENTIFIED)),
                    List.of());

            ListWrittenMessages200ResponseInner response = WrittenMessageWebMapper.toResponse(answer);

            assertThat(response.getOutcome().orElse(null)).isEqualTo(WrittenMessageOutcome.NOTHING_IDENTIFIED);
            assertThat(response.getDays()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("toResponses()")
    class ToResponses {

        @Test
        @DisplayName("when one reading message is mapped - then the list holds that one as reading")
        void whenOneReadingMessageIsMapped_thenListHoldsThatOneAsReading() {
            List<ListWrittenMessages200ResponseInner> responses = WrittenMessageWebMapper.toResponses(
                    List.of(view(42L, WrittenMessageStatus.READING, Optional.empty())));

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(42L);
            assertThat(String.valueOf(responses.get(0).getState())).isEqualTo("READING");
        }

        @Test
        @DisplayName("when no message is mapped - then the list is empty")
        void whenNoMessageIsMapped_thenListIsEmpty() {
            assertThat(WrittenMessageWebMapper.toResponses(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("toBrowseWrittenMessagesCommand()")
    class ToBrowseWrittenMessagesCommand {

        @Test
        @DisplayName("when the state is READING - then the command carries the caller")
        void whenStateIsReading_thenCommandCarriesTheCaller() {
            BrowseWrittenMessagesCommand command =
                    WrittenMessageWebMapper.toBrowseWrittenMessagesCommand("READING", USER);

            assertThat(command.userId()).isEqualTo(USER);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"WAITING", "DONE", "reading", "", " "})
        @DisplayName("when the state is absent or not READING - then InvalidWrittenMessageException is thrown")
        void whenStateIsAbsentOrNotReading_thenThrowsInvalidWrittenMessageException(String state) {
            assertThatThrownBy(() -> WrittenMessageWebMapper.toBrowseWrittenMessagesCommand(state, USER))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }
    }
}
