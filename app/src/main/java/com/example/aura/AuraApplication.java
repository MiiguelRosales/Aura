package com.example.aura;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class AuraApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static int startedActivities;
    private static boolean appInForeground;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    public static boolean isAppInForeground() {
        return appInForeground;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
        appInForeground = true;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        appInForeground = startedActivities > 0;
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}