package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalTime;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class QueueOrderingService {

    private final QueueTokenRepository tokenRepository;
    private final QueueTimeService timeService;
    private final int earlyCallMinutes;

    public QueueOrderingService(
            QueueTokenRepository tokenRepository,
            QueueTimeService timeService,
            @Value("${syntagi.queue.early-call-minutes:0}") int earlyCallMinutes) {
        this.tokenRepository = tokenRepository;
        this.timeService = timeService;
        this.earlyCallMinutes = earlyCallMinutes;
    }

    public List<QueueToken> waitingTokens(UUID queueSessionId) {
        List<QueueToken> waiting = tokenRepository.findWaitingForOrdering(queueSessionId);
        LocalTime eligibleAt = timeService.businessTime(waiting.isEmpty()
                ? tokenRepository.findBusinessByQueueSessionId(queueSessionId).orElseThrow()
                : waiting.getFirst().getBusiness()).plusMinutes(earlyCallMinutes);
        waiting.sort(comparator(eligibleAt));
        return waiting;
    }

    public Optional<QueueToken> nextWaitingToken(UUID queueSessionId) {
        List<QueueToken> waiting = waitingTokens(queueSessionId);
        if (waiting.isEmpty()) {
            return Optional.empty();
        }
        LocalTime eligibleAt = timeService.businessTime(waiting.getFirst().getBusiness())
                .plusMinutes(earlyCallMinutes);
        return waiting.stream().filter(token -> isEligible(token, eligibleAt)).findFirst();
    }

    private static Comparator<QueueToken> comparator(LocalTime eligibleAt) {
        return Comparator
                .comparing((QueueToken token) -> eligibleAppointment(token, eligibleAt) ? 0 : 1)
                .thenComparing(token -> eligibleAppointment(token, eligibleAt)
                        ? token.getScheduledTime() : LocalTime.MAX)
                .thenComparing(QueueToken::getPriority, Comparator.reverseOrder())
                .thenComparingLong(QueueToken::getQueueOrder);
    }

    private static boolean isEligible(QueueToken token, LocalTime eligibleAt) {
        return token.getSourceType() != com.syntagi.queue.enums.QueueTokenSourceType.APPOINTMENT
                || eligibleAppointment(token, eligibleAt);
    }

    private static boolean eligibleAppointment(QueueToken token, LocalTime eligibleAt) {
        return token.getSourceType() == com.syntagi.queue.enums.QueueTokenSourceType.APPOINTMENT
                && token.getScheduledTime() != null
                && !token.getScheduledTime().isAfter(eligibleAt);
    }
}
