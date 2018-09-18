package com.fujitsu.digital.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.util.Collection;

@Entity(name = "USER")
public class User extends AuditableEntityIdImpl implements UserDetails {

    public static final String DEFAULT_ACCESS_LEVEL_NAME = "PUBLIC";
    public static final String USER_ACTIVE = "Y";
    public static final String USER_INACTIVE = "N";

    public enum Status {
        PENDING,
        ACTIVE,
        DISABLED
    }
    public enum SecurityLevel {
        PUBLIC,
        CONFIDENTIAL,
        PRIVATE
    }
    public SecurityLevel DEFAULT_SECURITY_LEVEL = SecurityLevel.PUBLIC;

    @Column(name = "STATUS")
    private Status status;

    @Column(name = "USER_NAME", unique = true)
    private String username;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "FIRST_NAME")
    private String firstName;

    @Column(name = "LAST_NAME")
    private String lastName;

    @Column(name = "EMAIL_ADDRESS", unique = true)
    private String emailAddress;

    @Column(name = "SECURITY_LEVEL")
    private SecurityLevel securityLevel;

    @Column(name = "ACTIVE")
    private String active;

    @Column(name = "TOKEN", unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ROLE_ID")
    private UserRole userRole;

    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        this.status = status;
    }

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }
    public void setLastName( String lastName) {
        this.lastName = lastName;
    }
    public String getFullName(){
        return getFirstName() + " " + getLastName();
    }

    public String getEmailAddress() {
        return emailAddress;
    }
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public SecurityLevel getSecurityLevel() {
        return securityLevel;
    }
    public void setSecurityLevel(SecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }

    public String getActive() {
        return active;
    }
    public void setActive(String active) {
        this.active = active;
    }
    public boolean isActive() {
        return USER_ACTIVE.equalsIgnoreCase( this.getActive());
    }

    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }

    public UserRole getUserRole() {
        return userRole;
    }
    public void setUserRole(UserRole userRole) {
        this.userRole = userRole;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public String getUsername() {
        return this.username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return "";
    }
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.isActive();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;
        if (username != null ? !username.equals(user.username) : user.username != null) return false;
        if (firstName != null ? !firstName.equals(user.firstName) : user.firstName != null) return false;
        if (lastName != null ? !lastName.equals(user.lastName) : user.lastName != null) return false;
        if (userRole != null ? !userRole.equals(user.userRole) : user.userRole != null) return false;
        return emailAddress != null ? emailAddress.equals(user.emailAddress) : user.emailAddress == null;

    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (firstName != null ? firstName.hashCode() : 0);
        result = 31 * result + (lastName != null ? lastName.hashCode() : 0);
        result = 31 * result + (emailAddress != null ? emailAddress.hashCode() : 0);
        result = 31 * result + (userRole != null ? userRole.hashCode() : 0);
        return result;
    }
}