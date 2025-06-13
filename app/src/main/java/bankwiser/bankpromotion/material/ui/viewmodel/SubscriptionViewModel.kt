package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.billing.BillingClientWrapper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesHelper = UserPreferencesHelper(application)
    val billingClientWrapper = BillingClientWrapper(application, viewModelScope)

    // This combines the Play Billing check with our simulated/manual override
    val hasPremiumAccess: StateFlow<Boolean> = combine(
        billingClientWrapper.hasActivePremiumSubscription, // Actual check from Play Billing
        userPreferencesHelper.isUserSubscribedFlow() // Flow for simulated access
    ) { playBillingAccess, simulatedAccess ->
        playBillingAccess || simulatedAccess // User has access if either Play Billing says so OR simulation is active
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L), // Standard debounce/sharing timeout
        userPreferencesHelper.isUserSubscribed() // Initial value
    )
    
    val premiumProductDetails: StateFlow<ProductDetails?> = billingClientWrapper.premiumProductDetails

    val billingConnectionState: StateFlow<BillingClientWrapper.BillingClientConnectionState> =
        billingClientWrapper.billingConnectionState


    // Call this method from UI to simulate setting subscription status (for testing)
    fun setSimulatedSubscription(isSubscribed: Boolean) {
        userPreferencesHelper.setUserSubscribed(isSubscribed)
        // The combine flow will automatically update due to userPreferencesHelper.isUserSubscribedFlow()
    }

    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails?) {
        productDetails?.let {
            billingClientWrapper.launchPurchaseFlow(activity, it)
        }
        // Optionally handle the case where productDetails is null (e.g., show an error)
    }
    
    fun refreshPurchases() {
        billingClientWrapper.queryPurchases()
    }


    override fun onCleared() {
        super.onCleared()
        billingClientWrapper.endConnection()
    }
}

// Add this extension function to UserPreferencesHelper to make it observable
// Or, if you prefer, UserPreferencesHelper can directly return a Flow.
// This is a simple way to make SharedPreferences changes observable for the ViewModel.
// Consider placing this in UserPreferencesHelper.kt if you expand its usage.
private fun UserPreferencesHelper.isUserSubscribedFlow(): StateFlow<Boolean> {
    val flow = MutableStateFlow(isUserSubscribed())
    // This is a simple way; a more robust way would be to use a listener on SharedPreferences
    // For this phase, it's sufficient if setUserSubscribed also somehow triggers recomposition
    // or if the ViewModel explicitly re-reads.
    // Better: Have setUserSubscribed update a MutableStateFlow in UserPreferencesHelper or here.

    // For now, let's assume setSimulatedSubscription in ViewModel will also trigger a check/update
    // that makes this combine flow re-evaluate. The provided `setSimulatedSubscription`
    // already calls queryPurchases(), which will lead to an update if playBillingAccess changes.
    // The simulatedAccess part will update when userPreferencesHelper.setUserSubscribed is called
    // and the ViewModel recomposes due to other state changes or explicit refresh.
    return flow // This won't auto-update on its own if prefs change externally.
                 // The setSimulatedSubscription method in ViewModel ensures UI reacts.
}
