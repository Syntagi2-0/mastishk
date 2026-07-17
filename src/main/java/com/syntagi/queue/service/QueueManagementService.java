package com.syntagi.queue.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.queue.dto.response.QueueCurrentResponse;
import com.syntagi.queue.dto.response.WaitingQueueTokenResponse;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.mapper.QueueMapper;
import com.syntagi.queue.repository.QueueTokenRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueManagementService {

    private final AuthenticatedBusinessContextService contextService;
    private final QueueSessionAccessService sessionAccessService;
    private final QueueOrderingService orderingService;
    private final QueueTokenRepository tokenRepository;
    private final QueueMapper mapper;
    private final QueueTimeService timeService;
    private final QueueTokenLifecycleService tokenLifecycleService;
    private final QueueNotificationCoordinator notificationCoordinator;

    public QueueManagementService(
            AuthenticatedBusinessContextService contextService,
            QueueSessionAccessService sessionAccessService,
            QueueOrderingService orderingService,
            QueueTokenRepository tokenRepository,
            QueueMapper mapper,
            QueueTimeService timeService,
            QueueTokenLifecycleService tokenLifecycleService,
            QueueNotificationCoordinator notificationCoordinator) {
        this.contextService = contextService;
        this.sessionAccessService = sessionAccessService;
        this.orderingService = orderingService;
        this.tokenRepository = tokenRepository;
        this.mapper = mapper;
        this.timeService = timeService;
        this.tokenLifecycleService = tokenLifecycleService;
        this.notificationCoordinator = notificationCoordinator;
    }

    @Transactional(readOnly = true)
    public QueueCurrentResponse current(UUID serviceId) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionAccessService.findToday(context.business(), serviceId);
        return currentResponse(session);
    }

    @Transactional(readOnly = true)
    public List<WaitingQueueTokenResponse> waiting(UUID serviceId) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionAccessService.findToday(context.business(), serviceId);
        return orderingService.waitingTokens(session.getId()).stream()
                .map(mapper::toWaitingResponse)
                .toList();
    }

    @Transactional
    public QueueCurrentResponse next(UUID serviceId) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionAccessService.lockToday(context.business(), serviceId);
        ensureOpen(session);
        QueueToken current = session.getCurrentToken();
        if (current != null) {
            tokenLifecycleService.complete(current, timeService.nowOffset());
            session.clearCurrentToken();
        }
        callNext(session);
        return currentResponse(session);
    }

    @Transactional
    public QueueCurrentResponse skip(UUID serviceId) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionAccessService.lockToday(context.business(), serviceId);
        ensureOpen(session);
        QueueToken current = session.getCurrentToken();
        if (current == null) {
            throw new ApplicationException(ErrorCode.NO_CURRENT_QUEUE_TOKEN);
        }
        current.skip(timeService.nowOffset());
        notificationCoordinator.tokenSkipped(current);
        session.clearCurrentToken();
        callNext(session);
        return currentResponse(session);
    }

    @Transactional(readOnly = true)
    public QueueCurrentResponse recall(UUID serviceId) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionAccessService.findToday(context.business(), serviceId);
        if (session.getCurrentToken() == null) {
            throw new ApplicationException(ErrorCode.NO_CURRENT_QUEUE_TOKEN);
        }
        return currentResponse(session);
    }

    private void callNext(QueueSession session) {
        orderingService.nextWaitingToken(session.getId()).ifPresent(next -> {
            next.call(timeService.nowOffset());
            notificationCoordinator.tokenCalled(next);
            session.setCurrentToken(next);
        });
    }

    private QueueCurrentResponse currentResponse(QueueSession session) {
        long waiting = tokenRepository.countByQueueSessionIdAndStatus(
                session.getId(), com.syntagi.queue.enums.QueueTokenStatus.WAITING);
        return mapper.toCurrentResponse(session, waiting);
    }

    private static void ensureOpen(QueueSession session) {
        if (!session.isOpen()) {
            throw new ApplicationException(ErrorCode.QUEUE_SESSION_CLOSED);
        }
    }
}
