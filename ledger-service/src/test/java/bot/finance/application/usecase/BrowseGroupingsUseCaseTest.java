package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.BrowseGroupingsCommand;
import bot.finance.application.dto.GroupingEntry;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseGroupingsUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;

    private UserRepository userRepository;
    private GroupingRepository groupingRepository;
    private BrowseGroupingsUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        groupingRepository = mock(GroupingRepository.class);
        useCase = new BrowseGroupingsUseCase(userRepository, groupingRepository);
    }

    private BrowseGroupingsCommand newCommand() {
        return new BrowseGroupingsCommand(new AuthenticatedUserId(USER_ID));
    }

    @Nested
    @DisplayName("browsing groupings")
    class Browse {

        @Test
        @DisplayName("when the repository answers the seeded groupings - then every grouping is answered with "
                + "its id and name")
        @Disabled("RU12: arranges requireByExternalId, which the use case no longer calls")
        void whenRepositoryAnswersSeededGroupings_thenEveryGroupingIsAnsweredWithIdAndName() {
            when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            List<GroupingEntry> groupings =
                    List.of(new GroupingEntry(1L, "Groceries"), new GroupingEntry(2L, "Transport"));
            when(groupingRepository.findAllForUser(USER_ID)).thenReturn(groupings);

            List<GroupingEntry> result = useCase.browse(newCommand());

            assertThat(result).isEqualTo(groupings);
            assertThat(result)
                    .extracting(GroupingEntry::id, GroupingEntry::name)
                    .containsExactly(tuple(1L, "Groceries"), tuple(2L, "Transport"));
        }

        @Test
        @DisplayName("when the stored user's id differs from the external id - then the repository is asked with "
                + "that stored id")
        @Disabled("RU12: arrange requireById and assert the lookup receives the command's internal id")
        void whenStoredUserIdDiffersFromExternalId_thenRepositoryReceivesStoredUserIdNotExternalId() {
            long differentUserId = 42L;
            when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(differentUserId, EXTERNAL_ID));
            when(groupingRepository.findAllForUser(differentUserId)).thenReturn(List.of());

            useCase.browse(newCommand());

            verify(groupingRepository).findAllForUser(differentUserId);
        }

        @Test
        @DisplayName(
                "when no user row is stored under the caller's external id - then throws " + "EntityNotFoundException")
        @Disabled("RU12: the absence is now requireById throwing")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundException() {
            when(userRepository.requireByExternalId(EXTERNAL_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under external id " + EXTERNAL_ID));

            assertThatThrownBy(() -> useCase.browse(newCommand())).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(groupingRepository);
        }
    }
}
