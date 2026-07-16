package com.syntagi.queue.entity;

import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
import com.syntagi.queue.enums.QueueSessionStatus;
import com.syntagi.queue.exception.QueueSessionClosedException;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.entity.ServiceSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "queue_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_service_id", nullable = false)
    private BusinessService businessService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_schedule_id")
    private ServiceSchedule serviceSchedule;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QueueSessionStatus status = QueueSessionStatus.OPEN;

    @Column(name = "token_counter", nullable = false)
    private int tokenCounter;

    @Column(name = "appointment_token_counter", nullable = false)
    private int appointmentTokenCounter;

    @Column(name = "walk_in_token_counter", nullable = false)
    private int walkInTokenCounter;

    @Column(name = "staff_token_counter", nullable = false)
    private int staffTokenCounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_token_id")
    private QueueToken currentToken;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    public QueueSession(
            Business business,
            BusinessService businessService,
            ServiceSchedule serviceSchedule,
            LocalDate businessDate,
            OffsetDateTime openedAt) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.businessService = Objects.requireNonNull(
                businessService, "businessService is required");
        this.serviceSchedule = serviceSchedule;
        this.businessDate = Objects.requireNonNull(businessDate, "businessDate is required");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt is required");
    }

    public void close(OffsetDateTime closedAt) {
        ensureOpen();
        OffsetDateTime closingTime = Objects.requireNonNull(closedAt, "closedAt is required");
        if (closingTime.isBefore(openedAt)) {
            throw new InvalidEntityStateException("Queue closing time cannot precede opening time");
        }
        this.status = QueueSessionStatus.CLOSED;
        this.closedAt = closingTime;
    }

    public boolean isOpen() {
        return status == QueueSessionStatus.OPEN;
    }

    public void setCurrentToken(QueueToken token) {
        ensureOpen();
        QueueToken next = Objects.requireNonNull(token, "token is required");
        if (next.getQueueSession() != this && !this.equals(next.getQueueSession())) {
            throw new InvalidEntityStateException(
                    "Current token must belong to the same queue session");
        }
        this.currentToken = next;
    }

    public void clearCurrentToken() {
        currentToken = null;
    }

    public int nextAppointmentTokenNumber() {
        ensureOpen();
        return ++appointmentTokenCounter;
    }

    public int nextWalkInTokenNumber() {
        ensureOpen();
        return ++walkInTokenCounter;
    }

    public int nextStaffTokenNumber() {
        ensureOpen();
        return ++staffTokenCounter;
    }

    public int nextGlobalTokenNumber() {
        ensureOpen();
        return ++tokenCounter;
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new QueueSessionClosedException();
        }
    }
}
