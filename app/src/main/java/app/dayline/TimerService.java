package app.dayline;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import app.dayline.core.Model.*;

public final class TimerService extends Service {
    public static final String CHANGED="app.dayline.CHANGED";
    private static final String CHANNEL="active_timers";
    public static void sync(Context context) {
        Intent i=new Intent(context,TimerService.class);
        if(Store.get(context).snapshot().runningCount(0)>0)context.startForegroundService(i);else context.stopService(i);
    }
    @Override public void onCreate() {
        super.onCreate();NotificationChannel c=new NotificationChannel(CHANNEL,"Работающие таймеры",NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Текущие занятия и остановка таймеров по группам");getSystemService(NotificationManager.class).createNotificationChannel(c);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        Store store=Store.get(this);
        if(intent!=null&&"STOP_GROUP".equals(intent.getAction())) {
            long id=intent.getLongExtra("group",-1);if(id>0)store.stopGroup(id,System.currentTimeMillis());
            sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));
        }
        Snapshot data=store.snapshot();int count=data.runningCount(0);
        if(count==0) { stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY; }
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_timer).setContentTitle("Идёт учёт · "+count)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Build.VERSION.SDK_INT>=31?Notification.CATEGORY_STOPWATCH:Notification.CATEGORY_SERVICE);
        Notification.InboxStyle style=new Notification.InboxStyle();long oldest=Long.MAX_VALUE;String first="";
        for(Session s:data.sessions)if(s.running()) { Task t=data.task(s.taskId);if(t==null)continue;style.addLine(t.name+" · "+data.group(t.groupId).name);oldest=Math.min(oldest,s.start);if(first.isEmpty())first=t.name; }
        b.setContentText(first).setWhen(oldest).setUsesChronometer(true).setStyle(style);
        int actions=0;for(Group g:data.groups)if(data.runningCount(g.id)>0&&actions++<3) {
            Intent stop=new Intent(this,TimerService.class).setAction("STOP_GROUP").putExtra("group",g.id);
            PendingIntent pi=PendingIntent.getService(this,(int)g.id,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null,"Стоп · "+g.name,pi).build());
        }
        if(Build.VERSION.SDK_INT>=34)startForeground(10,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(10,b.build());
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
