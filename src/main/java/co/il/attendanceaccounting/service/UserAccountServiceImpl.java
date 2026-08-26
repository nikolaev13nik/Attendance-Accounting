package co.il.attendanceaccounting.service;

import java.util.ArrayList;
import java.util.List;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import co.il.attendanceaccounting.dao.UserRepository;
import co.il.attendanceaccounting.dto.UserEditDto;
import co.il.attendanceaccounting.dto.UserProfileDto;
import co.il.attendanceaccounting.dto.UserRegisterDto;
import co.il.attendanceaccounting.exceptions.UserExistsException;
import co.il.attendanceaccounting.exceptions.UserNotFoundException;
import co.il.attendanceaccounting.model.User;

@Service
public class UserAccountServiceImpl implements UserAccountService {

	@Autowired
	UserRepository accountRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Override
	public UserProfileDto register(UserRegisterDto userRegisterDto) {
		if (accountRepository.existsById(userRegisterDto.getIdUser())) {
			throw new UserExistsException();
		}
		String hashPassword = passwordEncoder.encode(userRegisterDto.getPassword());
		User user = User.builder()
				.idUser(userRegisterDto.getIdUser())
				.password(hashPassword)
				.firstName(userRegisterDto.getFirstName())
				.lastName(userRegisterDto.getLastName())
				.role("User")
				.records(new ArrayList<>())
				.build();
		accountRepository.save(user);
		return userToUserProfileDto(user);
	}

	private UserProfileDto userToUserProfileDto(User userAccount) {
		return UserProfileDto.builder()
				.idUser(userAccount.getIdUser())
				.firstName(userAccount.getFirstName())
				.lastName(userAccount.getLastName())
				.roles(userAccount.getRoles())
				.build();
	}

	@Override
	public UserProfileDto login(Integer idUser) {
		User user = accountRepository.findById(idUser).get();
		return userToUserProfileDto(user);
	}

	@Transactional
	@Override
	public UserProfileDto editUser(Integer idUser, UserEditDto userEditDto) {
		User user = accountRepository.findById(idUser).get();
		if (userEditDto.getFirstName() != null) {
			user.setFirstName(userEditDto.getFirstName());
		}
		if (userEditDto.getLastName() != null) {
			user.setLastName(userEditDto.getLastName());
		}
		if (userEditDto.getPassword() != null) {
			String hashPassword = passwordEncoder.encode(userEditDto.getPassword());
			user.setPassword(hashPassword);
		}
		accountRepository.save(user);
		return userToUserProfileDto(user);
	}

	@Transactional
	@Override
	public UserProfileDto removeUser(Integer idUser) {
		User userAccount = accountRepository.findById(idUser)
				.orElseThrow(() -> new UserNotFoundException(idUser.toString()));
		accountRepository.deleteById(idUser);
		return userToUserProfileDto(userAccount);
	}

	@Transactional
	@Override
	public boolean changePassword(Integer idUser, String password) {
		User user = accountRepository.findById(idUser).get();
		String hashPassword = passwordEncoder.encode(password);
		user.setPassword(hashPassword);
		return true;
	}

	@Transactional
	@Override
	public UserProfileDto addRole(Integer idUser, String role) {
		User user = accountRepository.findById(idUser)
				.orElseThrow(() -> new UserNotFoundException(idUser.toString()));
		user.addRole(role);
		accountRepository.save(user);
		return userToUserProfileDto(user);
	}

	@Transactional
	@Override
	public UserProfileDto removeRole(Integer idUser, String role) {
		User userAccount = accountRepository.findById(idUser)
				.orElseThrow(() -> new UserNotFoundException(idUser.toString()));
		userAccount.removeRole(role);
		accountRepository.save(userAccount);
		return userToUserProfileDto(userAccount);
	}

	@Override
	public List<UserProfileDto> getAllUsers() {
		List<User> listUsers = accountRepository.findAll();
		return listUsers.stream().map(this::userToUserProfileDto).toList();
	}
}