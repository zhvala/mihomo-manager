package com.mihomo.manager

import android.app.Application
import com.mihomo.manager.data.repository.AppRepository
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MihomoApp : Application() {

    @Inject lateinit var appRepository: AppRepository

    companion object {
        init {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(30)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        Shell.getShell { shell ->
            if (shell.isRoot) {
                appRepository.preloadCache()
            }
        }
    }
}
