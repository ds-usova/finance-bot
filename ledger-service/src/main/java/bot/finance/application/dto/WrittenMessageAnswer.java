package bot.finance.application.dto;

import java.time.LocalDate;
import java.util.List;

public record WrittenMessageAnswer(WrittenMessageView message, List<LocalDate> days) {}
