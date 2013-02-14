package info.bottiger.podcast;

import info.bottiger.podcast.provider.FeedItem;
import info.bottiger.podcast.provider.Subscription;
import info.bottiger.podcast.service.PlayerService;
import info.bottiger.podcast.utils.FilesizeUpdater;
import info.bottiger.podcast.utils.Log;
import info.bottiger.podcast.utils.StrUtils;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

/* Copy of PodcastBaseActivity */
public abstract class PodcastBaseFragment extends FixedListFragment {

	public static final int COLUMN_INDEX_TITLE = 1;

	protected View fragmentView;

	// protected static PodcastService mServiceBinder = null;
	public static PlayerService mPlayerServiceBinder = null;
	protected static ComponentName mService = null;
	// protected final Log log = Log.getLog(getClass());

	protected SimpleCursorAdapter mAdapter;
	// protected Cursor mCursor = null;

	// protected boolean mInit = false;
	protected Intent mPrevIntent = null;

	protected Intent mNextIntent = null;

	OnItemSelectedListener mListener;

	protected final Log log = Log.getLog(getClass());

	private long mLastSeekEventTime;
	private boolean mFromTouch;

	public static final int REFRESH = 1;
	public static final int PLAYITEM = 2;
	public static final int UPDATE_FILESIZE = 3;

	private boolean mShow = true;

	protected Cursor mCursor;

	private static TextView mCurrentTime = null;
	private static SeekBar mProgressBar = null;
	private static TextView mDuration = null;

	public TextView getCurrentTime() {
		return mCurrentTime;
	}

	public void setCurrentTime(TextView mCurrentTime) {
		PodcastBaseFragment.mCurrentTime = mCurrentTime;
	}

	public SeekBar getProgress() {
		return mProgressBar;
	}

	public void setProgressBar(SeekBar mProgress) {
		PodcastBaseFragment.mProgressBar = mProgress;
	}

	public void setDuration(TextView mDuration) {
		PodcastBaseFragment.mDuration = mDuration;
	}

