package com.example.neosynth.depsInjection

import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.LyricsApiService
import com.example.neosynth.data.remote.NeteaseApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LrclibRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusixmatchRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NeteaseRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideDynamicUrlInterceptor() = DynamicUrlInterceptor()

    @Provides
    @Singleton
    fun provideNavidromeApi(interceptor: DynamicUrlInterceptor): NavidromeApiService {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://placeholder/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NavidromeApiService::class.java)
    }
    
    // LRCLIB API (sin autenticación)
    @Provides
    @Singleton
    @LrclibRetrofit
    fun provideLrclibRetrofit(): Retrofit {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideLrclibApi(@LrclibRetrofit retrofit: Retrofit): LyricsApiService {
        return retrofit.create(LyricsApiService::class.java)
    }
    
    // Netease Cloud Music API (sin autenticación)
    @Provides
    @Singleton
    @NeteaseRetrofit
    fun provideNeteaseRetrofit(): Retrofit {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        return Retrofit.Builder()
            .baseUrl("https://netease-cloud-music-api-mauve.vercel.app/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideNeteaseApi(@NeteaseRetrofit retrofit: Retrofit): NeteaseApiService {
        return retrofit.create(NeteaseApiService::class.java)
    }
}