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

// For Phase 6, we'll mostly focus on checking existing purchases.
// The actual product IDs will be defined later when setting up in-app products in Play Console.
const val PREMIUM_SUBSCRIPTION_ID = "bankwiser_pro_yearly_subscription" // Example ID

class BillingClientWrapper(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val _billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val _hasPremiumAccess = MutableStateFlow(false)
    val hasPremiumAccess: StateFlow<Boolean> = _hasPremiumAccess.asStateFlow()

    private val _billingConnectionState = MutableStateFlow<BillingClientConnectionState>(BillingClientConnectionState.DISCONNECTED)
    val billingConnectionState: StateFlow<BillingClientConnectionState> = _billingConnectionState.asStateFlow()

    enum class BillingClientConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CLOSED,
        ERROR
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        if (_billingClient.isReady) {
            _billingConnectionState.value = BillingClientConnectionState.CONNECTED
            queryPurchases() // Query purchases once connected
            return
        }
        _billingConnectionState.value = BillingClientConnectionState.CONNECTING
        _billingClient.startConnection(this)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _billingConnectionState.value = BillingClientConnectionState.CONNECTED
            queryPurchases()
        } else {
            _billingConnectionState.value = BillingClientConnectionState.ERROR
            // Handle billing setup error
        }
    }

    override fun onBillingServiceDisconnected() {
        _billingConnectionState.value = BillingClientConnectionState.DISCONNECTED
        // Try to restart the connection on the next operation attempt or with a delay
        // For simplicity now, we might just require a manual retry or app restart
    }

    fun queryPurchases() {
        if (!_billingClient.isReady) {
            startConnection() // Attempt to reconnect if not ready
            return
        }

        externalScope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            _billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    processPurchases(purchases)
                } else {
                    // Handle error querying purchases
                }
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>?) {
        var grantedPremium = false
        purchases?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (purchase.products.contains(PREMIUM_SUBSCRIPTION_ID)) {
                    grantedPremium = true
                    // If an active subscription is found, acknowledge it if not already acknowledged
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase.purchaseToken)
                    }
                }
            }
        }
        _hasPremiumAccess.value = grantedPremium
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        _billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            // Handle acknowledge result if necessary
        }
    }

    // Launch purchase flow - will be implemented more fully in a later phase
    fun launchPurchaseFlow(activity: Activity, productId: String = PREMIUM_SUBSCRIPTION_ID) {
        if (!_billingClient.isReady) {
             _billingConnectionState.value = BillingClientConnectionState.ERROR // Or try to reconnect
            return
        }

        externalScope.launch {
            val productList = mutableListOf<QueryProductDetailsParams.Product>()
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

            val (billingResult, productDetailsList) = _billingClient.queryProductDetails(params)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0] // Assuming one product for now
                 // For subscriptions, there's usually one offerToken if you have base plans and offers.
                // If you have multiple offers, you'll need logic to select the correct one.
                val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""

                if (offerToken.isEmpty() && productDetails.productType == BillingClient.ProductType.SUBS) {
                    // Handle error: No offer token found for subscription
                    return@launch
                }

                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .apply {
                            if (productDetails.productType == BillingClient.ProductType.SUBS) {
                                setOfferToken(offerToken)
                            }
                        }
                        .build()
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                withContext(Dispatchers.Main) {
                     _billingClient.launchBillingFlow(activity, billingFlowParams)
                }
            } else {
                // Handle error fetching product details
            }
        }
    }


    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle user cancellation
        } else {
            // Handle other billing errors
        }
    }

    fun endConnection() {
        if (_billingClient.isReady) {
            _billingClient.endConnection()
            _billingConnectionState.value = BillingClientConnectionState.CLOSED
        }
    }
}
