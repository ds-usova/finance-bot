package bot.finance.ai.adapter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryRowChange;
import bot.finance.ai.domain.value.ChangeOperation;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.SpendingKind;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.SpendingRowChange;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChangeStreamEntryReaderTest {

    private static final String PAYLOAD_MISSING_DESCRIPTION =
            """
            {"op":"c","source":{"table":"expense","lsn":100,"txId":"tx-9","ts_ms":1700000000000},\
            "before":null,"after":{"id":1,"user_id":10,"category_id":5,"merchant":null,\
            "amount_minor_units":500,"currency_code":"USD","incoming_message_id":null}}""";

    private final ChangeStreamEntryReader reader = new ChangeStreamEntryReader();

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("when a proposal c body carries enrichment - then the command holds a PROPOSAL CREATED "
                + "change built from it")
        void whenProposalCreatedBodyCarriesEnrichment_thenCommandHoldsProposalCreatedChange() {
            Map<String, String> body = ChangeStreamEntryFixtures.proposalCreated(
                    42L, 7L, "msg-1", "Coffee", "Roastery", 550L, "USD", 3L, "Dining", "Food", "tx-100");

            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-1", body);

            assertThat(result).isPresent();
            LearnMessageOutcomeCommand command = result.orElseThrow();
            assertThat(command.deliveryId()).isEqualTo("entry-1");
            assertThat(command.change()).isInstanceOf(SpendingRowChange.class);
            SpendingRowChange change = (SpendingRowChange) command.change();
            assertThat(change.kind()).isEqualTo(SpendingKind.PROPOSAL);
            assertThat(change.op()).isEqualTo(ChangeOperation.CREATED);
            assertThat(change.transactionId()).isEqualTo("tx-100");
            assertThat(change.after()).isPresent();
            SpendingRow after = change.after().orElseThrow();
            assertThat(after.id()).isEqualTo(42L);
            assertThat(after.userId()).isEqualTo(7L);
            assertThat(after.incomingMessageId()).contains("msg-1");
            assertThat(after.description()).isEqualTo("Coffee");
            assertThat(after.merchant()).contains("Roastery");
            assertThat(after.amountMinorUnits()).isEqualTo(550L);
            assertThat(after.currencyCode()).isEqualTo(CurrencyCode.of("USD"));
            assertThat(after.categoryId()).isEqualTo(3L);
            assertThat(after.categoryName()).contains("Dining");
            assertThat(after.groupingName()).contains("Food");
        }

        @Test
        @DisplayName("when a proposal d body is read - then the change is DELETED with a before row and no after")
        void whenProposalDeletedBodyIsRead_thenChangeIsDeletedWithBeforeRowAndNoAfter() {
            Map<String, String> body = ChangeStreamEntryFixtures.proposalDeleted(
                    42L, 7L, "msg-1", "Coffee", "Roastery", 550L, "USD", 3L, "tx-101");

            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-2", body);

            SpendingRowChange change = (SpendingRowChange) result.orElseThrow().change();
            assertThat(change.op()).isEqualTo(ChangeOperation.DELETED);
            assertThat(change.before()).isPresent();
            assertThat(change.after()).isEmpty();
        }

        @Test
        @DisplayName("when an expense u body's enrichment names differ before and after - then each row keeps its "
                + "own names")
        void whenExpenseUpdatedBodyHasDifferentEnrichmentNames_thenBeforeAndAfterHoldTheirOwnNames() {
            Map<String, String> body = ChangeStreamEntryFixtures.expenseUpdated(
                    55L,
                    8L,
                    "msg-2",
                    "Groceries",
                    "Market",
                    1200L,
                    "USD",
                    4L,
                    "OldCategory",
                    "OldGrouping",
                    "NewCategory",
                    "NewGrouping",
                    "tx-102");

            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-3", body);

            SpendingRowChange change = (SpendingRowChange) result.orElseThrow().change();
            SpendingRow before = change.before().orElseThrow();
            SpendingRow after = change.after().orElseThrow();
            assertThat(before.categoryName()).contains("OldCategory");
            assertThat(before.groupingName()).contains("OldGrouping");
            assertThat(after.categoryName()).contains("NewCategory");
            assertThat(after.groupingName()).contains("NewGrouping");
        }

        @Test
        @DisplayName("when an expense c body's row has a null incoming_message_id - then the after row's message "
                + "id is empty")
        void whenExpenseCreatedBodyHasNullIncomingMessageId_thenAfterRowMessageIdIsEmpty() {
            Map<String, String> body = ChangeStreamEntryFixtures.expenseCreated(
                    56L, 8L, null, "Groceries", "Market", 1200L, "USD", 4L, "Food", "Living", "tx-103");

            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-4", body);

            SpendingRowChange change = (SpendingRowChange) result.orElseThrow().change();
            assertThat(change.after().orElseThrow().incomingMessageId()).isEmpty();
        }

        @Test
        @DisplayName("when a spending body has no enrichment block - then both names are empty on every row")
        void whenSpendingBodyHasNoEnrichmentBlock_thenBothNamesAreEmptyOnEveryRow() {
            Map<String, String> body = ChangeStreamEntryFixtures.expenseUpdated(
                    57L, 8L, "msg-3", "Rent", null, 90000L, "USD", 6L, null, null, null, null, "tx-104");

            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-5", body);

            SpendingRowChange change = (SpendingRowChange) result.orElseThrow().change();
            SpendingRow before = change.before().orElseThrow();
            SpendingRow after = change.after().orElseThrow();
            assertThat(before.categoryName()).isEmpty();
            assertThat(before.groupingName()).isEmpty();
            assertThat(after.categoryName()).isEmpty();
            assertThat(after.groupingName()).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("bot.finance.ai.adapter.redis.ChangeStreamEntryReaderTest#categoryUpdateBodies")
        @DisplayName("when a category u body is read - then the after row's parentId matches what the row carried")
        void whenCategoryUpdatedBodyIsRead_thenAfterRowParentIdMatchesRow(
                Map<String, String> body, Optional<Long> expectedParentId) {
            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-6", body);

            CategoryRowChange change = (CategoryRowChange) result.orElseThrow().change();
            assertThat(change.after()).isPresent();
            assertThat(change.after().orElseThrow().parentId()).isEqualTo(expectedParentId);
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("bot.finance.ai.adapter.redis.ChangeStreamEntryReaderTest#ignoredBodies")
        @DisplayName("when the body is an r op or names a table this reader ignores - then it answers empty")
        void whenBodyIsIgnored_thenAnswersEmpty(Map<String, String> body, String caseName) {
            Optional<LearnMessageOutcomeCommand> result = reader.read("entry-7", body);

            assertThat(result).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("bot.finance.ai.adapter.redis.ChangeStreamEntryReaderTest#invalidBodies")
        @DisplayName("when the body is not a valid change event - then InvalidValueException is thrown")
        void whenBodyIsNotValidChangeEvent_thenInvalidValueExceptionIsThrown(
                Map<String, String> body, String caseName) {
            assertThatThrownBy(() -> reader.read("entry-8", body)).isInstanceOf(InvalidValueException.class);
        }
    }

    static Stream<Arguments> categoryUpdateBodies() {
        return Stream.of(
                Arguments.of(
                        ChangeStreamEntryFixtures.categoryUpdatedWithParent(9L, 8L, 2L, "Old", "New", "tx-105"),
                        Optional.of(2L)),
                Arguments.of(
                        ChangeStreamEntryFixtures.categoryUpdatedWithoutParent(9L, 8L, "Old", "New", "tx-106"),
                        Optional.empty()));
    }

    static Stream<Arguments> ignoredBodies() {
        return Stream.of(
                Arguments.of(ChangeStreamEntryFixtures.snapshotRead("expense", "{}", "tx-107"), "an r body"),
                Arguments.of(ChangeStreamEntryFixtures.unknownTable("tx-108"), "a body naming an unwatched table"));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of(ChangeStreamEntryFixtures.withNoPayload(), "a body with no payload field"),
                Arguments.of(Map.of("payload", "not-json"), "a body whose payload is not JSON"),
                Arguments.of(Map.of("payload", PAYLOAD_MISSING_DESCRIPTION), "a row missing a required column"));
    }
}
