package com.syntagi.queue.service;

import com.syntagi.business.entity.Business;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class QueueTimeService {

    private final Clock clock;

    public QueueTimeService(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public OffsetDateTime nowOffset() {
        return OffsetDateTime.ofInstant(now(), ZoneOffset.UTC);
    }

    public LocalDate businessDate(Business business) {
        return now().atZone(ZoneId.of(business.getTimezone())).toLocalDate();
    }

    public LocalTime businessTime(Business business) {
        return now().atZone(ZoneId.of(business.getTimezone())).toLocalTime();
    }
}
