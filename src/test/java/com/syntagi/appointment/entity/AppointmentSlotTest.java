package com.syntagi.appointment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.syntagi.appointment.exception.SlotCapacityExceededException;
import com.syntagi.business.entity.Business;
import com.syntagi.common.exception.InvalidEntityStateException;
import com.syntagi.servicecatalog.entity.BusinessService;
import com.syntagi.servicecatalog.enums.ServiceMode;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class AppointmentSlotTest {

    @Test
    void reserveOneRespectsCapacityAndReleaseNeverBecomesNegative() {
        Business business = new Business("Clinic", "clinic", "CLINIC", "CLINIC-Q");
        AppointmentSlot slot = new AppointmentSlot(
                business,
                new BusinessService(
                        business, "Consultation", "CONSULT", ServiceMode.BOTH),
                LocalDate.of(2030, 1, 10),
                LocalTime.of(9, 0),
                LocalTime.of(9, 30),
                1);

        assertThat(slot.hasAvailability()).isTrue();
        slot.reserveOne();
        assertThat(slot.hasAvailability()).isFalse();
        assertThatThrownBy(slot::reserveOne).isInstanceOf(SlotCapacityExceededException.class);

        slot.releaseOne();
        assertThat(slot.getBookedCount()).isZero();
        assertThatThrownBy(slot::releaseOne).isInstanceOf(InvalidEntityStateException.class);
    }
}
