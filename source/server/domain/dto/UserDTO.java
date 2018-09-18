package com.fujitsu.digital.domain.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fujitsu.digital.security.PermissionNames;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserDTO extends AuditableDTOIdImpl implements UserDetails {

    private String username;
    private String password;
    private String newPassword;
    private String firstName;
    private String lastName;
    private String fullName;
    private String emailAddress;
    private Boolean active;
    private Long roleId;
    private String roleName;
    private String status;
    private String securityLevel;
    private String token;
    private boolean credentialsExpired = Boolean.FALSE;
    private List<UserPermissionDTO> userPermissions = new ArrayList<>();

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return fullName;
    }
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNewPassword() {
        return newPassword;
    }
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getRoleName() {
        return roleName;
    }
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Long getRoleId() {
        return roleId;
    }
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }
    public boolean isActive() {
        return active;
    }

    public Boolean getCredentialsExpired() {
        return credentialsExpired;
    }
    public void setCredentialsExpired( Boolean credentialsExpired) {
        this.credentialsExpired = credentialsExpired;
    }
    public boolean isCredentialsExpired() {
        return credentialsExpired;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }
    public void setSecurityLevel(String securityLevel) {
        this.securityLevel = securityLevel;
    }

    public List<UserPermissionDTO> getUserPermissions() {
        return userPermissions;
    }
    public void setUserPermissions(List<UserPermissionDTO> userPermissions) {
        this.userPermissions = userPermissions;
    }

    @JsonIgnore
    public boolean isAdminUser() {
        for ( UserPermissionDTO permission : userPermissions) {
            if (PermissionNames.IP_ADMIN.equalsIgnoreCase( permission.getName())) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean hasPermission( String permissionName) {
        for ( UserPermissionDTO permission : userPermissions) {
            if ( permissionName.equalsIgnoreCase( permission.getName())) {
                return true;
            }
        }
        return false;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.userPermissions;
    }

    @Override
    public String getPassword() {
        return this.password;
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
        return !credentialsExpired;
    }

    @Override
    public boolean isEnabled() {
        return this.isActive();
    }

}