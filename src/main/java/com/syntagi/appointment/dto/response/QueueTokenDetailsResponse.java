package com.syntagi.appointment.dto.response;

import com.syntagi.queue.enums.QueueTokenStatus;

public record QueueTokenDetailsResponse(String token, QueueTokenStatus status) {
}
