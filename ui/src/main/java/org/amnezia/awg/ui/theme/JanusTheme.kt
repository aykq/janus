/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val JanusLight = lightColorScheme(
    primary = Color(0xFF4355B9),
    secondary = Color(0xFF5B5D72),
    tertiary = Color(0xFF77536D),
)

private val JanusDark = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    secondary = Color(0xFFC4C5DD),
    tertiary = Color(0xFFE6BAD7),
)

@Composable
fun JanusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> JanusDark
        else -> JanusLight
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
