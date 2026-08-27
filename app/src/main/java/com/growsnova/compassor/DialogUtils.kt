package com.growsnova.compassor

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Utility class for creating Material Design 3 compliant dialogs
 */
object DialogUtils {

    /**
     * Shows a Material Design 3 input dialog
     */
    fun showInputDialog(
        context: Context,
        title: String,
        hint: String,
        initialValue: String = "",
        onPositive: (String) -> Unit,
        onNegative: () -> Unit = {}
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_input, null)
        val textInputLayout = view.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = view.findViewById<TextInputEditText>(R.id.editText)
        
        editText.setText(initialValue)
        textInputLayout.hint = hint
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    onPositive(input)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onNegative()
            }
            .create()

        editText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    onPositive(input)
                    dialog.dismiss()
                }
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    /**
     * Option item representation with label and optional icon resource ID.
     */
    data class OptionItem(
        val label: String,
        val iconResId: Int? = null,
        val isDestructive: Boolean = false
    )

    /**
     * Shows a standardized custom menu dialog matching waypoint options menu style (with title, divider line, icons).
     */
    fun showStandardMenuDialog(
        context: Context,
        title: String,
        options: List<OptionItem>,
        floorText: String? = null,
        onOptionSelected: (Int) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_options, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.dialogTitle)
        val floorView = view.findViewById<android.widget.TextView>(R.id.dialogFloor)
        val optionsContainer = view.findViewById<android.widget.LinearLayout>(R.id.optionsContainer)

        titleView.text = title
        if (floorText != null) {
            floorView.text = floorText
            floorView.visibility = android.view.View.VISIBLE
        } else {
            floorView.visibility = android.view.View.GONE
        }

        val alertDialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()

        options.forEachIndexed { index, option ->
            val button = com.google.android.material.button.MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                text = option.label
                option.iconResId?.let { iconRes ->
                    setIconResource(iconRes)
                    iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
                }
                if (option.isDestructive) {
                    val errorColor = getThemeColor(context, com.google.android.material.R.attr.colorError)
                    setTextColor(errorColor)
                    iconTint = android.content.res.ColorStateList.valueOf(errorColor)
                } else {
                    val primaryColor = getThemeColor(context, com.google.android.material.R.attr.colorPrimary)
                    val onSurfaceColor = getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
                    setTextColor(onSurfaceColor)
                    iconTint = android.content.res.ColorStateList.valueOf(primaryColor)
                }
                applyTouchScale()
                setOnClickListener {
                    alertDialog.dismiss()
                    onOptionSelected(index)
                }
            }
            optionsContainer.addView(button)
        }

        alertDialog.show()
    }

    private fun getThemeColor(context: Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        if (context.theme.resolveAttribute(attr, typedValue, true)) {
            return if (typedValue.resourceId != 0) {
                androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
        return android.graphics.Color.BLACK
    }

    /**
     * Shows a Material Design 3 options dialog
     */
    fun showOptionsDialog(
        context: Context,
        title: String,
        options: Array<String>,
        onOptionSelected: (Int) -> Unit
    ) {
        val optionItems = options.map { option ->
            val (iconRes, isDestructive) = when {
                option == context.getString(R.string.start_navigation) || option == context.getString(R.string.set_destination) ->
                    Pair(android.R.drawable.ic_menu_mylocation, false)
                option == context.getString(R.string.save_location) ->
                    Pair(android.R.drawable.ic_menu_save, false)
                option == context.getString(R.string.share_location) ->
                    Pair(android.R.drawable.ic_menu_share, false)
                option == context.getString(R.string.open_in_external_maps) ->
                    Pair(android.R.drawable.ic_menu_mapmode, false)
                option == context.getString(R.string.view_details) || option == context.getString(R.string.edit_route) ->
                    Pair(android.R.drawable.ic_menu_info_details, false)
                option == context.getString(R.string.export_route) || option == context.getString(R.string.export_waypoints) ->
                    Pair(android.R.drawable.ic_menu_upload, false)
                option == context.getString(R.string.import_waypoints) || option == context.getString(R.string.import_route) ->
                    Pair(android.R.drawable.ic_menu_add, false)
                option == context.getString(R.string.stop_navigation) || option == context.getString(R.string.delete) || option == context.getString(R.string.delete_route) ->
                    Pair(android.R.drawable.ic_menu_delete, true)
                else -> Pair(null, false)
            }
            OptionItem(label = option, iconResId = iconRes, isDestructive = isDestructive)
        }
        showStandardMenuDialog(context, title, optionItems, onOptionSelected = onOptionSelected)
    }

    /**
     * Shows a Material Design 3 confirmation dialog
     */
    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        onPositive: () -> Unit,
        onNegative: () -> Unit = {}
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                onPositive()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onNegative()
            }
            .show()
    }

    /**
     * Shows a Material Design 3 info dialog
     */
    fun showInfoDialog(
        context: Context,
        title: String,
        message: String,
        onDismiss: () -> Unit = {}
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                onDismiss()
            }
            .setOnCancelListener { onDismiss() }
            .show()
    }

    /**
     * Shows a Material Design 3 single choice dialog
     */
    fun showSingleChoiceDialog(
        context: Context,
        title: String,
        items: Array<String>,
        checkedItem: Int = -1,
        onItemSelected: (Int) -> Unit,
        onPositive: () -> Unit,
        onNegative: () -> Unit = {}
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(items, checkedItem) { _, which ->
                onItemSelected(which)
            }
            .setPositiveButton(R.string.confirm) { _, _ ->
                onPositive()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onNegative()
            }
            .show()
    }

    /**
     * Shows a Material Design 3 list dialog
     */
    fun showListDialog(
        context: Context,
        title: String,
        items: Array<String>,
        onItemSelected: (Int) -> Unit,
        positiveButtonText: Int? = null,
        onPositive: (() -> Unit)? = null,
        neutralButtonText: Int? = null,
        onNeutral: (() -> Unit)? = null
    ) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(items) { _, which ->
                onItemSelected(which)
            }
        
        positiveButtonText?.let { text ->
            builder.setPositiveButton(text) { _, _ -> onPositive?.invoke() }
        }
        
        neutralButtonText?.let { text ->
            builder.setNeutralButton(text) { _, _ -> onNeutral?.invoke() }
        }
        
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    /**
     * Shows a simple toast message
     */
    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    /**
     * Shows a success toast
     */
    fun showSuccessToast(context: Context, message: String) {
        showToast(context, message)
    }

    /**
     * Shows an error toast
     */
    fun showErrorToast(context: Context, message: String) {
        showToast(context, message)
    }
}