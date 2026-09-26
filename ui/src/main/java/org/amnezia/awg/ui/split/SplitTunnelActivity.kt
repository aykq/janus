/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.split

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.amnezia.awg.ui.theme.JanusTheme

class SplitTunnelActivity : ComponentActivity() {
    private val viewModel: SplitTunnelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JanusTheme {
                SplitTunnelScreen(viewModel, onBack = ::finish)
            }
        }
    }
}
