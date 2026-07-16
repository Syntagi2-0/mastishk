package com.syntagi.queue.service;

import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class QueueOrderingService {

    private static final Sort MVP_ORDER = Sort.by(
            Sort.Order.desc("priority"), Sort.Order.asc("queueOrder"));

    private final QueueTokenRepository tokenRepository;

    public QueueOrderingService(QueueTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public List<QueueToken> waitingTokens(UUID queueSessionId) {
        return tokenRepository.findByQueueSessionIdAndStatus(
                queueSessionId, QueueTokenStatus.WAITING, MVP_ORDER);
    }

    public Optional<QueueToken> nextWaitingToken(UUID queueSessionId) {
        return waitingTokens(queueSessionId).stream().findFirst();
    }
}
