package com.brentvatne.exoplayer;

import android.app.Notification;
import android.content.Context;

import com.brentvatne.react.R;
import com.facebook.react.ReactApplication;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.util.List;

public class ReactDownloadService extends DownloadService {

    private static final int JOB_ID = 1;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private Context context;


    public ReactDownloadService(){
        // No idea of channedId jsut a random number atm.
        super(FOREGROUND_NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, "ReactVideoPlayerNotification", 0);
        this.context = getApplicationContext();
    }


    @Override
    public DownloadManager getDownloadManager() {
        // This will only happen once, because getDownloadManager is guaranteed to be called only once
        // in the life cycle of the process.
        ReactApplication application = (ReactApplication) getApplication();
        DatabaseProvider databaseProvider = new ExoDatabaseProvider(context);


        File cacheDir = new File(getCacheDir(), "Downloads");
        SimpleCache downloadCache = new SimpleCache(cacheDir, new NoOpCacheEvictor(), databaseProvider);



        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory("ReactNativeExoPlayer");

        DownloadManager downloadManager = new DownloadManager(context, databaseProvider, downloadCache, dataSourceFactory);

//        DownloadNotificationHelper downloadNotificationHelper =
//                application.getDownloadNotificationHelper();
//        downloadManager.addListener(
//                new TerminalStateNotificationHelper(
//                        this, downloadNotificationHelper, FOREGROUND_NOTIFICATION_ID + 1));
        downloadManager.setMinRetryCount(3);

        return downloadManager;
    }


    @Override
    public PlatformScheduler getScheduler(){
        return Util.SDK_INT >= 21 ? new PlatformScheduler(this, JOB_ID) : null;
    }

    @Override
    protected Notification getForegroundNotification(List<Download> downloads){

        DownloadNotificationHelper notificationHelper = new DownloadNotificationHelper(context, "ReactVideoPlayerNotification");

        return notificationHelper.buildDownloadCompletedNotification(R.drawable.download_icon, null, null);

    }

}
