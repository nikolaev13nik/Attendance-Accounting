package co.il.attendanceaccounting.mapper;

import org.mapstruct.Mapper;

import java.util.List;

import co.il.attendanceaccounting.dto.DataTimeDto;
import co.il.attendanceaccounting.dto.UserDto;
import co.il.attendanceaccounting.model.DataTime;
import co.il.attendanceaccounting.model.User;

@Mapper(componentModel = "spring")
public interface DataTimeMapper {


    DataTimeDto toDto(DataTime dataTime);
    List<DataTimeDto> toDtoList(List<DataTime> list);
    UserDto toUserDto(User user);
}