package io.github.sds100.keymapper.util

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES.O_MR1
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import io.github.sds100.keymapper.Constants
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.WidgetsManager
import io.github.sds100.keymapper.compatibleIme
import io.github.sds100.keymapper.data.AppPreferences
import io.github.sds100.keymapper.data.model.CompatibleImeListItemModel
import io.github.sds100.keymapper.databinding.FragmentSelectInputMethodBinding
import io.github.sds100.keymapper.util.PermissionUtils.isPermissionGranted
import io.github.sds100.keymapper.util.result.*
import splitties.alertdialog.appcompat.alertDialog
import splitties.alertdialog.appcompat.negativeButton
import splitties.alertdialog.appcompat.okButton
import splitties.init.appCtx
import splitties.systemservices.inputMethodManager
import splitties.toast.toast
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Created by sds100 on 28/12/2018.
 */

object KeyboardUtils {
    //DON'T CHANGE THESE!!!
    const val KEY_MAPPER_INPUT_METHOD_ACTION_INPUT_DOWN_UP = "io.github.sds100.keymapper.inputmethod.ACTION_INPUT_DOWN_UP"
    const val KEY_MAPPER_INPUT_METHOD_ACTION_INPUT_DOWN = "io.github.sds100.keymapper.inputmethod.ACTION_INPUT_DOWN"
    const val KEY_MAPPER_INPUT_METHOD_ACTION_INPUT_UP = "io.github.sds100.keymapper.inputmethod.ACTION_INPUT_UP"
    const val KEY_MAPPER_INPUT_METHOD_ACTION_TEXT = "io.github.sds100.keymapper.inputmethod.ACTION_INPUT_TEXT"

    const val KEY_MAPPER_INPUT_METHOD_EXTRA_KEYCODE = "io.github.sds100.keymapper.inputmethod.EXTRA_KEYCODE"
    const val KEY_MAPPER_INPUT_METHOD_EXTRA_METASTATE = "io.github.sds100.keymapper.inputmethod.EXTRA_METASTATE"
    const val KEY_MAPPER_INPUT_METHOD_EXTRA_TEXT = "io.github.sds100.keymapper.inputmethod.EXTRA_TEXT"

    private const val KEY_MAPPER_GUI_IME_PACKAGE = "io.github.sds100.keymapper.inputmethod.latin"
    private const val KEY_MAPPER_GUI_IME_MIN_API = Build.VERSION_CODES.KITKAT

    var selectedImePackageName: String
        get() {
            var packageName = appCtx.defaultSharedPreferences.getString(
                appCtx.str(R.string.key_pref_selected_compatible_ime_package_name), Constants.PACKAGE_NAME
            ) ?: Constants.PACKAGE_NAME

            if (!PackageUtils.isAppInstalled(packageName)) {
                packageName = Constants.PACKAGE_NAME
                selectedImePackageName = packageName
            }

            return packageName
        }
        set(value) {
            appCtx.defaultSharedPreferences.edit {
                putString(appCtx.str(R.string.key_pref_selected_compatible_ime_package_name), value)
                apply()
            }
        }

    suspend fun enableSelectedIme(activity: FragmentActivity) {

        if (!AppPreferences.approvedSelectCompatibleImePrompt) {
            selectCompatibleIme(activity)
        }

        if (isPermissionGranted(Constants.PERMISSION_ROOT)) {
            enableSelectedImeRoot()
        } else {
            openImeSettings()
        }
    }

    fun enableSelectedImeRoot() {
        getImeId(selectedImePackageName).onSuccess {
            RootUtils.executeRootCommand("ime enable $it")
        }
    }

