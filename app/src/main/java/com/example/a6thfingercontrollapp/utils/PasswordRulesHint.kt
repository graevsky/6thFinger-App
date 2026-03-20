package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.utils.PasswordPolicy

@Composable
fun PasswordRulesHint(rules: PasswordPolicy.Result) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.password_rules_title),
            style = MaterialTheme.typography.bodySmall
        )
        RuleLine(ok = rules.minLen, text = stringResource(R.string.password_rule_len))
        RuleLine(ok = rules.hasUpper, text = stringResource(R.string.password_rule_upper))
        RuleLine(ok = rules.hasLower, text = stringResource(R.string.password_rule_lower))
        RuleLine(ok = rules.hasDigit, text = stringResource(R.string.password_rule_digit))
        RuleLine(ok = rules.hasSpecial, text = stringResource(R.string.password_rule_special))
    }
}

@Composable
private fun RuleLine(ok: Boolean, text: String) {
    val prefix = if (ok) "✓ " else "• "
    Text(prefix + text, style = MaterialTheme.typography.bodySmall)
}