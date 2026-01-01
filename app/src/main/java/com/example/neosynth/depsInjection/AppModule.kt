package com.example.neosynth.depsInjection

import android.content.Context
import androidx.room.Room
import com.example.neosynth.data.local.MusicDatabase
import com.example.neosynth.data.local.MusicDao
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(interceptor: DynamicUrlInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "neosynth_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMusicDao(db: MusicDatabase): MusicDao = db.musicDao

    @Provides
    fun provideServerDao(db: MusicDatabase): ServerDao = db.serverDao
    
    @Provides
    @Singleton
    fun provideSettingsPreferences(@ApplicationContext context: Context): SettingsPreferences {
        return SettingsPreferences(context)
    }
}