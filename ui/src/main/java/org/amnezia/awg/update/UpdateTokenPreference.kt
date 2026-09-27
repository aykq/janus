/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.amnezia.awg.R
import org.amnezia.awg.util.lifecycleScope

class UpdateTokenPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var tokenSet = false

    override fun getSummary(): CharSequence =
        context.getString(if (tokenSet) R.string.update_token_summary_set else R.string.update_token_summary_unset)

    init {
        lifecycleScope.launch {
            tokenSet = TokenStore.isSet()
            notifyChanged()
        }
    }

    override fun onClick() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = context.getString(R.string.update_token_hint)
            isSingleLine = true
        }
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val container = FrameLayout(context).apply { setPadding(padding, 0, padding, 0); addView(input) }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_token_title)
            .setMessage(R.string.update_token_message)
            .setView(container)
            .setPositiveButton(R.string.update_token_save) { _, _ -> save(input.text.toString()) }
            .setNeutralButton(R.string.update_token_clear) { _, _ -> save(null) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save(value: String?) {
        lifecycleScope.launch {
            TokenStore.set(value)
            tokenSet = TokenStore.isSet()
            notifyChanged()
            if (tokenSet) {
                val status = try {
                    UpdateChecker.check(context.applicationContext, notify = false, force = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    CheckStatus.Failed(e.message ?: e.javaClass.simpleName)
                }
                Toast.makeText(context, UpdateStatusText.describe(context, status.kind, status.arg), Toast.LENGTH_LONG).show()
            }
        }
    }
}
