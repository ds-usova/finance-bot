package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProposalIdsTest {

    @Nested
    @DisplayName("building proposal ids from a caller-supplied list")
    class Of {

        @Test
        @DisplayName("when of is called with two distinct ids above zero - then the value carries both in the "
                + "order given")
        void whenCalledWithTwoDistinctIdsAboveZero_thenValueCarriesBothInOrderGiven() {
            ProposalIds ids = ProposalIds.of(List.of(5L, 3L));

            assertThat(ids.ids()).containsExactly(5L, 3L);
        }

        @Test
        @DisplayName("when of is called with exactly 100 distinct ids - then it is accepted")
        void whenCalledWithExactly100DistinctIds_thenItIsAccepted() {
            List<Long> ids =
                    LongStream.rangeClosed(1, ProposalIds.MAX_IDS).boxed().toList();

            assertThatCode(() -> ProposalIds.of(ids)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidIdLists")
        @DisplayName("when of is called with invalid list - then throws InvalidExpenseAcceptanceException naming "
                + "the broken field and bound")
        void whenCalledWithInvalidList_thenThrowsInvalidExpenseAcceptanceExceptionNamingBrokenFieldAndBound(
                List<Long> ids, String expectedField, String expectedBound) {
            assertThatThrownBy(() -> ProposalIds.of(ids))
                    .isInstanceOf(InvalidExpenseAcceptanceException.class)
                    .hasMessageContaining(expectedField)
                    .hasMessageContaining(expectedBound);
        }

        static Stream<Arguments> invalidIdLists() {
            List<Long> tooMany =
                    LongStream.rangeClosed(1, ProposalIds.MAX_IDS + 1).boxed().toList();

            return Stream.of(
                    Arguments.of(List.of(), "ids", "empty"),
                    Arguments.of(null, "ids", "empty"),
                    Arguments.of(tooMany, "ids", String.valueOf(ProposalIds.MAX_IDS)),
                    Arguments.of(List.of(1L, 1L), "ids", "duplicate"),
                    Arguments.of(List.of(0L), "id", "1"),
                    Arguments.of(List.of(-1L), "id", "1"));
        }
    }
}
