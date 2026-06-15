package com.mihomo.manager.di

import android.content.Context
import com.mihomo.manager.data.repository.AppRepository
import com.mihomo.manager.data.repository.ConfigRepository
import com.mihomo.manager.data.shell.CronController
import com.mihomo.manager.data.shell.MihomoController
import com.mihomo.manager.data.shell.ShellExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideShellExecutor(): ShellExecutor = ShellExecutor()

    @Provides @Singleton
    fun provideMihomoController(shell: ShellExecutor): MihomoController = MihomoController(shell)

    @Provides @Singleton
    fun provideCronController(shell: ShellExecutor): CronController = CronController(shell)

    @Provides @Singleton
    fun provideConfigRepository(shell: ShellExecutor, controller: MihomoController): ConfigRepository =
        ConfigRepository(shell, controller)

    @Provides @Singleton
    fun provideAppRepository(@ApplicationContext context: Context, shell: ShellExecutor): AppRepository =
        AppRepository(context, shell)
}
