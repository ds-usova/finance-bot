package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.api.model.RenderedMoney;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyRendererTest {

    @Nested
    @DisplayName("rendering a Money figure for a reader")
    class Render {

        @Test
        @DisplayName("when a EUR figure is rendered - then the amount, the euro symbol and an empty separator are "
                + "answered")
        void whenFigureIsInEur_thenAmountSymbolAndEmptySeparatorAreAnswered() {
            RenderedMoney rendered = MoneyRenderer.render(new Money(1250L, CurrencyCode.of("EUR")));

            assertThat(rendered.getAmount()).isEqualTo("12.50");
            assertThat(rendered.getCurrency()).isEqualTo("€");
            assertThat(rendered.getSeparator()).isEqualTo("");
        }

        @Test
        @DisplayName("when a JPY figure is rendered - then the amount carries no decimal point and the yen symbol "
                + "is answered")
        void whenFigureIsInJpy_thenAmountHasNoDecimalPointAndYenSymbolIsAnswered() {
            RenderedMoney rendered = MoneyRenderer.render(new Money(900L, CurrencyCode.of("JPY")));

            assertThat(rendered.getAmount()).isEqualTo("900");
            assertThat(rendered.getCurrency()).isEqualTo("¥");
            assertThat(rendered.getSeparator()).isEqualTo("");
        }

        @Test
        @DisplayName("when a large EUR figure is rendered - then the amount is grouped and carries no symbol or code")
        void whenFigureIsLarge_thenAmountIsGroupedAndCarriesNoSymbolOrCode() {
            RenderedMoney rendered = MoneyRenderer.render(new Money(124500L, CurrencyCode.of("EUR")));

            assertThat(rendered.getAmount())
                    .isEqualTo("1,245.00")
                    .doesNotContain("€")
                    .doesNotContain("EUR");
        }

        @Test
        @DisplayName("when the currency has no English symbol - then its ISO code is the currency and the "
                + "separator is one space")
        void whenCurrencyHasNoEnglishSymbol_thenIsoCodeIsCurrencyAndSeparatorIsOneSpace() {
            RenderedMoney rendered = MoneyRenderer.render(new Money(124500L, CurrencyCode.of("CHF")));

            assertThat(rendered.getAmount()).isEqualTo("1,245.00");
            assertThat(rendered.getCurrency()).isEqualTo("CHF");
            assertThat(rendered.getSeparator()).isEqualTo(" ");
        }

        @Test
        @DisplayName("when a figure of zero minor units is rendered - then the amount is zero with decimals and "
                + "the currency is the symbol")
        void whenFigureIsZero_thenAmountIsZeroWithDecimalsAndCurrencyIsSymbol() {
            RenderedMoney rendered = MoneyRenderer.render(new Money(0L, CurrencyCode.of("EUR")));

            assertThat(rendered.getAmount()).isEqualTo("0.00");
            assertThat(rendered.getCurrency()).isEqualTo("€");
        }

        @Test
        @DisplayName("when the JVM's default locale is non-English - then the amount and currency still render in "
                + "English")
        void whenJvmDefaultLocaleIsNonEnglish_thenAmountAndCurrencyStillRenderInEnglish() {
            Locale previousDefault = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);

                RenderedMoney rendered = MoneyRenderer.render(new Money(124500L, CurrencyCode.of("EUR")));

                assertThat(rendered.getAmount()).isEqualTo("1,245.00");
                assertThat(rendered.getCurrency()).isEqualTo("€");
            } finally {
                Locale.setDefault(previousDefault);
            }
        }
    }
}
