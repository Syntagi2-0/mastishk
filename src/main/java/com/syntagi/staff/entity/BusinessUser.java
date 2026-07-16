package com.syntagi.staff.entity;

import com.syntagi.auth.entity.User;
import com.syntagi.auth.enums.BusinessRole;
import com.syntagi.auth.enums.BusinessUserStatus;
import com.syntagi.business.entity.Business;
import com.syntagi.common.persistence.BaseEntity;
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
@Table(name = "business_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessUserStatus status = BusinessUserStatus.ACTIVE;

    public BusinessUser(Business business, User user, BusinessRole role) {
        this.business = Objects.requireNonNull(business, "business is required");
        this.user = Objects.requireNonNull(user, "user is required");
        this.role = Objects.requireNonNull(role, "role is required");
    }

    public void activate() {
        status = BusinessUserStatus.ACTIVE;
    }

    public void deactivate() {
        status = BusinessUserStatus.INACTIVE;
    }

    public void changeRole(BusinessRole role) {
        this.role = Objects.requireNonNull(role, "role is required");
    }
}
