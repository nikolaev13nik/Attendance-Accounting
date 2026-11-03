package co.il.attendanceaccounting.service.strategy;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import co.il.attendance.security.JwtClaimsResolver;
import co.il.attendanceaccounting.context.AccountContext;
import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.exceptions.CurrentPasswordMismatchException;
import co.il.attendanceaccounting.model.User;
import co.il.attendanceaccounting.model.UserBasicKey;
import co.il.attendanceaccounting.service.base.AccountServiceBase;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Service
public class EditUserService extends AccountServiceBase<UserEditDto> {

    @Override
    protected void fetch(AccountContext<UserEditDto> context) {
        context.getUsers().add(findUserOrThrow(context));
    }

    @Override
    protected void validate(AccountContext<UserEditDto> context) {
        UserEditDto userEditDto = context.getTask();
        if (userEditDto.getPassword() != null && isCallerTheUser(context)
                && isFalse(passwordMatches(context.getSingleUser(), userEditDto.getCurrentPassword()))) {
            throw new CurrentPasswordMismatchException();
        }
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

    private boolean isCallerTheUser(AccountContext<UserEditDto> context) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserBasicKey key = context.getUserBasicKey();
        return auth != null
                && key.getIdUser().toString().equals(auth.getName())
                && key.getTenantId().toString().equals(JwtClaimsResolver.currentTenantId(auth));
    }
}
