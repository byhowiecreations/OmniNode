package com.fileapex.platform

/**
 * Shared on-disk locations for the macOS main app and Finder/Share extensions.
 * Uses Application Support (not App Groups) so ad-hoc builds can share the roster DB.
 */
object MacOsSharedPaths {
    const val BUNDLE_ID = "com.fileapex"
    const val DATABASE_FILE_NAME = "fileapex.db"
}
