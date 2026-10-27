package io.nekohasekai.sfa.update

object TubingUpdatePolicy {
    fun selectApkName(names: List<String>, supportedABIs: List<String>, legacy: Boolean): String? {
        val compatible = names.filter {
            it.startsWith("Tubing-") && it.endsWith(".apk") &&
                !it.contains("-play") && it.contains("-legacy-android-5") == legacy
        }
        supportedABIs.forEach { abi ->
            compatible.firstOrNull { it.removeSuffix(".apk").removeSuffix("-legacy-android-5").endsWith("-$abi") }?.let { return it }
        }
        return compatible.firstOrNull { it.removeSuffix(".apk").removeSuffix("-legacy-android-5").endsWith("-universal") }
    }

    fun isNewerVersion(candidateCode: Int, installedCode: Int): Boolean = candidateCode > installedCode
}
