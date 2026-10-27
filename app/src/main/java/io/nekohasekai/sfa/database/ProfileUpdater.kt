package io.nekohasekai.sfa.database

import android.util.AtomicFile
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Date

object ProfileUpdater {
    private val updateMutex = Mutex()
    private const val DIRECT_PLACEHOLDER = """{"outbounds":[{"type":"direct","tag":"direct"}]}"""

    fun writeAtomically(file: File, content: String) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    // 所有更新入口串行执行，编译失败时保留上次有效配置。
    suspend fun refresh(profile: Profile): Boolean = updateMutex.withLock {
        val current = ProfileManager.get(profile.id) ?: error("Profile not found")
        val changed = refreshLocked(current, null, false)
        profile.typed.lastUpdated = current.typed.lastUpdated
        profile.typed.templateError = current.typed.templateError
        changed
    }

    suspend fun saveTemplate(profile: Profile, source: String, initial: Boolean = false): Boolean = updateMutex.withLock {
        val current = if (initial) profile else ProfileManager.get(profile.id) ?: error("Profile not found")
        val changed = refreshLocked(current, source, initial)
        profile.typed.lastUpdated = current.typed.lastUpdated
        profile.typed.templateError = current.typed.templateError
        changed
    }

    private suspend fun refreshLocked(profile: Profile, source: String?, initial: Boolean): Boolean {
        val typed = profile.typed
        val file = File(typed.path)
        var ready = true
        val content = if (typed.type == TypedProfile.Type.Template) {
            if (source != null) writeAtomically(File(typed.templatePath), source)
            try {
                val template = source ?: File(typed.templatePath).readText()
                if (!TemplateProfileSource.hasSubscriptions(template)) {
                    ready = false
                    typed.templateError = Application.application.getString(R.string.profile_template_pending)
                    DIRECT_PLACEHOLDER
                } else {
                    Libbox.processTemplate(template).also { Libbox.checkConfig(it) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                typed.templateError = Application.application.getString(R.string.profile_template_compile_failed) +
                    (error.message?.let { "\n$it" } ?: "")
                if (!initial) {
                    persistStatus(profile)
                    throw IllegalStateException(typed.templateError)
                }
                ready = false
                DIRECT_PLACEHOLDER
            }
        } else {
            HTTPClient().use { it.getString(typed.remoteURL) }.also { Libbox.checkConfig(it) }
        }
        val changed = !file.exists() || file.readText() != content
        if (changed) writeAtomically(file, content)
        if (ready) {
            typed.lastUpdated = Date()
            typed.templateError = ""
        } else {
            typed.lastUpdated = Date(0)
        }
        if (!initial) persistStatus(profile)
        return changed
    }

    private suspend fun persistStatus(profile: Profile) {
        // 下载期间用户可能修改名称或更新设置，只写回编译结果，保留这些修改。
        val latest = ProfileManager.get(profile.id) ?: error("Profile not found")
        latest.typed.lastUpdated = profile.typed.lastUpdated
        latest.typed.templateError = profile.typed.templateError
        ProfileManager.update(latest)
    }
}
