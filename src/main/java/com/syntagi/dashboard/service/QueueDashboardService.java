package com.syntagi.dashboard.service;

import com.syntagi.auth.service.AuthenticatedBusinessContext;
import com.syntagi.auth.service.AuthenticatedBusinessContextService;
import com.syntagi.dashboard.dto.response.QueueDashboardResponse;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.queue.service.QueueSessionAccessService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueDashboardService {

    private final AuthenticatedBusinessContextService contextService;
    private final QueueSessionAccessService sessionAccessService;
    private final QueueTokenRepository tokenRepository;

    public QueueDashboardService(
            AuthenticatedBusinessContextService contextService,
            QueueSessionAccessService sessionAccessService,
            QueueTokenRepository tokenRepository) {
        this.contextService = contextService;
        this.sessionAccessService = sessionAccessService;
        this.tokenRepository = tokenRepository;
    }

    @Transactional(readOnly = true)
    public QueueDashboardResponse getQueueDashboard(UUID serviceId) {
        AuthenticatedBusinessContext context = contextService.current();
        QueueSession session = sessionAccessService.findToday(context.business(), serviceId);
        return new QueueDashboardResponse(
                session.getBusinessService().getId(),
                session.getBusinessService().getName(),
                session.getStatus(),
                session.getCurrentToken() == null
                        ? null : session.getCurrentToken().getTokenDisplay(),
                count(session, QueueTokenStatus.WAITING),
                count(session, QueueTokenStatus.COMPLETED),
                count(session, QueueTokenStatus.SKIPPED));
    }

    private long count(QueueSession session, QueueTokenStatus status) {
        return tokenRepository.countByQueueSessionIdAndStatus(session.getId(), status);
    }
}
