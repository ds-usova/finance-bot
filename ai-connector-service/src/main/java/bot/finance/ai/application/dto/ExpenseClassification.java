package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.CategoryChoice;
import java.util.Map;

public record ExpenseClassification(
        Integer position, CategoryChoice choice, Double confidence, Map<CategoryChoice, Double> probabilities) {}
