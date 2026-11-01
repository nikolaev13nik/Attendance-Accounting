package co.il.attendanceaccounting.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.data.domain.Page;

import java.util.List;

import co.il.attendanceaccounting.dto.SignUpRequestDto;
import co.il.attendanceaccounting.dto.TenantMappingDto;
import co.il.attendanceaccounting.dto.TenantPageDto;
import co.il.attendanceaccounting.model.TenantMapping;

@Mapper(componentModel = "spring")
public interface TenantMappingMapper {

    TenantMappingDto toDto(TenantMapping tenantMapping);

    List<TenantMappingDto> toDtoList(List<TenantMapping> tenantMappings);

    default TenantPageDto toPageDto(Page<TenantMapping> tenantPage) {
        return TenantPageDto.builder()
                .items(toDtoList(tenantPage.getContent()))
                .page(tenantPage.getNumber())
                .size(tenantPage.getSize())
                .totalElements(tenantPage.getTotalElements())
                .totalPages(tenantPage.getTotalPages())
                .build();
    }

    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "companyName", qualifiedByName = "trim")
    @Mapping(target = "contactName", expression = "java(contactName(signUpRequestDto))")
    @Mapping(target = "phone", qualifiedByName = "trim")
    @Mapping(target = "email", qualifiedByName = "trim")
    TenantMapping toEntity(SignUpRequestDto signUpRequestDto);

    default String contactName(SignUpRequestDto signUpRequestDto) {
        return (trim(signUpRequestDto.getFirstName()) + " " + trim(signUpRequestDto.getLastName())).trim();
    }

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }

}
