package io.neoterm.customize.pm

import io.neoterm.R
import io.neoterm.preference.NeoPreference
import io.neoterm.preference.NeoTermPath
import java.io.File
import java.net.URL

/**
 * @author kiva
 */
object NeoPackageManagerUtils {

    fun detectSourceFiles(): ArrayList<File> {
        val sourceFiles = ArrayList<File>()
        val sourceUrl = NeoPreference.loadString(R.string.key_package_source, NeoTermPath.DEFAULT_SOURCE)
        val packageFilePrefix = detectSourceFilePrefix(sourceUrl)
        if (packageFilePrefix.isNotEmpty()) {
            File(NeoTermPath.PACKAGE_LIST_DIR)
                    .listFiles()
                    .filterTo(sourceFiles) { it.name.startsWith(packageFilePrefix) }
        }
        return sourceFiles
    }

    fun detectSourceFilePrefix(sourceUrl: String): String {
        try {
            val url = URL(sourceUrl)
            val builder = StringBuilder()
            builder.append(url.host)
            if (url.path != null && url.path.isNotEmpty()) {
                builder.append("_")
                builder.append(url.path.substring(1)) // Skip '/'
            }
            builder.append("_dists_stable_main_binary-")
            return builder.toString()
        } catch (e: Exception) {
            return ""
        }
    }
}