package com.brentvatne.exoplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;

import com.brentvatne.react.R;
import com.brentvatne.receiver.AudioBecomingNoisyReceiver;
import com.brentvatne.receiver.BecomingNoisyListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Dynamic;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import timber.log.Timber;

@SuppressLint("ViewConstructor")
class ReactExoPlayerView extends FrameLayout implements LifecycleEventListener, BandwidthMeter.EventListener, BecomingNoisyListener, AudioManager.OnAudioFocusChangeListener,
        MetadataOutput,
        Player.EventListener, DefaultDrmSessionManager.EventListener {
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private static final CookieManager DEFAULT_COOKIE_MANAGER;
    private static final int SHOW_PROGRESS = 1;

    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    private final VideoEventEmitter eventEmitter;

    private Handler mainHandler;
    private ExoPlayerView exoPlayerView;
    private SubtitleView subView;

    private DataSource.Factory mediaDataSourceFactory;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private DefaultLoadControl defaultLoadControl;
    private DefaultRenderersFactory rendererFactory;

    //private PlayerEventListener playerEventListener;

    private PlayerUtils.PlaybackState playbackState;
    private PlayerUtils.PlaybackLocation playbackLocation;
    private Long playbackPosition;

    private DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;

    private UUID drmUUID = null;
    private String drmLicenseUrl = null;
    private String[] drmLicenseHeader = null;

    private boolean playerNeedsSource;

    private int resumeWindow;
    private long resumePosition;
    private boolean loadVideoStarted;
    private boolean isFullscreen;
    private boolean isInBackground;
    private boolean isPaused;
    private boolean isBuffering;
    private float rate = 1f;
    private float audioVolume = 1f;
    private int maxBitRate = 0;
    private long seekTime = C.TIME_UNSET;

    private int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
    private int bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private int bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;

    // Props from React
    private Uri srcUri;
    private String extension;
    private String drmToken;

    private boolean repeat;
    private String audioTrackType;
    private Dynamic audioTrackValue;
    private String videoTrackType;
    private Dynamic videoTrackValue;
    private ReadableArray audioTracks;
    private String textTrackType;
    private Dynamic textTrackValue;
    private ReadableArray textTracks;
    private boolean disableFocus;
    private float mProgressUpdateInterval = 250.0f;
    private boolean playInBackground = false;
    private Map<String, String> requestHeaders;
    private boolean mReportBandwidth = false;
    // \ End props

    // React
    private final ThemedReactContext themedReactContext;
    private final AudioManager audioManager;
    private final AudioBecomingNoisyReceiver audioBecomingNoisyReceiver;

    @SuppressLint("HandlerLeak") private final Handler progressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (player != null
                            && player.getPlaybackState() == ExoPlayer.STATE_READY
                            && player.getPlayWhenReady()
                    ) {
                        long pos = player.getCurrentPosition();
                        long bufferedDuration = player.getBufferedPercentage() * player.getDuration() / 100;
                        eventEmitter.progressChanged(pos, bufferedDuration, player.getDuration());
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, Math.round(mProgressUpdateInterval));
                    }
                    break;
            }
        }
    };


    public ReactExoPlayerView(ThemedReactContext context) {
        super(context);
        Timber.d("ReactExoPlayerView constructor");
        themedReactContext = context;

        eventEmitter = new VideoEventEmitter(context);

        initializeTheCore();

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        themedReactContext.addLifecycleEventListener(this);
        audioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver(themedReactContext);

        createViews();

        Timber.d("from the constructor");
        //initializePlayer();
        initializePlayerOld();
    }


    private void initializeTheCore() {
        Timber.d("-------- initializeTheCore");
        clearResumePosition();

        createDataSourceFactories();

        mainHandler = new Handler();
        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }
    }


    private void createDataSourceFactories() {
        Timber.d("createDataSourceFactories");
        mediaDataSourceFactory = buildDataSourceFactory(true);
    }


    private void createViews() {
        Timber.d("createViews");

        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        exoPlayerView = new ExoPlayerView(getContext());
        exoPlayerView.setLayoutParams(layoutParams);

        addView(exoPlayerView, 0, layoutParams);

        //TODO: in here, create the SubtitleView subView and add it to the layout
        configureSubtitleView();
    }


    private void configureSubtitleView() {
        Timber.d("-------- configureSubtitleView");

        //subtitleView.setUserDefaultTextSize();
        //subtitleView.setStyle(new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null));
        //subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION);
    }


    private void initializePlayerOld() {
        Long startPosition = 0L;
        Timber.d("-------- initializePlayerOld and starting position is %d", startPosition);

        prePlaySetup();

        initializeTheBasics();
        setupPlaybackParametersForAudioVideoAndSubtitles();
        setupDRM();
        setupThePlayer();
        setMediaToPlay();
    }


    private void prePlaySetup() {
        Timber.d("-------- prePlaySetup, where we register the device and we define the location (local/remote) and position (0/some-other-point-in-time) of the playback of the media");
        //try to register the device. The old endpoint was:
        //@GET("/{baseHref}/devices")
        //Observable<Response> authDevice(@Path(value = "baseHref", encode = false) String baseHref, @Query("apiKey") String apikey, @Query("manufacturer") String manufacturer, @Query("model") String model, @Query("udid") String id);

        //After successfully registering the device, proceed to get the bookmarks for this media, so we know if we should resume from a specific point the playback or not
        //Note that if we are showing a trailer, always start its playback from the beginning, otherwise fetch the bookmarks for this media and set 'playbackPosition' accordingly
        playbackPosition = 0L;

        //Finally, check if Cast is connected and set 'playbackLocation' accordingly
        playbackLocation = PlayerUtils.PlaybackLocation.LOCAL;
    }


    private void initializeTheBasics() {
        Timber.d("-------- initializeTheBasics");

        //Here we initialize some basic objects that we will need for the player such as the track (audio, video, subtitles) selector, the renderer factory and the load control.
        //Things like a general PlayerEventListener, an analytics manager and other listeners could also be initialized here
        trackSelector = new DefaultTrackSelector(new AdaptiveTrackSelection.Factory());

        defaultLoadControl = new DefaultLoadControl.Builder()
                .setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs).createDefaultLoadControl();

    }


    private void setupPlaybackParametersForAudioVideoAndSubtitles() {
        Timber.d("-------- setupPlaybackParametersForAudioVideoAndSubtitles");

        //Here we should handle the preferred language of the user for the subtitles and audio tracks of the media and set the trackSelector parameters accordingly
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));
    }


    private void setupDRM() {
        Timber.d("-------- setupDRM and drmUUID is %s and drmLicenseUrl is %s", drmUUID, drmLicenseUrl);

        drmSessionManager = null;

        if (drmUUID != null) {
            try {
                drmSessionManager = buildDrmSessionManager(drmUUID, drmLicenseUrl, drmLicenseHeader);
            } catch (UnsupportedDrmException e) {
                e.printStackTrace();
                int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
                Timber.d("Drm Info: %s", getResources().getString(errorStringId));
                eventEmitter.error(getResources().getString(errorStringId), e);
                return;
            }
        }

        if (drmSessionManager == null) {
            Timber.e("An error occurred while setting up the DRM magic");
        }

        rendererFactory = new DefaultRenderersFactory(getContext(), drmSessionManager, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        //rendererFactory = new DefaultRenderersFactory(getContext());
    }


    private void setupThePlayer() {
        Timber.d("-------- setupThePlayer");
        if (player == null) {
            Timber.i("The player was null so I created and initialized it");

            //player = ExoPlayerFactory.newSimpleInstance(getContext(), rendererFactory, trackSelector, defaultLoadControl, drmSessionManager);
            player = ExoPlayerFactory.newSimpleInstance(getContext(), rendererFactory, trackSelector, defaultLoadControl);

            player.seekTo(0);
            player.addListener(this);
            player.addTextOutput(new CustomSubtitlesListener());
            player.setPlaybackParameters(new PlaybackParameters(rate, 1f));

            exoPlayerView.setPlayer(player);

            audioBecomingNoisyReceiver.setListener(this);

            setPlayWhenReady(!isPaused);
            playerNeedsSource = true;
        } else {
            Timber.w("The player existed so I didn't do anything with it");
        }
    }


    private void setMediaToPlay() {
        Timber.d("-------- setMediaToPlay");
        if (playerNeedsSource && srcUri != null) {
            Timber.d("playerNeedsSource is true and the srcUri is %s", srcUri.toString());

            boolean haveResumePosition = doWeHaveResumePosition();
            MediaSource mergedMediaSource = mergeSourcesAndContinue();

            player.prepare(mergedMediaSource, !haveResumePosition, false);
            playerNeedsSource = false;

            eventEmitter.loadStart();
            loadVideoStarted = true;
        } else {
            if (!playerNeedsSource) {
                Timber.w("playerNeedsSource was false");
            }
        }
    }


    private boolean doWeHaveResumePosition() {
        boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
        if (haveResumePosition) {
            player.seekTo(resumeWindow, resumePosition);
        }

        return haveResumePosition;
    }


    private MediaSource mergeSourcesAndContinue() {
        ArrayList<MediaSource> mediaSourceList = buildTextSources();
        MediaSource videoSource = buildMediaSource(srcUri, extension);
        MediaSource mergedMediaSource;
        if (mediaSourceList.size() == 0) {
            mergedMediaSource = videoSource;
        } else {
            mediaSourceList.add(0, videoSource);
            MediaSource[] textSourceArray = mediaSourceList.toArray(new MediaSource[mediaSourceList.size()]);
            mergedMediaSource = new MergingMediaSource(textSourceArray);
        }

        return mergedMediaSource;
    }


    // Internal methods
    private void initializePlayer() {
        Timber.d("initializePlayer");

        if (player == null) {
            Timber.d("player was null so I created and initialized it");

            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
            trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));

            DefaultAllocator allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
            DefaultLoadControl defaultLoadControl = new DefaultLoadControl(allocator, minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs, -1, true);
            player = ExoPlayerFactory.newSimpleInstance(getContext(), trackSelector, defaultLoadControl);
            player.addListener(this);
            player.setMetadataOutput(this);
            exoPlayerView.setPlayer(player);
            audioBecomingNoisyReceiver.setListener(this);
            BANDWIDTH_METER.addEventListener(new Handler(), this);
            setPlayWhenReady(!isPaused);
            playerNeedsSource = true;

            PlaybackParameters params = new PlaybackParameters(rate, 1f);
            player.setPlaybackParameters(params);
        }

        if (playerNeedsSource && srcUri != null) {
            Timber.d("playerNeedsSource and the srcUri is %s", srcUri.toString());

            ArrayList<MediaSource> mediaSourceList = buildTextSources();
            MediaSource videoSource = buildMediaSource(srcUri, extension);
            MediaSource mediaSource;
            if (mediaSourceList.size() == 0) {
                mediaSource = videoSource;
            } else {
                mediaSourceList.add(0, videoSource);
                MediaSource[] textSourceArray = mediaSourceList.toArray(new MediaSource[mediaSourceList.size()]);
                mediaSource = new MergingMediaSource(textSourceArray);
            }

            boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
            if (haveResumePosition) {
                player.seekTo(resumeWindow, resumePosition);
            }
            player.prepare(mediaSource, !haveResumePosition, false);
            playerNeedsSource = false;

            eventEmitter.loadStart();
            loadVideoStarted = true;
        }
    }


    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension : uri.getLastPathSegment());
        Timber.d("buildMediaSource for type %d and uri %s", type, uri.toString());
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource(uri, buildDataSourceFactory(false), new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);

            case C.TYPE_DASH:
                return new DashMediaSource(uri, buildDataSourceFactory(false), new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);

            case C.TYPE_HLS:
                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);

            case C.TYPE_OTHER:
                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(), mainHandler, null);

            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }


    private ArrayList<MediaSource> buildTextSources() {
        Timber.d("buildTextSources");
        ArrayList<MediaSource> textSources = new ArrayList<>();
        if (textTracks == null) {
            return textSources;
        }

        for (int i = 0; i < textTracks.size(); ++i) {
            ReadableMap textTrack = textTracks.getMap(i);
            String language = textTrack.getString("language");
            String title = textTrack.hasKey("title") ? textTrack.getString("title") : language + " " + i;
            Uri uri = Uri.parse(textTrack.getString("uri"));
            MediaSource textSource = buildTextSource(title, uri, textTrack.getString("type"), language);
            if (textSource != null) {
                textSources.add(textSource);
            }
        }
        return textSources;
    }


    private MediaSource buildTextSource(String title, Uri uri, String mimeType, String language) {
        Format textFormat = Format.createTextSampleFormat(title, mimeType, Format.NO_VALUE, language);
        return new SingleSampleMediaSource(uri, mediaDataSourceFactory, textFormat, C.TIME_UNSET);
    }


    private void releasePlayer() {
        Timber.d("releasePlayer");
        if (player != null) {
            updateResumePosition();
            player.release();
            player.setMetadataOutput(null);
            player = null;
            trackSelector = null;
        }

        progressHandler.removeMessages(SHOW_PROGRESS);
        themedReactContext.removeLifecycleEventListener(this);
        audioBecomingNoisyReceiver.removeListener();
        BANDWIDTH_METER.removeEventListener(this);
    }


    private boolean requestAudioFocus() {
        if (disableFocus) {
            return true;
        }

        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }


    private void setPlayWhenReady(boolean playWhenReady) {
        if (player == null) {
            return;
        }

        if (playWhenReady) {
            boolean hasAudioFocus = requestAudioFocus();
            if (hasAudioFocus) {
                player.setPlayWhenReady(true);
            }
        } else {
            player.setPlayWhenReady(false);
        }
    }


    @Override
    public void setId(int id) {
        super.setId(id);
        Timber.d("setId to be %d", id);
        eventEmitter.setViewId(id);
    }


    //BandwidthMeter.EventListener implementation
    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        //Timber.d("onBandwidthSample");
        if (mReportBandwidth) {
            eventEmitter.bandwidthReport(bitrate);
        }
    }


    @Override
    public void onDrmKeysLoaded() {
        Timber.d("onDrmKeysLoaded");
    }


    @Override
    public void onDrmSessionManagerError(Exception error) {
        Timber.d("onDrmSessionManagerError");
        error.printStackTrace();
    }


    @Override
    public void onDrmKeysRestored() {
        Timber.d("onDrmKeysRestored");
    }


    @Override
    public void onDrmKeysRemoved() {
        Timber.d("onDrmKeysRemoved");
    }


    public class CustomSubtitlesListener implements TextOutput {
        @Override
        public void onCues(List<Cue> cues) {
            if (cues != null && !cues.isEmpty()) {
                Timber.d("-------- on cue: %s", cues.get(0).text.toString());

                /*
                if (subtitleView != null) {
                    subtitleView.onCues(cues);
                }
                */
            }
        }
    }


    private void startPlayback() {
        Timber.d("******* startPlayback and playbackState is %d", player.getPlaybackState());
        if (player != null) {
            switch (player.getPlaybackState()) {
                case ExoPlayer.STATE_IDLE:
                case ExoPlayer.STATE_ENDED:
                    //initializePlayer();
                    initializePlayerOld();
                    break;

                case ExoPlayer.STATE_BUFFERING:
                case ExoPlayer.STATE_READY:
                    if (!player.getPlayWhenReady()) {
                        setPlayWhenReady(true);
                    }
                    break;
                default:
                    break;
            }
        } else {
            //initializePlayer();
            initializePlayerOld();
        }
        if (!disableFocus) {
            setKeepScreenOn(true);
        }
    }


    private void pausePlayback() {
        Timber.d("pausePlayback");
        if (player != null) {
            if (player.getPlayWhenReady()) {
                setPlayWhenReady(false);
            }
        }
        setKeepScreenOn(false);
    }


    private void stopPlayback() {
        onStopPlayback();
        releasePlayer();
    }


    private void onStopPlayback() {
        if (isFullscreen) {
            setFullscreen(false);
        }
        setKeepScreenOn(false);
        audioManager.abandonAudioFocus(this);
    }


    private void updateResumePosition() {
        Timber.d("updateResumePosition");
        resumeWindow = player.getCurrentWindowIndex();
        resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition()) : C.TIME_UNSET;
    }


    private void clearResumePosition() {
        Timber.d("-------- clearResumePosition");
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }


    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        Timber.d("-------- buildDataSourceFactory");
        return DataSourceUtil.getDefaultDataSourceFactory(this.themedReactContext, useBandwidthMeter ? BANDWIDTH_METER : null, requestHeaders);
    }


    // AudioManager.OnAudioFocusChangeListener implementation
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                eventEmitter.audioFocusChanged(false);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                eventEmitter.audioFocusChanged(true);
                break;
            default:
                break;
        }

        if (player != null) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // Lower the volume
                player.setVolume(audioVolume * 0.8f);
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Raise it back to normal
                player.setVolume(audioVolume * 1);
            }
        }
    }


    // AudioBecomingNoisyListener implementation
    @Override
    public void onAudioBecomingNoisy() {
        eventEmitter.audioBecomingNoisy();
    }


    // ExoPlayer.EventListener implementation
    @Override
    public void onLoadingChanged(boolean isLoading) {
        //Timber.d("onLoadingChanged");
        // Do nothing.
    }


    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        String text = "onStateChanged: playWhenReady=" + playWhenReady + ", playbackState=";
        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                text += "IDLE";
                eventEmitter.idle();
                break;

            case ExoPlayer.STATE_BUFFERING:
                text += "BUFFERING";
                onBuffering(true);
                break;

            case ExoPlayer.STATE_READY:
                text += "READY";
                eventEmitter.ready();
                onBuffering(false);
                startProgressHandler();
                videoLoaded();
                break;

            case ExoPlayer.STATE_ENDED:
                text += "ENDED";
                eventEmitter.end();
                onStopPlayback();
                break;

            default:
                text += "UNKNOWN";
                break;
        }

        Timber.d("--> %s", text);
    }


    private void startProgressHandler() {
        progressHandler.sendEmptyMessage(SHOW_PROGRESS);
    }


    private void videoLoaded() {
        Timber.d("videoLoaded");
        if (loadVideoStarted) {
            loadVideoStarted = false;
            setSelectedAudioTrack(audioTrackType, audioTrackValue);
            setSelectedVideoTrack(videoTrackType, videoTrackValue);
            setSelectedTextTrack(textTrackType, textTrackValue);
            Format videoFormat = player.getVideoFormat();
            int width = videoFormat != null ? videoFormat.width : 0;
            int height = videoFormat != null ? videoFormat.height : 0;
            eventEmitter.load(player.getDuration(), player.getCurrentPosition(), width, height, getAudioTrackInfo(), getTextTrackInfo(), getVideoTrackInfo());
        }
    }


    private WritableArray getAudioTrackInfo() {
        WritableArray audioTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_AUDIO);
        if (info == null || index == C.INDEX_UNSET) {
            return audioTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            WritableMap audioTrack = Arguments.createMap();
            audioTrack.putInt("index", i);
            audioTrack.putString("title", format.id != null ? format.id : "");
            audioTrack.putString("type", format.sampleMimeType);
            audioTrack.putString("language", format.language != null ? format.language : "");
            audioTrack.putString("bitrate", format.bitrate == Format.NO_VALUE ? "" : String.format(Locale.US, "%.2fMbps", format.bitrate / 1000000f));
            audioTracks.pushMap(audioTrack);
        }
        return audioTracks;
    }


    private WritableArray getVideoTrackInfo() {
        WritableArray videoTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_VIDEO);
        if (info == null || index == C.INDEX_UNSET) {
            return videoTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            TrackGroup group = groups.get(i);

            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                Format format = group.getFormat(trackIndex);
                WritableMap videoTrack = Arguments.createMap();
                videoTrack.putInt("width", format.width == Format.NO_VALUE ? 0 : format.width);
                videoTrack.putInt("height", format.height == Format.NO_VALUE ? 0 : format.height);
                videoTrack.putInt("bitrate", format.bitrate == Format.NO_VALUE ? 0 : format.bitrate);
                videoTrack.putString("codecs", format.codecs != null ? format.codecs : "");
                videoTrack.putString("trackId", format.id == null ? String.valueOf(trackIndex) : format.id);
                videoTracks.pushMap(videoTrack);
            }
        }
        return videoTracks;
    }


    private WritableArray getTextTrackInfo() {
        WritableArray textTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_TEXT);
        if (info == null || index == C.INDEX_UNSET) {
            return textTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            WritableMap textTrack = Arguments.createMap();
            textTrack.putInt("index", i);
            textTrack.putString("title", format.id != null ? format.id : "");
            textTrack.putString("type", format.sampleMimeType);
            textTrack.putString("language", format.language != null ? format.language : "");
            textTracks.pushMap(textTrack);
        }
        return textTracks;
    }


    private void onBuffering(boolean buffering) {
        Timber.d("onBuffering");
        if (isBuffering == buffering) {
            return;
        }

        isBuffering = buffering;
        if (buffering) {
            eventEmitter.buffering(true);
        } else {
            eventEmitter.buffering(false);
        }
    }


    @Override
    public void onPositionDiscontinuity(int reason) {
        Timber.d("onPositionDiscontinuity");
        if (playerNeedsSource) {
            // This will only occur if the user has performed a seek whilst in the error state. Update the
            // resume position so that if the user then retries, playback will resume from the position to
            // which they seeked.
            updateResumePosition();
        }
        // When repeat is turned on, reaching the end of the video will not cause a state change
        // so we need to explicitly detect it.
        if (reason == ExoPlayer.DISCONTINUITY_REASON_PERIOD_TRANSITION
                && player.getRepeatMode() == Player.REPEAT_MODE_ONE) {
            eventEmitter.end();
        }
    }


    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        //Timber.d("onTimelineChanged");
        // Do nothing.
    }


    @Override
    public void onSeekProcessed() {
        //Timber.d("onSeekProcessed");
        eventEmitter.seek(player.getCurrentPosition(), seekTime);
        seekTime = C.TIME_UNSET;
    }


    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        Timber.d("onShuffleModeEnabledChanged");
        // Do nothing.
    }


    @Override
    public void onRepeatModeChanged(int repeatMode) {
        Timber.d("onRepeatModeChanged");
        // Do nothing.
    }


    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        //Timber.d("onTracksChanged");
        // Do Nothing.
    }


    @Override
    public void onPlaybackParametersChanged(PlaybackParameters params) {
        Timber.d("onPlaybackParametersChanged");
        eventEmitter.playbackRateChange(params.speed);
    }


    @Override
    public void onPlayerError(ExoPlaybackException e) {
        Timber.d("onPlayerError");

        String errorString = null;
        Exception ex = e;
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException = (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getResources().getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getResources().getString(R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType);
                    } else {
                        errorString = getResources().getString(R.string.error_no_decoder,
                                decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getResources().getString(R.string.error_instantiating_decoder,
                            decoderInitializationException.decoderName);
                }
            }
        } else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
            ex = e.getSourceException();
            errorString = getResources().getString(R.string.unrecognized_media_format);
        }
        if (errorString != null) {
            eventEmitter.error(errorString, ex);
        }

        playerNeedsSource = true;
        if (isBehindLiveWindow(e)) {
            clearResumePosition();
            //initializePlayer();
            initializePlayerOld();
        } else {
            updateResumePosition();
        }
    }


    private static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }


    public int getTrackRendererIndex(int trackType) {
        if (player != null) {
            int rendererCount = player.getRendererCount();
            for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
                if (player.getRendererType(rendererIndex) == trackType) {
                    return rendererIndex;
                }
            }
        }
        return C.INDEX_UNSET;
    }


    @Override
    public void onMetadata(Metadata metadata) {
        eventEmitter.timedMetadata(metadata);
    }


    // ReactExoplayerViewManager public api


    public void setDrmType(UUID drmType) {
        this.drmUUID = drmType;
    }


    public void setDrmLicenseUrl(String licenseUrl) {
        Timber.d("setDrmLicenseUrl: %s", licenseUrl);
        this.drmLicenseUrl = licenseUrl;
    }


    public void setDrmLicenseHeader(String[] header) {
        Timber.d("setDrmLicenseHeader: %s", header.toString());
        this.drmLicenseHeader = header;
    }


    public void setSrc(final Uri uri, final String extension, Map<String, String> headers) {
        if (uri != null) {
            Timber.d("setSrc and uri is %s, with extension %s", uri.toString(), extension);

            boolean isOriginalSourceNull = srcUri == null;
            boolean isSourceEqual = uri.equals(srcUri);

            this.srcUri = uri;
            this.extension = extension;
            this.requestHeaders = headers;
            this.mediaDataSourceFactory = DataSourceUtil.getDefaultDataSourceFactory(this.themedReactContext, BANDWIDTH_METER, this.requestHeaders);

            if (!isOriginalSourceNull && !isSourceEqual) {
                reloadSource();
            }
        } else {
            Timber.d("setSrc and uri was null with extension %s", extension);
        }
    }


    public void setRawSrc(final Uri uri, final String extension) {
        if (uri != null) {
            boolean isOriginalSourceNull = srcUri == null;
            boolean isSourceEqual = uri.equals(srcUri);

            this.srcUri = uri;
            this.extension = extension;
            this.mediaDataSourceFactory = buildDataSourceFactory(true);

            if (!isOriginalSourceNull && !isSourceEqual) {
                reloadSource();
            }
        }
    }


    public void setProgressUpdateInterval(final float progressUpdateInterval) {
        mProgressUpdateInterval = progressUpdateInterval;
    }


    public void setReportBandwidth(boolean reportBandwidth) {
        mReportBandwidth = reportBandwidth;
    }


    public void setTextTracks(ReadableArray textTracks) {
        Timber.d("setTextTracks");
        this.textTracks = textTracks;
        reloadSource();
    }


    private void reloadSource() {
        Timber.d("reloadSource");
        playerNeedsSource = true;
        //initializePlayer();
        initializePlayerOld();
    }


    public void setResizeModeModifier(@ResizeMode.Mode int resizeMode) {
        exoPlayerView.setResizeMode(resizeMode);
    }


    public void setRepeatModifier(boolean repeat) {
        if (player != null) {
            if (repeat) {
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
            } else {
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
            }
        }
        this.repeat = repeat;
    }


    public void setSelectedTrack(int trackType, String type, Dynamic value) {
        //Timber.d("setSelectedTrack");
        int rendererIndex = getTrackRendererIndex(trackType);
        if (rendererIndex == C.INDEX_UNSET) {
            return;
        }
        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        if (info == null) {
            return;
        }

        TrackGroupArray groups = info.getTrackGroups(rendererIndex);
        int groupIndex = C.INDEX_UNSET;
        int[] tracks = {0};

        if (TextUtils.isEmpty(type)) {
            type = "default";
        }

        DefaultTrackSelector.Parameters disableParameters = trackSelector.getParameters()
                .buildUpon()
                .setRendererDisabled(rendererIndex, true)
                .build();

        if (type.equals("disabled")) {
            trackSelector.setParameters(disableParameters);
            return;
        } else if (type.equals("language")) {
            for (int i = 0; i < groups.length; ++i) {
                Format format = groups.get(i).getFormat(0);
                if (format.language != null && format.language.equals(value.asString())) {
                    groupIndex = i;
                    break;
                }
            }
        } else if (type.equals("title")) {
            for (int i = 0; i < groups.length; ++i) {
                Format format = groups.get(i).getFormat(0);
                if (format.id != null && format.id.equals(value.asString())) {
                    groupIndex = i;
                    break;
                }
            }
        } else if (type.equals("index")) {
            if (value.asInt() < groups.length) {
                groupIndex = value.asInt();
            }
        } else if (type.equals("resolution")) {
            int height = value.asInt();
            for (int i = 0; i < groups.length; ++i) { // Search for the exact height
                TrackGroup group = groups.get(i);
                for (int j = 0; j < group.length; j++) {
                    Format format = group.getFormat(j);
                    if (format.height == value.asInt()) {
                        groupIndex = i;
                        tracks[0] = j;
                        break;
                    }
                }
            }
        } else if (rendererIndex == C.TRACK_TYPE_TEXT && Util.SDK_INT > 18) { // Text default
            // Use system settings if possible
            CaptioningManager captioningManager
                    = (CaptioningManager) themedReactContext.getSystemService(Context.CAPTIONING_SERVICE);
            if (captioningManager != null && captioningManager.isEnabled()) {
                groupIndex = getGroupIndexForDefaultLocale(groups);
            }
        } else if (rendererIndex == C.TRACK_TYPE_AUDIO) { // Audio default
            groupIndex = getGroupIndexForDefaultLocale(groups);
        }

        if (groupIndex == C.INDEX_UNSET && trackType == C.TRACK_TYPE_VIDEO) { // Video auto
            if (groups.length != 0) {
                TrackGroup group = groups.get(0);
                tracks = new int[group.length];
                groupIndex = 0;
                for (int j = 0; j < group.length; j++) {
                    tracks[j] = j;
                }
            }
        } else if (groupIndex == C.INDEX_UNSET) {
            trackSelector.setParameters(disableParameters);
            return;
        }

        DefaultTrackSelector.Parameters selectionParameters = trackSelector.getParameters()
                .buildUpon()
                .setRendererDisabled(rendererIndex, false)
                .setSelectionOverride(rendererIndex, groups, new DefaultTrackSelector.SelectionOverride(groupIndex, tracks))
                .build();
        trackSelector.setParameters(selectionParameters);
    }


    private int getGroupIndexForDefaultLocale(TrackGroupArray groups) {
        if (groups.length == 0) {
            return C.INDEX_UNSET;
        }

        int groupIndex = 0; // default if no match
        String locale2 = Locale.getDefault().getLanguage(); // 2 letter code
        String locale3 = Locale.getDefault().getISO3Language(); // 3 letter code
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            String language = format.language;
            if (language != null && (language.equals(locale2) || language.equals(locale3))) {
                groupIndex = i;
                break;
            }
        }
        return groupIndex;
    }


    public void setSelectedVideoTrack(String type, Dynamic value) {
        Timber.d("setSelectedVideoTrack, type: %s", type);
        videoTrackType = type;
        videoTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_VIDEO, videoTrackType, videoTrackValue);
    }


    public void setSelectedAudioTrack(String type, Dynamic value) {
        Timber.d("setSelectedAudioTrack, type: %s", type);
        audioTrackType = type;
        audioTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_AUDIO, audioTrackType, audioTrackValue);
    }


    public void setSelectedTextTrack(String type, Dynamic value) {
        Timber.d("setSelectedTextTrack, type: %s", type);
        textTrackType = type;
        textTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_TEXT, textTrackType, textTrackValue);
    }


    public void setPausedModifier(boolean paused) {
        isPaused = paused;
        if (player != null) {
            if (!paused) {
                startPlayback();
            } else {
                pausePlayback();
            }
        }
    }


    public void setMutedModifier(boolean muted) {
        audioVolume = muted ? 0.f : 1.f;
        if (player != null) {
            player.setVolume(audioVolume);
        }
    }


    public void setVolumeModifier(float volume) {
        audioVolume = volume;
        if (player != null) {
            player.setVolume(audioVolume);
        }
    }


    public void seekTo(long positionMs) {
        if (player != null) {
            seekTime = positionMs;
            player.seekTo(positionMs);
        }
    }


    public void setRateModifier(float newRate) {
        rate = newRate;

        if (player != null) {
            PlaybackParameters params = new PlaybackParameters(rate, 1f);
            player.setPlaybackParameters(params);
        }
    }


    public void setMaxBitRateModifier(int newMaxBitRate) {
        maxBitRate = newMaxBitRate;
        if (player != null) {
            trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));
        }
    }


    public void setPlayInBackground(boolean playInBackground) {
        this.playInBackground = playInBackground;
    }


    public void setDisableFocus(boolean disableFocus) {
        this.disableFocus = disableFocus;
    }


    public void setFullscreen(boolean fullscreen) {
        if (fullscreen == isFullscreen) {
            return; // Avoid generating events when nothing is changing
        }
        isFullscreen = fullscreen;

        Activity activity = themedReactContext.getCurrentActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        int uiOptions;
        if (isFullscreen) {
            if (Util.SDK_INT >= 19) { // 4.4+
                uiOptions = SYSTEM_UI_FLAG_HIDE_NAVIGATION | SYSTEM_UI_FLAG_IMMERSIVE_STICKY | SYSTEM_UI_FLAG_FULLSCREEN;
            } else {
                uiOptions = SYSTEM_UI_FLAG_HIDE_NAVIGATION | SYSTEM_UI_FLAG_FULLSCREEN;
            }
            eventEmitter.fullscreenWillPresent();
            decorView.setSystemUiVisibility(uiOptions);
            eventEmitter.fullscreenDidPresent();
        } else {
            uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
            eventEmitter.fullscreenWillDismiss();
            decorView.setSystemUiVisibility(uiOptions);
            eventEmitter.fullscreenDidDismiss();
        }
    }


    public void setUseTextureView(boolean useTextureView) {
        //exoPlayerView.setUseTextureView(useTextureView);
        boolean finallyUseTextureView = useTextureView && this.drmUUID == null;
        exoPlayerView.setUseTextureView(finallyUseTextureView);
    }


    public void setHideShutterView(boolean hideShutterView) {
        exoPlayerView.setHideShutterView(hideShutterView);
    }


    public void setBufferConfig(int newMinBufferMs, int newMaxBufferMs, int newBufferForPlaybackMs, int newBufferForPlaybackAfterRebufferMs) {
        Timber.d("setBufferConfig");
        minBufferMs = newMinBufferMs;
        maxBufferMs = newMaxBufferMs;
        bufferForPlaybackMs = newBufferForPlaybackMs;
        bufferForPlaybackAfterRebufferMs = newBufferForPlaybackAfterRebufferMs;
        releasePlayer();
        //initializePlayer();
        initializePlayerOld();
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Timber.d("onAttachedWindow");
        //initializePlayer();
        initializePlayerOld();
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        /* We want to be able to continue playing audio when switching tabs.
         * Leave this here in case it causes issues.
         */
        // stopPlayback();
    }


    @Override
    public void onHostResume() {
        Timber.d("onHostResume");
        if (!playInBackground || !isInBackground) {
            setPlayWhenReady(!isPaused);
        }
        isInBackground = false;
    }


    @Override
    public void onHostPause() {
        isInBackground = true;
        if (playInBackground) {
            return;
        }
        setPlayWhenReady(false);
    }


    @Override
    public void onHostDestroy() {
        stopPlayback();
    }


    public void cleanUpResources() {
        stopPlayback();
    }


    //--------------------------------------------
    //DRM related
    //--------------------------------------------
    private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid, String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
        if (Util.SDK_INT < 18) {
            Timber.w("The SDK was lower than 18, so we can't play DRM content :(");
            return null;
        }

        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl, buildHttpDataSourceFactory(false));

        if (keyRequestPropertiesArray != null) {
            for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
                drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i], keyRequestPropertiesArray[i + 1]);
            }
        }

        return new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), drmCallback, null, mainHandler, this);
    }


    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return new DefaultHttpDataSourceFactory("sctv", useBandwidthMeter ? BANDWIDTH_METER : null);
    }
}
