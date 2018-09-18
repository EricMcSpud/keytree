import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { timer } from 'rxjs/observable/timer';
import { ReplaySubject } from 'rxjs/ReplaySubject';
import { Subscription } from 'rxjs/Subscription';
import { AuthInfo } from '../interfaces/auth-info';
import { AuthInfoImpl } from '../classes/auth-info-impl';
import { UserInfo } from '../interfaces/user-info';
import { configSettings } from '../../../environments/environment';
import { UserRole } from '../interfaces/user-role';
import { UserPermission } from '../interfaces/user-permission';

@Injectable()
export class UserService {

  private baseURL: string = configSettings.serverBaseURL + '/api/users';
  private baseAdminURL: string = configSettings.serverBaseURL + '/api/admin/users';
  private rolesAdminURL: string = configSettings.serverBaseURL + '/api/admin/roles';
  private permissionsAdminURL: string = configSettings.serverBaseURL + '/api/admin/permissions';
  private registerURL: string = this.baseURL + '/register';
  private verifyURL: string = this.baseURL + '/verify';
  private signinURL: string = this.baseURL + '/signin';
  private signoutURL: string = this.baseURL + '/signout';
  private userInfoURL: string = this.baseURL + '/me';
  private userHealthURL: string = this.userInfoURL + '/health';
  private startUserResetURL: string = this.baseURL + '/reset/start';
  private finishUserResetURL: string = this.baseURL + '/reset/finish';

  // Single application-wide truth about the current user's details, whether annonymous or signed in
  private currentUser: UserInfo = null;
  // and when Components need to acquire the current user's details (e.g. following sign-in), they'll "observe" this Subject
  private currentUserSubject: ReplaySubject<UserInfo>;
  // and when Components need to acquire details about the auth procedure, they'll "observe" this Subject
  private currentAuthSubject: ReplaySubject<AuthInfo>;

  // User heatbeat timer keeps users logged in until they explcitiy log out, or there's a comms error
  private _timeoutSeconds: number = 60;
  private timerSubscription: Subscription;
  private timerObservable: Observable<number>;

  // HTTP options; ensure that we supply user authentication details on every request
  private options: Object = {
    withCredentials: true
  };

  constructor( private http: HttpClient) {
    // Callers can subscribe to UserInfo to make use of a signed in user's details
    this.currentUserSubject = new ReplaySubject<UserInfo>( 1);

    // Or callers can subscribe to AuthInfo to also receive information about the authentication procedure
    this.currentAuthSubject = new ReplaySubject<AuthInfo>( 1);

    // User heatbeat timer is fired up after user has signed-in
    this.timerObservable = timer( this._timeoutSeconds * 1000);
  }

  /**
   * Provides access to the signed-in user's UserInfo via a Subject (a specialised Observable)
   */
  getUserInfoSubject(): ReplaySubject<UserInfo> {
    return this.currentUserSubject;
  }

  /**
   * Provides access to the auth status of the current user via a Subject (a specialised Observable)
   */
  getAuthInfoSubject(): ReplaySubject<AuthInfo> {
    return this.currentAuthSubject;
  }

  /**
   * Requests a new user registration using the supplied details.
   * @param userInfo A fully populated UserInfo object instance.
   * @returns An Observable ready for subscription.
   */
  registerUser( userInfo: UserInfo): Observable<UserInfo> {
    return this.http.post<UserInfo>( this.registerURL, userInfo, this.options);
  } 

  /**
   * Verifies and completes a new user registration. Having clicked back through to the app. from a
   *  registration acknowledgement email, a new user can verify their registration details.
   * @param userInfo The details of the user to be verified.
   */
  verifyUser( userInfo: UserInfo): Observable<boolean> {
    return this.http.post<boolean>( this.verifyURL, userInfo, this.options);
  }

