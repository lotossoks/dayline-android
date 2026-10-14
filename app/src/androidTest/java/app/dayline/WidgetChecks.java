package app.dayline;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import app.dayline.core.Model.*;
import java.util.*;
import java.util.function.*;

/** Runs only in the separate test APK. Launcher checks require a disposable, fresh installation. */
final class WidgetChecks {
    private final Instrumentation test;private final Context c;private int checks;
    WidgetChecks(Instrumentation test){this.test=test;c=test.getTargetContext();}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void await(BooleanSupplier condition,String message){for(int n=0;n<60&&!condition.getAsBoolean();n++)SystemClock.sleep(150);check(condition.getAsBoolean(),message);}
    int store(){
        String name="dayline-widget-test.db";c.deleteDatabase(name);Store store=new Store(c,name);long now=System.currentTimeMillis();
        try{
            long work=store.saveTask(0,1,"Работа",""),life=store.saveTask(0,2,"Жизнь",""),closed=store.saveTask(0,1,"Завершённая","");store.archive(closed,true,now);
            store.saveSession(0,work,now-100000,now-90000,now);
            check(store.controlTimer(work,0,0,now-5000),"widget starts task");Session first=store.snapshot().running(work);
            check(!store.controlTimer(work,0,0,now-4000),"duplicate start does not toggle");check(store.snapshot().running(work).id==first.id,"duplicate start preserves session");
            check(store.controlTimer(life,0,0,now-4000),"widget permits parallel task");
            Snapshot compact=store.widgetSnapshot();check(compact.sessions.size()==2&&compact.tasks.size()==4&&compact.groups.size()==3,"widget snapshot excludes completed intervals only");
            List<Task> all=TimerWidget.tasks(compact,0);check(all.size()==3&&all.get(0).id==work&&all.get(1).id==life&&all.get(2).id==1,"active first, archive excluded");
            List<Task> filtered=TimerWidget.tasks(compact,2);check(filtered.size()==1&&filtered.get(0).id==life,"group filter");
            check(!store.controlTimer(work,first.id,first.start+1,now-3000),"stale start timestamp rejected");
            check(store.controlTimer(work,first.id,first.start,now-3000),"stop exact session");
            check(!store.controlTimer(work,first.id,first.start,now-2000),"duplicate stop does not restart");
            check(store.snapshot().running(life)!=null,"stop leaves other task running");
            check(store.controlTimer(work,0,0,now-1000),"restart creates new session");
            check(!store.controlTimer(work,first.id,first.start,now),"old stop cannot stop new session");check(store.snapshot().running(work)!=null,"new session still running");
            int before=store.snapshot().sessions.size();boolean rejected=false;try{store.controlTimer(closed,0,0,now);}catch(IllegalArgumentException expected){rejected=true;}check(rejected&&store.snapshot().sessions.size()==before,"archived task start rejected without changes");
            rejected=false;try{store.controlTimer(999,0,0,now);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"missing task rejected");
            Store reopened=new Store(c,name);check(reopened.widgetSnapshot().runningCount(0)==2,"widget state survives reopening");reopened.close();
        }finally{store.close();c.deleteDatabase(name);}
        return checks;
    }
    private AccessibilityNodeInfo walk(AccessibilityNodeInfo node,Predicate<AccessibilityNodeInfo> predicate){if(node==null)return null;if(predicate.test(node))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=walk(node.getChild(i),predicate);if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo find(String label){return walk(test.getUiAutomation().getRootInActiveWindow(),n->label.equalsIgnoreCase(String.valueOf(n.getText()))||label.equalsIgnoreCase(String.valueOf(n.getContentDescription())));}
    private void click(String label){await(()->find(label)!=null,"visible: "+label);AccessibilityNodeInfo n=find(label);while(n!=null&&!n.isClickable())n=n.getParent();check(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK),"click: "+label);SystemClock.sleep(350);}
    private void home(){c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(700);}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())){byte[] buffer=new byte[1024];while(in.read(buffer)>0){}}}
    private void screenshot(String name)throws Exception{android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();check(bitmap!=null,"screenshot: "+name);java.io.File folder=new java.io.File(c.getExternalFilesDir(null),"qa");folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private String clock(String task){AccessibilityNodeInfo title=find(task);AccessibilityNodeInfo n=title==null?null:walk(title.getParent(),v->"app.dayline:id/widget_clock".equals(v.getViewIdResourceName()));return n==null?"":String.valueOf(n.getText());}
    int launcher()throws Exception{
        android.accessibilityservice.AccessibilityServiceInfo accessibility=test.getUiAutomation().getServiceInfo();accessibility.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;test.getUiAutomation().setServiceInfo(accessibility);
        Store store=Store.get(c);check(store.snapshot().tasks.size()==1,"launcher test requires fresh installation");
        long work=store.saveTask(0,1,"TGPA-204 · Отчёт",""),life=store.saveTask(0,2,"Прогулка",""),done=store.saveTask(0,1,"Завершённая задача","");store.archive(done,true,System.currentTimeMillis());
        MainActivity main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        click("Настройки и экспорт");click("Виджет на рабочий стол");click("Add to home screen");home();
        await(()->find("Запустить TGPA-204 · Отчёт")!=null,"launcher renders real widget collection");
        check(find("Завершённая задача")==null,"archived task not shown");
        AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=manager.getAppWidgetIds(new ComponentName(c,TimerWidget.class));check(ids.length==1,"one pinned widget");int id=ids[0];
        click("Запустить TGPA-204 · Отчёт");await(()->store.snapshot().running(work)!=null,"launcher start stored");
        click("Запустить Прогулка");await(()->store.snapshot().runningCount(0)==2,"two timers started from launcher");
        await(()->find("Все группы · идёт: 2")!=null,"active count refreshed");
        check(!"app.dayline".contentEquals(test.getUiAutomation().getRootInActiveWindow().getPackageName()),"start stays on launcher");
        String firstClock=clock("TGPA-204 · Отчёт");await(()->!firstClock.equals(clock("TGPA-204 · Отчёт"))&&!clock("TGPA-204 · Отчёт").isEmpty(),"launcher chronometer advances");screenshot("widget-active");
        click("Остановить TGPA-204 · Отчёт");await(()->store.snapshot().running(work)==null,"launcher stop stored");check(store.snapshot().running(life)!=null,"other timer continues");
        click("Настроить группу виджета");click("Все группы");click("Работа");click("Сохранить виджет");home();
        await(()->find("Работа · идёт: 0")!=null,"selected group displayed");check(TimerWidget.settings(c).getLong(TimerWidget.key(id),-1)==1,"widget group persisted");check(find("Прогулка")==null,"other group hidden");screenshot("widget-work");
        click("Открыть Dayline");await(()->find("Изменить группу Работа")!=null,"widget opens selected group in app");
        click("Запустить TGPA-204 · Отчёт");home();await(()->find("Остановить TGPA-204 · Отчёт")!=null,"app changes refresh launcher");
        // Notification uses this same explicit group-stop service intent.
        c.startService(new Intent(c,TimerService.class).setAction("STOP_GROUP").putExtra("group",1L));
        await(()->find("Запустить TGPA-204 · Отчёт")!=null,"notification group stop refreshes widget");check(store.snapshot().running(life)!=null,"group stop preserves other group");
        store.deleteGroup(1,2);TimerWidget.refresh(c);await(()->find("Все группы · идёт: 1")!=null,"deleted group falls back to all groups");check(store.snapshot().running(life)!=null&&store.snapshot().task(work).groupId==2,"group transfer preserves timer and task");
        // A long task list must remain reachable without opening the app or sending a huge view tree.
        for(int i=0;i<101;i++)store.saveTask(0,2,"Дополнительная "+i,"");TimerWidget.refresh(c);
        await(()->find("Следующие задачи")!=null,"long list has paging controls");click("Следующие задачи");
        AccessibilityNodeInfo nextTask=walk(test.getUiAutomation().getRootInActiveWindow(),n->String.valueOf(n.getContentDescription()).startsWith("Запустить Дополнительная "));check(nextTask!=null,"next page exposes additional task");String action=nextTask.getContentDescription().toString(),name=action.substring("Запустить ".length());long idOnPage=0;for(Task t:store.snapshot().tasks)if(t.name.equals(name))idOnPage=t.id;final long extraId=idOnPage;
        click(action);await(()->store.snapshot().running(extraId)!=null,"next-page button targets correct task");await(()->find("Остановить "+name)!=null,"starting task returns to active first page");
        click("Остановить "+name);await(()->store.snapshot().running(extraId)==null,"paged task stops");
        store.stopGroup(2,System.currentTimeMillis());TimerService.sync(c);
        click("Открыть Dayline");click("Настройки и экспорт");click("Виджет на рабочий стол");click("Add to home screen");home();
        await(()->manager.getAppWidgetIds(new ComponentName(c,TimerWidget.class)).length==2,"second widget pinned");int secondId=0;for(int candidate:manager.getAppWidgetIds(new ComponentName(c,TimerWidget.class)))if(candidate!=id)secondId=candidate;
        test.startActivitySync(new Intent(c,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,secondId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));click("Все группы");click("Сон");click("Сохранить виджет");home();
        android.util.DisplayMetrics screen=c.getResources().getDisplayMetrics();for(int attempt=0;attempt<4&&find("Сон · идёт: 0")==null;attempt++){shell("input swipe "+screen.widthPixels*4/5+" "+screen.heightPixels*4/5+" "+screen.widthPixels/5+" "+screen.heightPixels*4/5+" 250");SystemClock.sleep(500);}
        await(()->find("Сон · идёт: 0")!=null,"second widget shows its own group");check(TimerWidget.settings(c).getLong(TimerWidget.key(id),-1)==0,"second widget does not change first widget settings");
        click("Запустить Сон");await(()->store.snapshot().running(1)!=null,"second widget starts selected task");click("Остановить Сон");await(()->store.snapshot().running(1)==null,"second widget stops selected task");
        return checks;
    }
}
