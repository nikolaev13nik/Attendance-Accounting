package co.il.attendanceaccounting.service.strategy;

import org.springframework.stereotype.Service;

import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

import static java.util.Optional.ofNullable;

@Service
public class EditUserService extends AccountServiceBase<UserEditDto> {

    @Override
    protected void fetch(AccountContext<UserEditDto> context) {
        context.getUsers().add(findUserOrThrow(context.getIdUser()));
    }

    @Override
    protected void executeBusiness(AccountContext<UserEditDto> context) {
        UserEditDto userEditDto = context.getTask();
        User user = context.getSingleUser();
        ofNullable(userEditDto.getFirstName()).ifPresent(user::setFirstName);
        ofNullable(userEditDto.getLastName()).ifPresent(user::setLastName);
        ofNullable(userEditDto.getPassword()).ifPresent(password -> encodePassword(user, password));
        ofNullable(userEditDto.getEmail()).ifPresent(user::setEmail);
    }
}
