package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ProposalIds;

public record DiscardExpensesCommand(AuthenticatedUserId userId, ProposalIds ids) {}
