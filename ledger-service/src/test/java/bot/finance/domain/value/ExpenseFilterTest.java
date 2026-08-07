package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseFilterException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpenseFilterTest {

    @Nested
    @DisplayName("constructing an expense filter")
    class ExpenseFilterConstructor {

        @ParameterizedTest
        @MethodSource("bot.finance.domain.value.ExpenseFilterTest#validLimits")
        @DisplayName("when limit is 1 or MAX_LIMIT and offset is zero - then the filter carries the values it was given")
        void whenLimitIsOneOrMaxLimitAndOffsetIsZero_thenTheFilterCarriesTheValuesItWasGiven(int limit) {
            ExpenseFilter filter = new ExpenseFilter(ExpenseStatus.PENDING, 7L, null, limit, 0);

            assertThat(filter.limit()).isEqualTo(limit);
            assertThat(filter.offset()).isZero();
            assertThat(filter.status()).isEqualTo(ExpenseStatus.PENDING);
            assertThat(filter.categoryId()).isEqualTo(7L);
        }

        @ParameterizedTest
        @MethodSource("bot.finance.domain.value.ExpenseFilterTest#invalidLimits")
        @DisplayName("when limit breaks its bound - then throws InvalidExpenseFilterException naming the limit and the bound")
        void whenLimitBreaksItsBound_thenThrowsInvalidExpenseFilterExceptionNamingLimitAndBound(int limit, String bound) {
            assertThatThrownBy(() -> new ExpenseFilter(null, null, null, limit, 0))
                    .isInstanceOf(InvalidExpenseFilterException.class)
                    .hasMessageContaining("limit")
                    .hasMessageContaining(bound);
        }

        @Test
        @DisplayName("when offset is negative - then throws InvalidExpenseFilterException naming the offset")
        void whenOffsetIsNegative_thenThrowsInvalidExpenseFilterExceptionNamingOffset() {
            assertThatThrownBy(() -> new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, -1))
                    .isInstanceOf(InvalidExpenseFilterException.class)
                    .hasMessageContaining("offset");
        }

        @Test
        @DisplayName(
                "when status, categoryId and period are null - then the filter is accepted because every dimension is optional")
        void whenStatusCategoryIdAndPeriodAreNull_thenTheFilterIsAcceptedBecauseEveryDimensionIsOptional() {
            ExpenseFilter filter = new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);

            assertThat(filter.status()).isNull();
            assertThat(filter.categoryId()).isNull();
            assertThat(filter.period()).isNull();
            assertThat(filter.limit()).isEqualTo(ExpenseFilter.DEFAULT_LIMIT);
            assertThat(filter.offset()).isZero();
        }
    }

    static Stream<Integer> validLimits() {
        return Stream.of(1, ExpenseFilter.MAX_LIMIT);
    }

    static Stream<Arguments> invalidLimits() {
        return Stream.of(
                Arguments.of(0, "1"),
                Arguments.of(-1, "1"),
                Arguments.of(ExpenseFilter.MAX_LIMIT + 1, String.valueOf(ExpenseFilter.MAX_LIMIT)));
    }
}
