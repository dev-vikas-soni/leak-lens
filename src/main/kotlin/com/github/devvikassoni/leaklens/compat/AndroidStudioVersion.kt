package com.github.devvikassoni.leaklens.compat

/**
 * Common Android Studio version names matched against build numbers.
 */
enum class AndroidStudioVersion(val buildPrefix: Int) {
    KOALA(241),
    LADYBUG(242),
    MEERKAT(243),
    NARWHAL(251),
    UNKNOWN(0);

    companion object {
        fun current(): AndroidStudioVersion {
            val prefix = IdeInfo.build.baselineVersion
            return entries.find { it.buildPrefix == prefix } ?: UNKNOWN
        }
    }
}
