package io.neoterm.view.eks

import io.neoterm.customize.eks.EksConfigParser
import io.neoterm.preference.NeoTermPath
import io.neoterm.utils.FileUtils
import java.io.File

/**
 * @author kiva
 */
object ExtraKeysUtils {
    fun generateDefaultFile(defaultFile: File) {
        val DEFAULT_FILE_CONTENT = "version " + EksConfigParser.PARSER_VERSION + "\n" +
                "program default\n" +
                "define - false\n" +
                "define / false\n" +
                "define \\ false\n" +
                "define | false\n" +
                "define $ false\n" +
                "define < false\n" +
                "define > false\n"
        FileUtils.writeFile(defaultFile, DEFAULT_FILE_CONTENT.toByteArray())
    }

    fun getDefaultFile(): File {
        val defaultFile = File(NeoTermPath.EKS_DEFAULT_FILE)
        if (!defaultFile.exists()) {
            generateDefaultFile(defaultFile)
        }
        return defaultFile
    }
}