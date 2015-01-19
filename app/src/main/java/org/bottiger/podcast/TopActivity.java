package org.bottiger.podcast;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.transition.Explode;
import android.transition.Slide;
import android.transition.Transition;
import android.view.Window;

public class TopActivity extends FragmentActivity {
	
	private static SharedPreferences prefs;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Transition transition = new Slide();
            getWindow().setEnterTransition(transition);
            getWindow().setExitTransition(transition);
        }

        if (ApplicationConfiguration.DEBUGGING)
            ViewServer.get(this).addWindow(this);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
	}
	
	public static SharedPreferences getPreferences() {
		return prefs;
	}

}