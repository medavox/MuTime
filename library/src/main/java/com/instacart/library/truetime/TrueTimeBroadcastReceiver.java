package com.instacart.library.truetime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootCompletedBroadcastReceiver
      extends BroadcastReceiver {

    private static final String TAG = "TrueTimeBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        switch(intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                Log.i(TAG, "clearing TrueTime disk cache as we've detected a boot");
                TrueTime.clearCachedInfo(context);
                break;

            case Intent.ACTION_TIME_CHANGED:
                Log.i(TAG, "manual system clock change detected; clearing TrueTime disk cache");
                TrueTime.clearCachedInfo(context);
        }
    }
}
