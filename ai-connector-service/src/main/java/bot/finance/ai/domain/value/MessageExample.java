package bot.finance.ai.domain.value;

import java.util.List;

public record MessageExample(String text, List<ExampleExpense> expenses) {

    public MessageExample {
        // TODO: refuse a null or blank text, a null or empty expense list and a null element, as
        // InvalidValueException, and copy the list so a later mutation of it cannot reach the stored value
    }
}
