package home.horizon.tv;

import android.content.Context;

import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** One place that builds ExoPlayer the same way for the full-screen player and the Live TV preview. */
final class Players {
    /** Same identity the old built-in player sent, so the provider sees no change. */
    static final String USER_AGENT = "stagefright/1.2 (Linux;Android 9)";

    private Players() { }

    /**
     * Fixed, small buffer (15-30 s, never more than 24 MB) so memory stays flat on the 1 GB stick.
     * The preview uses a shorter buffer so channels start quicker.
     */
    static ExoPlayer create(Context c, boolean preview) {
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(preview ? 8000 : 15000, preview ? 15000 : 30000, 1500, 3000)
                .setTargetBufferBytes((preview ? 12 : 24) * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(c).setEnableDecoderFallback(true);
        return new ExoPlayer.Builder(c, renderers)
                .setLoadControl(load)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
    }
}
