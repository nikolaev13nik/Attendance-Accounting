package co.il.attendanceaccounting.service.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.exceptions.InvalidPageException;
import co.il.attendanceaccounting.mapper.TenantMappingMapper;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
public class GetAllTenantsService extends AccountServiceBase<Void> {

    private static final int MIN_PAGE = 0;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort BY_TENANT_ID = Sort.by("tenantId");

    @Autowired
    private TenantMappingMapper tenantMappingMapper;

    @Override
    protected void fetch(AccountContext<Void> context) {
        if (isValidPage(context)) {
            context.setTenantPage(tenantMappingRepository.findAll(
                    PageRequest.of(context.getPage(), context.getPageSize(), BY_TENANT_ID)));
        }
    }

    @Override
    protected void validate(AccountContext<Void> context) {
        if (isFalse(isValidPage(context))) {
            throw new InvalidPageException(context.getPage(), context.getPageSize());
        }
    }

    @Override
    protected void persist(AccountContext<Void> context) {
    }

    @Override
    protected void mapResult(AccountContext<Void> context) {
        context.setResponseTenantPageDto(tenantMappingMapper.toPageDto(context.getTenantPage()));
    }

    private static boolean isValidPage(AccountContext<Void> context) {
        return context.getPage() >= MIN_PAGE
                && context.getPageSize() >= MIN_PAGE_SIZE
                && context.getPageSize() <= MAX_PAGE_SIZE;
    }
}
