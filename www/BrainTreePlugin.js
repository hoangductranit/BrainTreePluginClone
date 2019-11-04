var exec = require('cordova/exec');

var Plugin_ID='BrainTreePlugin';
var BrainTreePlugin={};

/**
 * Used to initialize the Braintree client.
 * 
 * The client must be initialized before other methods can be used.
 * 
 * @param {string} token - The client token or tokenization key to use with the Braintree client.
 * @param [function] successCallback - The success callback for this asynchronous function.
 * @param [function] failureCallback - The failure callback for this asynchronous function; receives an error string.
 */
BrainTreePlugin.initialize = function initialize(token, successCallback, failureCallback) {

    if (!token || typeof(token) !== "string") {
        failureCallback("A non-null, non-empty string must be provided for the token parameter.");
        return;
    }

    exec(successCallback, failureCallback, Plugin_ID, "initialize", [token]);
};

/**
 * Shows Braintree's drop-in payment UI.
 * 
 * @param {object} options - The options used to control the drop-in payment UI.
 * @param [function] successCallback - The success callback for this asynchronous function; receives a result object.
 * @param [function] failureCallback - The failure callback for this asynchronous function; receives an error string.
 */
BrainTreePlugin.presentDropInPaymentUI = function showDropInUI(options, successCallback, failureCallback) {

    if (!options) {
        options = {};
    }

    if (typeof(options.amount) !== "string") {
        options.amount = "0";
    };

    if (typeof(options.currencyMode) !== "string") {
        options.currencyMode = "USD";
    };

    if (typeof(options.googleProductEnv) !== "boolean") {
        options.googleProductEnv = false;
    };

    if (typeof(options.googleMerchantId) !== "string") {
        options.googleMerchantId ="";
    };
    
    var pluginOptions = [
        options.amount,
        options.currencyMode,
        options.googleProductEnv,
        options.googleMerchantId
    ];
    exec(successCallback, failureCallback, Plugin_ID, "presentDropInPaymentUI", pluginOptions);
};

module.exports=BrainTreePlugin;