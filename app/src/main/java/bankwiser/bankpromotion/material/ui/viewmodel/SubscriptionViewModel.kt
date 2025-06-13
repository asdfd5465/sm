package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.billing.BillingClientWrapper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesHelper = UserPreferencesHelper(application)
    val billingClientWrapper = BillingClientWrapper(application, viewModelScope)

    // This combines the Play Billing check with our simulated/manual override
    val hasPremiumAccess: StateFlow<Boolean> = combine(
        billingClientWrapper.hasPremiumAccess, // Actual check from Play Billing
        // For Phase 6, we can add the simulated flag here if needed for testing,
        // but ideally, the UI should react to billingClientWrapper.hasPremiumAccess.
        // Let's rely on billingClientWrapper for now.
        // If you need to test without actual purchases, you'd modify this.
        // For example: MutableStateFlow(userPreferencesHelper.isUserSubscribed())
        // For now, direct passthrough:
        MutableStateFlow(userPreferencesHelper.isUserSubscribed()) // Initial value, can be overridden by billingClientWrapper
    ) { playBillingAccess, simulatedAccess ->
        playBillingAccess || simulatedAccess // User has access if either is true
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        userPreferencesHelper.isUserSubscribed() // Initial value
    )

    // Call this method from UI to simulate setting subscription status (for testing)
    fun setSimulatedSubscription(isSubscribed: Boolean) {
        userPreferencesHelper.setUserSubscribed(isSubscribed)
        // We need to re-trigger the combine flow or directly update a state that feeds into it.
        // For simplicity, we'll rely on the UI recomposing and re-checking.
        // A more reactive way would be to have userPreferencesHelper expose a Flow.
        billingClientWrapper.queryPurchases() // Re-query to potentially update the combined state
    }


    override fun onCleared() {
        super.onCleared()
        billingClientWrapper.endConnection()
    }
}
