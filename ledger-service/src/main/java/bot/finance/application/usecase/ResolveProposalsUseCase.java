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
import bot.finance.domain.value.MessageReference;
import java.time.Clock;
import java.time.Instant;

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
        ResolutionAcknowledgement acknowledgement = userRepository
                .findByExternalId(command.userExternalId())
                .map(user -> acknowledgementFor(user.id().orElseThrow(), command))
                .orElseGet(() -> acknowledgement(command, ResolutionOutcome.NOTHING_TO_RESOLVE, 0));

        log.info(
                "resolved proposals under reference {} as {} with outcome {} and count {}",
                command.reference(),
                command.resolution(),
                acknowledgement.outcome(),
                acknowledgement.count());

        messageDeliveryPort.acknowledge(acknowledgement);
    }

    private ResolutionAcknowledgement acknowledgementFor(long userId, ResolveProposalsCommand command) {
        int resolvedCount = applyResolution(userId, command);
        if (resolvedCount > 0) {
            return acknowledgement(command, resolvedOutcome(command.resolution()), resolvedCount);
        }
        int alreadyAcceptedCount = expenseRepository.countByMessageReference(userId, command.reference());
        if (alreadyAcceptedCount > 0) {
            return acknowledgement(command, ResolutionOutcome.ALREADY_ACCEPTED, alreadyAcceptedCount);
        }
        return acknowledgement(command, ResolutionOutcome.NOTHING_TO_RESOLVE, 0);
    }

    private int applyResolution(long userId, ResolveProposalsCommand command) {
        MessageReference reference = command.reference();
        return switch (command.resolution()) {
            case ACCEPT -> expenseProposalRepository.accept(userId, reference, Instant.now(clock));
            case DISCARD -> expenseProposalRepository.discard(userId, reference);
        };
    }

    private static ResolutionOutcome resolvedOutcome(ProposalResolution resolution) {
        return switch (resolution) {
            case ACCEPT -> ResolutionOutcome.ACCEPTED;
            case DISCARD -> ResolutionOutcome.DISCARDED;
        };
    }

    private static ResolutionAcknowledgement acknowledgement(
            ResolveProposalsCommand command, ResolutionOutcome outcome, int count) {
        return new ResolutionAcknowledgement(
                command.conversationId(), command.reportMessageId(), command.interactionId(), outcome, count);
    }
}
