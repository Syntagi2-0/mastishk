package com.syntagi.queue.mapper;

import com.syntagi.customer.entity.Customer;
import com.syntagi.queue.dto.response.QueueCurrentResponse;
import com.syntagi.queue.dto.response.QueueCustomerResponse;
import com.syntagi.queue.dto.response.WaitingQueueTokenResponse;
import com.syntagi.queue.entity.QueueSession;
import com.syntagi.queue.entity.QueueToken;
import org.springframework.stereotype.Component;

@Component
public class QueueMapper {

    public QueueCurrentResponse toCurrentResponse(QueueSession session, long waitingCount) {
        QueueToken current = session.getCurrentToken();
        return new QueueCurrentResponse(
                session.getBusinessService().getId(),
                session.getBusinessService().getName(),
                current == null ? null : toCustomerResponse(current.getCustomer()),
                current == null ? null : current.getTokenDisplay(),
                session.getStatus(),
                waitingCount);
    }

    public WaitingQueueTokenResponse toWaitingResponse(QueueToken token) {
        return new WaitingQueueTokenResponse(
                token.getTokenDisplay(),
                token.getStatus(),
                toCustomerResponse(token.getCustomer()),
                token.getJoinedAt(),
                token.getPriority());
    }

    private QueueCustomerResponse toCustomerResponse(Customer customer) {
        return new QueueCustomerResponse(customer.getFullName(), customer.getMobile());
    }
}