	public ServiceConnection playerServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mPlayerServiceBinder = ((PlayerService.PlayerBinder) service)
					.getService();
			// log.debug("onServiceConnected");
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mPlayerServiceBinder = null;
			// log.debug("onServiceDisconnected");
		}
	};

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mListener = (OnItemSelectedListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement OnArticleSelectedListener");
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		OnItemLongClickListener listener = new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
					int position, long id) {
				return false;
			}

		};

		registerForContextMenu(getListView());
		getListView().setOnItemLongClickListener(listener);

	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.podcast_context, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {

		if (!AdapterView.AdapterContextMenuInfo.class.isInstance(item
				.getMenuInfo()))
			return false;

		AdapterView.AdapterContextMenuInfo cmi = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();

		Object o = getListView().getItemAtPosition(cmi.position);
		Subscription sub = this.getSubscription(o);

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.unsubscribe:
			sub.unsubscribe(getActivity().getApplicationContext());
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	// Container Activity must implement this interface
	public interface OnItemSelectedListener {
		public void onItemSelected(long id);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mService = getActivity().startService(
				new Intent(getActivity(), PlayerService.class));
		Intent bindIntent = new Intent(getActivity(), PlayerService.class);
		getActivity().bindService(bindIntent, playerServiceConnection,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			// unbindService(serviceConnection); TODO
		} catch (Exception e) {
			e.printStackTrace();

		}
	}

	@Override
	public void onPause() {
		super.onPause();

	}

	/*
	abstract public void startInit();
	*/

	public static final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case REFRESH:
				long next = refreshUI();
				queueNextRefresh(next);
				// log.debug("REFRESH: "+next);
				break;

			case UPDATE_FILESIZE:
				FeedItem item = (FeedItem) msg.obj;
				TextView tv = FilesizeUpdater.get(item);
				if (tv != null)
					tv.setText(Math
							.round(item.chunkFilesize * 100.0 / 1024 / 1024)
							/ 100.0 + " MB");
				break;

			default:
				break;
			}
		}
	};

	public static void queueNextRefresh(long delay) {
		Message msg = mHandler.obtainMessage(REFRESH);
		mHandler.removeMessages(REFRESH);
		// if (mPlayerServiceBinder.isPlaying()) // FIXME or something is
		// downloading
		mHandler.sendMessageDelayed(msg, delay);
	}

	protected static long refreshUI() {
		long refresh_time = 500;

		if (mPlayerServiceBinder == null)
			return refresh_time;

		if (mPlayerServiceBinder.isPlaying()) {
			updateCurrentPosition();
		}

		return refresh_time;

	}

	protected long refreshNow() {

		if (mPlayerServiceBinder == null)
			return 500;

		try {
			if (mPlayerServiceBinder.isInitialized() == false) {
				// mCurrentTime.setVisibility(View.INVISIBLE);
				// mTotalTime.setVisibility(View.INVISIBLE);
				mProgressBar.setProgress(0);
				return 500;
			}

			long pos = mPlayerServiceBinder.position();
			long duration = mPlayerServiceBinder.duration();

			// update secondary progress bar
			long chunkSize = mPlayerServiceBinder.getCurrentItem()
					.getCurrentFileSize();
			long totalFileSize = mPlayerServiceBinder.getCurrentItem().filesize;
			int fileProgress;
			fileProgress = (totalFileSize != 0) ? (int) (chunkSize
					/ totalFileSize * mProgressBar.getMax()) : 0;

			// mProgressBar.setSecondaryProgress(fileProgress);
			int fileProgress2 = mPlayerServiceBinder.bufferProgress();
			int fileProgress3 = fileProgress2 * mProgressBar.getMax();
			int fileProgress4 = fileProgress3 / 100;
			mProgressBar.setSecondaryProgress(fileProgress4);

			// updateCurrentPosition(mPlayerServiceBinder.getCurrentItem());
			updateCurrentPosition();

			// mTotalTime.setVisibility(View.VISIBLE);
			// mTotalTime.setText(formatTime( duration ));

			if (mPlayerServiceBinder.isPlaying() == false) {
				// mCurrentTime.setVisibility(View.VISIBLE);
				// mCurrentTime.setText(StrUtils.formatTime( pos ));

				mProgressBar.setProgress((int) (1000 * pos / duration));
				return 500;
			}

			long remaining = 1000 - (pos % 1000);
			if ((pos >= 0) && (duration > 0)) {
				String timeCounter = StrUtils.formatTime(pos);
				// + " / " + StrUtils.formatTime( duration );

				mCurrentTime.setText(timeCounter);

				if (mPlayerServiceBinder.isInitialized()) {
					// mCurrentTime.setVisibility(View.VISIBLE);
					// mTotalTime.setVisibility(View.VISIBLE);
				}
				int nextPos = (int) (1000 * pos / mPlayerServiceBinder
						.duration());
				mProgressBar.setProgress(nextPos);
			} else {
				// mCurrentTime.setText("--:--");
				mProgressBar.setProgress(1000);
			}

			return remaining;
		} catch (Exception ex) {
		}
		return 500;
	}

	protected static void updateCurrentPosition() {
		if (mCurrentTime != null) {
			long pos = mPlayerServiceBinder.position();
			long duration = mPlayerServiceBinder.duration();

			String timeCounter = StrUtils.formatTime(pos);
			String durationString = StrUtils.formatTime(duration);
			mCurrentTime.setText(timeCounter);
			if (mDuration != null)
				mDuration.setText(durationString);
			PlayerActivity.setProgressBar(mProgressBar, mPlayerServiceBinder);
		}
	}

	protected Cursor getCursor() {
		return this.mCursor;
	}

	// HACK, FIX IT
	Subscription getSubscription(Object o) {
		return null;
	}

	public void startInit(int id, Uri columns, String[] projection,
			String condition, String order) {
		assert condition != null;
		assert order != null;
		assert projection != null;

		setListAdapter(mAdapter);

		// Prepare the loader. Either re-connect with an existing one,
		// or start a new one.
		Bundle mBundle = new Bundle();
		mBundle.putParcelable("uri", columns);
		mBundle.putStringArray("projection", projection);
		mBundle.putString("order", order);
		mBundle.putString("condition", condition);
		// FIXME
		getLoaderManager().initLoader(id, mBundle, loaderCallback);
	}

	abstract SimpleCursorAdapter getAdapter(Cursor cursor);

	private LoaderManager.LoaderCallbacks<Cursor> loaderCallback = new LoaderCallbacks<Cursor>() {
		@Override
		public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {

			Uri uri = bundle.getParcelable("uri");
			String[] projection = bundle.getStringArray("projection");

			String order = bundle.getString("order");
			String condition = bundle.getString("condition");

			return new CursorLoader(getActivity(), uri, projection, condition,
					null, order);
		}

		@Override
		public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
			// Swap the new cursor in. (The framework will take care of closing
			// the
			// old cursor once we return.)
			mAdapter = getAdapter(data);
			mAdapter.swapCursor(data);

			// The list should now be shown.
			if (isResumed()) {
				setListShown(true);
			} else {
				setListShownNoAnimation(true);
			}
		}

		@Override
		public void onLoaderReset(Loader<Cursor> loader) {
			// This is called when the last Cursor provided to onLoadFinished()
			// above is about to be closed. We need to make sure we are no
			// longer using it.
			mAdapter.swapCursor(null);

		}
	};
}
