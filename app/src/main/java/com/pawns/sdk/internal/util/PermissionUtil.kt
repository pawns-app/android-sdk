package com.pawns.sdk.internal.util

import android.content.Context
import android.content.pm.PackageManager

internal object PermissionUtil {

    internal fun hasPermissionInManifest(context: Context, permission: String): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = packageInfo.requestedPermissions

        if (permissions.isNullOrEmpty())
            return false

        return permissions.any { it == permission }
    }
}