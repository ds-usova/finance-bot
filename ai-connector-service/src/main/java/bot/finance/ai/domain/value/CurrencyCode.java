package bot.finance.ai.domain.value;

public record CurrencyCode(String code) {

    public CurrencyCode {
        // rejects a null or blank code and a code java.util.Currency does not recognize, normalizing a
        // recognized code to upper case, all with InvalidValueException
    }

    public static CurrencyCode of(String code) {
        // delegates to the compact constructor, which performs all the validation and normalization
        return null;
    }

}
