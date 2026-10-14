package bot.finance.ai.domain.value;

import java.util.Optional;

public record ExpenseToFile(String description, Optional<String> merchant, String amount, CurrencyCode currency) {

    public ExpenseToFile {
        // TODO: refuse a null or blank description, a null merchant Optional, a null amount or currency, and an
        // amount that is not a positive decimal; read a present but blank merchant as absent
    }
}
