package app.dayline;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try { TimerService.sync(context); }catch(RuntimeException ignored) {
                // Saved timestamps remain authoritative if a manufacturer blocks background startup.
            }
        }
    }
}
