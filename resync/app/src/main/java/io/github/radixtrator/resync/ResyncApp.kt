package io.github.radixtrator.resync

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.radixtrator.resync.data.Http
import io.github.radixtrator.resync.notify.InboxWorker
import io.github.radixtrator.resync.ui.theme.Motion
import io.github.radixtrator.resync.watch.WatchWorker
import okhttp3.OkHttpClient

class ResyncApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The crash report screen's process shows a saved report and nothing else.
        if (CrashReporter.isCrashProcess(this)) return
        CrashReporter.install(this)
        container = AppContainer(this)
        // WorkManager opens its database the first time it's used, so the schedules are checked off the main
        // thread, where they'd only hold up the first frame.
        Thread {
            // KEEP leaves a running schedule alone; this only repairs one lost to a data wipe or an update.
            WatchWorker.schedule(this, !container.watch.isEmpty)
            // Sync's message check: keeps the repeating check in line with the setting, or runs "On launch".
            InboxWorker.schedule(this, container.settings.settings.value.messageCheck)
        }.apply { name = "resync-schedules" }.start()
    }

    /**
     * Animated GIF/WebP support (platform decoder on API 28+, Coil's own below), and a descriptive
     * User-Agent: some image hosts (e.g. Wikimedia) answer 403 to OkHttp's generic "okhttp/x.y".
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient() }))
            }
            // Short enough not to read as lag when scrolling onto images that are still decoding.
            .crossfade(Motion.FADE)
            .build()

    private fun imageHttpClient(): OkHttpClient = Http.base.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Resync/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()
}
