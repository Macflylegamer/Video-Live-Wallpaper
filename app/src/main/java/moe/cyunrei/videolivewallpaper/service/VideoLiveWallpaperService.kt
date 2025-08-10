package moe.cyunrei.videolivewallpaper.service

import android.app.WallpaperManager
import android.content.*
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import java.io.IOException

class VideoLiveWallpaperService : WallpaperService() {
    internal inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var broadcastReceiver: BroadcastReceiver? = null
        private var videoFilePath: String? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            try {
                videoFilePath = this@VideoLiveWallpaperService.openFileInput("video_live_wallpaper_file_path")
                    .bufferedReader().readText()
                    
                // Verify the file exists and is readable
                val file = File(videoFilePath)
                if (!file.exists() || !file.canRead()) {
                    throw IOException("Video file is not accessible")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                videoFilePath = null
            }
            
            val intentFilter = IntentFilter(VIDEO_PARAMS_CONTROL_ACTION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.getBooleanExtra(KEY_ACTION, false)
                    if (action) {
                        mediaPlayer!!.setVolume(0f, 0f)
                    } else {
                        mediaPlayer!!.setVolume(1.0f, 1.0f)
                    }
                }
            }.also { broadcastReceiver = it }
            registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
                val prefs = getSharedPreferences("moe.cyunrei.videolivewallpaper_preferences", Context.MODE_PRIVATE)
                val looping = prefs.getBoolean("video_looping", false)
                try {
                    if (videoFilePath == null) {
                        android.util.Log.e("VideoLiveWallpaper", "videoFilePath is null")
                        return
                    }
                    val file = File(videoFilePath)
                    if (!file.exists() || !file.canRead()) {
                        android.util.Log.e("VideoLiveWallpaper", "Video file does not exist or is not readable: $videoFilePath")
                        return
                    }
                    mediaPlayer = MediaPlayer().apply {
                        setSurface(holder.surface)
                        setDataSource(videoFilePath)
                        isLooping = looping
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        setOnPreparedListener { mp ->
                            val duration = mp.duration
                            android.util.Log.d("VideoLiveWallpaper", "Video duration: $duration ms")
                            if (duration > 0) {
                                mp.start()
                            } else {
                                android.util.Log.e("VideoLiveWallpaper", "Video duration is zero")
                            }
                        }
                        setOnCompletionListener { mp ->
                            if (!looping) {
                                val duration = mp.duration
                                val lastFramePosition = (duration * 0.999).toInt()
                                if (duration > 0) {
                                    mp.seekTo(lastFramePosition)
                                    mp.pause()
                                    android.util.Log.d("VideoLiveWallpaper", "Seeked to last frame and paused")
                                }
                            }
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.util.Log.e("VideoLiveWallpaper", "Exception in MediaPlayer setup: ${e.message}")
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                try {
                    val file = File("$filesDir/unmute")
                    if (file.exists()) mediaPlayer?.setVolume(1.0f, 1.0f) else mediaPlayer?.setVolume(0f, 0f)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            val prefs = getSharedPreferences("moe.cyunrei.videolivewallpaper_preferences", Context.MODE_PRIVATE)
            val looping = prefs.getBoolean("video_looping", false)
            
            if (visible) {
                if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
                    // Only start from beginning if looping is enabled
                    if (looping) {
                        mediaPlayer!!.seekTo(0)
                        mediaPlayer!!.start()
                    }
                }
            } else {
                mediaPlayer?.pause()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        override fun onDestroy() {
            super.onDestroy()
            mediaPlayer?.release()
            mediaPlayer = null
            unregisterReceiver(broadcastReceiver)
        }
    }

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    companion object {
        const val VIDEO_PARAMS_CONTROL_ACTION = "moe.cyunrei.livewallpaper"
        private const val KEY_ACTION = "music"
        private const val ACTION_MUSIC_UNMUTE = false
        private const val ACTION_MUSIC_MUTE = true
        fun muteMusic(context: Context) {
            Intent(VIDEO_PARAMS_CONTROL_ACTION).apply {
                putExtra(KEY_ACTION, ACTION_MUSIC_MUTE)
            }.also { context.sendBroadcast(it) }
        }

        fun unmuteMusic(context: Context) {
            Intent(VIDEO_PARAMS_CONTROL_ACTION).apply {
                putExtra(KEY_ACTION, ACTION_MUSIC_UNMUTE)
            }.also {
                context.sendBroadcast(it)
            }
        }

        fun setToWallPaper(context: Context) {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, VideoLiveWallpaperService::class.java)
                )
            }.also {
                context.startActivity(it)
            }
            try {
                WallpaperManager.getInstance(context).clear()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}