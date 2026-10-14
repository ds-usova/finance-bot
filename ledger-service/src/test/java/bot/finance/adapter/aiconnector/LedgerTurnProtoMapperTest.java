package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.mcp.CreateExpenseProposalToolRequest;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LedgerTurnProtoMapperTest {

    private static final Instant STORED_AT = Instant.parse("2026-10-13T09:15:00Z");

    private static Expense stored(Optional<String> merchant, Money amount) {
        return Expense.stored(
                7L,
                10L,
                20L,
                "coffee",
                merchant,
                amount,
                ExpenseStatus.PENDING,
                Optional.of(new IncomingMessageId("web:17")),
                STORED_AT,
                STORED_AT);
    }

    @Nested
    @DisplayName("mapping a filed-proposal request onto the tools' request")
    class ToToolRequest {

        @Test
        @DisplayName("when the request carries every field - then the tools' request carries each unchanged")
        void whenRequestCarriesEveryField_thenToolRequestCarriesEachUnchanged() {
            FileExpenseProposalRequest request = FileExpenseProposalRequest.newBuilder()
                    .setGrouping("Dining")
                    .setCategory("Cafés")
                    .setDescription("coffee")
                    .setMerchant("Bean Bar")
                    .setAmount("4.50")
                    .setCurrencyCode("EUR")
                    .build();

            CreateExpenseProposalToolRequest toolRequest = LedgerTurnProtoMapper.toToolRequest(request);

            assertThat(toolRequest)
                    .isEqualTo(new CreateExpenseProposalToolRequest(
                            "Cafés", "Dining", "coffee", "Bean Bar", "4.50", "EUR"));
        }

        @Test
        @DisplayName("when the request sets no merchant - then the tools' request's merchant is null")
        void whenRequestSetsNoMerchant_thenToolRequestMerchantIsNull() {
            FileExpenseProposalRequest request = FileExpenseProposalRequest.newBuilder()
                    .setGrouping("Dining")
                    .setCategory("Cafés")
                    .setDescription("coffee")
                    .setAmount("4.50")
                    .setCurrencyCode("EUR")
                    .build();

            CreateExpenseProposalToolRequest toolRequest = LedgerTurnProtoMapper.toToolRequest(request);

            assertThat(toolRequest).isNotNull();
            assertThat(toolRequest.merchant()).isNull();
        }
    }

    @Nested
    @DisplayName("mapping a stored entry onto the filed-proposal answer")
    class ToResponseForAnEntry {

        @Test
        @DisplayName("when the entry has no merchant - then the answer carries its fields and no merchant")
        void whenEntryHasNoMerchant_thenAnswerCarriesItsFieldsAndNoMerchant() {
            Expense entry = stored(Optional.empty(), new Money(450L, CurrencyCode.of("EUR")));

            FileExpenseProposalResponse response = LedgerTurnProtoMapper.toResponse(entry, "Dining", "Cafés");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(7L);
            assertThat(response.getGrouping()).isEqualTo("Dining");
            assertThat(response.getCategory()).isEqualTo("Cafés");
            assertThat(response.getDescription()).isEqualTo("coffee");
            assertThat(response.getAmount()).isEqualTo("4.50");
            assertThat(response.getCurrencyCode()).isEqualTo("EUR");
            assertThat(response.getCreatedAt()).isEqualTo("2026-10-13T09:15:00Z");
            assertThat(response.hasMerchant()).isFalse();
        }

        @Test
        @DisplayName("when the entry has a merchant - then the answer carries it")
        void whenEntryHasAMerchant_thenAnswerCarriesIt() {
            Expense entry = stored(Optional.of("Bean Bar"), new Money(450L, CurrencyCode.of("EUR")));

            FileExpenseProposalResponse response = LedgerTurnProtoMapper.toResponse(entry, "Dining", "Cafés");

            assertThat(response).isNotNull();
            assertThat(response.hasMerchant()).isTrue();
            assertThat(response.getMerchant()).isEqualTo("Bean Bar");
        }

        @Test
        @DisplayName("when the entry is 7200 HUF - then the amount is written 7200 with no exponent")
        void whenEntryIs7200Huf_thenAmountIsWrittenWithNoExponent() {
            Expense entry = stored(Optional.empty(), new Money(7200L, CurrencyCode.of("HUF")));

            FileExpenseProposalResponse response = LedgerTurnProtoMapper.toResponse(entry, "Dining", "Cafés");

            assertThat(response).isNotNull();
            assertThat(response.getAmount()).isEqualTo("7200");
        }
    }

    @Nested
    @DisplayName("mapping a spending period onto the recorded-period answer")
    class ToResponseForAPeriod {

        @Test
        @DisplayName("when the period is a single day - then from and to are both that day")
        void whenPeriodIsASingleDay_thenFromAndToAreBothThatDay() {
            LocalDate day = LocalDate.parse("2026-10-12");

            RecordSpendingPeriodResponse response = LedgerTurnProtoMapper.toResponse(new SpendingPeriod(day, day));

            assertThat(response).isNotNull();
            assertThat(response.getFrom()).isEqualTo("2026-10-12");
            assertThat(response.getTo()).isEqualTo("2026-10-12");
        }
    }
}
