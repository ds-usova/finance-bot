package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.adapter.cdc.CategoryRow;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(CategoryRowReader.class)
class CategoryRowReaderTest {

    @Autowired
    private CategoryRowReader reader;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("finding one category row by its id")
    class FindRow {

        @Test
        @DisplayName("when a category is stored under a grouping - then answers its own name and its grouping's id")
        void whenCategoryStoredUnderAGrouping_thenAnswersOwnNameAndGroupingId() {
            long userId = storedUserId("category-row-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            long categoryId = storedCategoryId(userId, groupingId, "Supermarkets");

            Optional<CategoryRow> found = reader.findRow(categoryId);

            assertThat(found).contains(new CategoryRow("Supermarkets", Optional.of(groupingId)));
        }

        @Test
        @DisplayName("when the id given is one no category row carries - then answers nothing")
        void whenIdMatchesNoCategoryRow_thenAnswersNothing() {
            Optional<CategoryRow> found = reader.findRow(-1L);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("when the id given is a grouping's own id - then answers its name and no parent")
        void whenIdIsAGroupingsOwnId_thenAnswersItsNameAndNoParent() {
            long userId = storedUserId("grouping-own-id-user");
            long groupingId = storedGroupingId(userId, "Travel");

            Optional<CategoryRow> found = reader.findRow(groupingId);

            assertThat(found).contains(new CategoryRow("Travel", Optional.empty()));
        }

        @Test
        @DisplayName("when two people hold a category of the same name - then answers that person's row by id alone")
        void whenTwoPeopleShareACategoryName_thenAnswersThatPersonsRowByIdAlone() {
            long firstUserId = storedUserId("first-shared-name-user");
            long secondUserId = storedUserId("second-shared-name-user");
            long firstGroupingId = storedGroupingId(firstUserId, "Home");
            long secondGroupingId = storedGroupingId(secondUserId, "Work");
            long firstCategoryId = storedCategoryId(firstUserId, firstGroupingId, "Supplies");
            storedCategoryId(secondUserId, secondGroupingId, "Supplies");

            Optional<CategoryRow> found = reader.findRow(firstCategoryId);

            assertThat(found).contains(new CategoryRow("Supplies", Optional.of(firstGroupingId)));
        }
    }

    @Nested
    @DisplayName("with a mocked store")
    class WithAMockedStore {

        private final CategoryEntityRepository mockedCategoryEntityRepository = mock(CategoryEntityRepository.class);
        private final CategoryRowReader mockedReader = new CategoryRowReader(mockedCategoryEntityRepository);

        @Test
        @DisplayName("when the database is unreachable - then it throws PersistenceFailedException wrapping it")
        void whenDatabaseIsUnreachable_thenThrowsPersistenceFailedExceptionWrappingIt() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedCategoryEntityRepository.findCategoryRow(42L)).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedReader.findRow(42L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedGroupingId(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    private long storedCategoryId(long userId, long groupingId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, name);
    }
}
