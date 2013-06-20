package org.bottiger.podcast.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.bottiger.podcast.provider.BulkUpdater;
import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.provider.gpodder.GPodderSubscriptionWrapper;
import org.bottiger.podcast.service.PodcastDownloadManager;
import org.bottiger.podcast.utils.PodcastLog;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

/**
 * 
 * @author Arvid Böttiger
 * 
 */
public class JSONFeedParserWrapper {

	private final PodcastLog log = PodcastLog.getLog(getClass());
	private Context mContext;
	private ContentResolver cr;
	private SimpleDateFormat dt = new SimpleDateFormat(FeedItem.default_format);

	private SharedPreferences sharedPrefs;

	public JSONFeedParserWrapper(Context context) {
		this.mContext = context;
		this.cr = context.getContentResolver();
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	/**
	 * Parses a json Object an updates the feed
	 * 
	 * @param jsonArray
	 * @param subscription
	 */
	public void feedParser(org.json.JSONArray jsonArray) {
		long start = System.currentTimeMillis();
		GPodderSubscriptionWrapper subscriptionWrapper = jsonParser(jsonArray);
		long end = System.currentTimeMillis();
		Log.d("Parser Profiler", "gson time: " + (end - start));
		if (subscriptionWrapper != null) { // FIXME this should not be null
			start = System.currentTimeMillis();
			Subscription subscription = subscriptionWrapper.getSubscription(cr);
			ArrayList<FeedItem> episodes = subscriptionWrapper.getEpisodes(cr); // Optimize?
																				// FIXME
			end = System.currentTimeMillis();
			Log.d("Parser Profiler", "object creation time: " + (end - start));

			start = System.currentTimeMillis();
			updateFeed(subscription, episodes, false);
			end = System.currentTimeMillis();
			Log.d("Parser Profiler", "updateFeed time: " + (end - start));
			PodcastDownloadManager.startDownload(mContext);
		}
	}

	/**
	 * 
	 * @param jsonArray
	 * @param subscription
	 * @return
	 */
	private GPodderSubscriptionWrapper jsonParser(org.json.JSONArray jsonArray) {

		Gson gson = new Gson();
		GPodderSubscriptionWrapper subscriptionWrapper = null;

		GPodderSubscriptionWrapper[] subscriptionWrappers = gson.fromJson(
				jsonArray.toString(), GPodderSubscriptionWrapper[].class);

		if (subscriptionWrappers.length > 1) {
			int i = subscriptionWrappers.length;
			i = 6 + i;
		}

		if (subscriptionWrappers.length > 0) {
			subscriptionWrapper = subscriptionWrappers[0];

			Subscription subscription = subscriptionWrapper.getSubscription(cr);
			subscription.update(cr);
		}

		return subscriptionWrapper;
	}

	public int updateFeed(Subscription subscription, ArrayList<FeedItem> items, boolean updateExisting) {
		long update_date = subscription.lastItemUpdated;
		int add_num = 0;
		boolean insertSucces = false;
		boolean autoDownload = sharedPrefs.getBoolean(
				"pref_download_on_update", true);

		if (!items.isEmpty()) {

			// Sort the items to find the oldest
			try {
			Collections.sort(items);
			} catch (Exception e) {
				e.printStackTrace();
			}
			FeedItem oldestItem = items.get(items.size() - 1);

			HashMap<String, FeedItem> databaseItems = FeedItem.allAsList(cr,
					subscription, oldestItem.getDate());

			// HashMap<String, FeedItem> databaseItems = FeedItem.allAsList(cr,
			// subscription, null);

			BulkUpdater bulkUpdater = new BulkUpdater();

			// we iterate over all the input items
			for (FeedItem item : items) {

				// and if the item is not included in the database already we
				// add it
				String url = item.getURL();
				if (databaseItems != null && !databaseItems.containsKey(url)) {

					Long itemDate = item.getLongDate();

					if (itemDate > update_date) {
						update_date = itemDate;
					}
					item.insert(cr);
					Log.d("Feed Updater","Inserting new episode: " + item.title);

					add_num++;

					/*
					 * Download podcasts
					 */
					if (autoDownload && item != null) {
						PodcastDownloadManager.addItemToQueue(item);
					}

					subscription.fail_count = 0;
					subscription.lastItemUpdated = update_date;
					ContentProviderOperation cpo = subscription.update(cr,
							true, true);
					bulkUpdater.addOperation(cpo);

					Log.d("Feed Updater","add url: " + subscription.url + "\n add num = "
							+ add_num);
				} else {
					if (updateExisting) {
					bulkUpdater.addOperation(item.update(cr, true, true));
					Log.d("Feed Updater","Updating episode: " + item.title + "("
							+ item.image + ")");
					}
				}

				// item = addItem(subscription, item, bulkUpdater);
			}

			long start = System.currentTimeMillis();
			bulkUpdater.commit(cr);
			long end = System.currentTimeMillis();
			Log.d("Parser Profiler", "commit database time: " + (end - start));
			return add_num;
		}
		return 0;
	}

}
