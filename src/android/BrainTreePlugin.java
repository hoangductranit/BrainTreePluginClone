package peter.plugin.braintree;

import android.app.Activity;
import android.content.Intent;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.braintreepayments.api.models.CardNonce;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.VenmoAccountNonce;
import com.braintreepayments.api.models.GooglePaymentCardNonce;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.GooglePayment;
import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;

import com.google.android.gms.identity.intents.model.UserAddress;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * This class echoes a string called from JavaScript.
 */
public class BrainTreePlugin extends CordovaPlugin {

    private static final int DROP_IN_REQUEST = 100;
    private static final int PAYMENT_BUTTON_REQUEST = 200;
    private static final int CUSTOM_REQUEST = 300;
    private static final int PAYPAL_REQUEST = 400;

    private DropInRequest dropInRequest = null;
    private CallbackContext dropInUICallbackContext = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action == null) {
            return false;
        }

        if (action.equals("initialize")) {

            try {
                this.initialize(args, callbackContext);
            }
            catch (Exception exception) {
                callbackContext.error("BraintreePlugin uncaught exception: " + exception.getMessage());
            }

            return true;
        }
        else if (action.equals("presentDropInPaymentUI")) {

            try {
                this.presentDropInPaymentUI(args, callbackContext);
            }
            catch (Exception exception) {
                callbackContext.error("BraintreePlugin uncaught exception: " + exception.getMessage());
            }

            return true;
        }
        else {
            // The given action was not handled above.
            return false;
        }
    }
    private synchronized void initialize(final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        // Ensure we have the correct number of arguments.
        if (args.length() != 1) {
            callbackContext.error("A token is required.");
            return;
        }

        // Obtain the arguments.
        String token = args.getString(0);

        if (token == null || token.equals("")) {
            callbackContext.error("A token is required.");
            return;
        }

        dropInRequest =  new DropInRequest().clientToken(token);

        if (dropInRequest == null) {
            callbackContext.error("The Braintree client failed to initialize.");
            return;
        }
        else
        {
            dropInRequest.collectDeviceData(true);
            dropInRequest.vaultManager(true);
            dropInRequest.disableVenmo();
        }

        callbackContext.success();
    }
    private synchronized void presentDropInPaymentUI(final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        String amount=null;
        String currencyMode=null;
        String googleMerchantId=null;
        Boolean googleProductEnv=false;

        // Ensure the client has been initialized.
        if (dropInRequest == null) {
            callbackContext.error("The Braintree client must first be initialized via BraintreePlugin.initialize(token)");
            return;
        }

        // Ensure we have the correct number of arguments.
        if (args.length() != 4) {
            callbackContext.error("Amount, Currency type, Google MerchantId are required.");
            return;
        }

        // Obtain the arguments.
        amount = args.getString(0);
        currencyMode = args.getString(1);
        googleMerchantId = args.getString(3);

        if (amount == null || amount.equals("")) {
            callbackContext.error("Amount is required.");
            return;
        }

        if (currencyMode == null || currencyMode.equals("")) {
            callbackContext.error("Currency type is required.");
            return;
        }

        if(args.getString(2)!=null)
        {
            googleProductEnv=Boolean.parseBoolean(args.getString(2));
        }

        if (googleMerchantId == null && googleProductEnv) {
            callbackContext.error("Google MerchantId is required.");
            return;
        }

        //PayPal Request
       dropInRequest.paypalRequest(SetUpPayPalRequest(
            amount,
            currencyMode));

        //Google Payment Request
        dropInRequest.googlePaymentRequest(SetUpGooglePayRequest(
                amount,
                currencyMode,
                googleProductEnv,
                googleMerchantId
        ));

        this.cordova.setActivityResultCallback(this);
        this.cordova.startActivityForResult(this, dropInRequest.getIntent(this.cordova.getActivity()), DROP_IN_REQUEST);

        dropInUICallbackContext = callbackContext;
    }

    //Setup Paypal Request
    private PayPalRequest SetUpPayPalRequest(String amount,String currencyCode)
    {
        PayPalRequest returnPayPalRequest=new PayPalRequest(amount);
        returnPayPalRequest.currencyCode(currencyCode);
        returnPayPalRequest.intent(PayPalRequest.INTENT_AUTHORIZE);
        return returnPayPalRequest;
    }
    //Setup Google Request
    private GooglePaymentRequest SetUpGooglePayRequest(String amount,String currencyCode,boolean productEnv,String googleMerchantId)
    {
        String environment="TEST";
        GooglePaymentRequest returnGooglePayment=new GooglePaymentRequest()
                .transactionInfo(TransactionInfo.newBuilder()
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .setCurrencyCode(currencyCode)
                        .setTotalPrice(amount)
                        .build())
                .billingAddressRequired(true)
                .emailRequired(true).paypalEnabled(true).allowPrepaidCards(true);
        if(productEnv) {
            environment = "PRODUCTION";
            returnGooglePayment.googleMerchantId(googleMerchantId);
        }
        returnGooglePayment.environment(environment);
        return returnGooglePayment;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (dropInUICallbackContext == null) {
            return;
        }

        if (requestCode == DROP_IN_REQUEST) {

            PaymentMethodNonce paymentMethodNonce = null;
            if(resultCode==Activity.RESULT_OK) {
                DropInResult result = intent.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                paymentMethodNonce = result.getPaymentMethodNonce();
                this.handleDropInPaymentUiResult(paymentMethodNonce);
            }
            else if(requestCode==Activity.RESULT_CANCELED)
            {
                Map<String, Object> resultMap = new HashMap<String, Object>();
                resultMap.put("userCancelled", true);
                dropInUICallbackContext.success(new JSONObject(resultMap));
                dropInUICallbackContext = null;
                return;
            }
            else {
                Exception error = (Exception)intent.getSerializableExtra(DropInActivity.EXTRA_ERROR);
                dropInUICallbackContext.error(error.getMessage());
                return;
            }
        }
        else if (requestCode == PAYMENT_BUTTON_REQUEST) {
            //TODO
            dropInUICallbackContext.error("Activity result handler for PAYMENT_BUTTON_REQUEST not implemented.");
        }
        else if (requestCode == CUSTOM_REQUEST) {
            dropInUICallbackContext.error("Activity result handler for CUSTOM_REQUEST not implemented.");
            //TODO
        }
        else if (requestCode == PAYPAL_REQUEST) {
            dropInUICallbackContext.error("Activity result handler for PAYPAL_REQUEST not implemented.");
            //TODO
        }
    }
    private void handleDropInPaymentUiResult(int resultCode, PaymentMethodNonce paymentMethodNonce) {

        if (dropInUICallbackContext == null) {
            return;
        }

        if (paymentMethodNonce == null) {
            dropInUICallbackContext.error("Result was not RESULT_CANCELED, but no PaymentMethodNonce was returned from the Braintree SDK.");
            dropInUICallbackContext = null;
            return;
        }

        Map<String, Object> resultMap = this.getPaymentUINonceResult(paymentMethodNonce);
        dropInUICallbackContext.success(new JSONObject(resultMap));
        dropInUICallbackContext = null;
    }
    private Map<String, Object> getPaymentUINonceResult(PaymentMethodNonce paymentMethodNonce) {

        Map<String, Object> resultMap = new HashMap<String, Object>();

        resultMap.put("nonce", paymentMethodNonce.getNonce());
        resultMap.put("type", paymentMethodNonce.getTypeLabel());
        resultMap.put("localizedDescription", paymentMethodNonce.getDescription());

        // Card
        if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce)paymentMethodNonce;

            Map<String, Object> innerMap = new HashMap<String, Object>();
            innerMap.put("lastTwo", cardNonce.getLastTwo());
            innerMap.put("network", cardNonce.getCardType());

            resultMap.put("card", innerMap);
        }

        // PayPal
        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce payPalAccountNonce = (PayPalAccountNonce)paymentMethodNonce;

            Map<String, Object> innerMap = new HashMap<String, Object>();
            resultMap.put("email", payPalAccountNonce.getEmail());
            resultMap.put("firstName", payPalAccountNonce.getFirstName());
            resultMap.put("lastName", payPalAccountNonce.getLastName());
            resultMap.put("phone", payPalAccountNonce.getPhone());
            resultMap.put("billingAddress", payPalAccountNonce.getBillingAddress()); //TODO
            resultMap.put("shippingAddress", payPalAccountNonce.getShippingAddress()); //TODO
            resultMap.put("clientMetadataId", payPalAccountNonce.getClientMetadataId());
            resultMap.put("payerId", payPalAccountNonce.getPayerId());

            resultMap.put("payPalAccount", innerMap);
        }
        //google pay
        if(paymentMethodNonce instanceof GooglePaymentCardNonce)
        {
            GooglePaymentCardNonce googlePaymentCardNonce=(GooglePaymentCardNonce)paymentMethodNonce;

            Map<String, Object> innerMap = new HashMap<String, Object>();
            resultMap.put("email", googlePaymentCardNonce.getEmail());
            resultMap.put("googleAccount", innerMap);

        }
        return resultMap;
    }
}
