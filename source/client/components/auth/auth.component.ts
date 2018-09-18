import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router'; 
import { Subscription } from 'rxjs/Subscription';
import { UserService } from '../../services/user.service';
import { UserInfo } from '../../interfaces/user-info';
import { AuthInfoImpl } from '../../classes/auth-info-impl';
import { UserInfoImpl } from '../../classes/user-info-impl';
declare var $: any;

const DIALOG_OPTS = {
  autoOpen: false, modal: true, resizable: true, show: true, hide: true, position: {my: "center top", at: "center top+10%", of: window}
};
const REMEMBERED_EMAIL: string = 'fujitsu.ip.rememberedEmail';
const KEYCODE_ENTER: number = 13;

@Component({
  selector: 'app-auth',
  templateUrl: './auth.component.html',
  styleUrls: ['./auth.component.css']
})
export class AuthComponent implements OnInit, OnDestroy {

  signinModel = {
    username: null,
    password: null
  };
  formControl = {
    authenticating: false,
    resetting: false,
    rememberMe: false,
    errorMessage: null
  };
  loading: boolean = false;

  currentUser: UserInfo = null;
  currentUserSubscription: Subscription;

  constructor( private userService: UserService, private router: Router) {}
  
  ngOnInit() {
    // Component life-cycle method - initialise the jQuery popup dialogs (that provide the Signin and Registration forms).
    // Note that we're using the bare minimum of jQuery here, and setting up click handlers using Angular bindings
    $('#signinDialog').dialog( DIALOG_OPTS);

    // Also subscribe to AuthInfo events (provided by the user service), so we'll get a new event each time there's a
    // change in the user's signed in / out status
    this.currentUserSubscription = this.userService.getAuthInfoSubject().subscribe(
      result => {
        this.loading = false;
        if ( result.status === AuthInfoImpl.STATUS_SIGNED_OUT && this.currentUser != null) {
          // We've just signed out, so route to the Splash page
          this.currentUser = null;
          this.router.navigate(['/splash']);
        }
        else if ( result.status === AuthInfoImpl.STATUS_SIGNED_IN && this.currentUser == null) {
          // User has just signed in, so save their details, conditionally save their email address, and close down
          // the sign-in dialog
          this.currentUser = result.userInfo;
          $('#signinDialog').dialog( "close");
        }
        else if ( result.status === AuthInfoImpl.STATUS_ERROR) {
          // User has finger trouble, so tell 'em
          this.formControl.errorMessage = "Sorry, we couldn't find that email address and/or password";
        }
        else {
          // Default path just saves user details
          this.currentUser = result.userInfo;
        }
      }
    );

    // Kicker to get User Info after an app. reload - helps maintain a user's signed-in context
    this.userService.getUserInfo();
  }

  ngOnDestroy() {
    // Component life-cycle method - unsubscribes from user info events (to release memory in the User Service)
    this.currentUserSubscription.unsubscribe();

    // Component life-cycle method - destroys our jQuery popup dialog widgets
    $('#signinDialog').dialog( "destroy");
  }

  onSignin() {
    this.initModel();
    $('#signinDialog').dialog( "open");
  }
  onSigninSubmit( formValid: boolean): void {
    if ( formValid) {
      this.formControl.authenticating = false;
      this.formControl.errorMessage = null;
      if ( this.formControl.rememberMe) {
        this.saveEmailAddress( this.signinModel.username);
      }
      this.loading = true;
      this.userService.signinUser( this.signinModel.username, this.signinModel.password);
    }
    else {
      this.formControl.authenticating = true;
    }
  }
  onSigninCancel() {
    this.initModel();
    $('#signinDialog').dialog( "close");
  }
  onSigninKeyUp( event, formValid: boolean): void {
    if ( event.keyCode === KEYCODE_ENTER) {
      this.onSigninSubmit( formValid);
    }
  }
  private saveEmailAddress( emailAddress: string): void {
    localStorage.setItem( REMEMBERED_EMAIL, emailAddress);
  }
  private retrieveEmailAddress(): string {
    return localStorage.getItem( REMEMBERED_EMAIL);
  }
  private removeEmailAddress(): void {
    localStorage.removeItem( REMEMBERED_EMAIL);
  }

  onSignout() {
    this.userService.signoutUser();
  }

  onStartUserReset( formValid: boolean) {
    if ( formValid) {
      this.formControl.resetting = false;
      this.formControl.errorMessage = null;
      let userInfo: UserInfoImpl = new UserInfoImpl();
      userInfo.setEmailAddress(  this.signinModel.username);
      this.loading = true;
      this.userService.startUserReset( userInfo).subscribe(
        registeredUserInfo => {
          this.loading = false;
          console.log( "onStartUserReset(); good result");
          $('#signinDialog').dialog( "close");
        },
        error => {
          this.loading = false;
          console.log( "onStartUserReset(); bad result = " + error.status);
          this.formControl.errorMessage = "Sorry, your request could not be completed";
        }
      );
    }
    else {
      this.formControl.resetting = true;
    }
  }

  initModel() {
    this.signinModel = {
      username: this.retrieveEmailAddress(),
      password: null
    };
    this.formControl = {
      authenticating: false,
      resetting: false,
      rememberMe: (this.retrieveEmailAddress() != null),
      errorMessage: null
    };
  }

}
