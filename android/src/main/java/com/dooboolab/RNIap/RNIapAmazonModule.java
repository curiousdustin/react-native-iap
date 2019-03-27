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
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.text.NumberFormat;
import java.text.ParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.PurchasingListener;

import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductType;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.RequestId;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;
import com.amazon.device.iap.model.FulfillmentResult;

public class RNIapAmazonModule extends ReactContextBaseJavaModule {
  final String TAG = "RNIapAmazonModule";

  // Constants for api operations
  private static final String GET_PRODUCT_DATA = "GET_PRODUCT_DATA";
  private static final String GET_PURCHASE_UPDATES = "GET_PURCHASE_UPDATES";
  private static final String GET_USER_DATA = "GET_USER_DATA";
  private static final String PURCHASE_ITEM = "PURCHASE_ITEM";

  // Promises: passed in from React layer. Resolved / rejected depending on response in listener
  private HashMap<String, ArrayList<Promise>> promises = new HashMap<>();

  
  @Override
  public String getName() {
    return TAG;
  }

  public RNIapAmazonModule (ReactApplicationContext reactContext) {
    super(reactContext);
    registerListener(reactContext);
  }

  private void registerListener(Context context) {
    PurchasingService.registerListener(context, purchasingListener);
  }

  // Primary methods for fetching data from Amazon
  // The set of skus must be <= 100
  @ReactMethod
  public RequestId getProductData(Set skus, Promise promise) {
    savePromise(GET_PRODUCT_DATA, promise);
    RequestId requestId = PurchasingService.getProductData(skus);
    return requestId;
  }

  @ReactMethod
  public RequestId getPurchaseUpdates(boolean reset, Promise promise) {
    savePromise(GET_PURCHASE_UPDATES, promise);
    RequestId requestId = PurchasingService.getPurchaseUpdates(reset);
    return requestId;
  }

  @ReactMethod 
  public RequestId getUserData(Promise promise) {
    Log.d(TAG, "Within getUserData java method");
    //System.out.println("Promise: " + promise);
    Log.d(TAG, "Promise: " + promise);
    savePromise(GET_USER_DATA, promise);
    RequestId requestId = PurchasingService.getUserData();
    return requestId;
  }

  @ReactMethod
  public void notifyFulfillment(String receiptId, FulfillmentResult result) {
    Log.d(TAG, "Notifying Amazon on fulfillment of " + receiptId + " with result " + result);
    PurchasingService.notifyFulfillment(receiptId, result);
  }

  @ReactMethod
  public RequestId purchase(String sku, Promise promise) {
    savePromise(PURCHASE_ITEM, promise);
    RequestId requestId = PurchasingService.purchase(sku);
    return requestId;
  }

