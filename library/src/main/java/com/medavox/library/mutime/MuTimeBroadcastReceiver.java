package com.medavox.library.mutime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MuTimeBroadcastReceiver
      extends BroadcastReceiver {

    private static final String TAG = "MuTimeBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        switch(intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                Log.i(TAG, "clearing MuTime disk cache as we've detected a boot");
                MuTime.clearCachedInfo();
                break;

            case Intent.ACTION_TIME_CHANGED:
                Log.i(TAG, "manual system clock change detected; clearing MuTime disk cache");
                MuTime.clearCachedInfo();
        }
    }
}
