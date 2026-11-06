package com.budgettracker.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgettracker.ui.components.ErrorBanner
import com.budgettracker.ui.components.IconBadge

/**
 * The first screen. It leads with Google because that is the primary path, and
 * offers the demo next — someone can see the whole app working on sample data
 * before deciding to hand over an account.
 */
@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            IconBadge(
                icon = Icons.Outlined.Savings,
                tint = MaterialTheme.colorScheme.primary,
                size = 64.dp,
            )

            Text(
                text = "Know where your money goes",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 20.dp),
            )

            Text(
                text = "Record what you earn and spend, scan receipts instead of typing " +
                    "them, and keep each month inside a budget you set.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            Column(
                modifier = Modifier.padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Highlight(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "Scan a receipt",
                    body = "The merchant, date and total are read for you, and the lines " +
                        "are sorted into categories.",
                )

                Highlight(
                    icon = Icons.Outlined.PieChart,
                    title = "Budget by category",
                    body = "Set a monthly limit and see what is left before you spend it.",
                )

                Highlight(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Learns as you correct it",
                    body = "Fix a category once and the next receipt from that shop gets " +
                        "it right.",
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            state.error?.let { message ->
                ErrorBanner(
                    message = message,
                    onDismiss = viewModel::dismissError,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (state.googleAvailable) {
                Button(
                    onClick = { viewModel.signInWithGoogle(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue with Google")
                }
            }

            OutlinedButton(
                onClick = viewModel::continueAsGuest,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("Try the demo")
                }
            }

            Text(
                text = "The demo is a throwaway account with sample data. You can turn it " +
                    "into a real one later without losing anything you add.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSignIn, enabled = !state.busy) {
                    Text("Sign in")
                }

                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TextButton(onClick = onCreateAccount, enabled = !state.busy) {
                    Text("Create an account")
                }
            }
        }
    }
}

@Composable
private fun Highlight(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp),
        )

        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
