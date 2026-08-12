package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.adapter.cdc.CategoryNames;
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
@Import(CategoryNameReader.class)
class CategoryNameReaderTest {

    @Autowired
    private CategoryNameReader reader;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("finding a category's name and its grouping's")
    class FindNames {

        @Test
        @DisplayName("when a category is stored under a grouping - then answers both the category's name and its "
                + "grouping's")
        void whenCategoryStoredUnderAGrouping_thenAnswersCategoryNameAndGroupingName() {
            long userId = storedUserId("category-names-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            long categoryId = storedCategoryId(userId, groupingId, "Supermarkets");

            Optional<CategoryNames> found = reader.findNames(categoryId);

            assertThat(found).contains(new CategoryNames("Supermarkets", "Groceries"));
        }

        @Test
        @DisplayName("when the id given is one no category row carries - then answers nothing")
        void whenIdMatchesNoCategoryRow_thenAnswersNothing() {
            Optional<CategoryNames> found = reader.findNames(-1L);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("when the id given is a grouping's own id - then answers nothing since a grouping has no parent")
        void whenIdIsAGroupingsOwnId_thenAnswersNothing() {
            long userId = storedUserId("grouping-own-id-user");
            long groupingId = storedGroupingId(userId, "Travel");

            Optional<CategoryNames> found = reader.findNames(groupingId);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("when two people hold a category of the same name - then answers that person's names by id alone")
        void whenTwoPeopleShareACategoryName_thenAnswersThatPersonsNamesByIdAlone() {
            long firstUserId = storedUserId("first-shared-name-user");
            long secondUserId = storedUserId("second-shared-name-user");
            long firstGroupingId = storedGroupingId(firstUserId, "Home");
            long secondGroupingId = storedGroupingId(secondUserId, "Work");
            long firstCategoryId = storedCategoryId(firstUserId, firstGroupingId, "Supplies");
            storedCategoryId(secondUserId, secondGroupingId, "Supplies");

            Optional<CategoryNames> found = reader.findNames(firstCategoryId);

            assertThat(found).contains(new CategoryNames("Supplies", "Home"));
        }
    }

    @Nested
    @DisplayName("with a mocked store")
    class WithAMockedStore {

        private final CategoryEntityRepository mockedCategoryEntityRepository = mock(CategoryEntityRepository.class);
        private final CategoryNameReader mockedReader = new CategoryNameReader(mockedCategoryEntityRepository);

        @Test
        @DisplayName("when the database is unreachable - then it throws PersistenceFailedException wrapping it")
        void whenDatabaseIsUnreachable_thenThrowsPersistenceFailedExceptionWrappingIt() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedCategoryEntityRepository.findCategoryNames(42L)).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedReader.findNames(42L))
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
