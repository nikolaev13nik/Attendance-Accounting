package co.il.avivsmile.mapper;

import org.mapstruct.Mapper;
import java.util.List;
import co.il.avivsmile.model.DataTime;
import co.il.avivsmile.model.User;
import co.il.avivsmile.dto.DataTimeDto;
import co.il.avivsmile.dto.UserDto;

@Mapper(componentModel = "spring")
public interface DataTimeMapper {
    DataTimeDto toDto(DataTime dataTime);
    List<DataTimeDto> toDtoList(List<DataTime> list);
    UserDto toUserDto(User user);
}