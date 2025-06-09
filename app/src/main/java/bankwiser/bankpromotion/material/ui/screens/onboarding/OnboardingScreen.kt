package bankwiser.bankpromotion.material.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bankwiser.bankpromotion.material.ui.theme.BankWiserProTheme
import bankwiser.bankpromotion.material.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class OnboardingPage(
    val icon: String,
    val title: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage("🎯", "Master Banking & Socio-Economics", "Access distilled content on RBI policies, bank circulars, and socio-economic updates."),
    OnboardingPage("🎧", "Learn On-the-Go", "Listen to audio summaries. Practice MCQs to test your knowledge anytime, anywhere."),
    OnboardingPage("🎁", "5 Months' Content Free!", "Get full access to our initial content for 5 months. Create an account to begin your journey!")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            // Controls
            val isLastPage = pagerState.currentPage == pages.size - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        onGetStarted()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLastPage) "Login with Google" else "Next")
            }

            if (!isLastPage) {
                TextButton(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Skip")
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp)) // To keep button position consistent
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(text = page.icon, fontSize = 72.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = TextSecondary
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    BankWiserProTheme {
        OnboardingScreen(onGetStarted = {})
    }
}