    suspend fun chooseSelectedIme(activity: FragmentActivity) {
        if (!AppPreferences.approvedSelectCompatibleImePrompt) {
            selectCompatibleIme(activity)
        }

        if (isPermissionGranted(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            getImeId(selectedImePackageName).onSuccess {
                switchIme(activity, it)
            }
        } else {
            showInputMethodPicker()
        }
    }

    /**
     * @return whether the ime was changed successfully
     */
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    fun switchIme(ctx: Context, imeId: String): Boolean {
        if (!isPermissionGranted(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            ctx.toast(R.string.error_need_write_secure_settings_permission)
            return false
        }

        ctx.putSecureSetting(Settings.Secure.DEFAULT_INPUT_METHOD, imeId)
        return true
    }

    fun showInputMethodPicker() {
        inputMethodManager.showInputMethodPicker()
    }

    fun showInputMethodPickerDialogOutsideApp() {
        /* Android 8.1 and higher don't seem to allow you to open the input method picker dialog
             * from outside the app :( but it can be achieved by sending a broadcast with a
             * system process id (requires root access) */

        if (Build.VERSION.SDK_INT < O_MR1) {
            inputMethodManager.showInputMethodPicker()
        } else if ((O_MR1..Build.VERSION_CODES.P).contains(Build.VERSION.SDK_INT)) {
            val command = "am broadcast -a com.android.server.InputMethodManagerService.SHOW_INPUT_METHOD_PICKER"
            RootUtils.executeRootCommand(command)
        } else {
            appCtx.toast(R.string.error_this_is_unsupported)
        }
    }

    fun getInputMethodLabel(id: String): Result<String> {
        val label = inputMethodManager.enabledInputMethodList.find { it.id == id }
            ?.loadLabel(appCtx.packageManager)?.toString() ?: return InputMethodNotFound(id)

        return Success(label)
    }

    fun getInputMethodIds(): Result<List<String>> {
        if (inputMethodManager.enabledInputMethodList.isEmpty()) {
            return NoEnabledInputMethods()
        }

        return Success(inputMethodManager.enabledInputMethodList.map { it.id })
    }

    fun inputMethodExists(imeId: String): Boolean = getInputMethodIds().handle(
        onSuccess = { it.contains(imeId) },
        onFailure = { false }
    )

    fun openImeSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK

            appCtx.startActivity(intent)
        } catch (e: Exception) {
            toast(R.string.error_cant_find_ime_settings)
        }
    }

    fun inputTextFromImeService(text: String) {
        Intent(KEY_MAPPER_INPUT_METHOD_ACTION_TEXT).apply {
            setPackage(selectedImePackageName)

            putExtra(KEY_MAPPER_INPUT_METHOD_EXTRA_TEXT, text)
            appCtx.sendBroadcast(this)
        }
    }

    fun inputKeyEventFromImeService(
        keyCode: Int,
        metaState: Int = 0,
        keyEventAction: KeyEventAction = KeyEventAction.DOWN_UP
    ) {
        val intentAction = when (keyEventAction) {
            KeyEventAction.DOWN -> KEY_MAPPER_INPUT_METHOD_ACTION_INPUT_DOWN
            KeyEventAction.DOWN_UP -> KEY_MAPPER_INPUT_METHOD_ACTION_INPUT_DOWN_UP
            KeyEventAction.UP -> KEY_MAPPER_INPUT_METHOD_ACTION_INPUT_UP
        }

        Intent(intentAction).apply {
            setPackage(selectedImePackageName)
            putExtra(KEY_MAPPER_INPUT_METHOD_EXTRA_KEYCODE, keyCode)
            putExtra(KEY_MAPPER_INPUT_METHOD_EXTRA_METASTATE, metaState)

            appCtx.sendBroadcast(this)
        }
    }

