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
import com.facebook.react.bridge.ReadableArray;

import java.util.HashMap;
import java.util.HashSet;
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

  // Error Code Constants
  private static final String E_UNKNOWN = "E_UNKNOWN";
  private static final String E_NOT_PREPARED = "E_NOT_PREPARED";
  private static final String E_NOT_ENDED = "E_NOT_ENDED";
  private static final String E_USER_CANCELLED = "E_USER_CANCELLED";
  private static final String E_ITEM_UNAVAILABLE = "E_ITEM_UNAVAILABLE";
  private static final String E_NETWORK_ERROR = "E_NETWORK_ERROR";
  private static final String E_SERVICE_ERROR = "E_SERVICE_ERROR";
  private static final String E_ALREADY_OWNED = "E_ALREADY_OWNED";
  private static final String E_REMOTE_ERROR = "E_REMOTE_ERROR";
  private static final String E_USER_ERROR = "E_USER_ERROR";
  private static final String E_DEVELOPER_ERROR = "E_DEVELOPER_ERROR";
  private static final String E_BILLING_RESPONSE_JSON_PARSE_ERROR = "E_BILLING_RESPONSE_JSON_PARSE_ERROR";

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
  public RequestId getProductData(ReadableArray skus, Promise promise) {
	
	//Build HashSet from ReadableArray
	final Set<String> skusSet = new HashSet<String>();
	for (int i = 0; i < skus.size(); i++) {
	  skusSet.add(skus.getString(i));
	}

    savePromise(GET_PRODUCT_DATA, promise);
    RequestId requestId = PurchasingService.getProductData(skusSet);
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
  public void notifyFulfillment(String receiptId, String result) {
	FulfillmentResult fulfillmentResult = FulfillmentResult.FULFILLED;
	switch (result) {
	  case "FULFILLED": 
	    fulfillmentResult = FulfillmentResult.FULFILLED;
		break;
	  case "UNAVAILABLE": 
	    fulfillmentResult = FulfillmentResult.UNAVAILABLE;
	    break;
	}
    Log.d(TAG, "Notifying Amazon on fulfillment of " + receiptId + " with result " + fulfillmentResult);
    PurchasingService.notifyFulfillment(receiptId, fulfillmentResult);
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
      Log.d(TAG, "onProductDataResponse: " + productDataResponse.toString());
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
			  
              Number number = 0.00;
              try {
			    String priceString = product.getPrice();
				if (priceString != null && !priceString.isEmpty()) {
					number = format.parse(priceString);
				}
              } catch (ParseException e) {
				Log.w(TAG, "onProductDataResponse: Failed to parse price for product: " + product.getSku());
				number = 0.00;
			  }
			  Log.d(TAG, "parsed price: " + number);

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
            rejectPromises(GET_PRODUCT_DATA, E_BILLING_RESPONSE_JSON_PARSE_ERROR, e.getMessage(), e);
          }
          break;
        case FAILED: 
          rejectPromises(GET_PRODUCT_DATA, E_SERVICE_ERROR, null, null);
          break;
        case NOT_SUPPORTED: 
        rejectPromises(GET_PRODUCT_DATA, E_SERVICE_ERROR, null, null);
          break;
      }
    }

    public void onPurchaseResponse(PurchaseResponse purchaseResponse) {
      final String localTag = "onPurchaseResponse";
      final PurchaseResponse.RequestStatus status = purchaseResponse.getRequestStatus();
      Log.d(TAG, "Response status: " + status);
	  Receipt receipt = null;
	  UserData userData = null;
	  switch (status) {
		case SUCCESSFUL: 
          receipt = purchaseResponse.getReceipt();
          userData = purchaseResponse.getUserData();
          // NOTE: In many cases, you would want to notifyFullfilment here. I've left this out in case of 
          // any need to handle things in the UI / React layer prior to notifying fullfilment. The function remains
          // Available as a React Function and can be called at any time 
          Date date = receipt.getPurchaseDate();
          Long transactionDate=date.getTime();
          try {
			WritableMap map = getPurchaseData(receipt.getSku(),
                                receipt.getReceiptId(),
                                userData.getUserId(),
                                transactionDate.doubleValue(),
                                getCancelDate(receipt));
            resolvePromises(PURCHASE_ITEM, map);
          } catch (Exception e) {
            rejectPromises(PURCHASE_ITEM, E_BILLING_RESPONSE_JSON_PARSE_ERROR, e.getMessage(), e);
          }
          break;
		case ALREADY_PURCHASED: 
		  rejectPromises(PURCHASE_ITEM, E_ALREADY_OWNED, "You have already purchased this item.", null);
		  break;
		case NOT_SUPPORTED:
		case INVALID_SKU:
	    case FAILED: 
          rejectPromises(PURCHASE_ITEM, E_UNKNOWN, null, null);
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
			UserData userData = purchaseUpdatesResponse.getUserData();
            for(Receipt receipt : receipts) {
              Date date = receipt.getPurchaseDate();
              Long transactionDate = date.getTime();
              WritableMap map = getPurchaseData(receipt.getSku(),
                                  receipt.getReceiptId(),
                                  userData.getUserId(),
                                  transactionDate.doubleValue(),
                                  getCancelDate(receipt));

              //Log.d(TAG, "Adding item: " + map.toString());
              maps.pushMap(map);
            }
            resolvePromises(GET_PURCHASE_UPDATES, maps);
          } catch (Exception e) {
            rejectPromises(GET_PURCHASE_UPDATES, E_BILLING_RESPONSE_JSON_PARSE_ERROR, e.getMessage(), e);
          }
          break;
        case FAILED:
          rejectPromises(GET_PURCHASE_UPDATES, E_UNKNOWN, null, null);
          break;
        case NOT_SUPPORTED:
          Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request");
          rejectPromises(GET_PURCHASE_UPDATES, E_SERVICE_ERROR, "Should retry request", null);
          break;
      }
    }

    private Double getCancelDate(Receipt reciept) {
      Date cancelDate = reciept.getCancelDate();

      if (cancelDate == null) return null;

      return ((Long) cancelDate.getTime()).doubleValue();
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
            rejectPromises(GET_USER_DATA, E_BILLING_RESPONSE_JSON_PARSE_ERROR, e.getMessage(), e);
          }
          break;
        case FAILED:
          Log.d(TAG, "onPurchaseUpdatesResponse: failed, should retry request");
          rejectPromises(GET_USER_DATA, E_UNKNOWN, null, null);
          break;
        case NOT_SUPPORTED:
          rejectPromises(GET_USER_DATA, E_SERVICE_ERROR, "Should retry request", null);
          break;
      }
    }
  };

  private WritableMap getPurchaseData(String productId, String receiptId, String userId,
    Double transactionDate, Double cancelDate) {
    WritableMap map = Arguments.createMap();
    map.putString("productId", productId);
    map.putString("receiptId", receiptId);
    if (cancelDate == null)
      map.putNull("cancelDateAmazon");
    else
      map.putString("cancelDateAmazon", Double.toString(cancelDate));
    map.putString("userIdAmazon", userId);
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
    Log.d(TAG, "reject promises: " + key + " " + code + ": " + message);
    if (promises.containsKey(key)) {
      ArrayList<Promise> list = promises.get(key);
      for (Promise promise : list) {
        promise.reject(code, message, err);
      }
      promises.remove(key);
    }
  }
}