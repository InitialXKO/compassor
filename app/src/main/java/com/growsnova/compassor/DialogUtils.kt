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
     * Shows a Material Design 3 options dialog
     */
    fun showOptionsDialog(
        context: Context,
        title: String,
        options: Array<String>,
        onOptionSelected: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(options) { _, which ->
                onOptionSelected(which)
            }
            .show()
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