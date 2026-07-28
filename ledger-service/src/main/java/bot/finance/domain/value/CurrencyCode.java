package bot.finance.domain.value;

public record CurrencyCode(String code) {

    public CurrencyCode {
        // TODO: GU01 normalizes the code to upper case, then rejects a null or blank one and one ISO 4217
        // does not know, with InvalidMoneyException. The normalization belongs here, not in of(String), so
        // no invalid or unnormalized instance can exist however it was built.
    }

    public static CurrencyCode of(String code) {
        // named factory over the compact constructor, which does the normalizing and the rejecting
        return new CurrencyCode(code);
    }
}
