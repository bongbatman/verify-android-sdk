/*
 * Copyright (c) 2015 Nexmo Inc
 * All rights reserved.
 *
 * Licensed only under the Nexmo Verify SDK License Agreement located at
 *
 * https://www.nexmo.com/terms-use/verify-sdk/ (the “License”)
 *
 * You may not use, exercise any rights with respect to or exploit this SDK,
 * or any modifications or derivative works thereof, except in accordance
 * with the License.
 */

package com.nexmo.sdk.verify.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.BuildConfig;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.nexmo.sdk.NexmoClient;
import com.nexmo.sdk.core.client.ResultCodes;
import com.nexmo.sdk.core.config.Defaults;
import com.nexmo.sdk.core.gcm.VerifyGcmListenerService;
import com.nexmo.sdk.util.DeviceUtil;
import com.nexmo.sdk.verify.core.event.BaseClientListener;
import com.nexmo.sdk.verify.core.event.CheckServiceListener;
import com.nexmo.sdk.verify.core.event.CommandServiceListener;
import com.nexmo.sdk.verify.core.event.SearchServiceListener;
import com.nexmo.sdk.verify.core.event.VerifyServiceListener;
import com.nexmo.sdk.verify.core.request.CommandRequest;
import com.nexmo.sdk.verify.core.request.SearchRequest;
import com.nexmo.sdk.verify.core.request.VerifyRequest;
import com.nexmo.sdk.verify.core.service.CheckService;
import com.nexmo.sdk.verify.core.service.CommandService;
import com.nexmo.sdk.verify.core.service.SearchService;
import com.nexmo.sdk.verify.core.service.VerifyService;
import com.nexmo.sdk.verify.event.Command;
import com.nexmo.sdk.verify.event.CommandListener;
import com.nexmo.sdk.verify.event.SearchListener;
import com.nexmo.sdk.verify.event.UserObject;
import com.nexmo.sdk.verify.event.UserStatus;
import com.nexmo.sdk.verify.event.VerifyClientListener;
import com.nexmo.sdk.verify.event.VerifyError;
import com.nexmo.sdk.verify.ui.VerifyPhoneNumberActivity;
import com.nexmo.sdk.verify.ui.response.ManagedVerifyResponse;

import java.io.IOException;
import java.util.HashSet;

