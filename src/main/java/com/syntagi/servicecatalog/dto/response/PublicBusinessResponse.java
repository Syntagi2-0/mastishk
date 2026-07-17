package com.syntagi.servicecatalog.dto.response;

import com.syntagi.queue.enums.QueueSessionStatus;
import java.util.List;

public record PublicBusinessResponse(
        String business,
        String businessType,
        QueueSessionStatus queueStatus,
        List<PublicServiceResponse> availableServices) {}
