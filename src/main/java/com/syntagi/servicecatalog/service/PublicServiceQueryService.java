package com.syntagi.servicecatalog.service;

import com.syntagi.business.entity.Business;
import com.syntagi.business.enums.BusinessStatus;
import com.syntagi.business.repository.BusinessRepository;
import com.syntagi.common.exception.ApplicationException;
import com.syntagi.common.exception.ErrorCode;
import com.syntagi.servicecatalog.dto.response.PublicServiceResponse;
import com.syntagi.servicecatalog.mapper.ServiceCatalogMapper;
import com.syntagi.servicecatalog.repository.BusinessServiceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicServiceQueryService {

    private final BusinessRepository businessRepository;
    private final BusinessServiceRepository serviceRepository;
    private final ServiceCatalogMapper mapper;

    public PublicServiceQueryService(
            BusinessRepository businessRepository,
            BusinessServiceRepository serviceRepository,
            ServiceCatalogMapper mapper) {
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PublicServiceResponse> listActiveServices(String publicQueueCode) {
        Business business = businessRepository.findByPublicQueueCode(publicQueueCode)
                .filter(candidate -> candidate.getStatus() == BusinessStatus.ACTIVE)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return serviceRepository
                .findByBusinessIdAndActiveTrueOrderByDisplayOrderAscNameAsc(business.getId())
                .stream()
                .map(mapper::toPublicServiceResponse)
                .toList();
    }
}
