package com.dooboolab.RNIap;

import android.util.Log;
import androidx.annotation.Nullable;
import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.CoinsReward;
import com.amazon.device.iap.model.FulfillmentResult;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.ProductType;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.RequestId;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RNIapAmazonListener implements PurchasingListener {
  final String TAG = "RNIapAmazonListener";

  private static final String E_PRODUCT_DATA_RESPONSE_FAILED =
    "E_PRODUCT_DATA_RESPONSE_FAILED";
  private static final String E_PRODUCT_DATA_RESPONSE_NOT_SUPPORTED =
    "E_PRODUCT_DATA_RESPONSE_NOT_SUPPORTED";

  private static final String E_PURCHASE_UPDATES_RESPONSE_FAILED =
    "E_PURCHASE_UPDATES_RESPONSE_FAILED";
  private static final String E_PURCHASE_UPDATES_RESPONSE_NOT_SUPPORTED =
    "E_PURCHASE_UPDATES_RESPONSE_NOT_SUPPORTED";

  private static final String E_PURCHASE_RESPONSE_FAILED =
    "E_PURCHASE_RESPONSE_FAILED";
  private static final String E_PURCHASE_RESPONSE_ALREADY_PURCHASED =
    "E_PURCHASE_RESPONSE_ALREADY_PURCHASED";
  private static final String E_PURCHASE_RESPONSE_NOT_SUPPORTED =
    "E_PURCHASE_RESPONSE_NOT_SUPPORTED";
  private static final String E_PURCHASE_RESPONSE_INVALID_SKU =
    "E_PURCHASE_RESPONSE_INVALID_SKU";

  private static final String E_USER_DATA_RESPONSE_FAILED =
    "E_USER_DATA_RESPONSE_FAILED";
  private static final String E_USER_DATA_RESPONSE_NOT_SUPPORTED =
    "E_USER_DATA_RESPONSE_NOT_SUPPORTED";

  private ReactContext reactContext;
  private List<Product> skus;
  private WritableNativeArray availableItems;
  private String availableItemsType;

  public RNIapAmazonListener(final ReactContext reactContext) {
    super();
    this.reactContext = reactContext;
    this.skus = new ArrayList<Product>();
    this.availableItems = new WritableNativeArray();
    this.availableItemsType = null;
  }

  public void getPurchaseUpdatesByType(final String type) {
    this.availableItemsType = type;
    PurchasingService.getPurchaseUpdates(true);
  }

  @Override
  public void onProductDataResponse(final ProductDataResponse response) {
    final ProductDataResponse.RequestStatus status = response.getRequestStatus();
    final String requestId = response.getRequestId().toString();
    Log.d(TAG, "onProductDataResponse: " + requestId + " (" + status + ")");
    Log.d(TAG, "onProductDataResponse: " + response.toString());

    switch (status) {
      case SUCCESSFUL:
        Log.d(TAG, "onProductDataResponse: SUCCESSFUL");
        final Map<String, Product> productData = response.getProductData();
        final Set<String> unavailableSkus = response.getUnavailableSkus();

        WritableNativeArray items = new WritableNativeArray();

        NumberFormat format = NumberFormat.getCurrencyInstance();
        for (Map.Entry<String, Product> skuDetails : productData.entrySet()) {
          Log.d(TAG, "onProductDataResponse: Attempting to parse Product...");

          Product product = skuDetails.getValue();

          Log.d(TAG, "onProductDataResponse: Product: " + product.toString());

          if (!skus.contains(product)) {
            skus.add(product);
          }

          ProductType productType = product.getProductType();
          final String productTypeString = (
              productType == ProductType.ENTITLED ||
              productType == ProductType.CONSUMABLE
            )
            ? "inapp"
            : "subs";
          Number priceNumber = 0.00;
          try {
            String priceString = product.getPrice();
            if (priceString != null && !priceString.isEmpty()) {
              priceNumber = format.parse(priceString);
            }
          } catch (ParseException e) {
            Log.w(
              TAG,
              "onProductDataResponse: Failed to parse price for product: " +
              product.getSku()
            );
          }

          Log.d(TAG, "onProductDataResponse: Create item from product.");

          WritableMap item = Arguments.createMap();
          CoinsReward coinsReward = product.getCoinsReward();
          item.putString("productId", product.getSku());
          item.putString("price", priceNumber.toString());
          item.putString("currency", product.getPrice().substring(0, 1));
          item.putString("type", productTypeString);
          item.putString("localizedPrice", product.getPrice());
          item.putString("title", product.getTitle());
          item.putString("description", product.getDescription());
          item.putNull("introductoryPrice");
          item.putNull("subscriptionPeriodAndroid");
          item.putNull("freeTrialPeriodAndroid");
          item.putNull("introductoryPriceCyclesAndroid");
          item.putNull("introductoryPricePeriodAndroid");
          item.putString("iconUrl", product.getSmallIconUrl());
          item.putString("originalJson", product.toString());
          item.putString("originalPrice", product.getPrice());
          if (coinsReward != null) {
            item.putInt("coinsRewardAmountAmazon", coinsReward.getAmount());
          }
          items.pushMap(item);

          Log.d(TAG, "onProductDataResponse: item pushed: " + product.getSku());
          Log.d(TAG, "onProductDataResponse:" + product.toString());
        }

        Log.d(TAG, "onProductDataResponse: RESOLVE");
        DoobooUtils
          .getInstance()
          .resolvePromisesForKey(
            RNIapAmazonModule.PROMISE_GET_PRODUCT_DATA,
            items
          );
        break;
      case FAILED:
        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_GET_PRODUCT_DATA,
            E_PRODUCT_DATA_RESPONSE_FAILED,
            null,
            null
          );
        break;
      case NOT_SUPPORTED:
        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_GET_PRODUCT_DATA,
            E_PRODUCT_DATA_RESPONSE_NOT_SUPPORTED,
            null,
            null
          );
        break;
    }
  }

  @Override
  public void onPurchaseUpdatesResponse(
    final PurchaseUpdatesResponse response
  ) {
    //Log.d(TAG, "onPurchaseUpdatesResponse: requestId (" + response.getRequestId()
    //             + ") purchaseUpdatesResponseStatus (" + response.getRequestStatus()
    //             + ") userId (" + response.getUserData().getUserId() + ")");
    Log.d(TAG, "onPurchaseUpdatesResponse: " + response.toString());

    //Info for potential error reporting
    String debugMessage = null;
    String errorCode = DoobooUtils.E_UNKNOWN;
    WritableMap error = Arguments.createMap();

    final PurchaseUpdatesResponse.RequestStatus status = response.getRequestStatus();
    switch (status) {
      case SUCCESSFUL:
        UserData userData = response.getUserData();
        WritableMap promiseItem = null;
        final List<Receipt> purchases = response.getReceipts();
        for (Receipt receipt : purchases) {
          WritableMap item = Arguments.createMap();
          item.putString("productId", receipt.getSku());
          //item.putString("transactionId", purchase.getOrderId());
          item.putDouble(
            "transactionDate",
            receipt.getPurchaseDate().getTime()
          );
          //item.putString("transactionReceipt", purchase.getOriginalJson());
          item.putString("purchaseToken", receipt.getReceiptId());
          //item.putString("dataAndroid", purchase.getOriginalJson());
          //item.putString("signatureAndroid", purchase.getSignature());
          //item.putBoolean("autoRenewingAndroid", purchase.isAutoRenewing());
          //item.putBoolean("isAcknowledgedAndroid", purchase.isAcknowledged());
          //item.putInt("purchaseStateAndroid", purchase.getPurchaseState());
          item.putString("originalJson", receipt.toJSON().toString());
          item.putString("userIdAmazon", userData.getUserId());
          item.putString("userMarketplaceAmazon", userData.getMarketplace());
          item.putString("userJsonAmazon", userData.toJSON().toString());

          promiseItem = new WritableNativeMap();
          promiseItem.merge(item);
          sendEvent(reactContext, "purchase-updated", item);

          ProductType productType = receipt.getProductType();
          final String productTypeString = (
              productType == ProductType.ENTITLED ||
              productType == ProductType.CONSUMABLE
            )
            ? "inapp"
            : "subs";
          if (productTypeString.equals(availableItemsType)) {
            availableItems.pushMap(promiseItem);
          }
        }
        if (response.hasMore()) {
          PurchasingService.getPurchaseUpdates(false);
        } else {
          if (purchases.size() > 0 && promiseItem != null) {
            DoobooUtils
              .getInstance()
              .resolvePromisesForKey(
                RNIapAmazonModule.PROMISE_BUY_ITEM,
                promiseItem
              );
          }
          DoobooUtils
            .getInstance()
            .resolvePromisesForKey(
              RNIapAmazonModule.PROMISE_QUERY_PURCHASES,
              true
            );

          DoobooUtils
            .getInstance()
            .resolvePromisesForKey(
              RNIapAmazonModule.PROMISE_QUERY_AVAILABLE_ITEMS,
              availableItems
            );
          availableItems = new WritableNativeArray();
          availableItemsType = null;
        }
        break;
      case FAILED:
        debugMessage =
          "An unknown or unexpected error has occured. Please try again later.";
        errorCode = DoobooUtils.E_UNKNOWN;
        error.putInt("responseCode", 0);
        error.putString("debugMessage", debugMessage);
        error.putString("code", errorCode);
        error.putString("message", debugMessage);
        sendEvent(reactContext, "purchase-error", error);

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_QUERY_PURCHASES,
            errorCode,
            debugMessage,
            null
          );

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_QUERY_AVAILABLE_ITEMS,
            errorCode,
            debugMessage,
            null
          );
        availableItems = new WritableNativeArray();
        availableItemsType = null;
        break;
      case NOT_SUPPORTED:
        debugMessage = "This feature is not available on your device.";
        errorCode = DoobooUtils.E_SERVICE_ERROR;
        error.putInt("responseCode", 0);
        error.putString("debugMessage", debugMessage);
        error.putString("code", errorCode);
        error.putString("message", debugMessage);
        sendEvent(reactContext, "purchase-error", error);

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_QUERY_PURCHASES,
            errorCode,
            debugMessage,
            null
          );

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_QUERY_AVAILABLE_ITEMS,
            errorCode,
            debugMessage,
            null
          );
        availableItems = new WritableNativeArray();
        availableItemsType = null;
        break;
    }
  }

  @Override
  public void onPurchaseResponse(final PurchaseResponse response) {
    final String requestId = response.getRequestId().toString();
    final String userId = response.getUserData().getUserId();
    final PurchaseResponse.RequestStatus status = response.getRequestStatus();

    //Info for potential error reporting
    String debugMessage = null;
    String errorCode = DoobooUtils.E_UNKNOWN;
    WritableMap error = Arguments.createMap();

    //Log.d(TAG, "onPurchaseResponse: requestId (" + requestId + ") userId ("
    //             + userId + ") purchaseRequestStatus (" + status + ")");
    Log.d(TAG, "onPurchaseResponse: " + response.toString());
    switch (status) {
      case SUCCESSFUL:
        final Receipt receipt = response.getReceipt();
        final UserData userData = response.getUserData();
        WritableMap item = Arguments.createMap();
        item.putString("productId", receipt.getSku());
        item.putDouble("transactionDate", receipt.getPurchaseDate().getTime());
        item.putString("purchaseToken", receipt.getReceiptId());
        item.putString("originalJson", receipt.toJSON().toString());
        item.putString("userIdAmazon", userData.getUserId());
        item.putString("userMarketplaceAmazon", userData.getMarketplace());
        item.putString("userJsonAmazon", userData.toJSON().toString());
        sendEvent(reactContext, "purchase-updated", item);

        DoobooUtils
          .getInstance()
          .resolvePromisesForKey(
            RNIapAmazonModule.PROMISE_GET_PRODUCT_DATA,
            true
          );
        break;
      case ALREADY_PURCHASED:
        debugMessage = "You already own this item.";
        errorCode = DoobooUtils.E_ALREADY_OWNED;
        error.putInt("responseCode", 0);
        error.putString("debugMessage", debugMessage);
        error.putString("code", errorCode);
        error.putString("message", debugMessage);
        sendEvent(reactContext, "purchase-error", error);

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_BUY_ITEM,
            errorCode,
            debugMessage,
            null
          );
        break;
      case FAILED:
        debugMessage =
          "An unknown or unexpected error has occured. Please try again later.";
        errorCode = DoobooUtils.E_UNKNOWN;
        error.putInt("responseCode", 0);
        error.putString("debugMessage", debugMessage);
        error.putString("code", errorCode);
        error.putString("message", debugMessage);
        sendEvent(reactContext, "purchase-error", error);

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_BUY_ITEM,
            errorCode,
            debugMessage,
            null
          );
        break;
      case INVALID_SKU:
        debugMessage = "That item is unavailable.";
        errorCode = DoobooUtils.E_ITEM_UNAVAILABLE;
        error.putInt("responseCode", 0);
        error.putString("debugMessage", debugMessage);
        error.putString("code", errorCode);
        error.putString("message", debugMessage);
        sendEvent(reactContext, "purchase-error", error);

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_BUY_ITEM,
            errorCode,
            debugMessage,
            null
          );
        break;
      case NOT_SUPPORTED:
        debugMessage = "This feature is not available on your device.";
        errorCode = DoobooUtils.E_SERVICE_ERROR;
        error.putInt("responseCode", 0);
        error.putString("debugMessage", debugMessage);
        error.putString("code", errorCode);
        error.putString("message", debugMessage);
        sendEvent(reactContext, "purchase-error", error);

        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_BUY_ITEM,
            errorCode,
            debugMessage,
            null
          );
        break;
    }
  }

  @Override
  public void onUserDataResponse(final UserDataResponse response) {
    //Log.d(TAG, "onGetUserDataResponse: requestId (" + response.getRequestId()
    //             + ") userIdRequestStatus: " + response.getRequestStatus() + ")");
    Log.d(TAG, "onGetUserDataResponse: " + response.toString());
    final UserDataResponse.RequestStatus status = response.getRequestStatus();
    switch (status) {
      case SUCCESSFUL:
        final UserData userData = response.getUserData();
        WritableMap item = Arguments.createMap();
        item.putString("userIdAmazon", userData.getUserId());
        item.putString("userMarketplaceAmazon", userData.getMarketplace());
        item.putString("userJsonAmazon", userData.toJSON().toString());
        sendEvent(reactContext, "userdata", item);

        DoobooUtils
          .getInstance()
          .resolvePromisesForKey(RNIapAmazonModule.PROMISE_GET_USER_DATA, true);
        break;
      case NOT_SUPPORTED:
        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_GET_USER_DATA,
            E_USER_DATA_RESPONSE_NOT_SUPPORTED,
            null,
            null
          );
        break;
      case FAILED:
        DoobooUtils
          .getInstance()
          .rejectPromisesForKey(
            RNIapAmazonModule.PROMISE_GET_USER_DATA,
            E_USER_DATA_RESPONSE_FAILED,
            null,
            null
          );
        break;
    }
  }

  private void sendEvent(
    ReactContext reactContext,
    String eventName,
    @Nullable WritableMap params
  ) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }
}
