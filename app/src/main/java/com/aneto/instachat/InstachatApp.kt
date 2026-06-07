package com.aneto.instachat

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InstachatApp :
    Application(),
    SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory())
            add(VideoFrameDecoder.Factory())
        }
        .build()
}
