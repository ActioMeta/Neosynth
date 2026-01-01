package com.example.neosynth.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicUrlInterceptor @Inject constructor() : Interceptor {

    private var baseUrl: HttpUrl? = null

    fun setBaseUrl(newUrl: String) {
        val mappedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        mappedUrl.toHttpUrlOrNull()?.let {
            baseUrl = it
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        baseUrl?.let { newUrl ->
            val newFullUrl = request.url.newBuilder()
                .scheme(newUrl.scheme)
                .host(newUrl.host)
                .port(newUrl.port)
                .build()

            request = request.newBuilder()
                .url(newFullUrl)
                .build()
        }

        return chain.proceed(request)
    }
}