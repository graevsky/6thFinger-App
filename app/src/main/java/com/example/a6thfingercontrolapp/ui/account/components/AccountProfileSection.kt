package com.example.a6thfingercontrolapp.ui.account.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R

/**
 * Account header, avatar controls and some auth actions.
 */
@Composable
internal fun AccountProfileSection(
    username: String?,
    avatarBitmap: ImageBitmap?,
    avatarMenuOpen: Boolean,
    onAvatarMenuOpenChange: (Boolean) -> Unit,
    onAvatarOpenFullscreen: () -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val avatarSize = 180.dp

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .clickable(enabled = avatarBitmap != null) { onAvatarOpenFullscreen() }
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = stringResource(R.string.account_avatar),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_avatar_placeholder),
                    contentDescription = stringResource(R.string.account_avatar),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = { onAvatarMenuOpenChange(true) },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.avatar_change),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = avatarMenuOpen,
                    onDismissRequest = { onAvatarMenuOpenChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.avatar_choose_photo)) },
                        onClick = onPickAvatar
                    )
                    if (avatarBitmap != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.avatar_remove_photo)) },
                            onClick = onRemoveAvatar
                        )
                    }
                }
            }
        }

        Text(
            text = username ?: stringResource(R.string.auth_guest),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 12.dp)
        )

        if (username == null) {
            Text(
                text = stringResource(R.string.auth_guest_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onLoginClick
                ) { Text(stringResource(R.string.auth_login)) }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onRegisterClick
                ) { Text(stringResource(R.string.auth_register)) }
            }
        }
    }
}