  /**
   * Requests a user sign-in using the supplied credentials. Callers can "observe" the resultant UserInfo
   *  by calling our observeUserInfo() method.
   * @param username A previously registered user's username.
   * @param password A previously registered user's password.
   */
  signinUser( username: string, password: string): void {
    this.http.post<UserInfo>( this.signinURL, {username: username, password: password}, this.options).subscribe(
      userInfo => {
        this.startTimer();  // User signed in ok; start the heatbeat timer
        this.currentUser = userInfo;
        this.currentUserSubject.next( this.deepCopy( userInfo));
        this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_IN, this.deepCopy( userInfo)));
      },
      error => {
        this.stopTimer();
        this.currentUser = null;
        this.currentUserSubject.next( null);
        this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_ERROR, null));
      }
    );
  }
  isUserSignedIn(): boolean {
    return (this.currentUser != null);
  }
  private deepCopy<T>( inputObject: T): T {
    return JSON.parse( JSON.stringify( inputObject));
  }

  /**
   * Requests a user sign-out.
   */
  signoutUser(): void {
    this.http.get( this.signoutURL, this.options).subscribe(
      result => {
        this.stopTimer();
        this.currentUser = null;
        this.currentUserSubject.next( null);
        this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_OUT, null));
      },
      error => {
        this.stopTimer();
        this.currentUser = null;
        this.currentUserSubject.next( null);
        this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_OUT, null));
      }
    );
  }

  /**
   * Checks the health of the signed-in user on a regular timer basis.
   */
  public healthCheckUser(): void {
    this.http.get<boolean>( this.userHealthURL, this.options).subscribe(
      userInfo => {
        // Anything other than true (erm, false then) is treated as an error
        if ( userInfo != true) {
          this.stopTimer();
          this.currentUser = null;
          this.currentUserSubject.next( null);
          this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_OUT, null));
        }
      },
      error => {
        this.stopTimer();
        this.currentUser = null;
        this.currentUserSubject.next( null);
        this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_OUT, null));
      }
    );
  }

  /**
   * After sign-in, we'd prefer not to have track every action in order to start/re-start a session timeout,
   *  so we use a heatbeat timer to effectively extend the user's session until sign-out or an error.
   * 
   * startTimer() is called after a successful sign-in, stopTimer() is called after a successful sign-out,
   *  and restartTimer() is a special case method, conditionally called if we're already signed-in, and the
   *  entire app. (i.e. the browser window) was forcibly reloaded.
   */
  private startTimer() {
    if ( this.timerSubscription) {
      this.timerSubscription.unsubscribe();
    }
    this.timerSubscription = this.timerObservable.subscribe( n => {
      this.timerComplete( n);
    });
  }
  private restartTimer() {
    if ( this.timerSubscription == null) {
      this.startTimer();
    }
  }
  private stopTimer() {
    if ( this.timerSubscription) {
      this.timerSubscription.unsubscribe();
      this.timerSubscription = null;
    }
  }

  /**
   * Called at the end of our timeout period to refresh the user's details.
   * @param id The unique ID of the timeout.
   */
  private timerComplete( id: number): void {
    this.startTimer();
    this.healthCheckUser();
  }

  /**
   * Requests a copy of the currently signed-in UserInfo details.
   */
  public getUserInfo(): void {
    this.http.get<UserInfo>( this.userInfoURL, this.options).subscribe(
      userInfo => {
        // We wouldn't normally expect a user's credentials to expire, but just in case they do, let all
        // subscribers know
        if ( userInfo.credentialsExpired) {
          this.stopTimer();
          this.currentUser = null;
          this.currentUserSubject.next( null);
          this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_OUT, null));
        }
        else {
          // Otherwise, just save the returned credentials, which will also inform all subscribers
          this.restartTimer();  // Make sure the timer is running. Allows a forced browser
          this.currentUser = userInfo;
          this.currentUserSubject.next( this.deepCopy( userInfo));
          this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_IN, this.deepCopy( userInfo)));
        }
      },
      error => {
        this.stopTimer();
        this.currentUser = null;
        this.currentUserSubject.next( null);
        this.currentAuthSubject.next( new AuthInfoImpl( AuthInfoImpl.STATUS_SIGNED_OUT, null));
      }
    );
  }

  /**
   * Prepares to update the current user's details. This is made available to signed users to update a limited
   *  set of their own user details; i.e. name and/or password.
   * @param user The details of the user to be updated.
   */
  updateUserInfo( user: UserInfo): Observable<UserInfo> {
    return this.http.put<UserInfo>( this.userInfoURL, user, this.options);
  }

  /**
   * Prepares to start the user password reset sequence. A non-signed in user can submit an email address to
   *  the server, and have it generate a password reset email.
   * @param user The details of the user to be updated.
   */
  startUserReset( user: UserInfo): Observable<UserInfo> {
    return this.http.post<UserInfo>( this.startUserResetURL, user, this.options);
  }

  /**
   * Prepares to complete the user password reset sequence. Having clicked back through to the app. from a
   *  password reset email, a non-signed in user can submit a new pasword. 
   * @param user The details of the user to be updated.
   */
  finishUserReset( user: UserInfo): Observable<UserInfo> {
    return this.http.post<UserInfo>( this.finishUserResetURL, user, this.options);
  }

  /**
   * Prepares to retrieve the full list of user details (Admin ONLY).
   */
  getUsers(): Observable<Array<UserInfo>> {
    return this.http.get<Array<UserInfo>>( this.baseAdminURL, this.options);
  }

  /**
   * Prepares to retrieve a user's details by id (Admin ONLY).
   * @param id The primary key of the user to be rtrieved.
   */
  getUser( id: string): Observable<UserInfo> {
    return this.http.get<UserInfo>( this.baseAdminURL + '/' + id, this.options);
  }

  /**
   * Prepares to updates a user's details by id (Admin ONLY).
   * @param id The primary key of the user to be updated.
   * @param user The full details of the user to be updated.
   */
  updateUser( id: number, user: UserInfo): Observable<UserInfo> {
    return this.http.put<UserInfo>( this.baseAdminURL + '/' + id, user, this.options);
  }

  /**
   * Prepares an Observable that can get all available User Roles (Admin ONLY).
   */
  getUserRoles(): Observable<Array<UserRole>> {
    return this.http.get<Array<UserRole>>( this.rolesAdminURL, this.options);
  }

  /**
   * Prepares to retrieve a User Role's details by id (Admin ONLY).
   * @param id The primary key of the User Role to be rtrieved.
   */
  getUserRole( id: string): Observable<UserRole> {
    return this.http.get<UserRole>( this.rolesAdminURL + '/' + id, this.options);
  }

  /**
   * Prepares to updates a User Role's details by id (Admin ONLY).
   * @param id The primary key of the User Role to be updated.
   * @param userRole The full details of the User Role to be updated.
   */
  updateUserRole( id: number, userRole: UserRole): Observable<UserRole> {
    return this.http.put<UserRole>( this.rolesAdminURL + '/' + id, userRole, this.options);
  }

  /**
   * Prepares to create a new User Role (Admin ONLY).
   * @param userRole The full details of the User Role to be created.
   */
  createUserRole( userRole: UserRole): Observable<UserRole> {
    return this.http.post<UserRole>( this.rolesAdminURL, userRole, this.options);
  }

  /**
   * Prepares an Observable that can get all available User Role Permissions (Admin ONLY).
   */
  getUserRolePermissions(): Observable<Array<UserPermission>> {
    return this.http.get<Array<UserPermission>>( this.permissionsAdminURL, this.options);
  }

  /**
   * This set of methods tests the current user's functional capabilities.
   */
  canAccess( subject: string): boolean {
    let perm: string = 'ip.'+subject;
    return this.isUserPermission( perm);
  }
  canCreate( subject: string): boolean {
    let perm: string = 'ip.'+subject+'.create';
    return this.isUserPermission( perm);
  }
  canUpdate( subject: string): boolean {
    let perm: string = 'ip.'+subject+'.update';
    return this.isUserPermission( perm);
  }
  canDelete( subject: string): boolean {
    let perm: string = 'ip.'+subject+'.delete';
    return this.isUserPermission( perm);
  }
  canProcess( subject: string): boolean {
    let perm: string = 'ip.'+subject+'.process';
    return this.isUserPermission( perm);
  }
  private isUserPermission( perm: string): boolean {
    return (this.currentUser != null && this.currentUser.userPermissions.find( permission => {return permission.name == perm}) != null);
  }

}
