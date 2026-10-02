package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.UserRepository;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseWrittenMessagesUseCaseTest {

    private static final long CALLER_ID = 5L;
    private static final long STORED_USER_ID = 7L;

    private UserRepository userRepository;
    private WrittenMessageRepository writtenMessageRepository;
    private BrowseWrittenMessagesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        useCase = new BrowseWrittenMessagesUseCase(userRepository, writtenMessageRepository);
    }

    private BrowseWrittenMessagesCommand newCommand() {
        return new BrowseWrittenMessagesCommand(new AuthenticatedUserId(CALLER_ID));
    }

    @Nested
    @DisplayName("browsing written messages")
    class Browse {

        @Test
        @DisplayName("when the repository answers one unsettled message - then the answer is that message")
        void whenRepositoryAnswersOneUnsettledMessage_thenAnswerIsThatMessage() {
            when(userRepository.requireById(CALLER_ID)).thenReturn(User.stored(STORED_USER_ID, "555"));
            WrittenMessageView view = new WrittenMessageView(
                    42L, STORED_USER_ID, "coffee 3.40", WrittenMessageStatus.READING, Optional.empty());
            when(writtenMessageRepository.findUnsettledForUser(STORED_USER_ID)).thenReturn(List.of(view));

            List<WrittenMessageView> answer = useCase.browse(newCommand());

            assertThat(answer).containsExactly(view);
        }

        @Test
        @DisplayName("when the repository answers none - then the answer is empty and asked by the stored id")
        void whenRepositoryAnswersNone_thenAnswerIsEmptyAndAskedByTheStoredId() {
            when(userRepository.requireById(CALLER_ID)).thenReturn(User.stored(STORED_USER_ID, "555"));
            when(writtenMessageRepository.findUnsettledForUser(STORED_USER_ID)).thenReturn(List.of());

            List<WrittenMessageView> answer = useCase.browse(newCommand());

            assertThat(answer).isEmpty();
            verify(writtenMessageRepository).findUnsettledForUser(STORED_USER_ID);
        }

        @Test
        @DisplayName("when no user row is stored for the caller - then throws EntityNotFoundException")
        void whenNoUserRowIsStored_thenThrowsEntityNotFoundExceptionAndRepositoryIsNeverAsked() {
            when(userRepository.requireById(CALLER_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under id " + CALLER_ID));

            assertThatThrownBy(() -> useCase.browse(newCommand())).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(writtenMessageRepository);
        }
    }
}