/**
 * The {@link com.nexmo.sdk.verify.client.VerifyClient} provides the entry point to verification flow provided by the Nexmo SDK.
 * <p> First step is to acquire a {@link VerifyClient} instance based on a built {@link NexmoClient} object.
 *     Remember to set a {@link VerifyClientListener} to receive status change notifications.
 * <p> After a new {@link VerifyClient} is created the verification can be initiated by calling {@link VerifyClient#getVerifiedUser(String, String)}.
 * <p> Example usage:
 * <pre>
 *     VerifyClient myVerifyClient = new VerifyClient(myNexmoClient);
 *     myVerifyClient.addVerifyListener(new VerifyClientListener() {
 *         &#64;Override
 *         public void onVerifyInProgress(final VerifyClient verifyClient, final UserObject user) {
 *              // Update the application UI here if needed. Usually provide an input field in the UI that allows PIN code input.
 *         }
 *
 *         &#64;Override
 *         public void onUserVerified(final VerifyClient verifyClient, final UserObject user) {
 *              // Update the application UI here if needed.
 *         }
 *
 *         &#64;Override
 *         public void onError(final VerifyClient verifyClient, final com.nexmo.sdk.verify.event.VerifyError errorCode, final UserObject user) {
 *              // Update the application UI here if needed.
 *         }
 *
 *         &#64;Override
 *         public void onException(final IOException exception) {
 *              // Update the application UI here if needed. Most probably there is a network connectivity exception.
 *         }
 *     }
 *     myVerifyClient.getVerifiedUser(myCountryCode, myPhoneNo);
 * </pre>
 * <p> Anytime the PIN code has been received by the end user, it should be supplied to the verify client:
 * <pre>
 *     myVerifyClient.checkPinCode("pinCode");
 * </pre>
 *  A successful verification will be completed once the {@link VerifyClientListener#onUserVerified(VerifyClient, com.nexmo.sdk.verify.event.UserObject)} event is invoked.
 *
 * <p> Checking if the current user is verified already or not is possible via {@link VerifyClient#getUserStatus(String, String, SearchListener)} .
 *     Remember to set a {@link com.nexmo.sdk.verify.event.SearchListener} to receive the user status.
 * <p> Example usage:
 * <pre>
 *     VerifyClient myVerifyClient = new VerifyClient(myNexmoClient);
 *     myVerifyClient.getUserStatus(myCountryCode, myPhoneNo, new SearchListener() {
 *         &#64;Override
 *         public void onUserStatus(final UserStatus userStatus) {
 *              // Update the application UI here if needed.
 *         }
 *
 *         &#64;Override
 *         public void onError(final com.nexmo.sdk.verify.event.VerifyError errorCode, final String errorMessage) {
 *              // Update the application UI here if needed.
 *         }
 *
 *         &#64;Override
 *         public void onException(IOException exception) {
 *              // Update the application UI here if needed. Most probably there is a network connectivity exception.
 *         }
 *     }
 * </pre>
 *
 * <p> Actions can be performed for the current user for controlling the verification flow.
 * The available commands are the {@link com.nexmo.sdk.verify.event.Command} actions.</p>
 * <p>Perform an action by calling {@link VerifyClient#command(String, String, com.nexmo.sdk.verify.event.Command, com.nexmo.sdk.verify.event.CommandListener)}
 * Remember to set {@link com.nexmo.sdk.verify.event.CommandListener} in order to receive command updates.</p>
 * <p> Example usage, performing a {@link com.nexmo.sdk.verify.event.Command#LOGOUT} for an already verified user:
 * <pre>
 *     VerifyClient myVerifyClient = new VerifyClient(myNexmoClient);
 *     myVerifyClient.command(myCountryCode, myPhoneNo, Command.LOGOUT, new CommandListener() {
 *         &#64;Override
 *         public void onSuccess(Command command) {
 *              // Update the application UI here if needed.
 *         }
 *
 *         &#64;Override
 *         public void onError(Command command, final com.nexmo.sdk.verify.event.VerifyError errorCode, String errorMessage) {
 *              // Update the application UI here if needed.
 *         }
 *
 *         &#64;Override
 *         public void onException(IOException exception) {
 *              // Update the application UI here if needed. Most probably there is a network connectivity exception.
 *         }
 *     }
 * </pre>
 */
public class VerifyClient implements BaseClientListener {

    public static final String TAG = VerifyClient.class.getSimpleName();
    public static final String ACTION_BROADCAST_COMMAND_TIMER = "com.nexmo.sdk.verify.client.BROADCAST_MESSAGE";
    public static final String MESSAGE_KEY_TIMER_STATE_DONE = "timer_state_done";
    private NexmoClient nexmoClient;
    private VerifyRequest verifyRequest = new VerifyRequest();
    private HashSet<VerifyClientListener> verifyClientListeners;
    // when userStatus becomes PENDING wait for 30s before enabling all the commands.
    private Handler commandsHandler = new Handler();
    private final Runnable commandsRunnable = new Runnable() {
        @Override
        public void run() {
            broadcastCommandTimerDone();
        }
    };
    // Internal listeners.
    private VerifyServiceListener verifyServiceListener;
    private CheckServiceListener checkServiceListener;
    private SearchServiceListener searchServiceListener;
    private BroadcastReceiver gcmPayloadBroadcastReceiver;
    private BroadcastReceiver managedVerifyUIReceiver;

    /**
     * Acquire a new {@link VerifyClient} instance.
     * The {@link VerifyClient} object provides the entry point to verification flow provided by the Nexmo SDK.
     * @param nexmoClient The {@link NexmoClient NexmoClient} is the Nexmo SDK entry point.
     */
    public VerifyClient(final NexmoClient nexmoClient) {
        this.verifyClientListeners = new HashSet<>();
        this.nexmoClient = nexmoClient;
        setGcmBroadcastReceiver();
    }

