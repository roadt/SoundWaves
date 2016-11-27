package org.bottiger.podcast.views;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.ScrollingView;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ScrollerCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.ChangeBounds;
import android.transition.Scene;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.ivbaranov.mfb.MaterialFavoriteButton;
import com.wdullaer.materialdatetimepicker.time.RadialPickerLayout;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import org.bottiger.podcast.SoundWaves;
import org.bottiger.podcast.adapters.PlayerChapterAdapter;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.player.GenericMediaPlayerInterface;

import org.bottiger.podcast.MainActivity;
import org.bottiger.podcast.R;
import org.bottiger.podcast.listeners.PaletteListener;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.IEpisode;
import org.bottiger.podcast.service.PlayerService;
import org.bottiger.podcast.utils.ColorUtils;
import org.bottiger.podcast.utils.PlaybackSpeed;
import org.bottiger.podcast.utils.UIUtils;
import org.bottiger.podcast.utils.chapter.Chapter;
import org.bottiger.podcast.utils.chapter.ChapterUtil;
import org.bottiger.podcast.utils.rxbus.RxBasicSubscriber;
import org.bottiger.podcast.utils.rxbus.RxBusSimpleEvents;
import org.bottiger.podcast.views.dialogs.DialogChapters;
import org.bottiger.podcast.views.dialogs.DialogPlaybackSpeed;

import java.util.Calendar;
import java.util.List;

import io.reactivex.functions.Predicate;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static org.bottiger.podcast.views.PlayerButtonView.StaticButtonColor;

/**
 * Created by apl on 30-09-2014.
 */
public class TopPlayer extends LinearLayout implements PaletteListener, ScrollingView, NestedScrollingChild {

    private static final String TAG = "TopPlayer";

    public NestedScrollingChildHelper scrollingChildHelper;
    private ViewConfiguration mViewConfiguration;

    private Context mContext;

    public static int sizeSmall                 =   -1;
    public static int sizeMedium                =   -1;
    public static int sizeLarge                 =   -1;
    public static int sizeActionbar             =   -1;

    public static int sizeStartShrink           =   -1;
    public static int sizeShrinkBuffer           =   -1;

    private static int sScreenHeight = -1;

    private int mControlHeight = -1;

    private float screenHeight;

    private String mDoFullscreentKey;
    private boolean mFullscreen = false;

    private String mDoDisplayTextKey;
    private boolean mDoDisplayText = false;

    @ColorInt private int mTextColor = Color.BLACK;

    @Nullable IEpisode mCurrentEpisode;

    private TopPlayer Layout;
    private LinearLayout mControls;
    private PlayPauseImageView mPlayPauseButton;

    @Nullable private ViewStub mMoreButtonsStub;
    @Nullable private LinearLayout mExpandedActionsBar;
    @Nullable private PlayerButtonView mFullscreenButton;
    @Nullable private PlayerButtonView mSleepButton;
    @Nullable private ImageView mChapterButton;
    @Nullable private Button mSpeedButton;

    @Nullable private ViewStub mStubChaptersList;
    @Nullable private View mChapterList;
    @Nullable private RecyclerView mChapterRecyclerView;
    @Nullable private PlayerChapterAdapter mAdapter;
    @Nullable private RecyclerView.LayoutManager mLayoutManager;

    private PlayerButtonView mDownloadButton;
    private MaterialFavoriteButton mFavoriteButton;
    private ImageView mFastForwardButton;
    private ImageView mRewindButton;
    private ImageButton mMoreButton;
    private PlayerSeekbar mPlayerSeekbar;

    private View mTriangle;

    private ImageViewTinted mPhoto;

    private boolean mPlaylistEmpty = true;

    private PlayerLayoutParameter mLargeLayout = new PlayerLayoutParameter();

    private GestureDetectorCompat mGestureDetector;
    private TopPLayerScrollGestureListener mTopPLayerScrollGestureListener;

