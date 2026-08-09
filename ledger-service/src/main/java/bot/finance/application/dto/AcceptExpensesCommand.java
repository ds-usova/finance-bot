package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ProposalIds;

public record AcceptExpensesCommand(AuthenticatedUserId userId, ProposalIds ids) {}
