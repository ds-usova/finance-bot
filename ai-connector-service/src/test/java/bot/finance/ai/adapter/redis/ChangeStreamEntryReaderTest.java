package bot.finance.ai.adapter.redis;

import static bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures.defaultPayload;
import static bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures.withPayload;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_AMOUNT;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_CATEGORY_ID;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_CATEGORY_NAME;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_CURRENCY;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_EXPENSE_ID;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_GROUPING_ID;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_GROUPING_NAME;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_MESSAGE_ID;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.DEFAULT_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryRef;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
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

    private static final String MERCHANT = "Corner Shop";
    private static final String DESCRIPTION = "lunch";

    private final ChangeStreamEntryReader reader = new ChangeStreamEntryReader();

    /** The shape every {@link ChangeStreamEntryFixtures} spending-entry builder shares. */
    @FunctionalInterface
    private interface SpendingEntry {
        Map<String, String> build(
                long eventId,
                long userId,
                String incomingMessageId,
                long expenseId,
                String description,
                String merchant,
                String amount,
                String currencyCode,
                long categoryId,
                String categoryName,
                Long groupingId,
                String groupingName);
    }

    private static Map<String, String> withDefaults(SpendingEntry builder) {
        return builder.build(
                1L,
                DEFAULT_USER_ID,
                DEFAULT_MESSAGE_ID,
                DEFAULT_EXPENSE_ID,
                DESCRIPTION,
                MERCHANT,
                DEFAULT_AMOUNT,
                DEFAULT_CURRENCY,
                DEFAULT_CATEGORY_ID,
                DEFAULT_CATEGORY_NAME,
                DEFAULT_GROUPING_ID,
                DEFAULT_GROUPING_NAME);
    }

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("when a ProposalCreated entry names a message - then the command holds its delivery id, "
                + "position, PROPOSED and the row")
        void whenProposalCreatedEntryNamesMessage_thenCommandHoldsDeliveryIdPositionProposedAndRow() {
            Map<String, String> body = withDefaults(ChangeStreamEntryFixtures::proposalCreated);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-3", body);

            assertThat(result).isPresent();
            LearnMessageOutcomeCommand command = result.get();
            assertThat(command.deliveryId()).isEqualTo("1700000000000-3");
            assertThat(command.position()).isEqualTo(new StreamPosition(1_700_000_000_000L, 3L));
            assertThat(command.status()).isEqualTo(RecordedStatus.PROPOSED);
            SpendingRow entry = command.entry();
            assertThat(entry.expenseId()).isEqualTo(DEFAULT_EXPENSE_ID);
            assertThat(entry.description()).isEqualTo("lunch");
            assertThat(entry.merchant()).contains(MERCHANT);
            assertThat(entry.amount()).isEqualTo(DEFAULT_AMOUNT);
            assertThat(entry.currencyCode()).isEqualTo(CurrencyCode.of(DEFAULT_CURRENCY));
            assertThat(entry.category()).isEqualTo(new CategoryRef(DEFAULT_CATEGORY_ID, DEFAULT_CATEGORY_NAME));
            assertThat(entry.grouping()).contains(new CategoryRef(DEFAULT_GROUPING_ID, DEFAULT_GROUPING_NAME));
        }

        @Test
        @DisplayName("when a ProposalRefiled entry is read - then the command's status is PROPOSED")
        void whenProposalRefiledEntryIsRead_thenCommandStatusIsProposed() {
            Map<String, String> body = withDefaults(ChangeStreamEntryFixtures::proposalRefiled);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(RecordedStatus.PROPOSED);
        }

        @Test
        @DisplayName("when a ProposalDiscarded entry's own status reads PENDING - then the command's status is "
                + "DISCARDED")
        void whenProposalDiscardedEntryOwnStatusReadsPending_thenCommandStatusIsDiscarded() {
            Map<String, String> body = withDefaults(ChangeStreamEntryFixtures::proposalDiscarded);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(RecordedStatus.DISCARDED);
        }

        @ParameterizedTest
        @MethodSource("bot.finance.ai.adapter.redis.ChangeStreamEntryReaderTest#acceptedEntries")
        @DisplayName("when an accepted-type entry is read - then the command's status is ACCEPTED")
        void whenAcceptedEntryIsRead_thenCommandStatusIsAccepted(Map<String, String> body) {
            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(RecordedStatus.ACCEPTED);
        }

        @Test
        @DisplayName("when the payload's grouping is null - then the entry's grouping is empty and its category "
                + "is unchanged")
        void whenPayloadGroupingIsNull_thenEntryGroupingIsEmptyAndCategoryUnchanged() {
            Map<String, String> body = ChangeStreamEntryFixtures.proposalCreated(
                    1L,
                    DEFAULT_USER_ID,
                    DEFAULT_MESSAGE_ID,
                    DEFAULT_EXPENSE_ID,
                    DESCRIPTION,
                    MERCHANT,
                    DEFAULT_AMOUNT,
                    DEFAULT_CURRENCY,
                    DEFAULT_CATEGORY_ID,
                    DEFAULT_CATEGORY_NAME,
                    null,
                    null);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isPresent();
            SpendingRow entry = result.get().entry();
            assertThat(entry.grouping()).isEmpty();
            assertThat(entry.category()).isEqualTo(new CategoryRef(DEFAULT_CATEGORY_ID, DEFAULT_CATEGORY_NAME));
        }

        @Test
        @DisplayName("when the payload's merchant is null - then the entry's merchant is empty")
        void whenPayloadMerchantIsNull_thenEntryMerchantIsEmpty() {
            Map<String, String> body = ChangeStreamEntryFixtures.proposalCreated(
                    1L,
                    DEFAULT_USER_ID,
                    DEFAULT_MESSAGE_ID,
                    DEFAULT_EXPENSE_ID,
                    DESCRIPTION,
                    null,
                    DEFAULT_AMOUNT,
                    DEFAULT_CURRENCY,
                    DEFAULT_CATEGORY_ID,
                    DEFAULT_CATEGORY_NAME,
                    DEFAULT_GROUPING_ID,
                    DEFAULT_GROUPING_NAME);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isPresent();
            assertThat(result.get().entry().merchant()).isEmpty();
        }

        @Test
        @DisplayName("when the payload's incomingMessageId is null - then the command is answered and its entry "
                + "carries no message id")
        void whenPayloadIncomingMessageIdIsNull_thenCommandAnsweredWithNoMessageId() {
            Map<String, String> body = ChangeStreamEntryFixtures.proposalCreated(
                    1L,
                    DEFAULT_USER_ID,
                    null,
                    DEFAULT_EXPENSE_ID,
                    DESCRIPTION,
                    MERCHANT,
                    DEFAULT_AMOUNT,
                    DEFAULT_CURRENCY,
                    DEFAULT_CATEGORY_ID,
                    DEFAULT_CATEGORY_NAME,
                    DEFAULT_GROUPING_ID,
                    DEFAULT_GROUPING_NAME);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isPresent();
            assertThat(result.get().entry().incomingMessageId()).isEmpty();
        }

        @Test
        @DisplayName("when the entry's type is none of the six - then it answers empty")
        void whenEntryTypeIsNoneOfTheSix_thenAnswersEmpty() {
            Map<String, String> body = ChangeStreamEntryFixtures.unknownType(1L, DEFAULT_USER_ID, DEFAULT_EXPENSE_ID);

            Optional<LearnMessageOutcomeCommand> result = reader.read("1700000000000-1", body);

            assertThat(result).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.ai.adapter.redis.ChangeStreamEntryReaderTest#invalidBodies")
        @DisplayName("when the entry is not readable as an event - then InvalidValueException is thrown")
        void whenEntryIsNotAValidEvent_thenInvalidValueExceptionIsThrown(String description, Map<String, String> body) {
            assertThatThrownBy(() -> reader.read("1700000000000-1", body)).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.ai.adapter.redis.ChangeStreamEntryReaderTest#invalidEntryIds")
        @DisplayName("when the entry id has no dash, a non-numeric half, or a non-positive ms - then "
                + "InvalidValueException is thrown")
        void whenEntryIdIsMalformed_thenInvalidValueExceptionIsThrown(String description, String entryId) {
            Map<String, String> body = withDefaults(ChangeStreamEntryFixtures::proposalCreated);

            assertThatThrownBy(() -> reader.read(entryId, body)).isInstanceOf(InvalidValueException.class);
        }
    }

    static Stream<Map<String, String>> acceptedEntries() {
        return Stream.of(
                withDefaults(ChangeStreamEntryFixtures::proposalAccepted),
                withDefaults(ChangeStreamEntryFixtures::expenseRecorded),
                withDefaults(ChangeStreamEntryFixtures::expenseRefiled));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("no payload", ChangeStreamEntryFixtures.withNoPayload()),
                Arguments.of("payload not JSON", ChangeStreamEntryFixtures.withNonJsonPayload()),
                Arguments.of("no type", withPayload(null, defaultPayload())),
                Arguments.of("expenseId absent", withPayload("ProposalCreated", payloadWithout("\"expenseId\":9001,"))),
                Arguments.of(
                        "expenseId not a number",
                        withPayload(
                                "ProposalCreated", payloadReplacing("\"expenseId\":9001", "\"expenseId\":\"abc\""))),
                Arguments.of(
                        "expenseId zero",
                        withPayload("ProposalCreated", payloadReplacing("\"expenseId\":9001", "\"expenseId\":0"))),
                Arguments.of(
                        "expenseId negative",
                        withPayload("ProposalCreated", payloadReplacing("\"expenseId\":9001", "\"expenseId\":-1"))),
                Arguments.of("userId absent", withPayload("ProposalCreated", payloadWithout("\"userId\":10,"))),
                Arguments.of(
                        "userId not a number",
                        withPayload("ProposalCreated", payloadReplacing("\"userId\":10", "\"userId\":\"abc\""))),
                Arguments.of(
                        "userId zero",
                        withPayload("ProposalCreated", payloadReplacing("\"userId\":10", "\"userId\":0"))));
    }

    static Stream<Arguments> invalidEntryIds() {
        return Stream.of(
                Arguments.of("no dash", "abc"),
                Arguments.of("ms not numeric", "abc-3"),
                Arguments.of("seq not numeric", "1700000000000-abc"),
                Arguments.of("ms zero", "0-3"),
                Arguments.of("ms negative", "-100-3"));
    }

    private static String payloadWithout(String field) {
        return defaultPayload().replace(field, "");
    }

    private static String payloadReplacing(String field, String replacement) {
        return defaultPayload().replace(field, replacement);
    }
}
