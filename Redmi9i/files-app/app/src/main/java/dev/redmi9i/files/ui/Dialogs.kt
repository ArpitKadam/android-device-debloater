package dev.redmi9i.files.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import dev.redmi9i.files.ops.Conflict

object Dialogs {
    fun confirm(ctx: Context, title: String, message: String, positive: String, onYes: () -> Unit) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(message)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null).show()
    }

    fun prompt(ctx: Context, title: String, initial: String, positive: String, onText: (String) -> Unit) {
        val input = EditText(ctx).apply {
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(0, initial.substringBeforeLast('.', initial).length)
        }
        AlertDialog.Builder(ctx).setTitle(title).setView(input)
            .setPositiveButton(positive) { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty() && !t.contains('/')) onText(t)
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun conflictPolicy(ctx: Context, count: Int, onChoice: (Conflict) -> Unit) {
        val labels = arrayOf("Keep both", "Replace", "Skip")
        val values = arrayOf(Conflict.KEEP_BOTH, Conflict.REPLACE, Conflict.SKIP)
        AlertDialog.Builder(ctx).setTitle("$count item(s) already exist here")
            .setItems(labels) { _, i -> onChoice(values[i]) }
            .setNegativeButton("Cancel", null).show()
    }

    fun info(ctx: Context, title: String, message: String) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
