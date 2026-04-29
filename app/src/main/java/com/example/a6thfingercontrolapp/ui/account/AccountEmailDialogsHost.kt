package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.ui.account.dialogs.EmailAddDialog
import com.example.a6thfingercontrolapp.ui.account.dialogs.EmailChangeDialog
import com.example.a6thfingercontrolapp.ui.account.dialogs.EmailRemoveDialog
import com.example.a6thfingercontrolapp.utils.FeatureFlags
import kotlinx.coroutines.launch

/**
 * Account email buttons UI state.
 */
@Composable
internal fun AccountEmailDialogsHost(
    state: AccountEmailUiState,
    authVm: AuthViewModel
) {
    if (!FeatureFlags.isEmailEnabled) return

    val scope = rememberCoroutineScope()

    if (state.emailDialogMode == EmailDialogMode.Add) {
        EmailAddDialog(
            step = state.addStep,
            email = state.addEmail,
            code = state.addCode,
            errorKey = state.addErrKey,
            busy = state.addBusy,
            onDismiss = { state.emailDialogMode = EmailDialogMode.None },
            onEmailChange = { state.addEmail = it.trim() },
            onCodeChange = { state.addCode = it.trim() },
            onStartAdd = {
                state.addBusy = true
                state.addErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartAdd(state.addEmail)
                        state.addCode = ""
                        state.addStep = AddStep.EnterCode
                    } catch (e: Exception) {
                        state.addErrKey = e.message
                    } finally {
                        state.addBusy = false
                    }
                }
            },
            onResend = {
                state.addBusy = true
                state.addErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartAdd(state.addEmail)
                    } catch (e: Exception) {
                        state.addErrKey = e.message
                    } finally {
                        state.addBusy = false
                    }
                }
            },
            onBackToEmail = { state.addStep = AddStep.EnterEmail },
            onConfirm = {
                state.addBusy = true
                state.addErrKey = null
                scope.launch {
                    try {
                        authVm.emailConfirmAdd(state.addEmail, state.addCode)
                        state.emailDialogMode = EmailDialogMode.None
                        state.refreshEmailInfo()
                    } catch (e: Exception) {
                        state.addErrKey = e.message
                    } finally {
                        state.addBusy = false
                    }
                }
            }
        )
    }

    if (state.emailDialogMode == EmailDialogMode.Remove) {
        EmailRemoveDialog(
            step = state.removeStep,
            code = state.removeCode,
            recoveryCode = state.removeRecovery,
            errorKey = state.removeErrKey,
            busy = state.removeBusy,
            onDismiss = { state.emailDialogMode = EmailDialogMode.None },
            onCodeChange = { state.removeCode = it.trim() },
            onRecoveryCodeChange = { state.removeRecovery = it },
            onChooseEmailMethod = {
                state.removeBusy = true
                state.removeErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartRemove()
                        state.removeCode = ""
                        state.removeStep = RemoveStep.EnterEmailCode
                    } catch (e: Exception) {
                        state.removeErrKey = e.message
                    } finally {
                        state.removeBusy = false
                    }
                }
            },
            onChooseRecoveryMethod = { state.removeStep = RemoveStep.EnterRecoveryCode },
            onResend = {
                state.removeBusy = true
                state.removeErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartRemove()
                    } catch (e: Exception) {
                        state.removeErrKey = e.message
                    } finally {
                        state.removeBusy = false
                    }
                }
            },
            onBack = { state.removeStep = RemoveStep.ChooseMethod },
            onConfirm = {
                state.removeBusy = true
                state.removeErrKey = null
                scope.launch {
                    try {
                        when (state.removeStep) {
                            RemoveStep.EnterEmailCode ->
                                authVm.emailConfirmRemove(
                                    code = state.removeCode,
                                    recoveryCode = null
                                )

                            RemoveStep.EnterRecoveryCode ->
                                authVm.emailConfirmRemove(
                                    code = null,
                                    recoveryCode = state.removeRecovery
                                )

                            else -> Unit
                        }

                        state.emailDialogMode = EmailDialogMode.None
                        state.refreshEmailInfo()
                    } catch (e: Exception) {
                        state.removeErrKey = e.message
                    } finally {
                        state.removeBusy = false
                    }
                }
            }
        )
    }

    if (state.emailDialogMode == EmailDialogMode.Change) {
        EmailChangeDialog(
            step = state.changeStep,
            oldCode = state.changeOldCode,
            oldRecoveryCode = state.changeOldRecovery,
            newEmail = state.changeNewEmail,
            newCode = state.changeNewCode,
            errorKey = state.changeErrKey,
            busy = state.changeBusy,
            onDismiss = { state.emailDialogMode = EmailDialogMode.None },
            onOldCodeChange = { state.changeOldCode = it.trim() },
            onOldRecoveryCodeChange = { state.changeOldRecovery = it },
            onNewEmailChange = { state.changeNewEmail = it.trim() },
            onNewCodeChange = { state.changeNewCode = it.trim() },
            onChooseOldEmailMethod = {
                state.changeBusy = true
                state.changeErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartRemove()
                        state.changeOldCode = ""
                        state.changeStep = ChangeStep.EnterOldEmailCode
                    } catch (e: Exception) {
                        state.changeErrKey = e.message
                    } finally {
                        state.changeBusy = false
                    }
                }
            },
            onChooseOldRecoveryMethod = { state.changeStep = ChangeStep.EnterOldRecoveryCode },
            onResendOldCode = {
                state.changeBusy = true
                state.changeErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartRemove()
                    } catch (e: Exception) {
                        state.changeErrKey = e.message
                    } finally {
                        state.changeBusy = false
                    }
                }
            },
            onResendNewCode = {
                state.changeBusy = true
                state.changeErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartAdd(state.changeNewEmail)
                    } catch (e: Exception) {
                        state.changeErrKey = e.message
                    } finally {
                        state.changeBusy = false
                    }
                }
            },
            onBackToOldMethod = { state.changeStep = ChangeStep.ChooseOldMethod },
            onBackToNewEmail = { state.changeStep = ChangeStep.EnterNewEmail },
            onStartNewEmail = {
                state.changeBusy = true
                state.changeErrKey = null
                scope.launch {
                    try {
                        authVm.emailStartAdd(state.changeNewEmail)
                        state.changeNewCode = ""
                        state.changeStep = ChangeStep.EnterNewEmailCode
                    } catch (e: Exception) {
                        state.changeErrKey = e.message
                    } finally {
                        state.changeBusy = false
                    }
                }
            },
            onConfirm = {
                state.changeBusy = true
                state.changeErrKey = null
                scope.launch {
                    try {
                        when (state.changeStep) {
                            ChangeStep.EnterOldEmailCode -> {
                                authVm.emailConfirmRemove(
                                    code = state.changeOldCode,
                                    recoveryCode = null
                                )
                                state.changeStep = ChangeStep.EnterNewEmail
                            }

                            ChangeStep.EnterOldRecoveryCode -> {
                                authVm.emailConfirmRemove(
                                    code = null,
                                    recoveryCode = state.changeOldRecovery
                                )
                                state.changeStep = ChangeStep.EnterNewEmail
                            }

                            ChangeStep.EnterNewEmailCode -> {
                                authVm.emailConfirmAdd(state.changeNewEmail, state.changeNewCode)
                                state.emailDialogMode = EmailDialogMode.None
                                state.refreshEmailInfo()
                            }

                            else -> Unit
                        }
                    } catch (e: Exception) {
                        state.changeErrKey = e.message
                    } finally {
                        state.changeBusy = false
                    }
                }
            }
        )
    }
}
