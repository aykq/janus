/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.split

import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader

object ConfigAppMigration {
    suspend fun migrate(config: Config): Config? {
        val stripped = ConfigAppLines.strip(config.toAwgQuickString())
        if (!stripped.hasApps) return null
        SplitStore.importApps(stripped.excluded, stripped.included)
        return Config.parse(BufferedReader(StringReader(stripped.text)))
    }
}
