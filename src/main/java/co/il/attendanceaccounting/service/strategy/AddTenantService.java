package co.il.attendanceaccounting.service.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.TenantAddDto;
import co.il.attendanceaccounting.exceptions.TenantExistsException;
import co.il.attendanceaccounting.mapper.TenantMappingMapper;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

@Service
public class AddTenantService extends AccountServiceBase<TenantAddDto> {

    @Autowired
    private TenantMappingMapper tenantMappingMapper;

    @Override
    protected void validate(AccountContext<TenantAddDto> context) {
        if (tenantMappingRepository.existsByCompanyNameIgnoreCase(context.getTask().getCompanyName().trim())) {
            throw new TenantExistsException();
        }
    }

    @Override
    protected void executeBusiness(AccountContext<TenantAddDto> context) {
        context.getTenantMappings().add(tenantMappingMapper.toEntity(context.getTask()));
    }

    @Override
    protected void mapResult(AccountContext<TenantAddDto> context) {
        context.getResponseTenantMappingDto()
                .add(tenantMappingMapper.toDto(context.getTenantMappings().get(0)));
    }
}
