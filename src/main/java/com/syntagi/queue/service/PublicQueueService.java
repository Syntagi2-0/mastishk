package com.syntagi.queue.service;

import com.syntagi.business.entity.Business;
import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.customer.entity.Customer;
import com.syntagi.customer.repository.CustomerRepository;
import com.syntagi.queue.dto.request.WalkInRequest;
import com.syntagi.queue.dto.response.LiveQueueResponse;
import com.syntagi.queue.dto.response.WalkInTokenResponse;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.repository.QueueSessionRepository;
import com.syntagi.queue.repository.QueueTokenRepository;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicQueueService {

    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository serviceRepository;
    private final CustomerRepository customerRepository;
    private final QueueSessionRepository sessionRepository;
    private final QueueTokenRepository tokenRepository;
    private final QueueOrderingService orderingService;
    private final QueueTimeService timeService;

    public PublicQueueService(
            BusinessRepository businessRepository,
            BusinessServiceRepository serviceRepository,
            CustomerRepository customerRepository,
            QueueSessionRepository sessionRepository,
            QueueTokenRepository tokenRepository,
            QueueOrderingService orderingService,
            QueueTimeService timeService) {
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.tokenRepository = tokenRepository;
        this.orderingService = orderingService;
        this.timeService = timeService;
    }

    @Transactional
    public WalkInTokenResponse joinWalkIn(String publicQueueCode, WalkInRequest request) {
        Business business = businessRepository.findByPublicQueueCode(publicQueueCode)
                .filter(candidate -> candidate.getStatus() == BusinessStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        BusinessService service = serviceRepository
                .findByIdAndBusinessId(request.serviceId(), business.getId())
                .filter(BusinessService::isActive)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SERVICE_NOT_FOUND));
        if (!service.supportsWalkIn()) {
            throw new ApplicationException(ErrorCode.WALK_IN_NOT_SUPPORTED);
        }

        LocalDate businessDate = timeService.businessDate(business);
        QueueSession session = sessionRepository.findTodayForUpdate(
                        business.getId(), service.getId(), businessDate)
                .filter(QueueSession::isOpen)
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_SESSION_NOT_FOUND));
        if (session.getServiceSchedule() != null
                && !session.getServiceSchedule().isWalkInEnabled()) {
            throw new ApplicationException(ErrorCode.WALK_IN_NOT_SUPPORTED);
        }

        String mobile = request.mobile().trim();
        Customer customer = customerRepository.findByBusinessIdAndMobile(business.getId(), mobile)
                .orElseGet(() -> customerRepository.save(
                        new Customer(business, request.fullName(), mobile, null)));

        int tokenNumber = session.nextWalkInTokenNumber();
        long queueOrder = session.nextGlobalTokenNumber();
        QueueToken token = tokenRepository.save(new QueueToken(
                session,
                business,
                service,
                customer,
                null,
                tokenNumber,
                "W%03d".formatted(tokenNumber),
                QueueTokenSourceType.WALK_IN,
                null,
                timeService.nowOffset(),
                queueOrder,
                0,
                null));

        List<QueueToken> waiting = orderingService.waitingTokens(session.getId());
        int position = waiting.indexOf(token) + 1;
        return new WalkInTokenResponse(
                token.getTokenDisplay(),
                token.getStatus(),
                business.getName(),
                service.getName(),
                token.getJoinedAt(),
                position,
                Math.max(0, position - 1L));
    }

    @Transactional(readOnly = true)
    public LiveQueueResponse getLiveQueue(String tokenDisplay) {
        QueueToken token = tokenRepository
                .findFirstByTokenDisplayOrderByJoinedAtDesc(
                        tokenDisplay.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApplicationException(ErrorCode.QUEUE_TOKEN_NOT_FOUND));
        QueueSession session = token.getQueueSession();
        List<QueueToken> waiting = orderingService.waitingTokens(session.getId());
        long waitingCustomers = waiting.size();
        Integer estimatedPosition = null;
        long estimatedWaitingCount = 0;
        if (token.getStatus() == QueueTokenStatus.WAITING) {
            estimatedPosition = waiting.indexOf(token) + 1;
            estimatedWaitingCount = Math.max(0, estimatedPosition - 1L);
        } else if (token.getStatus() == QueueTokenStatus.CALLED) {
            estimatedPosition = 0;
        }
        return new LiveQueueResponse(
                token.getTokenDisplay(),
                token.getStatus(),
                session.getCurrentToken() == null
                        ? null : session.getCurrentToken().getTokenDisplay(),
                waitingCustomers,
                estimatedPosition,
                estimatedWaitingCount);
    }
}
