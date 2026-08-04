package bot.finance.application.usecase;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.ResolutionOutcome;
import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ResolveProposalsPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.MessageReference;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class ResolveProposalsUseCase implements ResolveProposalsPort {

    private final UserRepository userRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final ExpenseRepository expenseRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final Clock clock;
    private final Logger log;

    public ResolveProposalsUseCase(
            UserRepository userRepository,
            ExpenseProposalRepository expenseProposalRepository,
            ExpenseRepository expenseRepository,
            MessageDeliveryPort messageDeliveryPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.expenseProposalRepository = expenseProposalRepository;
        this.expenseRepository = expenseRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(ResolveProposalsUseCase.class);
    }

    @Override
    public void resolve(ResolveProposalsCommand command) {
        if (command == null) {
            throw new InvalidIncomingMessageException("resolve-proposals command is absent");
        }
        Optional<User> user = userRepository.findByExternalId(command.userExternalId());
        ResolutionOutcome outcome = ResolutionOutcome.NOTHING_TO_RESOLVE;
        int count = 0;
        if (user.isPresent()) {
            long userId = user.get().id().orElseThrow();
            int resolvedCount = resolve(userId, command);
            if (resolvedCount > 0) {
                outcome = command.resolution() == ProposalResolution.ACCEPT
                        ? ResolutionOutcome.ACCEPTED
                        : ResolutionOutcome.DISCARDED;
                count = resolvedCount;
            } else {
                int alreadyResolvedCount = expenseRepository.countByMessageReference(userId, command.reference());
                if (alreadyResolvedCount > 0) {
                    outcome = ResolutionOutcome.ALREADY_ACCEPTED;
                    count = alreadyResolvedCount;
                }
            }
        }
        log.info(
                "resolved proposals under reference {} as {} with outcome {} and count {}",
                command.reference(),
                command.resolution(),
                outcome,
                count);
        messageDeliveryPort.acknowledge(new ResolutionAcknowledgement(
                command.conversationId(), command.reportMessageId(), command.interactionId(), outcome, count));
    }

    private int resolve(long userId, ResolveProposalsCommand command) {
        MessageReference reference = command.reference();
        return switch (command.resolution()) {
            case ACCEPT -> expenseProposalRepository.accept(userId, reference, Instant.now(clock));
            case DISCARD -> expenseProposalRepository.discard(userId, reference);
        };
    }
}
