package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.EndTurnCommand;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.InvalidTurnEndException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class EndTurnUseCaseTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long TURN_ID = 5L;
    private static final long TEXT_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-05T10:15:30Z");
    private static final IncomingMessageId TELEGRAM_REFERENCE = IncomingMessageId.of("777:1");
    private static final IncomingMessageId WEB_REFERENCE = IncomingMessageId.of("web:42");

    private IncomingTurnRepository incomingTurnRepository;
    private WrittenMessageRepository writtenMessageRepository;
    private ExpenseRepository expenseRepository;
    private SpendingQueryRepository spendingQueryRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private ProposalReportRepository proposalReportRepository;
    private TurnMeters turnMeters;
    private Logger log;
    private EndTurnUseCase useCase;

    @BeforeEach
    void setUp() {
        incomingTurnRepository = mock(IncomingTurnRepository.class);
        writtenMessageRepository = mock(WrittenMessageRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        spendingQueryRepository = mock(SpendingQueryRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        proposalReportRepository = mock(ProposalReportRepository.class);
        turnMeters = mock(TurnMeters.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(EndTurnUseCase.class)).thenReturn(log);
        useCase = new EndTurnUseCase(
                incomingTurnRepository,
                writtenMessageRepository,
                expenseRepository,
                spendingQueryRepository,
                messageDeliveryPort,
                proposalReportRepository,
                turnMeters,
                Clock.fixed(NOW, ZoneOffset.UTC),
                loggerFactory);
        when(incomingTurnRepository.delete(TURN_ID)).thenReturn(true);
        when(writtenMessageRepository.settle(anyLong(), any(), any())).thenReturn(true);
        when(messageDeliveryPort.deliver(any())).thenReturn(Optional.empty());
    }

    private static IncomingTurn turnOf(long userId) {
        return IncomingTurn.stored(
                TURN_ID,
                userId,
                "777",
                "1",
                "spent 12 on coffee",
                Instant.parse("2026-08-05T10:00:00Z"),
                Optional.empty(),
                Optional.of(Instant.parse("2026-08-05T10:01:00Z")));
    }

    private static EndTurnCommand endFor(long userId, IncomingMessageId reference) {
        return new EndTurnCommand(new AuthenticatedUserId(userId), reference, TurnEnding.ACTED_ON);
    }

    private PersistenceFailedException persistenceFailure() {
        return new PersistenceFailedException("failed", new RuntimeException());
    }

    enum Lookup {
        TURN,
        TEXT
    }

    @Nested
    @DisplayName("taking the end of a turn")
    class End {

        @Test
        @DisplayName("when the command is null - then throws InvalidTurnEndException and no port is called")
        void whenTheCommandIsNull_thenThrowsInvalidTurnEndExceptionAndNoPortIsCalled() {
            assertThatThrownBy(() -> useCase.end(null)).isInstanceOf(InvalidTurnEndException.class);

            verifyNoInteractions(log);
            verifyNoInteractions(incomingTurnRepository);
            verifyNoInteractions(writtenMessageRepository);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(spendingQueryRepository);
            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(proposalReportRepository);
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName(
                "when a turn is kept for the person under the reference - then it is deleted and its report delivered")
        void whenATurnIsKeptForThePersonUnderTheReference_thenItIsDeletedAndItsReportDelivered() {
            when(incomingTurnRepository.findFor(USER_ID, TELEGRAM_REFERENCE)).thenReturn(Optional.of(turnOf(USER_ID)));

            useCase.end(endFor(USER_ID, TELEGRAM_REFERENCE));

            InOrder order = inOrder(incomingTurnRepository, messageDeliveryPort);
            order.verify(incomingTurnRepository).delete(TURN_ID);
            ArgumentCaptor<TurnReport> captor = ArgumentCaptor.forClass(TurnReport.class);
            order.verify(messageDeliveryPort).deliver(captor.capture());
            assertThat(captor.getValue().outcome()).isEqualTo(ReportOutcome.NOTHING_IDENTIFIED);
            assertThat(captor.getValue().reference()).isEqualTo(TELEGRAM_REFERENCE);
        }

        @Test
        @DisplayName("when the turn under the reference is another person's - then nothing is deleted or delivered")
        void whenTheTurnUnderTheReferenceIsAnotherPersons_thenNothingIsDeletedOrDelivered() {
            when(incomingTurnRepository.findFor(OTHER_USER_ID, TELEGRAM_REFERENCE))
                    .thenReturn(Optional.of(turnOf(OTHER_USER_ID)));

            assertThatCode(() -> useCase.end(endFor(USER_ID, TELEGRAM_REFERENCE)))
                    .doesNotThrowAnyException();

            verify(incomingTurnRepository).findFor(USER_ID, TELEGRAM_REFERENCE);
            verify(incomingTurnRepository, never()).delete(anyLong());
            verifyNoInteractions(messageDeliveryPort);
            verifyNoInteractions(turnMeters);
        }

        @Test
        @DisplayName("when the reference names a written message of the person - then that text is settled RECORDED")
        void whenTheReferenceNamesAWrittenMessageOfThePerson_thenThatTextIsSettledRecorded() {
            when(writtenMessageRepository.findForUser(USER_ID, TEXT_ID))
                    .thenReturn(Optional.of(new WrittenMessageView(
                            TEXT_ID,
                            USER_ID,
                            "coffee 3.40",
                            WrittenMessageStatus.READING,
                            Optional.empty(),
                            NOW.minusSeconds(60))));
            when(expenseRepository.findDaysByMessageReference(USER_ID, WEB_REFERENCE))
                    .thenReturn(List.of(LocalDate.parse("2026-08-05")));

            useCase.end(endFor(USER_ID, WEB_REFERENCE));

            verify(writtenMessageRepository).settle(TEXT_ID, ReportOutcome.RECORDED, NOW);
            verify(turnMeters).countTurn(ReportOutcome.RECORDED);
            verifyNoInteractions(incomingTurnRepository);
        }

        @ParameterizedTest
        @EnumSource(Lookup.class)
        @DisplayName("when finding the turn or the text throws PersistenceFailedException - then it propagates")
        void whenFindingTheTurnOrTheTextThrowsPersistenceFailedException_thenItPropagates(Lookup lookup) {
            PersistenceFailedException failure = persistenceFailure();
            IncomingMessageId reference = lookup == Lookup.TURN ? TELEGRAM_REFERENCE : WEB_REFERENCE;
            switch (lookup) {
                case TURN ->
                    when(incomingTurnRepository.findFor(USER_ID, TELEGRAM_REFERENCE))
                            .thenThrow(failure);
                case TEXT ->
                    when(writtenMessageRepository.findForUser(USER_ID, TEXT_ID)).thenThrow(failure);
            }

            assertThatThrownBy(() -> useCase.end(endFor(USER_ID, reference))).isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
            verify(writtenMessageRepository, never()).settle(anyLong(), any(), any());
        }
    }
}