    fun getChosenImeId(ctx: Context): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }


    fun toggleSelectedCompatibleIme(ctx: Context) {
        if (!isSelectedImeEnabled()) {
            ctx.toast(R.string.error_ime_service_disabled)
            return
        }

        val imeId: String?

        if (isSelectedImeChosen()) {
            imeId = AppPreferences.defaultIme

        } else {
            AppPreferences.defaultIme = getChosenImeId(ctx)

            imeId = getImeId(selectedImePackageName).valueOrNull()
        }

        imeId ?: return

        //only show the toast message if it is successful
        if (switchIme(ctx, imeId)) {
            getInputMethodLabel(imeId).onSuccess { imeLabel ->
                toast(ctx.str(R.string.toast_chose_keyboard, imeLabel))
            }
        }
    }

    fun getImeId(packageName: String): Result<String> {
        val inputMethod = inputMethodManager.inputMethodList.find { it.packageName == packageName }
            ?: return KeyMapperImeNotFound()

        return Success(inputMethod.id)
    }

    fun isSelectedImeEnabled(): Boolean {
        val enabledMethods = inputMethodManager.enabledInputMethodList ?: return false

        return enabledMethods.any { it.packageName == selectedImePackageName }
    }

    fun isSelectedImeChosen(): Boolean {
        //get the current input input_method
        val chosenImeId = Settings.Secure.getString(
            appCtx.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )

        val chosenImePackageName =
            inputMethodManager.inputMethodList.find { it.id == chosenImeId }?.packageName

        return chosenImePackageName == selectedImePackageName
    }

    suspend fun selectCompatibleIme(activity: FragmentActivity, showDontShowAgainButton: Boolean = true) =
        suspendCoroutine<Unit> { block ->
            activity.apply {
                alertDialog {
                    FragmentSelectInputMethodBinding.inflate(layoutInflater).apply {

                        val callback = object : OpenUrlCallback {
                            override fun openUrl(url: String) = context.openUrl(url)
                        }

                        val models = arrayOf(
                            CompatibleImeListItemModel.build(
                                ctx = activity,
                                packageName = Constants.PACKAGE_NAME,
                                imeName = str(R.string.ime_service_label),
                                description = str(R.string.ime_key_mapper_description)
                            ),
                            CompatibleImeListItemModel.build(
                                ctx = activity,
                                packageName = KEY_MAPPER_GUI_IME_PACKAGE,
                                minApi = KEY_MAPPER_GUI_IME_MIN_API,
                                imeName = str(R.string.ime_key_mapper_gui_name),
                                description = str(R.string.ime_key_mapper_gui_description),
                                playStoreLink = str(R.string.url_play_store_keymapper_gui_keyboard),
                                githubLink = str(R.string.url_github_keymapper_gui_keyboard),
                                fdroidLink = str(R.string.url_fdroid_keymapper_gui_keyboard)
                            )
                        )

                        epoxyRecyclerView.withModels {

                            models.forEach {
                                compatibleIme {
                                    id(it.packageName)
                                    model(it)
                                    openUrlCallback(callback)

                                    onClick { _ ->
                                        if (it.isSupported) {
                                            selectedImePackageName = it.packageName
                                        }

                                        epoxyRecyclerView.requestModelBuild()
                                    }

                                    isSelected(selectedImePackageName == it.packageName)
                                }
                            }
                        }


                        if (showDontShowAgainButton) {
                            negativeButton(R.string.neg_dont_show_again) {
                                AppPreferences.approvedSelectCompatibleImePrompt = true
                                block.resume(Unit)
                            }
                        }

                        okButton {
                            block.resume(Unit)
                        }

                        setView(this.root)
                    }
                }.show()
            }
        }
}

@RequiresApi(Build.VERSION_CODES.N)
fun AccessibilityService.SoftKeyboardController.hide(ctx: Context) {
    showMode = AccessibilityService.SHOW_MODE_HIDDEN
    WidgetsManager.onEvent(ctx, WidgetsManager.EVENT_HIDE_KEYBOARD)
}

@RequiresApi(Build.VERSION_CODES.N)
fun AccessibilityService.SoftKeyboardController.show(ctx: Context) {
    showMode = AccessibilityService.SHOW_MODE_AUTO
    WidgetsManager.onEvent(ctx, WidgetsManager.EVENT_SHOW_KEYBOARD)
}

@RequiresApi(Build.VERSION_CODES.N)
fun AccessibilityService.SoftKeyboardController.toggle(ctx: Context) {
    if (showMode == AccessibilityService.SHOW_MODE_HIDDEN) {
        show(ctx)
    } else {
        hide(ctx)
    }
}