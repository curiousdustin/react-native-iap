
package com.dooboolab.RNIap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import java.lang.NullPointerException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

public class RNIapCombinedModule extends ReactContextBaseJavaModule {
  final String TAG = "RNIapCombinedModule";

  private final String AMAZON_DEVICE_IDENTIFIER = "amazon.hardware.fire_tv";
  private Boolean isAmazonDevice;
  
  public RNIapCombinedModule (ReactApplicationContext reactContext) {
    super(reactContext);
    isAmazonDevice = getReactApplicationContext().getPackageManager().hasSystemFeature(AMAZON_DEVICE_IDENTIFIER);
  }

  @ReactMethod     
  public void isAmazonDevice(Promise promise) {         
    promise.resolve(isAmazonDevice);     
  }

  @Override
  public String getName() {
    return TAG;
  }
}