package bot.finance.adapter.web;

import bot.finance.api.model.RenderedMoney;
import bot.finance.domain.value.Money;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class MoneyRenderer {

    private MoneyRenderer() {}

    public static RenderedMoney render(Money money) {
        Currency currency = Currency.getInstance(money.currencyCode().code());

        NumberFormat amountFormat = NumberFormat.getNumberInstance(Locale.ENGLISH);
        amountFormat.setGroupingUsed(true);
        amountFormat.setMinimumFractionDigits(currency.getDefaultFractionDigits());
        amountFormat.setMaximumFractionDigits(currency.getDefaultFractionDigits());
        String amount = amountFormat.format(money.amount());

        String label = currency.getSymbol(Locale.ENGLISH);
        String separator = label.equals(currency.getCurrencyCode()) ? " " : "";

        return new RenderedMoney(amount, label, separator);
    }
}
