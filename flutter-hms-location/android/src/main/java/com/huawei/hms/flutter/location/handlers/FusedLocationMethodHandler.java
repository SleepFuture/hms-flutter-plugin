/*
    Copyright 2020-2022. Huawei Technologies Co., Ltd. All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.huawei.hms.flutter.location.handlers;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.huawei.hms.flutter.location.constants.Action;
import com.huawei.hms.flutter.location.constants.Error;
import com.huawei.hms.flutter.location.listeners.DefaultFailureListener;
import com.huawei.hms.flutter.location.listeners.DefaultSuccessListener;
import com.huawei.hms.flutter.location.listeners.LocationSettingsFailureListener;
import com.huawei.hms.flutter.location.listeners.RemoveUpdatesSuccessListener;
import com.huawei.hms.flutter.location.listeners.RequestUpdatesFailureListener;
import com.huawei.hms.flutter.location.listeners.RequestUpdatesSuccessListener;
import com.huawei.hms.flutter.location.logger.HMSLogger;
import com.huawei.hms.flutter.location.utils.LocationUtils;
import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationEnhanceService;
import com.huawei.hms.location.LocationRequest;
import com.huawei.hms.location.LocationServices;
import com.huawei.hms.location.LocationSettingsRequest;
import com.huawei.hms.location.LocationSettingsStates;
import com.huawei.hms.location.NavigationRequest;
import com.huawei.hms.location.SettingsClient;

import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

public class FusedLocationMethodHandler implements MethodChannel.MethodCallHandler, ActivityResultListener {
    private final Activity activity;
    private final MethodChannel channel;
    private final Map<Integer, LocationCallbackHandler> callbacks;
    private final Map<Integer, PendingIntent> requests;
    private final SettingsClient settingsClient;
    private final FusedLocationProviderClient service;
    private final LocationEnhanceService enhanceService;

    private int requestCode = 0;
    private MethodChannel.Result result;

    public FusedLocationMethodHandler(final Activity activity, final MethodChannel channel) {
        this.activity = activity;
        this.channel = channel;
        callbacks = new HashMap<>();
        requests = new HashMap<>();
        settingsClient = LocationServices.getSettingsClient(activity);
        service = LocationServices.getFusedLocationProviderClient(activity);
        enhanceService = LocationServices.getLocationEnhanceService(activity);
    }

    private void checkLocationSettings(final MethodCall call, final MethodChannel.Result result) {
        final LocationSettingsRequest request = LocationUtils.fromMapToLocationSettingsRequest(call.arguments());
        this.result = result;

        settingsClient.checkLocationSettings(request)
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new LocationSettingsFailureListener(result, activity));
    }

    private void getLastLocation(final MethodCall call, final MethodChannel.Result result) {
        service.getLastLocation()
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
    }

    private void getLastLocationWithAddress(final MethodCall call, final MethodChannel.Result result) {
        service.getLastLocationWithAddress(
            LocationUtils.fromMapToLocationRequest(call.<Map<String, Object>>arguments()))
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
    }

    private void getLocationAvailability(final MethodCall call, final MethodChannel.Result result) {
        service.getLocationAvailability()
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
    }

    private void setMockMode(final MethodCall call, final MethodChannel.Result result) {
        service.setMockMode(call.<Boolean>arguments())
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
    }

    private void setMockLocation(final MethodCall call, final MethodChannel.Result result) {
        service.setMockLocation(LocationUtils.fromMapToLocation(call.arguments()))
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
    }

    private void requestLocationUpdates(final MethodCall call, final MethodChannel.Result result) {
        final Pair<Integer, PendingIntent> intentData = buildLocationIntent();
        final LocationRequest request = LocationUtils.fromMapToLocationRequest(call.<Map<String, Object>>arguments());

        service.requestLocationUpdates(request, intentData.second)
            .addOnSuccessListener(new RequestUpdatesSuccessListener(call.method, activity, result, intentData.first))
            .addOnFailureListener(
                new RequestUpdatesFailureListener<>(call.method, activity, result, intentData.first, requests));
    }

    private void requestLocationUpdatesCb(final MethodCall call, final MethodChannel.Result result) {
        final LocationRequest request = LocationUtils.fromMapToLocationRequest(call.<Map<String, Object>>arguments());
        final Pair<Integer, LocationCallbackHandler> callbackData = buildCallback(call.method);

        service.requestLocationUpdates(request, callbackData.second, Looper.getMainLooper())
            .addOnSuccessListener(new RequestUpdatesSuccessListener(call.method, activity, result, callbackData.first))
            .addOnFailureListener(
                new RequestUpdatesFailureListener<>(call.method, activity, result, callbackData.first, callbacks));
    }

    private void requestLocationUpdatesExCb(final MethodCall call, final MethodChannel.Result result) {
        final LocationRequest request = LocationUtils.fromMapToLocationRequest(call.<Map<String, Object>>arguments());
        final Pair<Integer, LocationCallbackHandler> callbackData = buildCallback(call.method);

        service.requestLocationUpdatesEx(request, callbackData.second, Looper.getMainLooper())
            .addOnSuccessListener(new RequestUpdatesSuccessListener(call.method, activity, result, callbackData.first))
            .addOnFailureListener(
                new RequestUpdatesFailureListener<>(call.method, activity, result, callbackData.first, callbacks));
    }

    private void removeLocationUpdates(final MethodCall call, final MethodChannel.Result result) {
        final int incomingRequestCode = call.<Integer>arguments();

        if (!requests.containsKey(incomingRequestCode)) {
            result.error(Error.NON_EXISTING_REQUEST_ID.name(), Error.NON_EXISTING_REQUEST_ID.message(), null);
        } else {
            service.removeLocationUpdates(requests.get(incomingRequestCode))
                .addOnSuccessListener(
                    new RemoveUpdatesSuccessListener<>(call.method, activity, result, incomingRequestCode, requests))
                .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
        }
    }

    private void removeLocationUpdatesCb(final MethodCall call, final MethodChannel.Result result) {
        final int callbackId = call.<Integer>arguments();

        if (!callbacks.containsKey(callbackId)) {
            result.error(Error.NON_EXISTING_REQUEST_ID.name(), Error.NON_EXISTING_REQUEST_ID.message(), null);
        } else {
            service.removeLocationUpdates(callbacks.get(callbackId))
                .addOnSuccessListener(
                    new RemoveUpdatesSuccessListener<>(call.method, activity, result, callbackId, callbacks))
                .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
        }
    }

    private void getNavigationContextState(final MethodCall call, final MethodChannel.Result result) {
        final NavigationRequest request = LocationUtils.fromMapToNavigationRequest(call.arguments());

        enhanceService.getNavigationState(request)
            .addOnSuccessListener(new DefaultSuccessListener<>(call.method, activity, result))
            .addOnFailureListener(new DefaultFailureListener(call.method, activity, result));
    }

    private Pair<Integer, LocationCallbackHandler> buildCallback(final String methodName) {
        final LocationCallbackHandler callBack = new LocationCallbackHandler(activity.getApplicationContext(),
            methodName, ++requestCode, channel);
        callbacks.put(requestCode, callBack);
        return Pair.create(requestCode, callBack);
    }

    private Pair<Integer, PendingIntent> buildLocationIntent() {
        final Intent intent = new Intent();
        intent.setPackage(activity.getPackageName());
        intent.setAction(Action.PROCESS_LOCATION);

        final PendingIntent pendingIntent = PendingIntent.getBroadcast(activity.getApplicationContext(), ++requestCode,
            intent, PendingIntent.FLAG_UPDATE_CURRENT);
        requests.put(requestCode, pendingIntent);
        return Pair.create(requestCode, pendingIntent);
    }

    @Override
    public void onMethodCall(@NonNull final MethodCall call, @NonNull final MethodChannel.Result result) {
        HMSLogger.getInstance(activity.getApplicationContext()).startMethodExecutionTimer(call.method);

        switch (call.method) {
            case "checkLocationSettings":
                checkLocationSettings(call, result);
                break;
            case "getLastLocation":
                getLastLocation(call, result);
                break;
            case "getLastLocationWithAddress":
                getLastLocationWithAddress(call, result);
                break;
            case "getLocationAvailability":
                getLocationAvailability(call, result);
                break;
            case "setMockMode":
                setMockMode(call, result);
                break;
            case "setMockLocation":
                setMockLocation(call, result);
                break;
            case "requestLocationUpdates":
                requestLocationUpdates(call, result);
                break;
            case "requestLocationUpdatesCb":
                requestLocationUpdatesCb(call, result);
                break;
            case "requestLocationUpdatesExCb":
                requestLocationUpdatesExCb(call, result);
                break;
            case "removeLocationUpdates":
                removeLocationUpdates(call, result);
                break;
            case "removeLocationUpdatesCb":
                removeLocationUpdatesCb(call, result);
                break;
            case "getNavigationContextState":
                getNavigationContextState(call, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public boolean onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        final MethodChannel.Result incomingResult = result;
        result = null;

        if (incomingResult != null && requestCode == 0) {
            if (resultCode == Activity.RESULT_OK) {
                final LocationSettingsStates states = LocationSettingsStates.fromIntent(intent);
                HMSLogger.getInstance(activity.getApplicationContext())
                    .sendSingleEvent("checkLocationSettings.onActivityResult");
                incomingResult.success(LocationUtils.fromLocationSettingsStatesToMap(states));
            } else {
                HMSLogger.getInstance(activity.getApplicationContext())
                    .sendSingleEvent("checkLocationSettings" + ".onActivityResult", "-1");
                incomingResult.error(Error.LOCATION_SETTINGS_NOT_AVAILABLE.name(),
                    Error.LOCATION_SETTINGS_NOT_AVAILABLE.message(), null);
            }
        }

        return true;
    }
}
