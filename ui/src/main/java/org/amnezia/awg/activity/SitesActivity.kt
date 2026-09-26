/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.activity

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.split.PrivateDns
import org.amnezia.awg.split.SiteStore

/**
 * Edits the global list of sites/IPs that bypass the tunnel, plus DNS options.
 */
class SitesActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var enabled: SwitchMaterial
    private lateinit var input: TextInputEditText
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private lateinit var dohUrl: TextInputEditText
    private lateinit var privateDnsBypass: SwitchMaterial
    private lateinit var privateDnsHost: TextInputEditText
    private lateinit var privateDnsDetected: TextView
    private var textFieldsFilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sites)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        root = findViewById(R.id.sites_root)
        enabled = findViewById(R.id.sites_enabled)
        input = findViewById(R.id.site_input)
        list = findViewById(R.id.sites_list)
        empty = findViewById(R.id.sites_empty)
        dohUrl = findViewById(R.id.doh_url)
        privateDnsBypass = findViewById(R.id.private_dns_bypass)
        privateDnsHost = findViewById(R.id.private_dns_host)
        privateDnsDetected = findViewById(R.id.private_dns_detected)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            findViewById<View>(R.id.sites_api_warning).visibility = View.VISIBLE

        findViewById<MaterialButton>(R.id.site_add).setOnClickListener { addSite() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSite()
                true
            } else false
        }
        enabled.setOnClickListener {
            lifecycleScope.launch { SiteStore.setEnabled(enabled.isChecked) }
        }
        privateDnsBypass.setOnClickListener {
            lifecycleScope.launch { SiteStore.setPrivateDnsBypass(privateDnsBypass.isChecked) }
        }
        findViewById<MaterialButton>(R.id.sites_apply).setOnClickListener { saveAndApply() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SiteStore.settings.collect { render(it) }
            }
        }
    }

    private fun render(settings: SiteStore.Settings) {
        enabled.isChecked = settings.enabled
        privateDnsBypass.isChecked = settings.privateDnsBypass
        if (!textFieldsFilled) {
            dohUrl.setText(settings.dohUrl)
            privateDnsHost.setText(settings.privateDnsHost)
            textFieldsFilled = true
        }
        val detected = PrivateDns.detectHost(this) ?: settings.lastDetectedPrivateDns
        privateDnsDetected.text = if (detected.isBlank())
            getString(R.string.sites_private_dns_not_detected)
        else
            getString(R.string.sites_private_dns_detected, detected)

        list.removeAllViews()
        empty.visibility = if (settings.sites.isEmpty()) View.VISIBLE else View.GONE
        for (entry in settings.sites)
            list.addView(siteRow(entry, cachedCount(settings, entry)))
    }

    private fun cachedCount(settings: SiteStore.Settings, entry: String): Int? {
        if (SiteStore.isNetwork(entry)) return null
        return ((settings.cache[entry] ?: emptyList()) + (settings.cache["www.$entry"] ?: emptyList()))
            .distinct().size
    }

    private fun siteRow(entry: String, ipCount: Int?): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (48 * density).toInt()
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = entry
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        texts.addView(TextView(this).apply {
            text = when (ipCount) {
                null -> getString(R.string.sites_entry_network)
                0 -> getString(R.string.sites_entry_unresolved)
                else -> resources.getQuantityString(R.plurals.sites_entry_ips, ipCount, ipCount)
            }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_action_delete)
            contentDescription = getString(R.string.sites_delete)
            background = null
            setOnClickListener { confirmRemove(entry) }
        })
        return row
    }

    private fun addSite() {
        val raw = input.text?.toString().orEmpty()
        if (raw.isBlank()) return
        lifecycleScope.launch {
            val entry = SiteStore.addSite(raw)
            if (entry == null) {
                Snackbar.make(root, getString(R.string.sites_invalid, raw), Snackbar.LENGTH_LONG).show()
            } else {
                input.setText("")
                Snackbar.make(root, getString(R.string.sites_added, entry), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRemove(entry: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.sites_remove_confirm, entry))
            .setPositiveButton(R.string.sites_delete) { _, _ -> lifecycleScope.launch { SiteStore.removeSite(entry) } }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveAndApply() {
        lifecycleScope.launch {
            val url = dohUrl.text?.toString().orEmpty().trim()
            if (url.isNotEmpty() && !url.startsWith("https://")) {
                Snackbar.make(root, R.string.sites_doh_invalid, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            SiteStore.setDohUrl(url)
            SiteStore.setPrivateDnsHost(privateDnsHost.text?.toString().orEmpty())

            val active = Application.getTunnelManager().getTunnels().filter { it.state == Tunnel.State.UP }
            for (tunnel in active) {
                try {
                    tunnel.setStateAsync(Tunnel.State.DOWN)
                    delay(300)
                    tunnel.setStateAsync(Tunnel.State.UP)
                } catch (e: Throwable) {
                    Snackbar.make(root, e.message ?: e.toString(), Snackbar.LENGTH_LONG).show()
                    return@launch
                }
            }
            val message = if (active.isEmpty()) R.string.sites_saved else R.string.sites_applied
            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
