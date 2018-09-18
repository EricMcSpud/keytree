package com.fujitsu.digital.controllers;

import com.fujitsu.digital.domain.UserRole;
import com.fujitsu.digital.domain.dto.UserDTO;
import com.fujitsu.digital.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.InvalidParameterException;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger( UserController.class);

    @Autowired
    UserService userService;
    void setUserService( UserService userService) {
        this.userService = userService;
    }

    /**
     * Starts a user registration sequence. Users who satisfy certain criteria may be asked to self-verify their
     *  details and thus auto-activate their account. Alternatively, admins may be asked to review the user's details
     *  and manually activate their account.
     * @param userDTO A UserDTO fully populated with a new user's registration details.
     * @return A fully populated UserDTO, or HTTP 409 if there is a username conflict, or HTTP 400
     *  if the request is badly formed.
     */
    @CrossOrigin(allowCredentials="true")
    @PostMapping("/register")
    public ResponseEntity<UserDTO> register( @RequestBody UserDTO userDTO) {
        logger.info( "register(); username = {}", userDTO.getUsername());
        UserDTO result = null;
        try {
            result = userService.createUser( userDTO);
        }
        catch ( DuplicateKeyException dke) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch ( InvalidParameterException ipe) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

    /**
     * Completes a user registration sequence (for self-verification only).
     * @param userDTO A sparsely populated UserDTO.
     * @return A Boolean which, when true, indicates success, or HTTP response 404 if the user doesn't exist.
     */
    @CrossOrigin(allowCredentials="true")
    @PostMapping("/verify")
    public ResponseEntity<Boolean> verify( @RequestBody UserDTO userDTO) {
        logger.info( "verify(); name = {} {}", userDTO.getFirstName(), userDTO.getLastName());
        Boolean result = null;
        try {
            result = userService.verifyUser( userDTO);
        }
        catch ( UsernameNotFoundException e) {
            return new ResponseEntity<>( HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

    /**
     * Attempts to sign-in an existing registered user.
     * @param credentials The user's registered credentials.
     * @return A fully populated UserDTO or HTTP response 404 if the user doesn't exist.
     */
    @CrossOrigin(allowCredentials="true")
    @PostMapping("/signin")
    public ResponseEntity<UserDTO> signin( @RequestBody Map<String,String> credentials) {
        logger.info( "signin(); username = {}", credentials.get( "username"));
        UserDTO result = null;
        try {
            result = userService.signin( credentials.get( "username"), credentials.get( "password"));
        }
        catch ( UsernameNotFoundException e) {
            return new ResponseEntity<>( HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

    /**
     * Ends a user's session by signing out.
     * @return A simple boolean (nearly always true!).
     */
    @CrossOrigin(allowCredentials="true")
    @RequestMapping("/signout")
    public ResponseEntity<Boolean> signout() {
        logger.info( "signout();");
        return new ResponseEntity<>( userService.signout(), HttpStatus.OK);
    }

    /**
     * Retrieves the current user's status. Forms part of our user session extension and server outage detection
     *  mechanism.
     * @return A boolean that, when true, indicates that the user is still alive and kicking.
     */
    @CrossOrigin(allowCredentials="true")
    @RequestMapping("/me/health")
    public ResponseEntity<Boolean> getUserHealth() {
        UserDTO user = userService.getCurrentUser();
        return new ResponseEntity<>( (user != null && user.isActive()), HttpStatus.OK);
    }

    /**
     * Retrieves the current user's details. Used occasionally to refresh a signed-in user's details after a client
     *  restart.
     * @return A fully populated UserDTO.
     */
    @CrossOrigin(allowCredentials="true")
    @RequestMapping("/me")
    public ResponseEntity<UserDTO> getUserInfo() {
        UserDTO result = userService.getCurrentUser();
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

    /**
     * Updates the current user's details.
     * @param userDTO A fully populated UserDTO.
     * @return A fully populated, updated UserDTO.
     */
    @CrossOrigin(allowCredentials="true")
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateUserInfo( @RequestBody UserDTO userDTO) {
        logger.info( "updateUserInfo(); name = {} {}", userDTO.getFirstName(), userDTO.getLastName());
        UserDTO result = null;
        try {
            result = userService.updateCurrentUser( userDTO);
        }
        catch ( DuplicateKeyException dke) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        catch ( InvalidParameterException ipe) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

    /**
     * Starts the user password reset sequence.
     * @param userDTO A sparsely populated UserDTO.
     * @return A Boolean which, when true, indicates success, or HTTP response 404 if the user doesn't exist.
     */
    @CrossOrigin(allowCredentials="true")
    @PostMapping("/reset/start")
    public ResponseEntity<Boolean> startReset( @RequestBody UserDTO userDTO) {
        logger.info( "startReset(); username = {}", userDTO.getUsername());
        Boolean result = null;
        try {
            result = userService.startResetUser( userDTO);
        }
        catch ( UsernameNotFoundException e) {
            return new ResponseEntity<>( HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

    /**
     * Completes the user password reset sequence.
     * @param userDTO A sparsely populated UserDTO.
     * @return A Boolean which, when true, indicates success, or HTTP response 404 if the user doesn't exist.
     */
    @CrossOrigin(allowCredentials="true")
    @PostMapping("/reset/finish")
    public ResponseEntity<Boolean> finishReset( @RequestBody UserDTO userDTO) {
        logger.info( "finishReset();");
        Boolean result = null;
        try {
            result = userService.finishResetUser( userDTO);
        }
        catch ( UsernameNotFoundException e) {
            return new ResponseEntity<>( HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>( result, HttpStatus.OK);
    }

}