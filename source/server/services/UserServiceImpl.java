package com.fujitsu.digital.services;

import com.fujitsu.digital.domain.User;
import com.fujitsu.digital.domain.UserRole;
import com.fujitsu.digital.domain.dto.UserDTO;
import com.fujitsu.digital.domain.dto.UserPermissionDTO;
import com.fujitsu.digital.repositories.UserRepository;
import com.fujitsu.digital.security.UserProvider;
import com.fujitsu.digital.utils.ConfigCache;
import com.fujitsu.digital.utils.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.fujitsu.digital.utils.EmailTemplateNames;

@Service(value="userService")
@Transactional
public class UserServiceImpl extends BaseServiceImpl<User, UserDTO, Long> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private UserProvider userProvider;
    private UserRoleService userRoleService;
    private UserRepository userRepository;
    private EmailSender emailSender;
    private ConfigCache configCache;

    @Autowired
    public void setUserProvider(UserProvider userProvider) {
        this.userProvider = userProvider;
    }

    @Autowired
    public void setUserRoleService(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @Autowired
    public void setEmailSender(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Autowired
    public void setConfigCache(ConfigCache configCache) {
        this.configCache = configCache;
    }

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
        super.setBaseRepository( userRepository);
    }

    @Override
    public UserDTO getById( Long id) {
        return findAsDTO( id);
    }

    @Override
    public List<UserDTO> getAll() {
        return findAllAsDTOs( sortById());
    }

    @Override
    public List<User> findByUserRole( UserRole userRole) {
        List<User> result = userRepository.findByUserRole( userRole);
        return result;
    }

    @Override
    public UserDTO getCurrentUser() {
        UserDTO result = userProvider.getAuthenticatedUser();
        if ( result == null) {
            result = new UserDTO();
            result.setUsername( "Annon");
            result.setFirstName( "No");
            result.setLastName( "One");
            result.setEmailAddress( "no.one@here.com");
            result.setStatus( User.Status.DISABLED.name());
            result.setRoleName( "Browser");
            result.setSecurityLevel( User.SecurityLevel.PUBLIC.name());
            result.setActive( false);
            result.setCredentialsExpired( true);
            result.setUserPermissions( new ArrayList<>());
        }
        return result;
    }

    /**
     * Starts a registration request sequence by creating a new user account and (conditionally, depending on email
     *  address), sending out a self-verification email.
     * @param userDTO A UserDTO fully populated with the user's registration details.
     * @return A fully populated UserDTO.
     *
     * @throws DuplicateKeyException if the username already exists.
     * @throws InvalidParameterException if any of the supplied input parameters are invalid (including auto-assigned
     *  Role and Access Level).
     */
    @Override
    public UserDTO createUser( UserDTO userDTO) throws DuplicateKeyException, InvalidParameterException {
        User result = null;

        // Populate the new User entity form user-supplied attributes
        User user = new User();
        user.setUsername( userDTO.getUsername());
        user.setPassword( userProvider.encryptPassword( userDTO.getPassword()));
        user.setFirstName( userDTO.getFirstName());
        user.setLastName( userDTO.getLastName());
        user.setEmailAddress( userDTO.getEmailAddress());
        user.setCreatedDate( LocalDateTime.now());

        // If auto-approve is enabled and the submitted email address meets the match criteria, we'll create their
        // account in an disabled state but send them a self-verification email containing a one-time usage token.
        // When they click back into the app, we'll then validate the token and activate the account.
        //
        // If auto-approve is not enabled, we'll still create a new account, but keep it disabled until an
        // administrator reviews the registration request and manually activates the account.
        String roleName = null;
        String accessLevelName = null;
        String token = null;
        if ( configCache.regApproveOn() && userEmailMatches( userDTO.getEmailAddress())) {
            user.setStatus( User.Status.PENDING);
            user.setActive( User.USER_INACTIVE);
            accessLevelName = configCache.regInitialAccessLevel();
            roleName = configCache.regInitialRole();
            // Generate and store the self-verification token alongside the new user's details
            token = UUID.randomUUID().toString();
            user.setToken( userProvider.encryptPassword( token));
        }
        else {
            user.setStatus( User.Status.PENDING);
            user.setActive( User.USER_INACTIVE);
            accessLevelName = User.DEFAULT_ACCESS_LEVEL_NAME;
            roleName = UserRole.DEFAULT_ROLE_NAME;
        }

        // Protect this method against a badly configured Access Level name. If this can't be resolved, then throw
        // our bad parameter exception, so it doesn't go undetected
        try {
            user.setSecurityLevel( User.SecurityLevel.valueOf( accessLevelName));
        }
        catch ( IllegalArgumentException iae) {
            throw new InvalidParameterException( "Bad Access Level name: " + accessLevelName);
        }

        // Protect this method against a badly configured Role name. I this can't be resolved, then throw our bad
        // parameter exception, so, once again, it doesn't go undetected
        UserRole userRole = userRoleService.findByName( roleName);
        if ( userRole == null) {
            throw new InvalidParameterException( "Bad Role name: " + roleName);
        }
        user.setUserRole( userRole);

        // Finally, save the new user details. If this fails due to a duplicate username, then throw our duplicate
        // key exception to notify the user
        try {
            result = save( user);

            // Email notifications depend on whether the registration is a candidate for self-verification or not
            if ( token != null) {
                // Tell the user that their registration request was received but now needs self-verification
                emailSender.sendHTMLMessage( user.getEmailAddress(), "Innovation Portal - Registration Verification", token, EmailTemplateNames.USER_VERIFICATION_REQUESTED);

                // Tell the system administrator(s) that a new user self-verification is now in progress
                emailSender.sendHTMLMessage( configCache.emailAdminArray(), "Innovation Portal - New User Verification Requested", userDTO.getFirstName() + " " + userDTO.getLastName() + " (" + userDTO.getEmailAddress() + ")", EmailTemplateNames.ADMIN_USER_VERIFICATION_REQUESTED);
            }
            else {
                // Tell the user that their registration request was received but is now awaiting review
                emailSender.sendHTMLMessage( userDTO.getEmailAddress(), "Innovation Portal - Registration Received", EmailTemplateNames.USER_REGISTERED);

                // Tell the system administrator(s) that a new user registration request is now pending
                emailSender.sendHTMLMessage( configCache.emailAdminArray(), "Innovation Portal - New User Registration", userDTO.getFirstName() + " " + userDTO.getLastName() + " (" + userDTO.getEmailAddress() + ")", EmailTemplateNames.ADMIN_USER_REGISTERED);
            }
        }
        catch ( DataIntegrityViolationException e) {
            throw new DuplicateKeyException( "Duplicate Username: " + userDTO.getUsername());
        }
        return asDTO( result);
    }

    /**
     * Completes a registration request sequence by verifying the user's email address (from an email link).
     * @param userDTO A UserDTO sparsely populated with *just* the user's name and verification token.
     * @return A boolean which, when true, indicates that the sequence has been successfully completed. Else indicates
     *  that there was an error.
     *
     * @throws UsernameNotFoundException if no user details match the supplied token.
     */
    @Override
    public Boolean verifyUser( UserDTO userDTO) throws UsernameNotFoundException {
        Boolean result = false;

        // Look up the user's account based on the passed in token. If found, check that the couple of extra details
        // match the account details
        String encryptedToken = userProvider.encryptPassword( userDTO.getToken());
        User user = userRepository.verifyByToken( userProvider.encryptPassword( userDTO.getToken()));
        if ( user != null && user.getFirstName().equalsIgnoreCase( userDTO.getFirstName()) && user.getLastName().equalsIgnoreCase( userDTO.getLastName())) {
            user.setStatus( User.Status.ACTIVE);
            user.setActive( User.USER_ACTIVE);
            user.setToken( null);
            save( user);
            result = true;

            // Tell the user that their registration is now complete
            emailSender.sendHTMLMessage( user.getEmailAddress(), "Innovation Portal - Registration Completed", EmailTemplateNames.USER_VERIFIED);

            // Tell the system administrator(s) that a new user registration is now complete
            emailSender.sendHTMLMessage( configCache.emailAdminArray(), "Innovation Portal - New User Verified", user.getFirstName() + " " + user.getLastName() + " (" + user.getEmailAddress() + ")", EmailTemplateNames.ADMIN_USER_VERIFIED);

        }
        else {
            throw new UsernameNotFoundException( "");
        }
        return result;
    }

    @Override
    public UserDTO updateCurrentUser( UserDTO userDTO) throws UsernameNotFoundException {
        UserDTO result = null;

        // Look up the user's details based on the authenticated user email address and the passed in password
        User user = userRepository.findByUsernameAndPassword( getCurrentUser().getEmailAddress(), userProvider.encryptPassword( userDTO.getPassword()));
        if ( user == null) {
            throw new UsernameNotFoundException( getCurrentUser().getEmailAddress());
        }
        else if ( getCurrentUser().getOid().equals( user.getOid())) {
            // We've found a user, and its the same one as the authenticated user, so now we apply the updates. These
            // are limited to just first name, last name, and (optionally) password
            user.setFirstName( userDTO.getFirstName());
            user.setLastName( userDTO.getLastName());
            if ( userDTO.getNewPassword() != null) {
                user.setPassword( userProvider.encryptPassword( userDTO.getNewPassword()));
            }

            // Persist the updates and tell the user that they've successfully updated their details
            User userUpdated = save( user);
            result = asDTO( userUpdated);
            emailSender.sendHTMLMessage( result.getEmailAddress(), "Innovation Portal - User Updated", EmailTemplateNames.USER_UPDATED);
        }
        return result;
    }

    /**
     * Starts a password reset sequence by sending out the initial email. This contains a link back to the
     *  application to complete the sequence.
     * @param userDTO A UserDTO sparsely populated with *just* the user's email address.
     * @return A boolean which, when true, indicates that the sequence has been started. Else indicates that there was
     *  an error.
     * @throws UsernameNotFoundException Thrown if no user details match the supplied email address.
     */
    @Override
    public Boolean startResetUser( UserDTO userDTO) throws UsernameNotFoundException {
        Boolean result = false;

        // Look up the user's account based on the passed in email address
        User user = userRepository.findByUsername( userDTO.getEmailAddress());
        if ( user == null) {
            throw new UsernameNotFoundException( userDTO.getEmailAddress());
        }
        else {
            // We've found a user, so generate the UUID token for inclusion in an email
            String token = UUID.randomUUID().toString();

            // Persist the encrypted token alongside the user's details
            String encryptedToken = userProvider.encryptPassword( token);
            user.setToken( encryptedToken);
            save( user);
            result = true;

            // Tell the user that their password reset is now in progress and needs further action
            emailSender.sendHTMLMessage( user.getEmailAddress(), "Innovation Portal - Password Reset Requested", token, EmailTemplateNames.USER_RESET_REQUESTED);
        }
        return result;
    }

    /**
     * Completes a password reset sequence by persisting the user's new password.
     * @param userDTO A UserDTO sparsely populated with *just* the reset token and user's new password.
     * @return A boolean which, when true, indicates that the sequence has been successfully completed. Else indicates
     *  that there was an error.
     * @throws UsernameNotFoundException Thrown if no user details match the supplied token.
     */
    @Override
    public Boolean finishResetUser( UserDTO userDTO) throws UsernameNotFoundException {
        Boolean result = false;

        // Validate the new passwords
        if ( userDTO.getPassword() != null && userDTO.getNewPassword() != null && userDTO.getPassword().equals( userDTO.getNewPassword())) {

            // Look up the user's account based on the passed in token
            User user = userRepository.findByToken( userProvider.encryptPassword( userDTO.getToken()));
            if ( user == null) {
                throw new UsernameNotFoundException( "");
            }
            else {
                user.setPassword( userProvider.encryptPassword( userDTO.getPassword()));
                user.setToken( null);
                save( user);
                result = true;

                // Tell the user that their password reset is complete
                emailSender.sendHTMLMessage( user.getEmailAddress(), "Innovation Portal - Password Reset Completed", EmailTemplateNames.USER_RESET);
            }
        }
        return result;
    }

    @Override
    public UserDTO signin( String userName, String password) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameAndPassword( userName, userProvider.encryptPassword( password));
        if ( user == null) {
            throw new UsernameNotFoundException( userName);
        }
        // User found - prepare the token and save in the User Provider's context
        UserRole role = user.getUserRole();
        List<Permission> authorities = new ArrayList<>();
        authorities.addAll( role.getUserPermissionList().stream().map( permission -> new PermissionImpl( permission.getName())).collect( Collectors.toList()));
        Map<String,String> credentials = new HashMap<>();
        credentials.put( "username", userName);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken( userName, credentials, authorities);
        auth.setDetails( this.asDTO( user));
        userProvider.setAuthentication( auth);

        UserDTO result = asDTO( user);
        return result;
    }

    @Override
    public Boolean signout() {
        userProvider.clearAuthentication();
        return Boolean.TRUE;
    }

    @Override
    public UserDTO updateUser( Long id, UserDTO dto) throws DuplicateKeyException, InvalidParameterException {
        UserDTO result = null;
        User user = userRepository.getOne( id);
        UserDTO dtoOriginal = asDTO( user);

        // Apply updates to the retrieved user details
        user.setUsername( dto.getUsername());
        user.setFirstName( dto.getFirstName());
        user.setLastName( dto.getLastName());
        user.setEmailAddress( dto.getUsername());
        if ( dto.getPassword() != null && dto.getPassword().length() > 0) {
            user.setPassword( userProvider.encryptPassword( dto.getPassword()));
        }
        user.setSecurityLevel( User.SecurityLevel.valueOf( dto.getSecurityLevel()));
        user.setUpdatedDate( LocalDateTime.now());
        user.setUserRole( userRoleService.findEntity( dto.getRoleId()));
        user.setStatus( dto.isActive() ? User.Status.ACTIVE : User.Status.DISABLED);
        user.setActive( dto.isActive() ? User.USER_ACTIVE : User.USER_INACTIVE);

        // Persist the changes, and prepare to return the new details. Catch any data integrity violation and
        // re-throw as a declared exception for our caller to process
        try {
            User userUpdated = save(user);
            result = asDTO(userUpdated);

            // Send an appropriate email to the user, depending on what changed
            if (!dtoOriginal.isActive() && result.isActive()) {
                // The user is NOW active (and was previously inactive)
                emailSender.sendHTMLMessage(result.getEmailAddress(), "Innovation Portal - User Activated", EmailTemplateNames.USER_ACTIVATED);
            }
            else if (result.isActive() && dto.getPassword() != null && dto.getPassword().length() > 0) {
                // A new password has been saved
                emailSender.sendHTMLMessage(result.getEmailAddress(), "Innovation Portal - Password Reset", EmailTemplateNames.USER_RESET);
            }
            else if (result.isActive()) {
                // Something else was updated
                emailSender.sendHTMLMessage(result.getEmailAddress(), "Innovation Portal - User Updated", EmailTemplateNames.USER_UPDATED);
            }
        }
        catch ( DataIntegrityViolationException e) {
            throw new DuplicateKeyException( "Duplicate!");
        }
        return result;
    }

    @Override
    public User asEntity( UserDTO dto) {
        User result = super.asEntity( dto);
        if ( result != null) {
            result.setOid( dto.getOid());
            result.setUsername( dto.getUsername());
            result.setFirstName( dto.getFirstName());
            result.setLastName( dto.getLastName());
            result.setEmailAddress( dto.getEmailAddress());
            result.setStatus( User.Status.valueOf( dto.getStatus()));
            result.setSecurityLevel( User.SecurityLevel.valueOf( dto.getSecurityLevel()));
            result.setActive( dto.isActive() ? User.USER_ACTIVE : User.USER_INACTIVE);
            result.setCreatedDate( dto.getCreatedDate());
            result.setUpdatedDate( dto.getUpdatedDate());
            result.setUserRole( userRoleService.findEntity( dto.getRoleId()));
        }
        return result;
    }

    @Override
    public UserDTO asDTO( User entity) {
        UserDTO result = super.asDTO( entity);
        if ( result != null) {
            result.setOid( entity.getOid());
            result.setToken( entity.getToken());
            result.setUsername( entity.getUsername());
            result.setPassword( "");
            result.setFirstName( entity.getFirstName());
            result.setLastName( entity.getLastName());
            result.setFullName( entity.getFirstName() + " " + entity.getLastName());
            result.setEmailAddress( entity.getEmailAddress());
            result.setStatus( entity.getStatus().name());
            result.setActive( entity.isActive());
            result.setSecurityLevel( entity.getSecurityLevel().name());
            result.setCreatedDate( entity.getCreatedDate());
            result.setUpdatedDate( entity.getUpdatedDate());
            if ( entity.getUserRole() != null) {
                result.setRoleName(entity.getUserRole().getName());
                result.setRoleId(entity.getUserRole().getOid());
            }
            result.setUserPermissions( entity.getUserRole().getUserPermissionList().stream().map( permission -> new UserPermissionDTO( permission.getOid(), permission.getName())).collect( Collectors.toList()));
        }
        return result;
    }

    @Override
    public UserDTO asSummaryDTO( User entity) {
        UserDTO result = super.asDTO( entity);
        if ( result != null) {
            result.setOid( entity.getOid());
            result.setUsername( entity.getUsername());
            result.setPassword( "");
            result.setFirstName( entity.getFirstName());
            result.setLastName( entity.getLastName());
            result.setFullName( entity.getFirstName() + " " + entity.getLastName());
            result.setEmailAddress( entity.getEmailAddress());
            result.setStatus( entity.getStatus().name());
            result.setActive( entity.isActive());
            result.setCreatedDate( entity.getCreatedDate());
            result.setUpdatedDate( entity.getUpdatedDate());
        }
        return result;
    }

    private boolean userEmailMatches( String emailAddress) {
        String normalisedEmailAddress = emailAddress.toLowerCase().trim();
        List<String> emailMatcher = configCache.regEmailMatcherList();
        for ( String regEmailMatcher : configCache.regEmailMatcherList()) {
            if ( normalisedEmailAddress.endsWith( regEmailMatcher)) {
                return true;
            }
        }
        return false;
    }

    private Sort sortById() {
        return new Sort( Sort.Direction.DESC, "oid");
    }

    public interface Permission extends GrantedAuthority {
        public String getName();
        public void setName(String name);
    }
    public class PermissionImpl implements Permission {
        String name;

        public PermissionImpl( String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getAuthority() {
            return this.name;
        }
    }
}