    private @ColorInt int mBackgroundColor = -1;

    private SharedPreferences prefs;
    public ScrollerCompat mScroller;

    private class PlayerLayoutParameter {
        public int SeekBarLeftMargin;
        public int PlayPauseSize;
    }

    public TopPlayer(Context context) {
        super(context, null);
        scrollingChildHelper = new NestedScrollingChildHelper(this);
    }

    public TopPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        scrollingChildHelper = new NestedScrollingChildHelper(this);
        init(context);
    }


    public TopPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        scrollingChildHelper = new NestedScrollingChildHelper(this);
        init(context);
    }

    @TargetApi(21)
    public TopPlayer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        scrollingChildHelper = new NestedScrollingChildHelper(this);
        init(context);
    }


    private void init(@NonNull Context argContext) {
        Log.v(TAG, "App start time: " + System.currentTimeMillis());

        mTopPLayerScrollGestureListener = new TopPLayerScrollGestureListener();
        scrollingChildHelper.setNestedScrollingEnabled(true);

        mGestureDetector = new GestureDetectorCompat(argContext,mTopPLayerScrollGestureListener);

        mViewConfiguration = ViewConfiguration.get(argContext);

        if (isInEditMode()) {
            return;
        }

        mContext = argContext;

        prefs = PreferenceManager.getDefaultSharedPreferences(argContext.getApplicationContext());
        mDoDisplayTextKey = argContext.getResources().getString(R.string.pref_top_player_text_expanded_key);
        mDoDisplayText = prefs.getBoolean(mDoDisplayTextKey, mDoDisplayText);

        mDoFullscreentKey = argContext.getResources().getString(R.string.pref_top_player_fullscreen_key);
        mFullscreen = prefs.getBoolean(mDoFullscreentKey, mFullscreen);

        sizeShrinkBuffer = (int) UIUtils.convertDpToPixel(400, argContext);
        sScreenHeight = UIUtils.getScreenHeight(argContext);

        sizeSmall = argContext.getResources().getDimensionPixelSize(R.dimen.top_player_size_minimum);
        sizeMedium = argContext.getResources().getDimensionPixelSize(R.dimen.top_player_size_medium);
        sizeLarge = sScreenHeight- argContext.getResources().getDimensionPixelSize(R.dimen.top_player_size_maximum_bottom);

        sizeStartShrink = sizeSmall+sizeShrinkBuffer;
        sizeActionbar = argContext.getResources().getDimensionPixelSize(R.dimen.action_bar_height);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
        }
    }


    @SuppressWarnings("ResourceType")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        // FIXME  && !mFullscreen
        if (sizeLarge > 0){
            int hSize = MeasureSpec.getSize(heightMeasureSpec);
            int hMode = MeasureSpec.getMode(heightMeasureSpec);

            int height = -1;
            int mode = MeasureSpec.AT_MOST;

            switch (hMode){
                case MeasureSpec.AT_MOST:
                    height = Math.min(hSize, sizeLarge);
                    break;
                case MeasureSpec.UNSPECIFIED:
                    height = (int) Math.max(hSize, screenHeight);
                    break;
                case MeasureSpec.EXACTLY: {
                    height = mPlaylistEmpty ? hSize : Math.min(hSize, sizeLarge);
                    mode = MeasureSpec.EXACTLY;
                    break;
                }
            }

            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, mode);

            screenHeight = height;
        }


        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();


        Layout = (TopPlayer) findViewById(R.id.top_player);
        mControls = (LinearLayout) findViewById(R.id.top_player_controls);

        getPlayerControlHeight(mControls);

        mPlayPauseButton = (PlayPauseImageView) findViewById(R.id.playpause);
        mDownloadButton = (PlayerButtonView) findViewById(R.id.download);
        mFavoriteButton = (MaterialFavoriteButton) findViewById(R.id.favorite);
        mFastForwardButton = (ImageView) findViewById(R.id.top_player_fastforward);
        mRewindButton = (ImageView) findViewById(R.id.top_player_rewind);
        mPhoto = (ImageViewTinted) findViewById(R.id.session_photo);
        mMoreButton = (ImageButton) findViewById(R.id.player_more_button);
        mPlayerSeekbar = (PlayerSeekbar) findViewById(R.id.top_player_seekbar);

        mMoreButtonsStub = (ViewStub) findViewById(R.id.stub_expanded_action_bar);
        mStubChaptersList = (ViewStub) findViewById(R.id.stub_chapters_list);

        mTriangle = findViewById(R.id.visual_triangle);

        int mPlayPauseLargeSize = mPlayPauseButton.getLayoutParams().height;
        mPlayPauseButton.setIconColor(Color.WHITE);

        mLargeLayout.SeekBarLeftMargin = 0;
        mLargeLayout.PlayPauseSize = mPlayPauseLargeSize;

        final PlayerService ps = PlayerService.getInstance();

        boolean isRecyclerViewEmpty = true;
        if (ps != null && ps.getPlaylist() != null) {
            isRecyclerViewEmpty = ps.getPlaylist().size()<2;
        }

        setPlaylistEmpty(isRecyclerViewEmpty);

        if (mDoDisplayText) {
            //showText();
            onClickMoreButton(mMoreButton);
        }

        mMoreButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View argView) {
                onClickMoreButton(argView);
            }
        });


        mFavoriteButton.setOnFavoriteChangeListener(new MaterialFavoriteButton.OnFavoriteChangeListener() {
            @Override
            public void onFavoriteChanged(MaterialFavoriteButton buttonView, boolean favorite) {
                if (ps != null) {
                    IEpisode episode = SoundWaves.getAppContext(getContext()).getPlaylist().first();
                    if (episode instanceof FeedItem) {
                        FeedItem feedItem = (FeedItem)episode;
                        feedItem.setIsFavorite(favorite);
                    }
                }
            }
        });

        mRewindButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayerService ps = PlayerService.getInstance();
                if (ps != null) {
                    ps.getPlayer().rewind(ps.getCurrentItem());
                }
            }
        });


        mFastForwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IEpisode episode = PlayerService.getCurrentItem();
                PlayerService ps = PlayerService.getInstance();
                if (ps != null) {
                    ps.getPlayer().fastForward(episode);
                }
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        SoundWaves.getRxBus().toObserverable()
                .ofType(RxBusSimpleEvents.PlaybackEngineChanged.class)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .filter(new Func1<RxBusSimpleEvents.PlaybackEngineChanged, Boolean>() {
                    @Override
                    public Boolean call(RxBusSimpleEvents.PlaybackEngineChanged playbackEngineChanged) {
                        return mSpeedButton != null;
                    }
                })
                .subscribe(new Action1<RxBusSimpleEvents.PlaybackEngineChanged>() {
                    @Override
                    public void call(RxBusSimpleEvents.PlaybackEngineChanged event) {
                        assert  mSpeedButton != null;

                        mSpeedButton.setText(getContext().getString(R.string.speed_multiplier, event.speed));
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        VendorCrashReporter.report("subscribeError" , throwable.toString());
                        Log.d(TAG, "error: " + throwable.toString());
                    }
                });

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    private void sleepButtonPressed() {
        int defaultminutes = mContext.getResources().getInteger(R.integer.player_sleep_default);
        sleepButtonPressed(defaultminutes);
    }

    private void sleepButtonPressed(int argMinutes) {
        String toast = getResources().getQuantityString(R.plurals.player_sleep, argMinutes, argMinutes);
        String toast_cancel = getResources().getString(R.string.player_sleep_cancel);

        setSleepTimer(argMinutes);

        if (argMinutes < 0) {
            UIUtils.disPlayBottomSnackBar(this.getRootView(), toast_cancel, null, true);
        } else {
            UIUtils.disPlayBottomSnackBar(this.getRootView(), toast, new OnClickListener() {
                @Override
                public void onClick(View view) {
                    openTimePicker();
                }
            }, R.string.snackbar_change, true);
        }
    }

    private boolean setSleepTimer(int minutes) {
        PlayerService ps = PlayerService.getInstance();
        if (ps == null) {
            String noPlayer = "Could not connect to the Player";
            Toast.makeText(mContext, noPlayer, Toast.LENGTH_LONG).show();
            Log.wtf(TAG, noPlayer);
            return false;
        }

        GenericMediaPlayerInterface player = ps.getPlayer();

        if (minutes < 0) {
            player.cancelFadeOut();
            return true;
        }

        int onemin = 1000 * 60;
        player.FaceOutAndStop(onemin*minutes);
        return true;
    }

    public void bind(@Nullable final IEpisode argEpisode) {

        // FIXME remove
        if (mCurrentEpisode == argEpisode)
            return;

        mCurrentEpisode = argEpisode;

        if (argEpisode == null)
            return;
    }

    private void openTimePicker() {

        Calendar rightNow = Calendar.getInstance();

        final int hour = rightNow.get(Calendar.HOUR_OF_DAY);
        final int minute = rightNow.get(Calendar.MINUTE);

        int minute_sleep = (minute + 30) % 60;

        int setHour = hour;
        if ((minute + 30) > 60) {
            setHour = hour+1;
        }

        TimePickerDialog tpd = TimePickerDialog.newInstance(new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(RadialPickerLayout view, int setHourOfDay, int setMinute, int setSecond) {
                int hourDiff = setHourOfDay - hour;
                int minuteDiff = setMinute-minute;

                int setMinutes = hourDiff*60 + minuteDiff;

                if (setMinutes > 0) {
                    sleepButtonPressed(setMinutes);
                }
            }
        }, setHour, minute_sleep, true);

        tpd.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                sleepButtonPressed(-1);
            }
        });

        tpd.setCancelText(R.string.player_sleep_dialog_cancel_button);
        tpd.setOkText(R.string.player_sleep_dialog_ok_button);

        if (getContext() instanceof Activity) {
            Activity activity = (Activity) getContext();
            tpd.show(activity.getFragmentManager(), "TimePickerDialog");
        }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    float getPlayerHeight() {
        return screenHeight;
    }

    public void setPlaybackSpeedView(float argSpeed) {
        if (mSpeedButton == null)
            return;

        mSpeedButton.setText(PlaybackSpeed.toString(argSpeed));
    }

    synchronized float scrollBy(float argY) {
        float oldHeight = getPlayerHeight();
        return setPlayerHeight(oldHeight - argY);
    }

    public void setPlaylistEmpty(boolean argDoFill) {
        Log.v(TAG, "Player height fill: " + argDoFill);

        if (argDoFill == mPlaylistEmpty)
            return;

        mPlaylistEmpty = argDoFill;
    }

    private float canConsume(float argDiff) {
        float currentHeight = getPlayerHeight();

        if (currentHeight <= sizeSmall) {
            return 0;
        }

        if (currentHeight >= sizeLarge) {
            return 0;
        }

        float newHeight = currentHeight+argDiff;

        if (argDiff > 0) {
            return newHeight-sizeLarge;
        }

        return newHeight-sizeSmall;
    }

    // returns the new height
    private float setPlayerHeight(float argScreenHeight) {
        Log.v(TAG, "Player height is set to: " + argScreenHeight);

        if (mPlaylistEmpty)
            return getHeight();

        if (!validateState()) {
            return -1;
        }

        Log.v("TopPlayer", "setPlayerHeight: " + argScreenHeight);


        screenHeight = argScreenHeight;

        setBackgroundVisibility(screenHeight);

        float minScreenHeight = screenHeight < sizeSmall ? sizeSmall : screenHeight;
        float minMaxScreenHeight = minScreenHeight > sizeLarge ? sizeLarge : minScreenHeight;

        screenHeight = minMaxScreenHeight;

        ViewGroup.LayoutParams lp = getLayoutParams();
        lp.height = (int)minMaxScreenHeight;
        setLayoutParams(lp);

        float controlTransY = minMaxScreenHeight-mControlHeight;
        Log.v("TopPlayer", "controlTransY: " +  controlTransY);
        if (controlTransY >= 0)
            controlTransY = 0;

        mControls.setTranslationY(controlTransY);

        return minMaxScreenHeight;

    }

    public float setBackgroundVisibility(float argTopPlayerHeight) {
        return setGenericVisibility(mPhoto, getMaximumSize(), 300, argTopPlayerHeight);
    }

    public float setGenericVisibility(@NonNull View argView, int argVisibleHeight, int argFadeDistance, float argTopPlayerHeight) {
        Log.d("Top", "setGenericVisibility: h: " + argTopPlayerHeight);

        int VisibleHeightPx = argVisibleHeight;
        int InvisibleHeightPx = argVisibleHeight-argFadeDistance;

        float VisibilityFraction = 1;

        if (argTopPlayerHeight > VisibleHeightPx) {
            VisibilityFraction = 1f;
        } else if (argTopPlayerHeight < InvisibleHeightPx || argTopPlayerHeight == sizeSmall) {
            VisibilityFraction = 0f;
        } else {
            float thressholdDiff = VisibleHeightPx - InvisibleHeightPx;
            float DistanceFromVisible = argTopPlayerHeight - InvisibleHeightPx;

            VisibilityFraction = DistanceFromVisible / thressholdDiff;
        }

        float scaleFraction = 1f - (1f - VisibilityFraction)/10;
        argView.setScaleX(scaleFraction);
        argView.setScaleY(scaleFraction);

        if (VisibilityFraction > 0f) {
            float newHeight = argVisibleHeight - argVisibleHeight * scaleFraction;
            float translationY = newHeight / 2;
            argView.setTranslationY(-translationY);
        }

        argView.setAlpha(VisibilityFraction);

        return VisibilityFraction;
    }

    public int getVisibleHeight() {
        return (int) (getHeight()+getTranslationY());
    }

    private boolean validateState() {
        if (sizeSmall < 0 || sizeMedium < 0 || sizeLarge < 0) {
            Log.d("TopPlayer", "Layout sizes needs to be defined");
            return false;
        }
        return true;
    }

    public boolean isMaximumSize() {
        return getHeight() >= sizeLarge;
    }

    public boolean isMinimumSize() {
        return sizeSmall >= getHeight()+getTranslationY();
    }

    public int getMaximumSize() {
        return sizeLarge;
    }

    @Override
    public void onPaletteFound(Palette argChangedPalette) {
        mTextColor = ColorUtils.getTextColor(getContext());
        int color = ColorUtils.getBackgroundColor(getContext());
        mBackgroundColor = StaticButtonColor(mContext, argChangedPalette, color);
        @ColorInt int bgcolor = ContextCompat.getColor(getContext(), R.color.playlist_background);
        int playcolor = ContextCompat.getColor(getContext(), R.color.colorPrimaryDark);


        bgcolor = ColorUtils.adjustToTheme(getResources(), argChangedPalette, bgcolor);
        setBackgroundColor(bgcolor);

        //mPlayPauseButton.setColor(argChangedPalette.getDarkVibrantColor(playcolor));
        mPlayPauseButton.onPaletteFound(argChangedPalette);

        ColorUtils.tintButton(mFavoriteButton,    mTextColor);
        ColorUtils.tintButton(mDownloadButton,    mTextColor);
        tintInflatedButtons();

        invalidate();
    }

    @Override
    public String getPaletteUrl() {
        return mPlayPauseButton.getPaletteUrl();
    }

    private void hideText() {
        mTriangle.setVisibility(GONE);
        mExpandedActionsBar.setVisibility(GONE);
    }

    private void showText() {

        mTriangle.setVisibility(VISIBLE);
        mExpandedActionsBar.setVisibility(VISIBLE);
    }

    private void getPlayerControlHeight(@NonNull final View argView) {
        ViewTreeObserver viewTreeObserver = argView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    UIUtils.removeOnGlobalLayoutListener(argView, this);
                    int controlHeight = argView.getHeight();
                    if (controlHeight > 0) {
                        mControlHeight = controlHeight;
                        argView.getLayoutParams().height = mControlHeight;
                    }
                }
            });
        }
    }

    public @ColorInt int getBackGroundColor() {
        return mBackgroundColor;
    }

    public synchronized void setFullscreen(boolean argNewState, boolean doAnimate) {
        mFullscreen = argNewState;
        try {
            if (doAnimate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Transition trans = new ChangeBounds();
                trans.setDuration(getResources().getInteger(R.integer.animation_quick));
                TransitionManager.go(new Scene(this), trans);
            }

            if (mFullscreen) {
                goFullscreen();
            } else {
                exitFullscreen();
            }
        } finally {
            prefs.edit().putBoolean(mDoFullscreentKey, argNewState).apply();
        }
    }

    private void goFullscreen() {
        exitFullscreen();
    }

    private void exitFullscreen() {

        Log.d(TAG, "Exit fullscreen mode");
        mFullscreenButton.setImageResource(R.drawable.ic_fullscreen_white);

        if (Layout != null) {
            CoordinatorLayout.LayoutParams layoutParams = new CoordinatorLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    sizeLarge);

            Layout.setLayoutParams(layoutParams);

            setPlayerHeight(sizeLarge);
            ((MainActivity) getContext()).exitFullScreen(Layout);
        }

    }

    public boolean isFullscreen() {
        return mFullscreen;
    }

    private synchronized void onClickMoreButton(@NonNull View argView) {

        if (mMoreButtonsStub != null) {
            View inflated = mMoreButtonsStub.inflate();
            mMoreButtonsStub = null;

            mFullscreenButton = (PlayerButtonView) inflated.findViewById(R.id.fullscreen_button);
            mSleepButton = (PlayerButtonView) inflated.findViewById(R.id.sleep_button);
            mChapterButton = (ImageView) inflated.findViewById(R.id.chapter_button);
            mSpeedButton = (Button) inflated.findViewById(R.id.speed_button);
            mExpandedActionsBar = (LinearLayout) inflated.findViewById(R.id.expanded_action_bar);

            initExpandedButtons();
        }

        try {
            mDoDisplayText = !mDoDisplayText;
            if (Build.VERSION.SDK_INT >= 19) {
                TransitionManager.beginDelayedTransition(Layout, UIUtils.getDefaultTransition(getResources()));
            }

            if (mDoDisplayText) {
                Log.d(TAG, "ShowText");
                showText();
            } else {
                Log.d(TAG, "HideText");
                hideText();
            }
        } finally {
            prefs.edit().putBoolean(mDoDisplayTextKey, mDoDisplayText).apply();
        }
    }

    private void tintInflatedButtons() {
        if (mSpeedButton != null) {
            ColorUtils.tintButton(mSpeedButton, mTextColor);
        }

        if (mFullscreenButton != null) {
            ColorUtils.tintButton(mFullscreenButton, mTextColor);
        }
    }

    private void initExpandedButtons() {
        assert mSleepButton != null;
        assert mChapterButton != null;
        assert mSpeedButton != null;
        assert mFullscreenButton != null;

        tintInflatedButtons();

        // Sleep buttons
        mSleepButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sleepButtonPressed();
            }
        });

        // Chapter button
        mChapterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                //toggleChapters();
                MainActivity activity = ((MainActivity)getContext());
                DialogChapters dialogChapters = DialogChapters.newInstance(mCurrentEpisode);
                dialogChapters.show(activity.getFragmentManager(), DialogPlaybackSpeed.class.getName());
            }
        });

        int visibility = SoundWaves.getAppContext(getContext()).getPlayer().canSetSpeed() ? View.VISIBLE : View.GONE;
        mSpeedButton.setVisibility(visibility);

        GenericMediaPlayerInterface player = SoundWaves.getAppContext(getContext()).getPlayer();
        float speedMultiplier = player.getCurrentSpeedMultiplier();
        setPlaybackSpeedView(speedMultiplier);

        mSpeedButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity activity = ((MainActivity)getContext());
                DialogPlaybackSpeed dialogPlaybackSpeed = DialogPlaybackSpeed.newInstance(DialogPlaybackSpeed.EPISODE);
                dialogPlaybackSpeed.show(activity.getFragmentManager(), DialogPlaybackSpeed.class.getName());
            }
        });

        mFullscreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mFullscreen = !mFullscreen;
                    setFullscreen(mFullscreen, true);
                } finally {
                    prefs.edit().putBoolean(mDoFullscreentKey, mFullscreen).apply();
                }
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        Log.w(TAG, "touchdebug intercept : " + event.getAction());
        return super.onInterceptTouchEvent(event);
    }

    public boolean scrollExternal(float distanceY, boolean argDispatchScrollEvents) {

        float diffY = distanceY;
        int dyConsumed = 0;

        float diffYabs = Math.abs(diffY);
        int dyUnconsumed = (int)diffY;

        if (argDispatchScrollEvents)
            scrollingChildHelper.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL);


        int[] offsetInWindow = new int[] {0, 0};

        int[] consumed = new int[] { 0, 0};


        if (argDispatchScrollEvents)
            scrollingChildHelper.dispatchNestedPreScroll(0, dyUnconsumed, consumed, offsetInWindow);


        //diffY = diffY + offsetInWindow[1];
        dyUnconsumed += offsetInWindow[1];
        offsetInWindow[1] = 0;


        int dyCanConsume = (int)canConsume(dyUnconsumed);
        dyConsumed = dyCanConsume > Math.abs(diffY) ? dyUnconsumed : dyCanConsume;

        dyUnconsumed = Math.abs(diffY) > Math.abs(dyConsumed) ? (dyUnconsumed - dyConsumed) : 0;


        if (argDispatchScrollEvents)
            scrollingChildHelper.dispatchNestedScroll(0, dyConsumed, 0, dyUnconsumed, offsetInWindow );


        int treshold = TopPlayer.this.mViewConfiguration.getScaledTouchSlop();
        if (diffYabs > 0) {//(diffYabs > ViewConfigurationCompat.getScaledPagingTouchSlop(TopPlayer.this.mViewConfiguration)) {
            Log.d(TAG, "onScroll: dispatchScroll: dyUnconsumed: " + dyConsumed);

            int consumedByActionbar = consumed[1];

            // if we are pulling down, but do not consume all the pulls
            if (dyConsumed < 0) {
                TopPlayer.this.scrollBy(dyConsumed-consumedByActionbar);
            }

            if (dyConsumed > 0 && !TopPlayer.this.isMinimumSize()) {
                TopPlayer.this.scrollBy(dyConsumed-consumedByActionbar);
            }
        }


        Log.d(TAG, "onScroll: ignore: diffY: " + diffY);
        return true;
    }

    class TopPLayerScrollGestureListener extends GestureDetector.SimpleOnGestureListener {

        private static final String DEBUG_TAG = "Gestures";

        private Runnable mFlingRunnable;

        public TopPLayerScrollGestureListener() {
        }

        @Override
        public  boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.d(DEBUG_TAG, "onScroll: e1Y: " + e1.getY() + " e2Y: " + e2.getY());
            return scrollExternal(distanceY, true);
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.d(DEBUG_TAG, "onFling: " + event1.toString() + event2.toString());


            if(this.mFlingRunnable != null) {
                removeCallbacks(this.mFlingRunnable);
            }

            if(TopPlayer.this.mScroller == null) {
                TopPlayer.this.mScroller = ScrollerCompat.create(getContext());
            }

            scrollingChildHelper.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL);

            TopPlayer.this.mScroller.fling(0, PlaylistBehavior.MAX_VELOCITY_Y, 0, Math.round(velocityY), 0, 0, 0, 10000);
            if(TopPlayer.this.mScroller.computeScrollOffset()) {
                this.mFlingRunnable = new FlingRunnable(TopPlayer.this);
                ViewCompat.postOnAnimation(TopPlayer.this, this.mFlingRunnable);
                return true;
            } else {
                this.mFlingRunnable = null;
                return false;
            }
        }
    }

    // Nested scrolling
    public void setNestedScrollingEnabled(boolean enabled) {
        scrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    public boolean isNestedScrollingEnabled() {
        return scrollingChildHelper.isNestedScrollingEnabled();
    }

    public boolean startNestedScroll(int axes) {
        return scrollingChildHelper.startNestedScroll(axes);
    }

    public void stopNestedScroll() {
        scrollingChildHelper.stopNestedScroll();
    }

    public boolean hasNestedScrollingParent() {
        return scrollingChildHelper.hasNestedScrollingParent();
    }

    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {

        return scrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed,
                dyUnconsumed, offsetInWindow);
    }

    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return scrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return scrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return scrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }


    // ScrollingView

    // Compute the horizontal extent of the horizontal scrollbar's thumb within the horizontal range.
    public int computeHorizontalScrollOffset() {
        return 0;// this.Layout.canScrollHorizontally()?this.Layout.computeHorizontalScrollOffset(this.mState):0;
    }

    // Compute the horizontal offset of the horizontal scrollbar's thumb within the horizontal range.
    public int computeHorizontalScrollExtent() {
        return 0;// this.Layout.canScrollHorizontally()?this.Layout.computeHorizontalScrollExtent(this.mState):0;
    }

    // Compute the horizontal range that the horizontal scrollbar represents.
    public int computeHorizontalScrollRange() {
        return 0;// this.Layout.canScrollHorizontally()?this.Layout.computeHorizontalScrollRange(this.mState):0;
    }

    // Compute the vertical extent of the vertical scrollbar's thumb within the vertical range.
    public int computeVerticalScrollOffset() {
        return 0;// this.Layout.canScrollVertically()?this.Layout.computeVerticalScrollOffset(this.mState):0;
    }

    // Compute the vertical offset of the vertical scrollbar's thumb within the horizontal range.
    public int computeVerticalScrollExtent() {
        return 0;// this.Layout.canScrollVertically()?this.Layout.computeVerticalScrollExtent(this.mState):0;
    }

    // Compute the vertical range that the vertical scrollbar represents.
    public int computeVerticalScrollRange() {
        return 0;// this.Layout.canScrollVertically()?this.Layout.computeVerticalScrollRange(this.mState):0;
    }


    class FlingRunnable implements Runnable {
        private final View mLayout;

        private int lastY = 0;

        FlingRunnable(View layout) {
            this.mLayout = layout;
        }

        public void run() {
            if(TopPlayer.this.mScroller != null && TopPlayer.this.mScroller.computeScrollOffset()) {

                int currY = TopPlayer.this.mScroller.getCurrY();

                if (lastY != 0) {
                    int diffY = currY-lastY;

                    Log.v(TAG, "scroll.run, diffY: " + diffY);
                    TopPlayer.this.scrollExternal(-diffY, false);
                }

                lastY = currY;
                ViewCompat.postOnAnimation(this.mLayout, this);
            }

        }
    }


}
