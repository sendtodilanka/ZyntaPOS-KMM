package com.zyntasolutions.zyntapos.feature.auth.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.User

/**
 * Full-screen PIN lock overlay shown after session idle timeout.
 *
 * Allows the currently-signed-in user to re-authenticate quickly via a 4–6 digit PIN,
 * or navigate to the full [LoginScreen] for a different account.
 *
 * This screen is stateless — all state is hoisted to the caller.
 *
 * @param currentUser       The user whose PIN is being validated.
 * @param onPinEntered      Callback with the completed PIN string (max 6 digits).
 * @param onDifferentUser   Navigates back to the full login screen.
 * @param isLoading         Shows a progress indicator while PIN is being validated.
 * @param errorMessage      Inline error message shown below the dot indicators.
 * @param modifier          Optional [Modifier].
 */
@Composable
fun PinLockScreen(
    currentUser: User?,
    onPinEntered: (pin: String) -> Unit,
    onDifferentUser: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    // Local pin accumulator — max 6 digits
    var pin by remember { mutableStateOf("") }

    // Auto-submit when 6 digits are entered
    LaunchedEffect(pin) {
        if (pin.length == 6) {
            onPinEntered(pin)
            pin = "" // clear for retry if error occurs
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ZyntaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── User avatar + name ────────────────────────────────────────────
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(ZyntaSpacing.md))

            Text(
                text = currentUser?.name ?: "Unknown User",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Enter your PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(ZyntaSpacing.xl))

            // ── PIN dot indicators ────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                repeat(6) { index ->
                    Surface(
                        shape = CircleShape,
                        color = if (index < pin.length)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(14.dp),
                    ) {}
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.xl))

            // ── ZyntaNumericPad in PIN mode ───────────────────────────────────
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                ZyntaNumericPad(
                    mode = NumericPadMode.PIN,
                    displayValue = pin,
                    onDigit = { digit ->
                        if (pin.length < 6) pin += digit
                    },
                    onDoubleZero = { /* disabled in PIN mode */ },
                    onDecimal = { /* disabled in PIN mode */ },
                    onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onClear = { pin = "" },
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.lg))

            // Submit button (also handles 4-digit PINs explicitly)
            if (pin.length in 4..6 && !isLoading) {
                ZyntaButton(
                    text = "Unlock",
                    onClick = { onPinEntered(pin); pin = "" },
                    size = ZyntaButtonSize.Large,
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                )
                Spacer(Modifier.height(ZyntaSpacing.md))
            }

            // ── Different user link ───────────────────────────────────────────
            TextButton(onClick = onDifferentUser) {
                Text(
                    text = "Different user? Sign in with password",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
