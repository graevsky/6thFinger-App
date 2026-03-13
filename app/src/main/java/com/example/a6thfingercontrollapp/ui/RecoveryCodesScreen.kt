package com.example.a6thfingercontrollapp.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.R

@Composable
fun RecoveryCodesScreen(
    username: String,
    codes: List<String>,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var savedChecked by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    val fileUsernameLine = remember(username) {
        context.getString(R.string.recovery_codes_file_username, username)
    }
    val fileCodesTitle = context.getString(R.string.recovery_codes_file_codes_title)

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        val content = buildString {
            appendLine(fileUsernameLine)
            appendLine()
            appendLine(fileCodesTitle)
            codes.forEach { appendLine(it) }
        }

        val ok = writeTextToUri(context, uri, content)
        info = if (ok) context.getString(R.string.recovery_codes_saved_file_ok)
        else context.getString(R.string.recovery_codes_saved_file_fail)
    }

    val contentForCopy = remember(codes) { codes.joinToString("\n") }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.recovery_codes_title),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = stringResource(R.string.recovery_codes_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        codes.forEach { c ->
                            Text(
                                text = c,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboard.setText(AnnotatedString(contentForCopy))
                            info = context.getString(R.string.recovery_codes_copied)
                        }
                    ) { Text(stringResource(R.string.recovery_codes_copy)) }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { createDocLauncher.launch("recovery_codes_$username.txt") }
                    ) { Text(stringResource(R.string.recovery_codes_download)) }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = savedChecked,
                            onValueChange = { savedChecked = it }
                        )
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = savedChecked, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.recovery_codes_saved_checkbox),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = stringResource(R.string.recovery_codes_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                if (!info.isNullOrBlank()) {
                    Text(info ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.auth_back)) }

                Button(
                    onClick = onContinue,
                    enabled = savedChecked
                ) { Text(stringResource(R.string.recovery_codes_continue)) }
            }
        }
    }
}

private fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(text.toByteArray(Charsets.UTF_8))
            os.flush()
        }
        true
    } catch (_: Exception) {
        false
    }
}