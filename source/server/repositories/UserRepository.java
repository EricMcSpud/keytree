package com.fujitsu.digital.repositories;

import com.fujitsu.digital.domain.User;
import com.fujitsu.digital.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT u FROM com.fujitsu.digital.domain.User AS u WHERE UPPER(u.username) = UPPER(:username) AND u.password = :password AND u.active = 'Y'")
    User findByUsernameAndPassword( @Param("username") String username, @Param("password") String password);

    @Query(value = "SELECT u FROM com.fujitsu.digital.domain.User AS u WHERE UPPER(u.username) = UPPER(:username) AND u.active = 'Y'")
    User findByUsername( @Param("username") String username);

    @Query(value = "SELECT u FROM com.fujitsu.digital.domain.User AS u WHERE u.token = :token AND u.active = 'Y'")
    User findByToken( @Param("token") String token);

    @Query(value = "SELECT u FROM com.fujitsu.digital.domain.User AS u WHERE u.token = :token")
    User verifyByToken( @Param("token") String token);

    @Query(value = "SELECT u FROM com.fujitsu.digital.domain.User AS u " +
            "JOIN u.userRole AS ur " +
            "JOIN ur.userPermissionList AS up WHERE up.name = :permissionName")
    List<User> findAllWithPermission( @Param("permissionName") String permissionName);
    List<User> findByUserRole( UserRole userRole);
}