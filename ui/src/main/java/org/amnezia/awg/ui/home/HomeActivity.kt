/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.amnezia.awg.ui.theme.JanusTheme

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JanusTheme {
                HomeRoute()
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun HomeRoute() {
    androidx.compose.material3.Text("Janus")
}
