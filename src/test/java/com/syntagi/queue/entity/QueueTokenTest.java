package com.syntagi.queue.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syntagi.business.entity.Business;
import com.syntagi.customer.entity.Customer;
import com.syntagi.queue.enums.QueueTokenSourceType;
import com.syntagi.queue.enums.QueueTokenStatus;
import com.syntagi.queue.exception.InvalidQueueTokenTransitionException;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.enums.ServiceMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QueueTokenTest {

    @Test
    void supportsLegalStatusTransitions() {
        QueueToken token = newToken();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        token.call(now);
        token.skip(now.plusMinutes(1));
        token.call(now.plusMinutes(2));
        token.complete(now.plusMinutes(3));

        assertThat(token.getStatus()).isEqualTo(QueueTokenStatus.COMPLETED);
        assertThat(token.isWaiting()).isFalse();
    }

    @Test
    void rejectsIllegalStatusTransitions() {
        QueueToken token = newToken();

        assertThatThrownBy(() -> token.complete(OffsetDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(InvalidQueueTokenTransitionException.class);
    }

    private static QueueToken newToken() {
        Business business = new Business("Salon", "salon", "SALON", "SALON-Q");
        BusinessService service =
                new BusinessService(business, "Haircut", "HAIRCUT", ServiceMode.BOTH);
        Customer customer = new Customer(business, "Customer", "9000000000", null);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QueueSession session = new QueueSession(business, service, null, LocalDate.now(), now);
        return new QueueToken(
                session,
                business,
                service,
                customer,
                null,
                1,
                "W001",
                QueueTokenSourceType.WALK_IN,
                null,
                now,
                1,
                0,
                null);
    }
}
