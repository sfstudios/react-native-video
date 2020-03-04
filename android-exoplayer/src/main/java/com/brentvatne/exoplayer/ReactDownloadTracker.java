package com.brentvatne.exoplayer;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.brentvatne.react.R;
import com.facebook.react.ReactApplication;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import okhttp3.OkHttpClient;


/**
 * Tracks downloaded media in android
 **/
public class ReactDownloadTracker extends ReactContextBaseJavaModule {



    // Listener for tracked downloads.
    public interface Listener {

        // Called onChange for tracked downloads.
        void onDownloadsChanged();
    }

    private static final String TAG = "DownloadTracker";

    private final Context context;
    //private final DataSource.Factory dataSourceFactory;
    private final CopyOnWriteArraySet<Listener> listeners;
    private final HashMap<Uri, Download> downloadHashMap;
    private final DownloadIndex downloadIndex;
    private final DefaultTrackSelector.Parameters trackSelectorParams;


    public ReactDownloadTracker(
            Context context,/* DataSource.Factory dataSourceFactory,*/ DownloadManager downloadManager
    ) {
        super((ReactApplicationContext)context.getApplicationContext());
        this.context = context.getApplicationContext();
        //this.dataSourceFactory = dataSourceFactory;
        listeners = new CopyOnWriteArraySet<>();
        downloadHashMap = new HashMap<>();
        downloadIndex = downloadManager.getDownloadIndex();
        trackSelectorParams = DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS;
        downloadManager.addListener(new DownloadManager.Listener() {
            @Override
            public void onDownloadChanged(DownloadManager downloadManager, Download download) {
                downloadHashMap.put(download.request.uri, download);
                for (Listener listener : listeners) {
                    listener.onDownloadsChanged();
                }
            }

            @Override
            public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
                downloadHashMap.remove(download.request.uri);
                for (Listener listener : listeners) {
                    listener.onDownloadsChanged();
                }
            }
        });
        loadDownloads();
    }

     @Override
    public String getName() {
        return TAG;
    }
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isDownloaded(Uri uri) {
        Download download = downloadHashMap.get(uri);
        return download != null && download.state != Download.STATE_FAILED;
    }

    @ReactMethod
    public void toggleDownload(Uri uri, @Nullable ReadableMap src) {
        Log.d("ReactDownloadTracker", "This is my uri: " + uri.toString());

        Log.d("ReactDownloadTracker", "This is my src Object? that is passed to toggleDL: " + src.toString());

        /*
        Download download = downloadHashMap.get(uri);

        if (download != null) {
            DownloadService.sendRemoveDownload(context, ReactDownloadService.class, download.request.uri.toString(), false);
        }

        RenderersFactory renderersFactory = new DefaultRenderersFactory(getReactApplicationContext());

        // Pass headers to datasourceFactory.
        OkHttpDataSourceFactory myDatasourceFactory = new OkHttpDataSourceFactory(new OkHttpClient(), Util.getUserAgent(getReactApplicationContext().getApplicationContext(), "ReactDownloader"));
        Map<String, String> requestHeaders = src.hasKey("headers") ? toStringMap(src.getMap("headers")) : null;
        myDatasourceFactory.getDefaultRequestProperties().set(requestHeaders);

        // DRM Variables
        // Below values are placeholders.
        UUID drmUUID = src.hasKey("drm") ? new UUID( 1, 2 ) : null;
        String drmLicenseUrl = src.hasKey("drm") ? getCorrectKey() : null;
        String[] drmLicenseHeader = src.hasKey("drm") ? getCorrectKey() : null;

        //DRMSession
        DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
                    if (drmUUID != null) {
                        try {
                            drmSessionManager = buildDrmSessionManager(drmUUID, drmLicenseUrl,
                                    drmLicenseHeader);
                        } catch (UnsupportedDrmException e) {
                            int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
                                    : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                                    ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
                            //eventEmitter.error(getResources().getString(errorStringId), e);
                            System.out.println(e);
                            return;
                        }
                    }

        DefaultTrackSelector.Parameters trackSelectorParameter = DefaultTrackSelector.Parameters.DEFAULT;

        // pass drmsessionmanager into DownloadHelper
        DownloadHelper.forDash(uri, myDatasourceFactory, renderersFactory, drmSessionManager, trackSelectorParameter);

         */
    }

    private void loadDownloads() {
        try (DownloadCursor loadedDownloads = downloadIndex.getDownloads()) {
            while (loadedDownloads.moveToNext()) {
                Download download = loadedDownloads.getDownload();
                downloadHashMap.put(download.request.uri, download);
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

     private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
                                                                            String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
        /*
        if (Util.SDK_INT < 18) {
            return null;
        }
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
                buildHttpDataSourceFactory(false));
        if (keyRequestPropertiesArray != null) {
            for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
                drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
                        keyRequestPropertiesArray[i + 1]);
            }
        }
        return new DefaultDrmSessionManager<>(uuid,
                FrameworkMediaDrm.newInstance(uuid), drmCallback, null, false, 3);*/
        return null;
    }

    private void startDownload(Uri resourceLocation, String contentId, String name) {

        startDownload(buildDownloadRequest(resourceLocation, contentId, name));
    }

    private void startDownload(DownloadRequest downloadRequest) {
        DownloadService.sendAddDownload(context, ReactDownloadService.class, downloadRequest, false);
    }

    private DownloadRequest buildDownloadRequest(Uri uri, String contentId, String name) {
       // DownloadHelper helper = new DownloadHelper()
        //return downloadHelper.getDownloadRequest(Util.getUtf8Bytes(name));

        List<StreamKey> streamKeyList = new ArrayList<StreamKey>();
        DownloadRequest myDownloadRequest = new DownloadRequest(contentId, DownloadRequest.TYPE_DASH, uri, streamKeyList, name, Util.getUtf8Bytes(name));

        return myDownloadRequest;
    }

    /**
     * toStringMap converts a {@link ReadableMap} into a HashMap.
     *
     * @param readableMap The ReadableMap to be conveted.
     * @return A HashMap containing the data that was in the ReadableMap.
     * @see 'Adapted from https://github.com/artemyarulin/react-native-eval/blob/master/android/src/main/java/com/evaluator/react/ConversionUtil.java'
     */
    public static Map<String, String> toStringMap(@Nullable ReadableMap readableMap) {
        if (readableMap == null)
            return null;

        com.facebook.react.bridge.ReadableMapKeySetIterator iterator = readableMap.keySetIterator();
        if (!iterator.hasNextKey())
            return null;

        Map<String, String> result = new HashMap<>();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            result.put(key, readableMap.getString(key));
        }

        return result;
    }

}
