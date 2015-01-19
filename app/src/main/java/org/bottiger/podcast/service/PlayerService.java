package org.bottiger.podcast.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.bottiger.podcast.SettingsActivity;
import org.bottiger.podcast.listeners.PlayerStatusObservable;
import org.bottiger.podcast.notification.NotificationPlayer;
import org.bottiger.podcast.playlist.Playlist;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.receiver.HeadsetReceiver;
import org.bottiger.podcast.utils.PodcastLog;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import javax.annotation.Nullable;

/**
 * The service which handles the audio extended_player. This is responsible for playing
 * including controlling the playback. Play, pause, stop etc.
 * 
 * @author Arvid Böttiger
 */
public class PlayerService extends Service implements
		AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "action_play";
    public static final String ACTION_PAUSE = "action_pause";
    public static final String ACTION_REWIND = "action_rewind";
    public static final String ACTION_FAST_FORWARD = "action_fast_foward";
    public static final String ACTION_NEXT = "action_next";
    public static final String ACTION_PREVIOUS = "action_previous";
    public static final String ACTION_STOP = "action_stop";

	/** Which action to perform when a track ends */
	private static enum NextTrack {
		NONE, NEW_TRACK, NEXT_IN_PLAYLIST
	}

	private static NextTrack nextTrack = NextTrack.NEXT_IN_PLAYLIST;
	
	private Playlist mPlaylist;

	private static final int FADEIN = 0;
	private static final int TRACK_ENDED = 1;
	private static final int SERVER_DIED = 2;
	public static final int PlayerService_STATUS = 1;

	private static final String WHERE = ItemColumns.STATUS + ">"
			+ ItemColumns.ITEM_STATUS_MAX_DOWNLOADING_VIEW + " AND "
			+ ItemColumns.STATUS + "<"
			+ ItemColumns.ITEM_STATUS_MAX_PLAYLIST_VIEW + " AND "
			+ ItemColumns.FAIL_COUNT + " > 100";

	private static final String ORDER = ItemColumns.FAIL_COUNT + " ASC";

	private final PodcastLog log = PodcastLog.getLog(getClass());

    private MediaSession mMediaSession;
	private MyPlayer mPlayer = null;
    private MediaController mController;


	private NotificationManager mNotificationManager;
    @Nullable private NotificationPlayer mNotificationPlayer;
    @Nullable private MediaSessionManager mMediaSessionManager;

	// AudioManager
	private AudioManager mAudioManager;
	private ComponentName mControllerComponentName;

	private FeedItem mItem = null;
	private boolean mUpdate = false;
	private boolean mResumePlayback = false;

	/**
	 * Phone state listener. Will pause the playback when the phone is ringing
	 * and continue it afterwards
	 */
	private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				int ringvolume = audioManager
						.getStreamVolume(AudioManager.STREAM_RING);
				if (ringvolume > 0) {
					mResumePlayback = (isPlaying() || mResumePlayback)
							&& (mItem != null);
					pause();
				}
			} else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
				mResumePlayback = (isPlaying() || mResumePlayback)
						&& (mItem != null);
				pause();
			} else if (state == TelephonyManager.CALL_STATE_IDLE) {
				if (mResumePlayback) {

					startAndFadeIn();
					mResumePlayback = false;
				}
			}
		}
	};

    @TargetApi(21)
    public MediaSession.Token getToken() {
        /*
        ComponentName componentName = new ComponentName(getPackageName(), NotificationListener.class.getName());
        List<MediaController> list =  mMediaSessionManager.getActiveSessions(componentName);
        return list.get(0).getSessionToken();*/
        return mMediaSession.getSessionToken();
    }

	private void startAndFadeIn() {
		handler.sendEmptyMessageDelayed(FADEIN, 10);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mPlaylist = new Playlist(this);
		mPlaylist.populatePlaylistIfEmpty();
		
		mPlayer = new MyPlayer();
		mPlayer.setHandler(handler);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mMediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }

		TelephonyManager tmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		tmgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		this.mControllerComponentName = new ComponentName(this,
				HeadsetReceiver.class);
		log.debug("onCreate(): " + mControllerComponentName);
		this.mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);


        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // from https://github.com/googlesamples/android-MediaBrowserService/blob/master/Application/src/main/java/com/example/android/mediabrowserservice/MusicService.java
            // Start a new MediaSession
            mMediaSession = new MediaSession(this, "MusicService");
            //setSessionToken(mMediaSession.getSessionToken());
            //mMediaSession.setCallback(new MediaSessionCallback());
            mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

            mController = new MediaController(getApplicationContext(), getToken()); // .fromToken( mSession.getSessionToken() );
        }
	}

    @TargetApi(21)
    private void handleIntent( Intent intent ) {
        if( intent == null || intent.getAction() == null )
            return;

        String action = intent.getAction();

        if( action.equalsIgnoreCase( ACTION_PLAY ) ) {
            //mController.getTransportControls().play();
            start();
        } else if( action.equalsIgnoreCase( ACTION_PAUSE ) ) {
            //mController.getTransportControls().pause();
            pause();
        } else if( action.equalsIgnoreCase( ACTION_FAST_FORWARD ) ) {
            mController.getTransportControls().fastForward();
        } else if( action.equalsIgnoreCase( ACTION_REWIND ) ) {
            mController.getTransportControls().rewind();
        } else if( action.equalsIgnoreCase( ACTION_PREVIOUS ) ) {
            mController.getTransportControls().skipToPrevious();
        } else if( action.equalsIgnoreCase( ACTION_NEXT ) ) {
            mController.getTransportControls().skipToNext();
        } else if( action.equalsIgnoreCase( ACTION_STOP ) ) {
            mController.getTransportControls().stop();
        }
    }

	@Override
	public void onAudioFocusChange(int focusChange) {
		if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
			// Pause playback
			pause();
			mResumePlayback = true;
		} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			// Resume playback
			if (mResumePlayback) {
				start();
				mResumePlayback = false;
			}
		} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
			mAudioManager
					.unregisterMediaButtonEventReceiver(mControllerComponentName);
			mAudioManager.abandonAudioFocus(this);
			// Stop playback
			stop();
		}
	}

	private class MyPlayer {
		private MediaPlayer mMediaPlayer = new MediaPlayer();

		private Handler mHandler;
		private boolean mIsInitialized = false;

		private boolean isPreparingMedia = false;

		int bufferProgress = 0;

		int startPos = 0;

		public MyPlayer() {
			// mMediaPlayer.setWakeMode(PlayerService.this,
			// PowerManager.PARTIAL_WAKE_LOCK);
		}

		public void setDataSourceAsync(String path, int startPos) {
			try {
				mMediaPlayer.reset();
				mMediaPlayer.setDataSource(path);
				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

				this.startPos = startPos;
				this.isPreparingMedia = true;

				mMediaPlayer.setOnPreparedListener(preparedlistener);
				mMediaPlayer.prepareAsync();
			} catch (IOException ex) {
				// TODO: notify the user why the file couldn't be opened
				mIsInitialized = false;
				return;
			} catch (IllegalArgumentException ex) {
				// TODO: notify the user why the file couldn't be opened
				mIsInitialized = false;
				return;
			}
			mMediaPlayer.setOnCompletionListener(listener);
			mMediaPlayer.setOnBufferingUpdateListener(bufferListener);
			mMediaPlayer.setOnErrorListener(errorListener);

			mIsInitialized = true;
		}

		public boolean isInitialized() {
			return mIsInitialized;
		}

		public void toggle() {
			if (mMediaPlayer.isPlaying())
				pause();
			else
				start();
		}

		public void start() {
			notifyStatus();

			// Request audio focus for playback
			int result = mAudioManager.requestAudioFocus(PlayerService.this,
			// Use the music stream.
					AudioManager.STREAM_MUSIC,
					// Request permanent focus.
					AudioManager.AUDIOFOCUS_GAIN);

			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				mAudioManager
						.registerMediaButtonEventReceiver(mControllerComponentName);
				mMediaPlayer.start();

				PlayerStatusObservable
						.updateStatus(PlayerStatusObservable.STATUS.PLAYING);
			}
		}

		public void stop() {
			mMediaPlayer.reset();
			mIsInitialized = false;
			stopForeground(true);
			PlayerStatusObservable
					.updateStatus(PlayerStatusObservable.STATUS.STOPPED);
		}

		public void release() {
			dis_notifyStatus();
			stop();
			mMediaPlayer.release();
			mAudioManager
					.unregisterMediaButtonEventReceiver(mControllerComponentName);
			mIsInitialized = false;
		}

		/**
		 * Test of the extended_player is playing something right now
		 * 
		 * @return Is the extended_player playing right now
		 */
		public boolean isPlaying() {
			return mMediaPlayer.isPlaying();
		}

		/**
		 * Pause the current playing item
		 */
		public void pause() {
			mMediaPlayer.pause();
			PlayerStatusObservable
					.updateStatus(PlayerStatusObservable.STATUS.PAUSED);
		}

		@Deprecated
		public int getBufferProgress() {
			return this.bufferProgress;
		}

		public void setHandler(Handler handler) {
			mHandler = handler;
		}

		MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				FeedItem item = PlayerService.this.mItem;
				// item.markAsListened();
				// item.update(getContentResolver());

				if (!isPreparingMedia)
					mHandler.sendEmptyMessage(TRACK_ENDED);

			}
		};

		MediaPlayer.OnBufferingUpdateListener bufferListener = new MediaPlayer.OnBufferingUpdateListener() {
			@Override
			public void onBufferingUpdate(MediaPlayer mp, int percent) {
				MyPlayer.this.bufferProgress = percent;
			}
		};

		MediaPlayer.OnPreparedListener preparedlistener = new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				// notifyChange(ASYNC_OPEN_COMPLETE);
				mp.seekTo(startPos);
				start();
				isPreparingMedia = false;
				PlayerService.setNextTrack(NextTrack.NEXT_IN_PLAYLIST);
			}
		};

		MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				log.debug("onError() " + what + " : " + extra);

				switch (what) {
				case MediaPlayer.MEDIA_ERROR_SERVER_DIED:

					dis_notifyStatus();
					mIsInitialized = false;
					mMediaPlayer.release();

					mMediaPlayer = new MediaPlayer();
					mHandler.sendMessageDelayed(
							mHandler.obtainMessage(SERVER_DIED), 2000);
					return true;
				default:
					break;
				}
				return false;
			}
		};

		public long duration() {
			return mMediaPlayer.getDuration();
		}

		public long position() {
			return mMediaPlayer.getCurrentPosition();
		}

		public long seek(long whereto) {
			mMediaPlayer.seekTo((int) whereto);
			return whereto;
		}

		public void setVolume(float vol) {
			mMediaPlayer.setVolume(vol, vol);
		}
	}

	private final Handler handler = new Handler() {
		float mCurrentVolume = 1.0f;

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case FADEIN:
				if (!isPlaying()) {
					mCurrentVolume = 0f;
					mPlayer.setVolume(mCurrentVolume);
					start();
					handler.sendEmptyMessageDelayed(FADEIN, 10);
				} else {
					mCurrentVolume += 0.01f;
					if (mCurrentVolume < 1.0f) {
						handler.sendEmptyMessageDelayed(FADEIN, 10);
					} else {
						mCurrentVolume = 1.0f;
					}
					mPlayer.setVolume(mCurrentVolume);
				}
				break;
			case TRACK_ENDED:
				long repeat_mode = getPref();

				if (mItem != null) {

					if (getNextTrack() == NextTrack.NEXT_IN_PLAYLIST) {
						long nextItemId = getNextId();

						if (nextItemId == -1) {
							dis_notifyStatus();
							mPlayer.stop();
						} else {
							playNext(nextItemId);
						}
						mUpdate = true;
					}

					// deprecated - I think
					/*
					 * if (repeat_mode == REPEAT_MODE_REPEAT_ONE) { long id =
					 * mItem.id; mItem = null; play(id); } else if (repeat_mode
					 * == REPEAT_MODE_REPEAT) { if (nextItemId == -1) { //
					 * nextItemId = getFirst(); }
					 * 
					 * if (nextItemId > -1) play(nextItemId);
					 * 
					 * } else if (repeat_mode == REPEAT_MODE_NO_REPEAT) { if
					 * (nextItemId > -1) play(nextItemId);
					 * 
					 * }
					 */

				}

				break;

			case SERVER_DIED:
				break;

			}
		}
	};

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		log.debug("onStart()");
	}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleIntent( intent );
        return super.onStartCommand(intent, flags, startId);
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mPlayer != null) {
			mPlayer.release();
		}

		log.debug("onDestroy()");
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		log.debug("onLowMemory()");
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	/**
	 * Hide the notification
	 */
	private void dis_notifyStatus() {
		// //mNotificationManager.cancel(R.layout.playing_episode);
		// setForeground(false);
		if (mNotificationPlayer != null)
			mNotificationPlayer.hide();
	}

	/**
	 * Display a notification with the current podcast
	 */
	private Notification notifyStatus() {

		if (mNotificationPlayer == null)
			mNotificationPlayer = new NotificationPlayer(this, mItem);

        mNotificationPlayer.setPlayerService(this);
		return mNotificationPlayer.show();
    }

	public void playNext(long nextId) {
		// assert playlistAdapter != null;

		// Cursor firstItem = (Cursor) playlistAdapter.getItem(0);
		// playlistAdapter.notifyDataSetChanged();
		if (mItem != null)
			mItem.trackEnded(getContentResolver());
		play(nextId);
	}

	public void play(long id) {

		// Pause the current episode in order to save the current state
		if (mPlayer.isPlaying())
			mPlayer.pause();

		if (mItem != null) {
			if ((mItem.id == id) && mPlayer.isInitialized()) {
				if (mPlayer.isPlaying() == false) {
					start();
				}
				return;
			}

			if (mPlayer.isPlaying()) {
				mItem.updateOffset(getContentResolver(), mPlayer.position());
				stop();
			}
		}

		mItem = FeedItem.getById(getContentResolver(), id);

		if (mItem == null)
			return;

		String dataSource = mItem.isDownloaded() ? mItem.getAbsolutePath()
				: mItem.getURL();

		int offset = mItem.offset < 0 ? 0 : mItem.offset;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()    );

        if (offset == 0 && prefs.getBoolean("pref_stream_proxy", false))
            dataSource = HTTPDService.proxyURL(mItem.id);

		mPlayer.setDataSourceAsync(dataSource, offset);
		
	    new Thread(new Runnable() {
	        public void run() {
	        	if (mItem.priority != 1)
	        		mItem.setPriority(null, getApplication());
	    		mItem.update(getContentResolver());
	        }
	    }).start();
	    
	}

    /**
     *
     * @param id
     * @return True of the songs start to play
     */
	public boolean toggle(long id) {
		if (mPlayer.isPlaying() == false || this.getCurrentItem().getId() != id) {
			play(id);
            return true;
		} else {
			mPlayer.pause();
            return false;
		}
	}

	@Deprecated
	public void toggle() {
		if (mPlayer.isPlaying() == false && mItem != null) {
			mPlayer.start();
		} else {
			mPlayer.pause();
		}
	}

	public void start() {
		if (mPlayer.isPlaying() == false) {
			mPlayer.start();
		}
	}

	public void pause() {
		if (mPlayer.isPlaying() == false) {
			return;
		}

		if ((mItem != null)) {
			mItem.updateOffset(getContentResolver(), mPlayer.position());
		} else {
			log.error("playing but no item!!!");

		}
		dis_notifyStatus();

		mPlayer.pause();
	}

	public void stop() {
		pause();
		mPlayer.stop();
		mItem = null;
		dis_notifyStatus();
		mUpdate = true;
	}

	public boolean isInitialized() {
		return mPlayer.isInitialized();
	}

	public boolean isPlaying() {
		return mPlayer.isPlaying();
	}

	/**
	 * Test of the extended_player is on pause right now
	 * 
	 * @return True if the extended_player is on pause right now
	 */
	public boolean isOnPause() {
		if (isPlaying() || getCurrentItem() == null)
			return false;

		return true;
	}

	public long seek(long offset) {
		offset = offset < 0 ? 0 : offset;

		return mPlayer.seek(offset);

	}

	public long position() {
		return mPlayer.position();
	}

	public long duration() {
		return mPlayer.duration();
	}

	public int bufferProgress() {
		int test = mPlayer.bufferProgress;
		return test;// mPlayer.bufferProgress;
	}

	public void setCurrentItem(FeedItem item) {
		stop();
		mItem = item;
	}

	public FeedItem getCurrentItem() {
		return mItem;
	}

	public boolean getUpdateStatus() {
		return mUpdate;
	}

	public void setUpdateStatus(boolean update) {
		mUpdate = update;
	}

	private FeedItem getFirst() {
		Cursor cursor = null;
		try {

			cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, WHERE, null, ORDER);
			if (cursor == null) {
				return null;
			}
			cursor.moveToFirst();

			FeedItem item = FeedItem.getByCursor(cursor);
			return item;
		} catch (Exception e) {

		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	/**
	 * @return The ID of the next episode in the playlist
	 */
	public long getNextId() {
		FeedItem next = mPlaylist.nextEpisode();
		if (next != null)
			return next.getId();
		return -1;
	}

	/**
	 * Returns whether the next episode to be played should come from the
	 * playlist, or somewhere else
	 * 
	 * @return The type of episode to be played next
	 */
	public static NextTrack getNextTrack() {
		return nextTrack;
	}

	/**
	 * 
	 * @param nextTrack
	 */
	public static void setNextTrack(NextTrack nextTrack) {
		PlayerService.nextTrack = nextTrack;
	}

	public FeedItem getPrev(FeedItem item) {
		FeedItem prev_item = null;
		FeedItem curr_item = null;

		Cursor cursor = null;

		if (item == null) {
			FeedItem.getByCursor(cursor);
			return null;
		}

		try {
			cursor = getContentResolver().query(ItemColumns.URI,
					ItemColumns.ALL_COLUMNS, WHERE, null, ORDER);
			if (cursor == null) {
				return null;
			}
			cursor.moveToFirst();

			do {
				prev_item = curr_item;
				curr_item = FeedItem.getByCursor(cursor);

				if ((curr_item != null) && (item.id == curr_item.id)) {
					return prev_item;
				}

			} while (cursor.moveToNext());

		} catch (Exception e) {

		} finally {
			if (cursor != null)
				cursor.close();
		}

		return null;
	}

	private long getPref() {
		SharedPreferences pref = getSharedPreferences(
				SettingsActivity.HAPI_PREFS_FILE_NAME, Context.MODE_PRIVATE);
		return pref.getLong("pref_repeat", 0);

	}

	private final IBinder binder = new PlayerBinder();

	public class PlayerBinder extends Binder {
		public PlayerService getService() {
			return PlayerService.this;
		}
	}

}
