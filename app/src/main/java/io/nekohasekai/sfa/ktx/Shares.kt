package io.nekohasekai.sfa.ktx

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.nekohasekai.libbox.ProfileContent
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.appcompat.R as AppCompatR

suspend fun Context.shareProfile(profile: Profile) {
    val content = ProfileContent()
    content.name = profile.name
    when (profile.typed.type) {
        TypedProfile.Type.Local -> {
            content.type = io.nekohasekai.libbox.Libbox.ProfileTypeLocal
        }
        TypedProfile.Type.Remote -> {
            content.type = io.nekohasekai.libbox.Libbox.ProfileTypeRemote
        }
        TypedProfile.Type.Template -> {
            content.type = io.nekohasekai.libbox.Libbox.ProfileTypeLocal
        }
    }
    content.config = File(profile.typed.path).readText()
    content.remotePath = profile.typed.remoteURL
    content.autoUpdate = profile.typed.autoUpdate
    content.autoUpdateInterval = profile.typed.autoUpdateInterval
    content.lastUpdated = profile.typed.lastUpdated.time

    val configDirectory = File(cacheDir, "share").also { it.mkdirs() }
    val profileFile = File(configDirectory, "${profile.name}.bpf")
    profileFile.writeBytes(content.encode())
    val uri = FileProvider.getUriForFile(this, "$packageName.cache", profileFile)
    withContext(Dispatchers.Main) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM, uri),
                getString(AppCompatR.string.abc_shareactionprovider_share_with),
            ),
        )
    }
}

suspend fun Context.shareProfileAsJson(profile: Profile, template: Boolean = false) {
    val configDirectory = File(cacheDir, "share").also { it.mkdirs() }
    require(!template || profile.typed.type == TypedProfile.Type.Template)
    // 模板源文件包含订阅信息，只有明确选择分享模板时才导出。
    val suffix = if (template) ".template.json" else ".json"
    val jsonFile = File(configDirectory, "${profile.name}$suffix")
    val sourcePath = if (template) profile.typed.templatePath else profile.typed.path
    jsonFile.writeText(File(sourcePath).readText())
    val uri = FileProvider.getUriForFile(this, "$packageName.cache", jsonFile)
    withContext(Dispatchers.Main) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/json")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_STREAM, uri),
                getString(AppCompatR.string.abc_shareactionprovider_share_with),
            ),
        )
    }
}
