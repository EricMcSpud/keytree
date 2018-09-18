package com.fujitsu.digital.services;

import com.fujitsu.digital.domain.User;
import com.fujitsu.digital.domain.UserRole;
import com.fujitsu.digital.domain.dto.UserDTO;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

public interface UserService extends BaseService<User, UserDTO, Long> {

    UserDTO getById(Long id);
    UserDTO asSummaryDTO(User user);
    List<UserDTO> getAll();
    List<User> findByUserRole(UserRole userRole);

    UserDTO getCurrentUser();
    UserDTO updateCurrentUser(UserDTO userDTO);

    Boolean startResetUser(UserDTO userDTO);
    Boolean finishResetUser(UserDTO userDTO);

    UserDTO signin(String userName, String password);
    Boolean signout();

    UserDTO createUser(UserDTO userDTO);
    Boolean verifyUser(UserDTO userDTO);

    UserDTO updateUser(Long id, UserDTO userDTO);
}
