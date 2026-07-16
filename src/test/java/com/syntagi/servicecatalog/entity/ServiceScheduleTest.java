package com.syntagi.servicecatalog.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.syntagi.business.entity.Business;
import com.syntagi.servicecatalog.enums.ServiceMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ServiceScheduleTest {

    @Test
    void queueOpeningTimeCrossesMidnight() {
        Business business = new Business("Bank", "bank", "BANK", "BANK-Q");
        BusinessService service =
                new BusinessService(business, "Accounts", "ACCOUNTS", ServiceMode.BOTH);
        ServiceSchedule schedule = new ServiceSchedule(
                service, DayOfWeek.MONDAY, LocalTime.of(0, 30), LocalTime.of(8, 0));
        schedule.configureQueueOpening(60);

        assertThat(schedule.calculateQueueOpeningTime()).isEqualTo(LocalTime.of(23, 30));
        assertThat(schedule.isOpenAt(LocalTime.of(0, 30))).isTrue();
        assertThat(schedule.isOpenAt(LocalTime.of(8, 0))).isFalse();
    }
}
