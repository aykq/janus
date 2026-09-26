/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.fragment.TunnelEditorFragment
import org.amnezia.awg.model.ObservableTunnel

class TunnelEditActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) finish()
        }
        if (savedInstanceState != null) return
        val name = intent.getStringExtra(EXTRA_TUNNEL) ?: return finish()
        lifecycleScope.launch {
            val tunnel = Application.getTunnelManager().getTunnels()[name] ?: return@launch finish()
            // The editor reads selectedTunnel when its view is restored, so select before adding it.
            selectedTunnel = tunnel
            supportFragmentManager.commit {
                add(android.R.id.content, TunnelEditorFragment())
                addToBackStack(null)
            }
        }
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) = true

    companion object {
        private const val EXTRA_TUNNEL = "janus.tunnel"

        fun intent(context: Context, tunnelName: String) =
            Intent(context, TunnelEditActivity::class.java).putExtra(EXTRA_TUNNEL, tunnelName)
    }
}
