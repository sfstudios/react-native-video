package com.brentvatne.exoplayer;

/**
 * Created by Sotiris Falieris (sotiris@onemanstudio.se) on 2018-09-18.
 */
@SuppressWarnings("unchecked")
public class PlayerUtils {
    /**
     * Whether we are doing a local or a remote playback
     */
    public enum PlaybackLocation {
        LOCAL, REMOTE
    }



    /**
     * The various states that we can be in
     */
    public enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE, FINISHED
    }
}
