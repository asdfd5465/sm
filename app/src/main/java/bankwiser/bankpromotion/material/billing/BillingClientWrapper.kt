package bankwiser.bankpromotion.material.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Replace with your actual SKU ID from Google Play Console
const val PREMIUM_SUBSCRIPTION_ID = "bankwiser_pro_yearly_subscription_test1" // Use a test ID or your actual one

class BillingClientWrapper(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO) // Use a passed scope or default
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val _billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases() // Required
        .build()

    // True if the user has an active, acknowledged premium subscription from Play Billing
    private val _hasActivePremiumSubscription = MutableStateFlow(false)
    val hasActivePremiumSubscription: StateFlow<Boolean> = _hasActivePremiumSubscription.asStateFlow()

    private val _billingConnectionState = MutableStateFlow<BillingClientConnectionState>(BillingClientConnectionState.DISCONNECTED)
    val billingConnectionState: StateFlow<BillingClientConnectionState> = _billingConnectionState.asStateFlow()

    // To hold fetched product details
    private val _premiumProductDetails = MutableStateFlow<ProductDetails?>(null)
    val premiumProductDetails: StateFlow<ProductDetails?> = _premiumProductDetails.asStateFlow()


    enum class BillingClientConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CLOSED,
        ERROR_SETUP, // More specific error for setup
        ERROR_QUERY // More specific error for querying
    }

    init {
        startConnection()
    }

    fun startConnection() {
        if (_billingClient.isReady) {
            _billingConnectionState.value = BillingClientConnectionState.CONNECTED
            queryPurchases()
            queryProductDetails() // Also query product details on connect
            return
        }
        _billingConnectionState.value = BillingClientConnectionState.CONNECTING
        _billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _billingConnectionState.value = BillingClientConnectionState.CONNECTED
            queryPurchases()
            queryProductDetails()
        } else {
            _billingConnectionState.value = BillingClientConnectionState.ERROR_SETUP
            // Log or handle billing setup error, e.g., billingResult.debugMessage
        }
    }

    override fun onBillingServiceDisconnected() {
        _billingConnectionState.value = BillingClientConnectionState.DISCONNECTED
        // Consider implementing a retry mechanism with backoff strategy here.
        // For now, connection needs to be manually re-initiated or on next app start.
    }

    fun queryPurchases() {
        if (!_billingClient.isReady) {
            _billingConnectionState.value = BillingClientConnectionState.ERROR_QUERY // Indicate an issue
            startConnection() // Attempt to reconnect
            return
        }

        externalScope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS) // For subscriptions
                .build()

            // queryPurchasesAsync is the recommended way for subscriptions and non-consumables
            _billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(purchases)
                } else {
                    // Handle error querying purchases, e.g., billingResult.debugMessage
                    _hasActivePremiumSubscription.value = false // Assume no access on error
                }
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>?) {
        var grantedPremium = false
        purchases?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Check if any of the purchased products match our premium subscription ID
                if (purchase.products.contains(PREMIUM_SUBSCRIPTION_ID)) {
                    grantedPremium = true
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase.purchaseToken)
                    }
                }
            }
            // You might also want to handle PurchaseState.PENDING if you enable pending purchases
        }
        _hasActivePremiumSubscription.value = grantedPremium
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        _billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Purchase acknowledged, user has access.
                // queryPurchases() // Optionally re-query to confirm state
            } else {
                // Handle acknowledgment error. User might lose access if not acknowledged.
            }
        }
    }

    fun queryProductDetails() {
        if (!_billingClient.isReady) {
            _billingConnectionState.value = BillingClientConnectionState.ERROR_QUERY
            startConnection()
            return
        }
        externalScope.launch {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PREMIUM_SUBSCRIPTION_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

            // queryProductDetails is a suspend function
            val productDetailsResult = _billingClient.queryProductDetails(params)
            if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _premiumProductDetails.value = productDetailsResult.productDetailsList?.firstOrNull {
                    it.productId == PREMIUM_SUBSCRIPTION_ID
                }
            } else {
                // Handle error fetching product details
                _premiumProductDetails.value = null
            }
        }
    }


    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        if (!_billingClient.isReady) {
            // Handle not ready state, maybe show a message to the user or retry connection
            return
        }

        // Subscriptions require an offer token. Get it from productDetails.
        // This example assumes a simple subscription with one base plan and potentially one offer.
        // For more complex scenarios (multiple base plans, multiple offers),
        // you'll need logic to select the correct offerToken.
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken

        if (productDetails.productType == BillingClient.ProductType.SUBS && offerToken == null) {
            // Handle error: No offer token found for subscription. This is a setup issue in Play Console.
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .apply {
                    if (productDetails.productType == BillingClient.ProductType.SUBS) {
                        offerToken?.let { setOfferToken(it) }
                    }
                }
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        // Launch the billing flow
        val billingResult = _billingClient.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Handle error launching billing flow
        }
    }


    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases) // Process new purchases
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle user cancellation
        } else {
            // Handle other errors like BillingResponseCode.ITEM_ALREADY_OWNED, etc.
        }
    }

    fun endConnection() {
        if (_billingClient.isReady) {
            _billingClient.endConnection()
            _billingConnectionState.value = BillingClientConnectionState.CLOSED
        }
    }
}