  private PurchasingListener purchasingListener = new PurchasingListener() {
    public void onProductDataResponse(ProductDataResponse productDataResponse) {
      final String localTag = "onProductDataResponse";
      Log.d(TAG, " onProductDataResponse: " + productDataResponse.toString());
      final ProductDataResponse.RequestStatus status = productDataResponse.getRequestStatus();
      Log.d(TAG, "Status: " + status);

      switch (status) {
        case SUCCESSFUL: 
          final Map<String, Product> productData = productDataResponse.getProductData();
          final Set<String> unavailableSkus = productDataResponse.getUnavailableSkus();
          WritableArray maps = Arguments.createArray();
          try {
            for (Map.Entry<String, Product> skuDetails : productData.entrySet()) {
              Product product = skuDetails.getValue();
              ProductType productType = product.getProductType();
              NumberFormat format = NumberFormat.getCurrencyInstance();

              Number number;
              try {
                number = format.parse(product.getPrice());
              } catch (ParseException e) {
                rejectPromises(GET_PRODUCT_DATA, "Pricing Parsing error in: " + localTag, e.getMessage(), e);
                return;
              }
              WritableMap map = Arguments.createMap();

              //JSONObject item = new JSONObject();
              map.putString("productId", product.getSku());
              map.putString("price", number.toString());
              map.putNull("currency");
              
              switch (productType) {
                case ENTITLED:
                case CONSUMABLE:
                  map.putString("type", "inapp");
                  break;
                case SUBSCRIPTION:
                  map.putString("type", "subs");
                  break;
              }
              map.putString("localizedPrice", product.getPrice());
              map.putString("title", product.getTitle());
              map.putString("description", product.getDescription());
              map.putNull("introductoryPrice");
              map.putNull("subscriptionPeriodAndroid");
              map.putNull("freeTrialPeriodAndroid");
              map.putNull("introductoryPriceCyclesAndroid");
              map.putNull("introductoryPricePeriodAndroid");
              // Log.d(TAG, "Adding item to items list: " + map.toString());
              maps.pushMap(map);
            }
            resolvePromises(GET_PRODUCT_DATA, maps);
          } catch (Exception e) { 
            rejectPromises(GET_PRODUCT_DATA, "PARSE_ERROR IN " + localTag, e.getMessage(), e);
          }
          break;
        case FAILED: 
          rejectPromises(GET_PRODUCT_DATA, "RESPONSE FAILURE IN " + localTag, null, null);
          break;
        case NOT_SUPPORTED: 
        rejectPromises(GET_PRODUCT_DATA, "OPERATION NOT SUPPORTED IN " + localTag, null, null);
          break;
      }
    }

    public void onPurchaseResponse(PurchaseResponse purchaseResponse) {
      final String localTag = "onPurchaseResponse";
      final PurchaseResponse.RequestStatus status = purchaseResponse.getRequestStatus();
      Log.d(TAG, "Response status: " + status);
      switch (status) {
        case SUCCESSFUL: 
          Receipt receipt = purchaseResponse.getReceipt();
          // NOTE: In many cases, you would want to notifyFullfilment here. I've left this out in case of 
          // any need to handle things in the UI / React layer prior to notifying fullfilment. The function remains
          // Available as a React Function and can be called at any time 
          Date date = receipt.getPurchaseDate();
          Long transactionDate=date.getTime();
          try {
            WritableMap map = getPurchaseData(receipt.getSku(),
                  receipt.getReceiptId(),
                  transactionDate.doubleValue());
            resolvePromises(PURCHASE_ITEM, map);
          } catch (Exception e) {
            rejectPromises(PURCHASE_ITEM, "JSON_PARSE_ERROR_ON_BILLING_RESPONSE", e.getMessage(), e);
          }
          break;
        case FAILED: 
          rejectPromises(PURCHASE_ITEM, "PURCHASE ITEM FAILURE", null, null);
          break;
      }
    }

    public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse purchaseUpdatesResponse) {
      final String localTag = "onPurchaseUpdatesResponse";
      final PurchaseUpdatesResponse.RequestStatus status = purchaseUpdatesResponse.getRequestStatus();

      switch (status) {
        case SUCCESSFUL:
          WritableArray maps = Arguments.createArray();
          try {
            List<Receipt> receipts = purchaseUpdatesResponse.getReceipts();
            for(Receipt receipt : receipts) {
              Date date = receipt.getPurchaseDate();
              Long transactionDate = date.getTime();
              WritableMap map = getPurchaseData(receipt.getSku(),
                      receipt.getReceiptId(),
                      transactionDate.doubleValue());

              //Log.d(TAG, "Adding item: " + map.toString());
              maps.pushMap(map);
            }
            resolvePromises(GET_PURCHASE_UPDATES, maps);
          } catch (Exception e) {
            rejectPromises(GET_PURCHASE_UPDATES, "BILLING_RESPONSE_JSON_PARSE_ERROR", e.getMessage(), e);
          }
          break;
        case FAILED:
          rejectPromises(GET_PURCHASE_UPDATES, "FAILED IN: " + localTag, null, null);
          break;
        case NOT_SUPPORTED:
          Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request");
          rejectPromises(GET_PURCHASE_UPDATES, localTag + " NOT_SUPPORTED", "Should retry request", null);
          break;
      }
    } 

    public void onUserDataResponse(UserDataResponse userDataResponse) {
      final String localTag = "onUserDataResponse";
      final UserDataResponse.RequestStatus status = userDataResponse.getRequestStatus();
      switch (status) {
        case SUCCESSFUL:
          try {
            UserData userData = userDataResponse.getUserData();

            WritableMap map = Arguments.createMap();
            map.putString("userId", userData.getUserId());
            map.putString("marketplace", userData.getMarketplace());

            resolvePromises(GET_USER_DATA, map);
          } catch (Exception e) {
            // TODO: If above works w/o error may not need the below catch block
            rejectPromises(GET_USER_DATA, "USER_DATA_RESPONSE_JSON_PARSE_ERROR", e.getMessage(), e);
          }
          break;
        case FAILED:
          Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request");
          rejectPromises(GET_USER_DATA, "FAILED IN: " + localTag, null, null);
          break;
        case NOT_SUPPORTED:
          rejectPromises(GET_PURCHASE_UPDATES, localTag + " NOT_SUPPORTED", "Should retry request", null);
          break;
      }
    }
  };

  private WritableMap getPurchaseData(String productId, String receiptId,
                             Double transactionDate) {
    WritableMap map = Arguments.createMap();
    map.putString("productId", productId);
    map.putString("receiptId", receiptId);
    map.putString("transactionDate", Double.toString(transactionDate));
    map.putNull("dataAndroid");
    map.putNull("signatureAndroid");
    map.putNull("purchaseToken");
    return map;
  }

  private void savePromise(String key, Promise promise) {
    Log.d(TAG, "saving promise w/ key: " + key);
    ArrayList<Promise> list;
    if (promises.containsKey(key)) {
      list = promises.get(key);
    }
    else {
      list = new ArrayList<Promise>();
      promises.put(key, list);
    }

    list.add(promise);
  }

  private void resolvePromises(String key, Object value) {
    Log.d(TAG, "resolving promises: " + key + " " + value);
    if (promises.containsKey(key)) {
      ArrayList<Promise> list = promises.get(key);
      for (Promise promise : list) {
        promise.resolve(value);
      }
      promises.remove(key);
    }
  }

  private void rejectPromises(String key, String code, String message, Exception err) {
    if (promises.containsKey(key)) {
      ArrayList<Promise> list = promises.get(key);
      for (Promise promise : list) {
        promise.reject(code, message, err);
      }
      promises.remove(key);
    }
  }
}