    /**
     * Adds a {@link VerifyClientListener} listener that handles events from the VerifyClient.
     *
     * @param verifyClientListener A verify client listener.
     */
    public void addVerifyListener(final VerifyClientListener verifyClientListener) {
        if(!this.verifyClientListeners.contains(verifyClientListener))
            synchronized(this) {
                this.verifyClientListeners.add(verifyClientListener);
            }
    }

    /**
     * Remove a {@link VerifyClientListener} listener from receiving verify events.
     *
     * @param verifyClientListener A verify client listener.
     * @return {@code true} if the object was removed, {@code false} otherwise.
     */
    public boolean removeVerifyListener(final VerifyClientListener verifyClientListener) {
        synchronized(this) {
            return this.verifyClientListeners.remove(verifyClientListener);
        }
    }

    /**
     * Remove all verify client listeners associated to this {@link VerifyClient} instance.
     */
    public void removeVerifyListeners() {
        synchronized(this) {
            this.verifyClientListeners.clear();
        }
    }

    /**
     * @deprecated Use {@link VerifyClient#getVerifiedUser(String, String)} method instead and supply the user's phone number and country code.
     *
     * Verify the user of the current handset, with the handset SIM details.
     * <p> Verification with no supplied phone number along with country code is not permitted for SIM less handsets.
     * <p>  In this case, the end user does not need to enter their own phone number, as they will be automatically read from the SIM card.
     *
     * <p> Note: This method will only succeed if the current handset does contain a SIM card.
     * Otherwise, please call {@link VerifyClient#getVerifiedUser(String, String)} with values provided by the user.
     */
    public void getVerifiedUser() {
        Context appContext = this.nexmoClient.getContext();

        if (DeviceUtil.isSIMAvailable(appContext))
            getVerifiedUser(DeviceUtil.getCountryCode(appContext), DeviceUtil.getPhoneNumber(appContext));
        else {
            warnIfMissingListener();
            if (BuildConfig.DEBUG)
                Log.d(TAG, "SIM card cannot be read. Please use the VerifyClient.getVerifiedUser method and supply params " +
                        "for phone number and country code for the verification to be initiated.");
            notifyErrorListeners(VerifyError.NUMBER_REQUIRED);
        }
    }

    /**
     * Verify the user of the current handset, with the provided country code and phone number.
     * Retrieve a verified user object for the current handset.
     * Use this method instead of {@link VerifyClient#getVerifiedUserFromDefaultManagedUI()} when you want to handle
     * all the UI-related components.
     * <p> In the case of a transient (typically network related) error an InternalNetworkException will be thrown.
     * <p> Add a {@link VerifyClientListener} to this {@link VerifyClient} before by calling {@link VerifyClient#addVerifyListener(VerifyClientListener)}
     * to be notified of user verify state, as well as error events.
     *
     * @param countryCode The country code of the current SIM card.
     * @param phoneNumber The phone number of the current handset. Only mobile numbers are accepted.
     */
    public void getVerifiedUser(final String countryCode,
                                final String phoneNumber) {
        getVerifiedUser(countryCode, phoneNumber, false);
    }

    /**
     * Stateless verification of an already verified user of the current handset, with the provided country code and phone number.
     * Retrieve a verified user object for the current handset.
     * <p> In the case of a transient (typically network related) error an InternalNetworkException will be thrown.
     * <p> Add a {@link VerifyClientListener} to this {@link VerifyClient} before by calling {@link VerifyClient#addVerifyListener(VerifyClientListener)}
     * to be notified of user verify state, as well as error events.
     *
     * @param countryCode The country code of the current SIM card.
     * @param phoneNumber The phone number of the current handset. Only mobile numbers are accepted.
     */
    public void verifyStandalone(final String countryCode,
                                 final String phoneNumber) {
        getVerifiedUser(countryCode, phoneNumber, true);
    }

