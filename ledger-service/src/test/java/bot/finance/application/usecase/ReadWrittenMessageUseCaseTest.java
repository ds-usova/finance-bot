package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.WrittenMessageNotFoundException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReadWrittenMessageUseCaseTest {

    private static final long CALLER_ID = 5L;
    private static final long STORED_USER_ID = 7L;
    private static final long MESSAGE_ID = 42L;
    private static final String TEXT = "coffee 3.40, taxi home 14";
    private static final IncomingMessageId REFERENCE = IncomingMessageId.of("web:42");

    private UserRepository userRepository;
    private WrittenMessageRepository writtenMessageRepository;
    private ExpenseRepository expenseRepository;
    private ReadWrittenMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        useCase = new ReadWrittenMessageUseCase(userRepository, writtenMessageRepository, expenseRepository);
    }

    private ReadWrittenMessageCommand newCommand() {
        return new ReadWrittenMessageCommand(new AuthenticatedUserId(CALLER_ID), MESSAGE_ID);
    }

    private void givenStoredUser() {
        when(userRepository.requireById(CALLER_ID)).thenReturn(User.stored(STORED_USER_ID, "555"));
    }

    private WrittenMessageView viewOf(WrittenMessageStatus status, Optional<ReportOutcome> outcome) {
        WrittenMessageView view = new WrittenMessageView(MESSAGE_ID, STORED_USER_ID, TEXT, status, outcome);
        when(writtenMessageRepository.findForUser(STORED_USER_ID, MESSAGE_ID)).thenReturn(Optional.of(view));
        return view;
    }

    @Nested
    @DisplayName("reading a written message")
    class Read {

        @Test
        @DisplayName("when the message is DONE and recorded - then the answer carries the message and its day")
        void whenMessageIsDoneAndRecorded_thenAnswerCarriesTheMessageAndItsDay() {
            givenStoredUser();
            WrittenMessageView view = viewOf(WrittenMessageStatus.DONE, Optional.of(ReportOutcome.RECORDED));
            LocalDate day = LocalDate.parse("2026-08-05");
            when(expenseRepository.findDaysByMessageReference(STORED_USER_ID, REFERENCE))
                    .thenReturn(List.of(day));

            WrittenMessageAnswer answer = useCase.read(newCommand());

            assertThat(answer.message()).isEqualTo(view);
            assertThat(answer.days()).containsExactly(day);
            verify(expenseRepository).findDaysByMessageReference(STORED_USER_ID, REFERENCE);
        }

        @Test
        @DisplayName("when the message is DONE and nothing is stored under it - then the answer carries no days")
        void whenMessageIsDoneAndNothingIsStored_thenAnswerCarriesNoDays() {
            givenStoredUser();
            WrittenMessageView view = viewOf(WrittenMessageStatus.DONE, Optional.of(ReportOutcome.NOTHING_IDENTIFIED));
            when(expenseRepository.findDaysByMessageReference(STORED_USER_ID, REFERENCE))
                    .thenReturn(List.of());

            WrittenMessageAnswer answer = useCase.read(newCommand());

            assertThat(answer.message()).isEqualTo(view);
            assertThat(answer.days()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(
                value = WrittenMessageStatus.class,
                names = {"WAITING", "READING"})
        @DisplayName("when the message is not yet DONE - then the answer carries no days and no expense is read")
        void whenMessageIsNotYetDone_thenAnswerCarriesNoDaysAndNoExpenseIsRead(WrittenMessageStatus status) {
            givenStoredUser();
            WrittenMessageView view = viewOf(status, Optional.empty());

            WrittenMessageAnswer answer = useCase.read(newCommand());

            assertThat(answer.message()).isEqualTo(view);
            assertThat(answer.days()).isEmpty();
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when the repository finds nothing for the caller - then throws WrittenMessageNotFoundException")
        void whenRepositoryFindsNothing_thenThrowsWrittenMessageNotFoundException() {
            givenStoredUser();
            when(writtenMessageRepository.findForUser(STORED_USER_ID, MESSAGE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.read(newCommand())).isInstanceOf(WrittenMessageNotFoundException.class);

            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when no user row is stored for the caller - then throws EntityNotFoundException")
        void whenNoUserRowIsStored_thenThrowsEntityNotFoundExceptionAndNoMessageIsLookedFor() {
            when(userRepository.requireById(CALLER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under id " + CALLER_ID));

            assertThatThrownBy(() -> useCase.read(newCommand())).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(writtenMessageRepository);
            verifyNoInteractions(expenseRepository);
        }
    }
}
