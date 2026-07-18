package com.syntagi.queue.entity;

import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.common.persistence.BaseEntity;
import com.syntagi.queue.enums.QueueStatus;
import com.syntagi.servicecatalog.entity.BusinessService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "queues", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueueConfiguration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_service_id", nullable = false)
    private BusinessService businessService;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QueueStatus status = QueueStatus.DRAFT;

    public QueueConfiguration(Business business, BusinessService businessService, String name) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.businessService = Objects.requireNonNull(
                businessService, "businessService is required");
        if (businessService.getBusiness() != business
                && !Objects.equals(businessService.getBusiness().getId(), business.getId())) {
            throw new InvalidEntityStateException("Queue service must belong to the same business");
        }
        this.name = requireName(name);
    }

    public void rename(String name) {
        ensureNotArchived();
        this.name = requireName(name);
    }

    public void activate() {
        ensureNotArchived();
        status = QueueStatus.ACTIVE;
    }

    public void pause() {
        if (status != QueueStatus.ACTIVE) {
            throw new InvalidEntityStateException("Only an active queue can be paused");
        }
        status = QueueStatus.PAUSED;
    }

    public void close() {
        if (status == QueueStatus.ARCHIVED) {
            throw new InvalidEntityStateException("Archived queue cannot be closed");
        }
        status = QueueStatus.CLOSED;
    }

    public void archive() {
        if (status == QueueStatus.ACTIVE) {
            throw new InvalidEntityStateException("Active queue must be closed before archiving");
        }
        status = QueueStatus.ARCHIVED;
    }

    public boolean isActive() {
        return status == QueueStatus.ACTIVE;
    }

    private void ensureNotArchived() {
        if (status == QueueStatus.ARCHIVED) {
            throw new InvalidEntityStateException("Archived queue cannot be changed");
        }
    }

    private static String requireName(String value) {
        String name = Objects.requireNonNull(value, "name is required").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        return name;
    }
}
