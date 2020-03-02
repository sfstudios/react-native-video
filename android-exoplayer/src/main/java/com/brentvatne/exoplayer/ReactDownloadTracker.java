package com.brentvatne.exoplayer;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.StreamKey;
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
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * Tracks downloaded media in android
 **/
public class ReactDownloadTracker extends ReactContextBaseJavaModule {

    @Override
    public String getName() {
        return null;
    }

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
        super((ReactApplicationContext)context);
        this.context = context;
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
    public void deleteDownload(FragmentManager fragmentManager, String name, Uri uri, String extension, RenderersFactory renderersFactory) {
        Download download = downloadHashMap.get(uri);

        if (download != null) {
            DownloadService.sendRemoveDownload(context, ReactDownloadService.class, download.request.uri.toString(), false);
        }
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

    @ReactMethod
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

    /*
    private final class StartDownloadDialogHelper implements DownloadHelper.Callback, DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
        private final FragmentManager fragmentManager;
        private final DownloadHelper downloadHelper;
        private final String name;

      //  private ReactTrackSelectionDialog trackSelectionDialog = new ReactTrackSelectionDialog();
        private MappedTrackInfo mappedTrackInfo;

        public StartDownloadDialogHelper(FragmentManager fragmentManager, DownloadHelper downloadHelper, String name){
            this.name = name;
            this.downloadHelper = downloadHelper;
            this.fragmentManager = fragmentManager;
            downloadHelper.prepare(this);

        }

        public void release(){
            downloadHelper.release();
        }

        @Override
        public void onPrepared( DownloadHelper helper){
            if(helper.getPeriodCount() == 0){
                Log.d(TAG, "No periods found donwloading entire stream.");
                startDownload();
                downloadHelper.release();
            }
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int which){
            return;
        }

        @Override
        public void onPrepareError(DownloadHelper helper, IOException e){
            Toast.makeText(context, "Something went wrong when starting the download", Toast.LENGTH_LONG).show();
            Log.e(TAG,  e.toString());
        }

        @Override
        public void onDismiss(DialogInterface dialogInterface){
            downloadHelper.release();
        }

        public void startSelectedDownload(){
            DownloadRequest downloadRequest = buildDownloadRequest();
            startDownload(downloadRequest);
        }

        private void startDownload() {
            startDownload(buildDownloadRequest());
        }

        private void startDownload(DownloadRequest downloadRequest){
            DownloadService.sendAddDownload(context, ReactDownloadService.class, downloadRequest, false);
        }

        private DownloadRequest buildDownloadRequest() {
            return downloadHelper.getDownloadRequest(Util.getUtf8Bytes(name));
        }
    }
    */

}
