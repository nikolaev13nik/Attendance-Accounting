package co.il.attendanceaccounting.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.dto.UserRegisterDto;
import co.il.attendanceaccounting.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "companyName", ignore = true)
    UserProfileDto toDto(User user);

    @Mapping(target = "companyName", source = "companyName")
    UserProfileDto toDto(User user, String companyName);

    List<UserProfileDto> toDtoList(List<User> users);

    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "role", ignore = true)
    User toEntity(UserRegisterDto userRegisterDto);

}
