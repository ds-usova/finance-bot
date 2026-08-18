package bot.finance.ai.domain.value;

public record CategoryRef(long id, String name) {

    public CategoryRef {
        // TODO: reject a non-positive id, and a null or blank name
    }
}
