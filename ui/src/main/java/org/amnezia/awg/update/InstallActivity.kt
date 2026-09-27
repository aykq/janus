/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.update

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class InstallActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_SHA256 = "sha256"
        const val EXTRA_ASSET_URL = "asset_url"

        fun intent(context: Context, release: UpdateRelease): Intent =
            Intent(context, InstallActivity::class.java)
                .putExtra(EXTRA_VERSION_CODE, release.versionCode)
                .putExtra(EXTRA_SHA256, release.sha256)
                .putExtra(EXTRA_ASSET_URL, release.assetUrl)
    }
}
