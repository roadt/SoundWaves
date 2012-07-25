package info.bottiger.podcast.utils;

import info.bottiger.podcast.PodcastBaseFragment;
import info.bottiger.podcast.R;
import info.bottiger.podcast.RecentItemFragment;
import info.bottiger.podcast.provider.FeedItem;
import info.bottiger.podcast.service.PlayerService;
import info.bottiger.podcast.service.PodcastService;
import info.bottiger.podcast.utils.ControlButtons.Holder;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class ControlButtons {
		
    public static class Holder {
    	public ImageButton playPauseButton;
        public ImageButton stopButton;
        public ImageButton infoButton;
        public ImageButton downloadButton;
        public ImageButton queueButton;
        public TextView currentTime;
        public SeekBar seekbar;
    }
    
    public static void setCurrentTime(final RecentItemFragment fragment, final Holder viewHolder, final long id) {
    	
    }

    public static void setListener(final RecentItemFragment fragment, final PodcastService podcastServiceConnection, final Holder viewHolder, final long id) {
    	
    	fragment.queueNextRefresh(1);
    	
    	final ImageButton b = (ImageButton)viewHolder.playPauseButton;
		b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	fragment.setProgress(viewHolder.seekbar);
            	fragment.setCurrentTime(viewHolder.currentTime);
                if (fragment.mPlayerServiceBinder.isPlaying()) {
                	b.setContentDescription("Play");
                	b.setImageResource(R.drawable.play);
                	fragment.mPlayerServiceBinder.pause();
                } else {
                	b.setImageResource(R.drawable.pause);
                	b.setContentDescription("Pause");
                	fragment.mPlayerServiceBinder.play(id);
                }
            }
        });
		
		viewHolder.stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	fragment.mPlayerServiceBinder.stop();
            }
        });
		
		viewHolder.infoButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            }
        });
		
		viewHolder.downloadButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	FeedItem item = FeedItem.getById(fragment.getActivity().getContentResolver(), id);
            	//item.startDownload(fragment.getActivity().getContentResolver());
            	podcastServiceConnection .downloadItem(fragment.getActivity().getContentResolver(), item);
            	viewHolder.downloadButton.setImageResource(R.drawable.trash);
            	viewHolder.downloadButton.setContentDescription("Trash");
            }
        });
		
		viewHolder.queueButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            }
        });
		
        if (viewHolder.seekbar instanceof SeekBar) {
        	viewHolder.seekbar.setOnSeekBarChangeListener(fragment.mSeekListener);
        }
        viewHolder.seekbar.setMax(1000); 
	}

}
