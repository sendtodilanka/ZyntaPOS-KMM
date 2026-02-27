package com.zyntasolutions.zyntapos.security.license

import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import com.zyntasolutions.zyntapos.security.rbac.RbacEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Combines feature-registry state (is the module activated?) with RBAC permission checks
 * (does the current user have the required permissions?) to determine whether a given
 * [ZyntaFeature] is accessible to a specific [User].
 *
 * Use [canAccess] in navigation guards and screen-level gating composables.
 */
class LicenseManager(
    private val featureRepo: FeatureRegistryRepository,
    private val rbacEngine: RbacEngine,
) {
    /**
     * Emits `true` only when the [feature] is both:
     * 1. Enabled in the feature registry (`FeatureConfig.isEnabled == true`), AND
     * 2. The [user] holds ALL of [ZyntaFeature.requiredPermissions].
     *
     * STANDARD features always pass the enablement check (initDefaults enables them).
     */
    fun canAccess(feature: ZyntaFeature, user: User): Flow<Boolean> =
        featureRepo.observe(feature).map { config ->
            config.isEnabled && feature.requiredPermissions.all { perm ->
                rbacEngine.hasPermission(user, perm)
            }
        }

    /**
     * Returns `true` synchronously if [feature] is STANDARD (always accessible
     * regardless of registry state — provides a fast-path for compile-time known features).
     */
    fun isAlwaysAccessible(feature: ZyntaFeature): Boolean =
        feature.edition == ZyntaEdition.STANDARD
}
