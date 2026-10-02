package app.dayline;

import android.app.*;
import android.content.*;
import android.os.*;
import app.dayline.core.*;
import app.dayline.core.Model.*;
import org.json.*;
import java.time.*;
import java.util.*;

public final class DaylineInstrumentation extends Instrumentation {
    private int checks=0;private Bundle options;
    @Override public void onCreate(Bundle args){super.onCreate(args);options=args==null?new Bundle():args;start();}
    private void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private void rejects(Runnable r,String label){checks++;try{r.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){}}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            if("true".equals(options.getString("analysis"))){analysisSmoke();result.putString("stream","PASS: "+checks+" analysis UI assertions.\n");finish(Activity.RESULT_OK,result);return;}
            if("true".equals(options.getString("timeline"))){timelineSmoke();result.putString("stream","PASS: "+checks+" timeline rendering assertions.\n");finish(Activity.RESULT_OK,result);return;}
            if("true".equals(options.getString("seed"))){seed();result.putString("stream","Demo data installed in debug app only.\n");finish(Activity.RESULT_OK,result);return;}
            if("true".equals(options.getString("ui"))){uiSmoke();result.putString("stream","PASS: "+checks+" UI assertions; screenshots saved.\n");finish(Activity.RESULT_OK,result);return;}
            Context c=getTargetContext();c.deleteDatabase("dayline-test.db");Store store=new Store(c,"dayline-test.db");
            long now=System.currentTimeMillis(),hour=TimeMath.HOUR,base=now-10*hour;Snapshot initial=store.snapshot();
            check(initial.groups.size()==3,"default groups");check(initial.tasks.size()==1&&initial.runningCount(0)==0,"sleep is not automatic");
            long work=initial.groups.get(0).id,life=initial.groups.get(1).id;
            long a=store.saveTask(0,work,"Первая","Описание"),b=store.saveTask(0,work,"Вторая",""),other=store.saveTask(0,life,"Дорога","");
            store.toggle(a,base);store.toggle(b,base+1000);store.toggle(other,base+2000);
            check(store.snapshot().runningCount(0)==3,"parallel timers");
            store.stopGroup(work,base+hour);check(store.snapshot().runningCount(work)==0&&store.snapshot().runningCount(life)==1,"stop only selected group");
            Store reopened=new Store(c,"dayline-test.db");check(reopened.snapshot().runningCount(life)==1,"running survives reopen");reopened.close();
            store.archive(other,true,base+2*hour);check(store.snapshot().task(other).archived&&store.snapshot().runningCount(0)==0,"archive stops timer and preserves history");
            check(store.snapshot().forTask(other).size()==1,"archived sessions retained");store.archive(other,false,now);
            long id=store.saveSession(0,a,base+3*hour,base+6*hour,now);int before=store.snapshot().sessions.size();
            rejects(()->store.saveSession(0,a,base+4*hour,base+5*hour,now),"same task overlap rejected");check(store.snapshot().sessions.size()==before,"failed change is atomic");
            rejects(()->store.saveSession(0,a,now,now+hour,now),"future rejected");
            store.cut(id,base+4*hour,base+5*hour,other,now);Snapshot cut=store.snapshot();check(cut.session(id)==null,"source replaced");
            check(TimeMath.sum(cut.forTask(a),base+3*hour,base+6*hour,now)==2*hour,"cut source remainders");
            check(TimeMath.sum(cut.forTask(other),base+3*hour,base+6*hour,now)==hour,"cut transferred to other task");
            check(store.canUndo(),"undo available");store.undo();check(store.snapshot().session(id)!=null,"undo restores original");
            long conflict=store.saveSession(0,other,base+4*hour,base+5*hour,now);
            rejects(()->store.cut(id,base+4*hour,base+5*hour,other,now),"transfer conflict rejected");check(store.snapshot().session(id)!=null&&store.snapshot().session(conflict)!=null,"conflict rollback");
            store.deleteSession(conflict);store.cut(id,base+3*hour,base+6*hour,0,now);check(store.snapshot().session(id)==null,"whole deletion");
            store.undo();check(store.snapshot().session(id)!=null,"undo whole deletion");
            store.toggle(a,now-1000);rejects(()->store.saveSession(0,a,now-500,now,now),"manual entry cannot overlap running timer");
            String backup=store.backup(now);JSONObject backupJson=new JSONObject(backup);for(int i=0;i<backupJson.getJSONArray("sessions").length();i++)check(backupJson.getJSONArray("sessions").getJSONObject(i).getLong("end")!=0,"backup freezes running sessions");
            check(store.snapshot().runningCount(0)==1,"backup does not stop live timer");
            store.restore(backup,now+1000);check(store.snapshot().runningCount(0)==0,"restore snapshot is stopped");check(store.snapshot().task(a).note.equals("Описание"),"notes retained");
            int rows=store.snapshot().sessions.size();try{store.restore("{\"format\":\"other\"}",now);throw new AssertionError("bad backup");}catch(IllegalArgumentException expected){}check(store.snapshot().sessions.size()==rows,"invalid restore preserves data");
            store.saveTask(a,life,"Переименована","Новое описание");check(store.snapshot().task(a).groupId==life,"task movement");
            store.close();c.deleteDatabase("dayline-test.db");gapTests(c,now);
            Intent launch=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity activity=startActivitySync(launch);waitForIdleSync();check(activity!=null,"activity launched");
            result.putString("stream","PASS: "+checks+" Android assertions; SQLite, lifecycle and main activity.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
    private void gapTests(Context c,long now){
        c.deleteDatabase("dayline-gaps-test.db");Store store=new Store(c,"dayline-gaps-test.db");long start=now-4*TimeMath.HOUR;
        long first=store.saveTask(0,1,"A",""),second=store.saveTask(0,1,"B",""),office=store.saveTask(0,1,"Office",""),target=store.saveTask(0,2,"Filler","");
        store.saveSession(0,first,start,start+60000,now);store.saveSession(0,second,start+120000,start+180000,now);store.saveSession(0,office,start,start+300000,now);
        int before=store.snapshot().sessions.size();Set<Long> coverage=new HashSet<>(Arrays.asList(first,second));
        check(store.fillGaps(target,null,start,start+300000,now,120000)==0,"global gap fill respects office");
        check(store.fillGaps(target,coverage,start,start+300000,now,120000)==2,"batch fills one- and two-minute filtered gaps");
        check(store.snapshot().sessions.size()==before+2,"all gap rows saved");check(TimeMath.sum(store.snapshot().forTask(target),start,now,now)==180000,"batch duration");
        check(store.fillGaps(target,coverage,start,start+300000,now,120000)==0,"repeat fill is idempotent");check(store.canUndo(),"no-op retains undo");store.undo();check(store.snapshot().sessions.size()==before,"single undo reverts whole batch");
        rejects(()->store.fillGaps(999,coverage,start,start+300000,now,0),"invalid target rejected atomically");check(store.snapshot().sessions.size()==before,"failed fill preserves all records");
        store.toggle(target,now-1000);store.fillGaps(target,coverage,now-120000,now+TimeMath.HOUR,now,0);check(store.snapshot().running(target)!=null,"filler leaves running timer active");
        for(Session row:store.snapshot().forTask(target))if(!row.running())check(row.end<=now-1000,"filled pieces do not overlap active target or future");
        store.close();c.deleteDatabase("dayline-gaps-test.db");
    }
    private android.view.accessibility.AccessibilityNodeInfo findNode(java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> predicate){
        for(int attempt=0;attempt<30;attempt++){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();android.view.accessibility.AccessibilityNodeInfo found=walk(root,predicate);if(found!=null)return found;SystemClock.sleep(150);}
        return null;
    }
    private android.view.accessibility.AccessibilityNodeInfo walk(android.view.accessibility.AccessibilityNodeInfo n,java.util.function.Predicate<android.view.accessibility.AccessibilityNodeInfo> predicate){
        if(n==null)return null;if(predicate.test(n))return n;for(int i=0;i<n.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo result=walk(n.getChild(i),predicate);if(result!=null)return result;}return null;
    }
    private void click(String text){
        android.view.accessibility.AccessibilityNodeInfo n=findNode(x->text.equalsIgnoreCase(x.getText()==null?"":x.getText().toString())||text.equalsIgnoreCase(x.getContentDescription()==null?"":x.getContentDescription().toString()));
        check(n!=null,"UI element present: "+text);while(n!=null&&!n.isClickable())n=n.getParent();check(n!=null&&n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"UI click: "+text);SystemClock.sleep(250);
    }
    private void screenshot(String name)throws Exception{
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"screenshot "+name);java.io.File dir=new java.io.File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private void uiSmoke()throws Exception{
        Context c=getTargetContext();Store main=Store.get(c);check(main.snapshot().tasks.size()==1,"UI smoke requires a fresh emulator installation");
        Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();screenshot("empty");
        click("＋ Задача");android.view.accessibility.AccessibilityNodeInfo input=findNode(x->"android.widget.EditText".contentEquals(x.getClassName()));check(input!=null,"task editor input");
        Bundle text=new Bundle();text.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Проверка интерфейса");check(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,text),"task name entered");click("СОХРАНИТЬ");
        Task created=null;for(Task t:main.snapshot().tasks)if(t.name.equals("Проверка интерфейса"))created=t;check(created!=null,"task saved through UI");
        click("Запустить Проверка интерфейса");check(main.snapshot().running(created.id)!=null,"timer started through UI");
        click("■ Стоп группы");check(main.snapshot().running(created.id)==null,"group stop through UI");
        click("▤  Анализ");screenshot("analysis-empty");click("День");screenshot("analysis-day");
        runOnMainSync(()->{MainActivity m=(MainActivity)activity;new Editors(m).session(null,0);});SystemClock.sleep(300);screenshot("manual-editor");click("ОТМЕНА");
        click("◷  Трекинг");screenshot("tracking");
    }
    private android.view.View findView(android.view.View root,java.util.function.Predicate<android.view.View> match){
        if(match.test(root))return root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)root;for(int i=0;i<g.getChildCount();i++){android.view.View result=findView(g.getChildAt(i),match);if(result!=null)return result;}}return null;
    }
    private void reveal(Activity activity,String description){
        runOnMainSync(()->{android.view.View v=findView(activity.getWindow().getDecorView(),x->description.contentEquals(x.getContentDescription()==null?"":x.getContentDescription()));if(v==null)throw new AssertionError("View missing: "+description);v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()),true);});SystemClock.sleep(350);
    }
    private void top(Activity activity){runOnMainSync(()->{android.view.View v=findView(activity.getWindow().getDecorView(),x->x instanceof android.widget.ScrollView);if(v!=null)((android.widget.ScrollView)v).scrollTo(0,0);});SystemClock.sleep(300);}
    private void analysisSmoke()throws Exception{
        Context c=getTargetContext();Store store=Store.get(c);check(store.snapshot().tasks.size()==1,"analysis UI requires fresh test emulator");
        long study=store.saveGroup(0,"Учёба",0xff467f9d),first=store.saveTask(0,1,"Задача 1",""),second=store.saveTask(0,1,"Задача 2",""),office=store.saveTask(0,1,"В офисе",""),learn=store.saveTask(0,study,"Матанализ",""),life=store.saveTask(0,2,"Прогулка","");
        long now=System.currentTimeMillis();ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.now();
        for(int i=1;i<=14;i++){long day=TimeMath.start(today.minusDays(i),zone);for(long[] row:new long[][]{{1,day,day+7*TimeMath.HOUR},{first,day+9*TimeMath.HOUR,day+10*TimeMath.HOUR},{second,day+9*TimeMath.HOUR+30*60000,day+11*TimeMath.HOUR},{first,day+11*TimeMath.HOUR+60000,day+12*TimeMath.HOUR},{office,day+8*TimeMath.HOUR,day+18*TimeMath.HOUR},{learn,day+15*TimeMath.HOUR,day+17*TimeMath.HOUR},{life,day+20*TimeMath.HOUR,day+21*TimeMath.HOUR}})store.saveSession(0,row[0],row[1],row[2],now);}
        MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{activity.mode=1;activity.weekly=false;activity.selectedDate=today.minusDays(1);activity.render(false);});SystemClock.sleep(400);
        reveal(activity,"Задача в анализе: Задача 1");android.view.accessibility.AccessibilityNodeInfo node=findNode(x->"Задача в анализе: Задача 1".contentEquals(x.getContentDescription()==null?"":x.getContentDescription()));
        check(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK),"long press selects task");SystemClock.sleep(350);check(activity.selectionActive&&activity.selectedTasks.contains(first),"selection enabled");
        reveal(activity,"Задача в анализе: Задача 2");click("Задача в анализе: Задача 2");check(activity.selectedTasks.size()==2&&!activity.selectedTasks.contains(office),"tap adds second task, office excluded");
        check(activity.selectionSummary.getText().toString().contains("3 ч 29 м")&&activity.selectionSummary.getText().toString().contains("2 ч 59 м"),"live selected sum and union");screenshot("selection");
        top(activity);click("Среднее");check(activity.averageMode,"average mode");check(activity.selectionSummary.getText().toString().contains("3 ч 29 м"),"averages use same task selection");screenshot("average-selected");
        click("Закрыть выбор задач");check(!activity.selectionActive&&activity.selectedTasks.isEmpty(),"cross clears selection");
        runOnMainSync(()->{android.view.View chart=findView(activity.getWindow().getDecorView(),v->v instanceof AverageChartView);check(chart!=null,"average chart attached");chart.requestRectangleOnScreen(new android.graphics.Rect(0,0,chart.getWidth(),chart.getHeight()),true);});SystemClock.sleep(350);screenshot("average-lanes");
        top(activity);runOnMainSync(()->{android.widget.Spinner spinner=(android.widget.Spinner)findView(activity.getWindow().getDecorView(),v->"День недели для среднего".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()));spinner.setSelection(1);});SystemClock.sleep(400);check(activity.averageWeekday==1,"weekday average selected");
        runOnMainSync(()->{activity.averageMode=false;activity.selectedTasks.add(first);activity.selectedTasks.add(second);activity.selectionActive=true;activity.saveSelection();activity.render(false);new GapFiller(activity).show(TimeMath.start(today.minusDays(1),zone),TimeMath.start(today,zone));});SystemClock.sleep(400);
        click("Искать пробелы только в выбранных задачах");screenshot("gap-fill");
        runOnMainSync(()->{android.view.View root=activity.getWindow().getDecorView();});
        android.view.accessibility.AccessibilityNodeInfo preview=findNode(x->x.getText()!=null&&x.getText().toString().startsWith("Промежутков: 1"));check(preview!=null,"one short filtered gap preview");
        int before=store.snapshot().sessions.size();click("ЗАПОЛНИТЬ");check(store.snapshot().sessions.size()==before+1,"fill applies all displayed gaps");check(store.canUndo(),"fill can undo");runOnMainSync(()->{store.undo();activity.changed(true);});check(store.snapshot().sessions.size()==before,"undo restores original history");
        check(activity.getPreferences(0).getStringSet("selectedTasks",Collections.emptySet()).size()==2,"selected tasks persisted");
    }
    private void timelineSmoke()throws Exception{
        Context c=getTargetContext();Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Snapshot data=new Snapshot();data.groups.add(new Group(1,"Группа",Ui.GREEN));
        ZoneId z=ZoneId.systemDefault();LocalDate monday=TimeMath.monday(LocalDate.now()).minusWeeks(1);
        for(int t=1;t<=8;t++){data.tasks.add(new Task(t,1,"Занятие "+t,"",false));for(int day=0;day<7;day++){long start=TimeMath.start(monday.plusDays(day),z)+8*TimeMath.HOUR;data.sessions.add(new Session(t*10+day,t,start,start+8*TimeMath.HOUR));}}
        android.widget.ScrollView scroll=new android.widget.ScrollView(activity);
        runOnMainSync(()->{TimelineView timeline=new TimelineView(activity,data,monday,true,0,s->{});scroll.addView(timeline);activity.setContentView(scroll);});
        waitForIdleSync();SystemClock.sleep(700);checkTimelinePixels("top of full week");
        runOnMainSync(()->scroll.fullScroll(android.view.View.FOCUS_DOWN));waitForIdleSync();SystemClock.sleep(700);checkTimelinePixels("bottom of full week");
    }
    private void checkTimelinePixels(String label){
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"timeline screenshot");int colored=0;
        for(int y=0;y<bitmap.getHeight();y+=3)for(int x=bitmap.getWidth()/3;x<bitmap.getWidth();x+=3){int color=bitmap.getPixel(x,y);if(android.graphics.Color.green(color)>android.graphics.Color.red(color)+30&&android.graphics.Color.green(color)>android.graphics.Color.blue(color)+10)colored++;}
        bitmap.recycle();check(colored>1000,"visible activity bars: "+label+" ("+colored+")");
    }
    private void seed(){
        Context c=getTargetContext();Store store=Store.get(c);Snapshot data=store.snapshot();if(data.tasks.size()>1)return;
        long work=data.groups.get(0).id,life=data.groups.get(1).id,sleep=data.tasks.get(0).id;long a=store.saveTask(0,work,"TGPA-204 · Отчёт","Подготовить результаты исследования и выводы для команды."),b=store.saveTask(0,work,"Обсуждение проекта","Встречи, письма и согласования."),study=store.saveTask(0,life,"Математический анализ","Лекции и практика"),walk=store.saveTask(0,life,"Прогулка","");
        ZoneId z=ZoneId.systemDefault();LocalDate today=LocalDate.now(),monday=TimeMath.monday(today);long now=System.currentTimeMillis();
        for(int i=0;i<7;i++){LocalDate d=monday.plusDays(i);if(d.isAfter(today))break;long start=TimeMath.start(d,z);for(long[] row:new long[][]{{sleep,start,start+7*TimeMath.HOUR},{a,start+9*TimeMath.HOUR,start+12*TimeMath.HOUR},{b,start+11*TimeMath.HOUR,start+12*TimeMath.HOUR},{study,start+14*TimeMath.HOUR,start+16*TimeMath.HOUR},{walk,start+17*TimeMath.HOUR,start+18*TimeMath.HOUR}})if(row[2]<now)store.saveSession(0,row[0],row[1],row[2],now);}
        store.toggle(a,now-24*60000);store.toggle(b,now-8*60000);
    }
}
