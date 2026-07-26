package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.RawIntent;

import java.util.List;

public interface IntentInferencePort {

    List<RawIntent> infer(String text, List<String> knownCategories);

}
