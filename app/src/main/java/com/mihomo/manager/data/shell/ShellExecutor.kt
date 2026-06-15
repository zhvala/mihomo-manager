package com.mihomo.manager.data.shell

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ShellExecutor"

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

@Singleton
class ShellExecutor @Inject constructor() {

    suspend fun hasRootAccess(): Boolean = withContext(Dispatchers.IO) {
        val result = Shell.isAppGrantedRoot() == true
        Log.d(TAG, "hasRootAccess: $result")
        result
    }

    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            val isRoot = shell.isRoot
            Log.d(TAG, "requestRoot: isRoot=$isRoot")
            isRoot
        } catch (e: Exception) {
            Log.e(TAG, "requestRoot failed", e)
            false
        }
    }

    private suspend fun ensureRootShell(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                Log.w(TAG, "Shell is not root")
                return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureRootShell failed", e)
            false
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "exec: $command")
        try {
            if (!ensureRootShell()) {
                return@withContext ShellResult(-1, "", "Root shell not available")
            }
            val result = Shell.cmd(command).exec()
            val stdout = result.out.joinToString("\n")
            val stderr = result.err.joinToString("\n")
            Log.d(TAG, "exec result: code=${result.code}, out=${result.out.size} lines, err=${result.err.size} lines")
            if (result.code != 0) {
                Log.w(TAG, "exec non-zero exit: code=${result.code}, err=$stderr")
            }
            ShellResult(result.code, stdout, stderr)
        } catch (e: Exception) {
            Log.e(TAG, "exec exception", e)
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    suspend fun exec(commands: Array<String>): ShellResult = withContext(Dispatchers.IO) {
        try {
            val result = Shell.cmd(*commands).exec()
            ShellResult(
                result.code,
                result.out.joinToString("\n"),
                result.err.joinToString("\n"),
            )
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    suspend fun readFile(path: String): ShellResult = exec("cat '$path'")

    suspend fun writeFile(path: String, content: String): ShellResult =
        exec("cat > '$path' << 'MIHOMO_EOF'\n$content\nMIHOMO_EOF")

    suspend fun fileExists(path: String): Boolean {
        val result = exec("[ -f '$path' ] && echo 'exists'")
        return result.output.trim() == "exists"
    }

    suspend fun dirExists(path: String): Boolean {
        val result = exec("[ -d '$path' ] && echo 'exists'")
        return result.output.trim() == "exists"
    }

    suspend fun copyFile(src: String, dst: String): ShellResult = exec("cp '$src' '$dst'")

    suspend fun listDir(path: String): List<String> {
        val result = exec("ls -1 '$path'")
        return if (result.isSuccess) {
            result.output.lines().filter { it.isNotBlank() }
        } else emptyList()
    }
}
