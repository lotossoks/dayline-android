package app.dayline;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import app.dayline.core.Model.*;
import java.util.*;
import java.util.concurrent.*;

/** A launcher-owned view; SQLite remains the source of truth for every timer. */
public final class TimerWidget extends AppWidgetProvider {
    static final String CONTROL="app.dayline.widget.CONTROL",REFRESH="app.dayline.widget.REFRESH",PAGE="app.dayline.widget.PAGE";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    static SharedPreferences settings(Context c){return c.getSharedPreferences("timer_widgets",Context.MODE_PRIVATE);}
    static String key(int id){return "group_"+id;}
    static long group(Context c,int id,Snapshot data){long value=settings(c).getLong(key(id),0);if(value!=0&&data.group(value)==null){settings(c).edit().putLong(key(id),0).putInt("page_"+id,0).apply();return 0;}return value;}
    static boolean owns(Context c,int id){AppWidgetProviderInfo info=AppWidgetManager.getInstance(c).getAppWidgetInfo(id);return info!=null&&new ComponentName(c,TimerWidget.class).equals(info.provider);}
    static List<Task> tasks(Snapshot data,long group){
        List<Task> result=new ArrayList<>();Set<Long> active=new HashSet<>();for(Session s:data.sessions)if(s.running())active.add(s.taskId);
        for(Task t:data.tasks)if(!t.archived&&(group==0||t.groupId==group))result.add(t);
        result.sort(Comparator.comparing((Task t)->!active.contains(t.id)).thenComparingLong(t->t.groupId).thenComparingLong(t->t.id));return result;
    }
    static Intent open(Context c,long group){return new Intent(c,MainActivity.class).setData(Uri.parse("dayline://open/"+group)).putExtra("widgetGroup",group).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    static Intent command(Context c,int id,String action){return new Intent(c,TimerWidget.class).setAction(action).setData(Uri.parse("dayline://widget/"+id+"/"+action)).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);}
    static RemoteViews taskView(Context c,int id,Snapshot data,Task task){
        RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_task);Session running=data.running(task.id);Group group=data.group(task.groupId);
        row.setTextViewText(R.id.widget_task_name,task.name);row.setTextViewText(R.id.widget_task_group,(running==null?"": "● Идёт · ")+group.name);
        row.setTextColor(R.id.widget_task_group,running==null?Ui.MUTED:group.color);
        row.setViewVisibility(R.id.widget_clock,running==null?View.GONE:View.VISIBLE);
        long base=SystemClock.elapsedRealtime()-(running==null?0:Math.max(0,System.currentTimeMillis()-running.start));row.setChronometer(R.id.widget_clock,base,null,running!=null);
        row.setTextViewText(R.id.widget_control,running==null?"▶":"■");row.setTextColor(R.id.widget_control,running==null?Ui.GREEN:Ui.WHITE);
        row.setInt(R.id.widget_control,"setBackgroundResource",running==null?R.drawable.widget_play:R.drawable.widget_stop);
        row.setContentDescription(R.id.widget_control,(running==null?"Запустить ":"Остановить ")+task.name);
        long session=running==null?0:running.id,started=running==null?0:running.start;
        Intent action=command(c,id,CONTROL).setData(Uri.parse("dayline://widget/"+id+"/task/"+task.id+"/"+session+"/"+started)).putExtra("task",task.id).putExtra("session",session).putExtra("started",started);
        row.setOnClickPendingIntent(R.id.widget_control,PendingIntent.getBroadcast(c,id,action,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));return row;
    }
    static RemoteViews views(Context c,int id,Snapshot data){
        Bundle options=AppWidgetManager.getInstance(c).getAppWidgetOptions(id);
        return new RemoteViews(pageViews(c,id,data,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,250)),pageViews(c,id,data,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,400)));
    }
    private static RemoteViews pageViews(Context c,int id,Snapshot data,int height){
        long group=group(c,id,data);List<Task> tasks=tasks(data,group);int running=0;for(Task t:tasks)if(data.running(t.id)!=null)running++;
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget_timers);
        views.setTextViewText(R.id.widget_scope,(group==0?"Все группы":data.group(group).name)+" · идёт: "+running);
        PendingIntent open=PendingIntent.getActivity(c,id,open(c,group),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title,open);views.setOnClickPendingIntent(R.id.widget_open,open);views.setOnClickPendingIntent(R.id.widget_empty,open);
        views.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(c,id,command(c,id,REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        Intent config=new Intent(c,WidgetConfigActivity.class).setData(Uri.parse("dayline://widget/"+id+"/configure")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        views.setOnClickPendingIntent(R.id.widget_settings,PendingIntent.getActivity(c,id,config,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));

        float scale=Math.max(1,c.getResources().getConfiguration().fontScale);int perPage=Math.max(1,Math.min(8,(int)((height-180)/(96*scale))));
        int pages=Math.max(1,(tasks.size()+perPage-1)/perPage),page=Math.max(0,Math.min(pages-1,settings(c).getInt("page_"+id,0)));
        views.removeAllViews(R.id.widget_tasks);
        for(int n=page*perPage;n<Math.min(tasks.size(),(page+1)*perPage);n++)views.addView(R.id.widget_tasks,taskView(c,id,data,tasks.get(n)));
        views.setViewVisibility(R.id.widget_empty,tasks.isEmpty()?View.VISIBLE:View.GONE);
        views.setViewVisibility(R.id.widget_pages,pages>1?View.VISIBLE:View.GONE);views.setTextViewText(R.id.widget_page,(page+1)+" / "+pages);
        views.setContentDescription(R.id.widget_page,"Страница "+(page+1)+" из "+pages);
        for(int direction:new int[]{-1,1}){int view=direction<0?R.id.widget_previous:R.id.widget_next;boolean enabled=direction<0?page>0:page<pages-1;
            Intent turn=command(c,id,PAGE).setData(Uri.parse("dayline://widget/"+id+"/page/"+page+"/"+direction)).putExtra("page",page).putExtra("direction",direction);
            views.setOnClickPendingIntent(view,PendingIntent.getBroadcast(c,id,turn,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));views.setBoolean(view,"setEnabled",enabled);views.setTextColor(view,enabled?Ui.GREEN:Ui.MUTED);
        }
        return views;
    }
    static void update(Context c,int id,Snapshot data){
        if(!owns(c,id))return;AppWidgetManager manager=AppWidgetManager.getInstance(c);manager.updateAppWidget(id,views(c,id,data));

    }
    private static void updateAll(Context c){int[] ids=AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c,TimerWidget.class));if(ids.length==0)return;Snapshot data=Store.get(c).widgetSnapshot();for(int id:ids)update(c,id,data);}
    static void refresh(Context context){Context c=context.getApplicationContext();WORK.execute(()->{try{updateAll(c);}catch(RuntimeException e){android.util.Log.w("DaylineWidget","Could not refresh widgets",e);}});}
    private void async(Context c,Runnable action){PendingResult result=goAsync();WORK.execute(()->{try{action.run();}catch(RuntimeException e){android.util.Log.w("DaylineWidget","Widget action failed",e);new Handler(Looper.getMainLooper()).post(()->Toast.makeText(c,"Не удалось обновить виджет. Откройте Dayline и повторите действие.",Toast.LENGTH_LONG).show());}finally{result.finish();}});}
    @Override public void onReceive(Context c,Intent intent){
        String action=intent.getAction();
        if(CONTROL.equals(action)||REFRESH.equals(action)||PAGE.equals(action)){
            int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
            async(c,()->{
                if(!owns(c,id))return;
                if(PAGE.equals(action)){int direction=intent.getIntExtra("direction",0);if(direction!=1&&direction!=-1)return;settings(c).edit().putInt("page_"+id,Math.max(0,intent.getIntExtra("page",0)+direction)).apply();update(c,id,Store.get(c).widgetSnapshot());return;}
                if(CONTROL.equals(action)){
                    Store store=Store.get(c);Snapshot data=store.widgetSnapshot();Task task=data.task(intent.getLongExtra("task",0));long group=group(c,id,data);
                    if(task!=null&&!task.archived&&(group==0||task.groupId==group)){
                        settings(c).edit().putInt("page_"+id,0).apply();store.controlTimer(task.id,intent.getLongExtra("session",0),intent.getLongExtra("started",0),System.currentTimeMillis());
                        try{TimerService.sync(c);}catch(RuntimeException e){android.util.Log.w("DaylineWidget","Timer saved; notification unavailable",e);}
                        c.sendBroadcast(new Intent(TimerService.CHANGED).setPackage(c.getPackageName()));
                    }
                }
                updateAll(c);
            });return;
        }
        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)){async(c,()->updateAll(c));return;}
        super.onReceive(c,intent);
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){async(c,()->{Snapshot data=Store.get(c).widgetSnapshot();for(int id:ids)update(c,id,data);});}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){async(c,()->update(c,id,Store.get(c).widgetSnapshot()));}
    @Override public void onDeleted(Context c,int[] ids){SharedPreferences.Editor edit=settings(c).edit();for(int id:ids)edit.remove(key(id)).remove("page_"+id);edit.apply();}
    @Override public void onRestored(Context c,int[] oldIds,int[] newIds){SharedPreferences prefs=settings(c);SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<oldIds.length;i++)edit.putLong(key(newIds[i]),prefs.getLong(key(oldIds[i]),0)).remove(key(oldIds[i]));edit.apply();}
}
