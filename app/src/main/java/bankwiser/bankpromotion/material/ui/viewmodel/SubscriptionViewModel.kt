package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.billing.BillingClientWrapper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.MutableStateFlow // <<< IMPORT ADDED HERE
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesHelper = UserPreferencesHelper(application)
    val billingClientWrapper = BillingClientWrapper(application, viewModelScope)

    private val _simulatedAccessFlow = MutableStateFlow(userPreferencesHelper.isUserSubscribed())

    val hasPremiumAccess: StateFlow<Boolean> = combine(
        billingClientWrapper.hasActivePremiumSubscription,
        _simulatedAccessFlow // Use the flow for simulated access
    ) { playBillingAccess, simulatedAccess ->
        playBillingAccess || simulatedAccess
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        userPreferencesHelper.isUserSubscribed() || billingClientWrapper.hasActivePremiumSubscription.value
    )

    val premiumProductDetails: StateFlow<ProductDetails?> = billingClientWrapper.premiumProductDetails

    val billingConnectionState: StateFlow<BillingClientWrapper.BillingClientConnectionState> =
        billingClientWrapper.billingConnectionState


    fun setSimulatedSubscription(isSubscribed: Boolean) {
        userPreferencesHelper.setUserSubscribed(isSubscribed)
        _simulatedAccessFlow.value = isSubscribed // Update the flow
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails?) {
        productDetails?.let {
            billingClientWrapper.launchPurchaseFlow(activity, it)
        }
    }

    fun refreshPurchases() {
        billingClientWrapper.queryPurchases()
    }

    override fun onCleared() {
        super.onCleared()
        billingClientWrapper.endConnection()
    }
}
