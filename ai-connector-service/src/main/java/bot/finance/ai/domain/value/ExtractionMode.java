package bot.finance.ai.domain.value;

public enum ExtractionMode {
    TOOLS("tools"),
    CLASSIFIER("classifier");

    private final String tagValue;

    ExtractionMode(String tagValue) {
        this.tagValue = tagValue;
    }

    public String tagValue() {
        return tagValue;
    }
}
