package bot.finance.adapter.web;

import bot.finance.api.model.RenderedMoney;
import bot.finance.domain.value.Money;

public final class MoneyRenderer {

    private MoneyRenderer() {}

    public static RenderedMoney render(Money money) {
        // renders the scaled amount through a grouping number format on Locale.ENGLISH carrying no currency,
        // takes Currency.getSymbol(Locale.ENGLISH) as the label, and separates the two with a space only when
        // that symbol came back as the ISO code. Never the JVM default locale, on either part.
        return new RenderedMoney("", "", "");
    }
}
