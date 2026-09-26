/*
 * Copyright © 2026 AykQ. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.backend;

import org.amnezia.awg.util.NonNullForAll;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;

@NonNullForAll
public final class SplitPlan {
    public static final SplitPlan EMPTY = new SplitPlan(Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), null);

    private final Set<String> excludedApps;
    private final Set<String> includedApps;
    private final List<String> excludedRoutes;
    @Nullable private final List<String> includedRoutes;

    public SplitPlan(final Set<String> excludedApps, final Set<String> includedApps,
                     final List<String> excludedRoutes, @Nullable final List<String> includedRoutes) {
        this.excludedApps = Collections.unmodifiableSet(new LinkedHashSet<>(excludedApps));
        this.includedApps = Collections.unmodifiableSet(new LinkedHashSet<>(includedApps));
        this.excludedRoutes = Collections.unmodifiableList(excludedRoutes);
        this.includedRoutes = includedRoutes == null ? null : Collections.unmodifiableList(includedRoutes);
    }

    public Set<String> getExcludedApps() { return excludedApps; }

    public Set<String> getIncludedApps() { return includedApps; }

    public List<String> getExcludedRoutes() { return excludedRoutes; }

    @Nullable
    public List<String> getIncludedRoutes() { return includedRoutes; }

    public SplitPlan merge(final Set<String> configExcludedApps, final Set<String> configIncludedApps) {
        final Set<String> included = new LinkedHashSet<>(configIncludedApps);
        included.addAll(includedApps);
        final Set<String> excluded = new LinkedHashSet<>();
        if (included.isEmpty()) {
            excluded.addAll(configExcludedApps);
            excluded.addAll(excludedApps);
        }
        return new SplitPlan(excluded, included, excludedRoutes, includedRoutes);
    }
}