    /**
     * Check verification enables you to check whether the PIN code the ned user has provided matches
     * the one Nexmo has sent.
     * <p> Validation on code length is made prior to checking the code against the service generated one.
     *
     * <p> Please note that any verification pin code is valid for 15 minutes. If the user is unable to
     * provide the pin in due time, verification will fail. In this case, the verification needs to
     * be re-started.
     * @param pinCode PIN code your end user has provided into your application (min 4 digits).
     */
    public void checkPinCode(final String pinCode) {
        warnIfMissingListener();

        manageCheckPin(pinCode);
    }

    /**
     * Get/Search the current state of the user, with the provided country code and phone number.
     * There is no point in making concurrent isUserVerified requests for the same user, therefore only one
     * request at a time will be accepted.
     *
     * <p> In the case of a transient (typically network related) error an InternalNetworkException will be thrown.
     * <p> Listen to {@link VerifyClientListener} events to be notified of user state, as well as error events.
     *
     * @param countryCode The country code of the current SIM card.
     * @param phoneNumber The phone number of the current handset. Only mobile numbers are accepted.
     * @param searchListener The search listener. This listener is mandatory, if it's set to null the method would fail.
     */
    public void getUserStatus(final String countryCode,
                              final String phoneNumber,
                              final SearchListener searchListener) {
        if (searchListener == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Warning: There is no SearchListener in place. " +
                        "Please set it to the getVerifiedUser to be able to receive search events.");
        }
        else {
            setupSearchListener(searchListener);
            SearchService service = SearchService.getInstance();
            service.init(new SearchRequest(countryCode, phoneNumber));
            service.start(this.nexmoClient,
                    this.searchServiceListener);
        }
    }

    /**
     * Initiate a command for the current user to control the verification workflow, in order to further tailor your
     * end-user experience.
     * <p>You should only consider implementing this API if there is a strong need to change the verification process
     * while it is underway, due to user-detected issues. In some cases, end-users may wish to manually invoke a retry
     * - something to the effect of "Send me my code again". Normally Verify manages retry timing by automatically
     * determining if it needs to attempt delivering the code once again. This retry is invoked if a check is not
     * received within the average expected duration for completing a verification successfully.</p>
     * <p>The available commands are:
     * <ul>
     *     <li>{@link com.nexmo.sdk.verify.event.Command#LOGOUT} Once a user is in {@link UserStatus#USER_VERIFIED}
     *     state a Logout command can be performed. </li>
     *     <li>{@link com.nexmo.sdk.verify.event.Command#CANCEL} Using command 'Cancel', an outstanding request may be
     *     cancelled and a new one issued. This approach, helps you to trigger the same channel as the first attempt,
     *     since it initiates the Verification flow all over again. This mimics the traditional "Retry" or "Correct my number"
     *     nexmo_verify_button, where a user initiates Verify for the same or a new number because something went wrong.</li>
     *     <li>{@link com.nexmo.sdk.verify.event.Command#TRIGGER_NEXT_EVENT} Using the command 'trigger_next_event',
     *     Verify can be instructed to failover immediately, instead of waiting for the default duration. This will trigger
     *     the next attempt to deliver the verification code, typically over Text to Speech. A relatable way to think of
     *     this is a "Did not receive your SMS?" or "Call me instead" nexmo_verify_button; which may be made actionable if you determine
     *     that the phone number should have good cellular connectivity.</li>
     * </ul>
     * </p>
     * <p>Note that trying to deliver the PIN code again in a shorter duration does not necessarily translate into
     * better conversion, it also doesn't result in a good user experience owing to the fact that users take time
     * to complete the verification process.</p>
     * @param countryCode       The country code of the current SIM card.
     * @param phoneNumber       The phone number of the current handset. Only mobile numbers are accepted.
     * @param command           The {@link com.nexmo.sdk.verify.event.Command} action to perform. The command is mandatory.
     * @param commandListener   The command action listener. This listener is mandatory, if it's set to null the method would fail.
     */
    public void command(final String countryCode,
                        final String phoneNumber,
                        final Command command,
                        final CommandListener commandListener) {
        if (commandListener == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Warning: There is no CommandListener in place. " +
                        "Please set it to the command method to be able to receive events.");
        } else if (command == null) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Warning: There is no command action in place. " +
                        "Please set it to the command method.");
        }
        else {
            CommandServiceListener commandServiceListener = new CommandServiceListener(command,
                    commandListener,
                    this);
            CommandService service = CommandService.getInstance();
            service.init(new CommandRequest(countryCode, phoneNumber, command));
            service.start(this.nexmoClient,
                    commandServiceListener);
        }
    }

    /**
     * Start the verify flow with the pre-defined UI inflated.
     * Use this method instead of {@link VerifyClient#getVerifiedUser(String, String)} when you don't want to setup any UI in place.
     * <p> The end user will be be responsible for providing the country and phone number of the device he is trying to verify.
     * <p> Listen to {@link VerifyClientListener} events to be notified of user verify state, as well as error events during the automated flow.
     * There is no action required after this step, just hang on and wait for verify completion event {@link VerifyClientListener#onUserVerified(VerifyClient, com.nexmo.sdk.verify.event.UserObject)} (String)}
     * Upon completion you will only get the phoneNumber for which the user tried to verify, regardless of the outcome.
     *
     * <p> In the case of a transient (typically network related) error an InternalNetworkException will be thrown.
     * <p> Add a {@link VerifyClientListener} to this {@link VerifyClient} before by calling {@link VerifyClient#addVerifyListener(VerifyClientListener)}
     * to be notified of user verify state, as well as error events.
     */
    public void getVerifiedUserFromDefaultManagedUI() {
        warnIfMissingListener();

        setManagedUIBroadcastReceiver();
        launchView(VerifyPhoneNumberActivity.class);
    }

    /**
     * Handle the error result code.
     * Notify all listeners on response codes.
     * @param resultCode The response code.
     */
    @Override
    public void handleErrorResult(final int resultCode) {
        switch(resultCode) {
            case ResultCodes.INVALID_NUMBER: {
                notifyErrorListeners(VerifyError.INVALID_NUMBER);
                break;
            }
            case ResultCodes.INVALID_CREDENTIALS:
            case ResultCodes.BAD_APP_ID:{
                notifyErrorListeners(VerifyError.INVALID_CREDENTIALS);
                break;
            }
            case ResultCodes.INVALID_CODE_TOO_MANY_TIMES: {
                notifyErrorListeners(VerifyError.INVALID_CODE_TOO_MANY_TIMES);
                break;
            }
            case ResultCodes.INVALID_PIN_CODE:
            case ResultCodes.INVALID_CODE: {
                notifyErrorListeners(VerifyError.INVALID_PIN_CODE);
                break;
            }
            case ResultCodes.REQUEST_REJECTED: {
                notifyErrorListeners(VerifyError.THROTTLED);
                break;
            }
            case ResultCodes.QUOTA_EXCEEDED: {
                notifyErrorListeners(VerifyError.QUOTA_EXCEEDED);
                break;
            }
            case ResultCodes.CANNOT_PERFORM_CHECK: {
                notifyErrorListeners(VerifyError.CANNOT_PERFORM_CHECK);
                break;
            }
            case ResultCodes.SDK_NOT_SUPPORTED: {
                notifyErrorListeners(VerifyError.SDK_REVISION_NOT_SUPPORTED);
                break;
            }
            case ResultCodes.OS_NOT_SUPPORTED: {
                notifyErrorListeners(VerifyError.OS_NOT_SUPPORTED);
                break;
            }
            default: {
                notifyErrorListeners(VerifyError.INTERNAL_ERR);
                break;
            }
        }
    }

    /**
     * Notify all VerifyClientListeners on error events.
     *
     * @param verifyError The verify error code.
     */
    @Override
    public void notifyErrorListeners(final VerifyError verifyError) {
        if (!this.verifyClientListeners.isEmpty()) {
            UserObject userObject = new UserObject(this.verifyRequest.getPhoneNumber());
            for (VerifyClientListener listener : this.verifyClientListeners)
                listener.onError(this, verifyError, userObject);
        }
    }

    /**
     * Notify all listeners of a user status change.
     *
     * @param userStatus The new user status.
     */
    @Override
    public void handleUserStateChanged(final UserStatus userStatus) {
        UserObject userObject = new UserObject(this.verifyRequest.getPhoneNumber());
        if (!this.verifyClientListeners.isEmpty()) {
            if (userStatus == UserStatus.USER_VERIFIED)
                for (VerifyClientListener listener : this.verifyClientListeners)
                    listener.onUserVerified(this, userObject);
            else if (userStatus == UserStatus.USER_PENDING)
                for (VerifyClientListener listener : this.verifyClientListeners)
                    listener.onVerifyInProgress(this, userObject);
            else if (userStatus == UserStatus.USER_BLACKLISTED)
                for (VerifyClientListener listener : this.verifyClientListeners)
                    listener.onError(this, VerifyError.USER_BLACKLISTED, userObject);
            else if(userStatus == UserStatus.USER_UNKNOWN)
                for (VerifyClientListener listener : this.verifyClientListeners)
                    listener.onError(this, VerifyError.USER_UNKNOWN, userObject);
            else if (userStatus == UserStatus.USER_EXPIRED)
                for (VerifyClientListener listener : this.verifyClientListeners)
                    listener.onError(this, VerifyError.USER_EXPIRED, userObject);
            else if (userStatus == UserStatus.USER_FAILED)
                for (VerifyClientListener listener : this.verifyClientListeners)
                    listener.onError(this, VerifyError.USER_FAILED, userObject);
        }
    }

    @Override
    public void handleNetworkException(final IOException exception) {
        if (!this.verifyClientListeners.isEmpty())
            for (VerifyClientListener listener : this.verifyClientListeners)
                listener.onException(exception);
    }

    private void getVerifiedUser(final String countryCode,
                                 final String phoneNumber,
                                 final boolean isStandalone) {
        warnIfMissingListener();

        if (isVerifyMissingInput(countryCode, phoneNumber))
            notifyErrorListeners(VerifyError.NUMBER_REQUIRED);
        else {
            updateVerifyRequest(countryCode, phoneNumber, isStandalone);
            setupVerifyClientListeners();
            VerifyService service = VerifyService.getInstance();
            service.init(this.verifyRequest);
            service.start(this.nexmoClient, this.verifyServiceListener);
        }
    }

    /**
     * Handle the GCM notifications broadcast receiver, enable it to automatically trigger the check request
     * for the ongoing verify.
     */
    private void setGcmBroadcastReceiver() {
        this.gcmPayloadBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(VerifyGcmListenerService.MESSAGE_KEY_PIN)) {
                    String pinCode = intent.getExtras().getString(VerifyGcmListenerService.MESSAGE_KEY_PIN);
                    Log.d(TAG, "gcmPayloadBroadcastReceiver Pin: " + pinCode);
                    manageCheckPin(pinCode);
                }
            }
        };

        LocalBroadcastManager.getInstance(this.nexmoClient.getContext()).
                registerReceiver(this.gcmPayloadBroadcastReceiver, new IntentFilter(VerifyGcmListenerService.ACTION_BROADCAST_PIN));
    }

    /**
     * Handle the managedUI notifications broadcast receiver, update {@link VerifyClient} on progress.
     */
    private void setManagedUIBroadcastReceiver() {
        this.managedVerifyUIReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    ManagedVerifyResponse response = extras.getParcelable(ManagedVerifyResponse.class.getSimpleName());
                    if (response != null && !response.isIoExceptionOccured()) {
                        updateVerifyRequest(response.getPhone());
                        if (response.getUserStatus() == UserStatus.USER_PENDING) {
                            handleUserStateChanged(UserStatus.USER_PENDING);
                            commandsHandler.postDelayed(commandsRunnable, 30000);
                        }
                         else if (response.getUserStatus() == UserStatus.USER_VERIFIED)
                            handleUserStateChanged(UserStatus.USER_VERIFIED);
                        else if (response.getUserStatus() == UserStatus.USER_FAILED)
                            handleUserStateChanged(UserStatus.USER_FAILED);
                        else
                            notifyErrorListeners(response.getVerifyError());
                    } else
                        handleNetworkException(new IOException("Network exception"));
                }
            }
        };

        LocalBroadcastManager.getInstance(this.nexmoClient.getContext()).
                registerReceiver(this.managedVerifyUIReceiver, new IntentFilter(VerifyPhoneNumberActivity.ACTION_BROADCAST_MANAGED_EVENT));
    }

    private void broadcastCommandTimerDone() {
        Intent intent = new Intent(ACTION_BROADCAST_COMMAND_TIMER);
        intent.putExtra(MESSAGE_KEY_TIMER_STATE_DONE, true);
        LocalBroadcastManager.getInstance(this.nexmoClient.getContext()).sendBroadcast(intent);
    }

    private void launchView(Class<VerifyPhoneNumberActivity> activityClass) {
        Context appContext = this.nexmoClient.getContext();
        Intent signInIntent = new Intent(appContext, activityClass);
        Bundle nexmoClientBundle = new Bundle();
        nexmoClientBundle.putParcelable(NexmoClient.class.getSimpleName(), this.nexmoClient);
        signInIntent.putExtras(nexmoClientBundle);
        signInIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(signInIntent);
    }

    private void manageCheckPin(final String pinCode) {
        if (TextUtils.isEmpty(pinCode) || pinCode.length() < Defaults.MIN_CODE_LENGTH) {
            // Any empty pin will not be stored, neither sent to the service.
            if(BuildConfig.DEBUG)
                Log.d(TAG, "Supplied phone number has an invalid length. Verify cannot be initiated.");
            notifyErrorListeners(VerifyError.INVALID_PIN_CODE);
        }
        else if (!this.verifyRequest.isPinCheckAvailable()) {
            // Any empty pin will not be stored, neither sent to the service.
            if(BuildConfig.DEBUG)
                Log.d(TAG, "PIN code cannot be checked because there is no verification in progress.Check cannot be initiated.");
            notifyErrorListeners(VerifyError.VERIFICATION_NOT_STARTED);
        }
        else {
            updateVerifyRequestPin(pinCode);
            CheckService service = CheckService.getInstance();
            service.init(this.verifyRequest);
            service.start(this.nexmoClient,
                    this.checkServiceListener);
        }
    }

    /**
     * Setup the search related listeners. Used only when the search functionality is requested.
     * @param searchListener The provided search listener.
     */
    private void setupSearchListener(final SearchListener searchListener) {
        this.searchServiceListener = new SearchServiceListener(searchListener, this);
    }

    private void setupVerifyClientListeners(){
        this.verifyServiceListener = new VerifyServiceListener(this);
        this.checkServiceListener = new CheckServiceListener(this);
    }

    private void updateVerifyRequest(final String countryCode,
                                     final String phoneNo,
                                     final boolean isStandalone) {
        synchronized(this){
            this.verifyRequest = new VerifyRequest(countryCode, phoneNo, isStandalone);
        }
    }

    private void updateVerifyRequest(final String phoneNo) {
        synchronized(this){
            this.verifyRequest.setPhoneNumber(phoneNo);
        }
    }

    private void updateVerifyRequestPin(final String pinCode) {
        synchronized(this) {
            this.verifyRequest.setPinCode(pinCode);
        }
    }

    private void warnIfMissingListener() {
        if (this.verifyClientListeners.isEmpty() && BuildConfig.DEBUG)
            Log.d(TAG, "Warning: There is no VerifyClientListener in place. " +
                    "Please set it on this VerifyClient instance to be able to receive verify events.");
    }

    private boolean isVerifyMissingInput(final String countryCode,
                                         final String phoneNumber) {
        if (TextUtils.isEmpty(countryCode) && BuildConfig.DEBUG) {
            Log.d(TAG, "Warning: The 'countryCode' parameter is missing. " +
                    "Please set it to the getVerifiedUser method.");
            return true;
        }
        else if (TextUtils.isEmpty(phoneNumber) && BuildConfig.DEBUG) {
            Log.d(TAG, "Warning: The 'phoneNumber' parameter is missing. " +
                    "Please set it to the getVerifiedUser method.");
            return true;
        }
        return false;
    }

}
