package app.dayline;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import app.dayline.core.*;
import app.dayline.core.Model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import static app.dayline.Ui.*;

public final class MainActivity extends Activity {
    Store store;Snapshot data;final ZoneId zone=ZoneId.systemDefault();
    long groupId=1,analysisGroup=0;int mode=0;boolean weekly=true,archived=false,zoom=false;
    LocalDate selectedDate=LocalDate.now();String query="";int historyLimit=30;
    final Set<Long> selectedTasks=new LinkedHashSet<>();
    boolean selectionActive=false,averageMode=false;int averageWeekday=0;
    TextView selectionSummary;
    private LinearLayout root,body,taskList;private ScrollView scroll;
    private final List<Runnable> live=new ArrayList<>(),taskLive=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());private TimelineView timeline;
    private LocalDate renderedToday;private boolean resumed;
    private Set<Long> exportTasks;
    private long exportGroup;private LocalDate exportWeek=TimeMath.monday(LocalDate.now());
    private final java.util.concurrent.ExecutorService files=Executors.newSingleThreadExecutor();
    private final Runnable ticker=new Runnable(){@Override public void run(){if(!resumed)return;if(!LocalDate.now().equals(renderedToday)){if(selectedDate.equals(renderedToday))selectedDate=LocalDate.now();render(false);}else{for(Runnable r:live)r.run();for(Runnable r:taskLive)r.run();if(timeline!=null)timeline.invalidate();}handler.postDelayed(this,1000);}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){changed(false);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?BG:GREEN);store=Store.get(this);groupId=getPreferences(0).getLong("group",1);
        selectionActive=getPreferences(0).getBoolean("selectionActive",false);
        for(String id:getPreferences(0).getStringSet("selectedTasks",Collections.emptySet()))try{selectedTasks.add(Long.parseLong(id));}catch(NumberFormatException ignored){}
        if(saved!=null){if(saved.containsKey("exportTasks")){exportTasks=new LinkedHashSet<>();for(long id:saved.getLongArray("exportTasks"))exportTasks.add(id);}averageMode=saved.getBoolean("averageMode",false);averageWeekday=saved.getInt("averageWeekday",0);groupId=saved.getLong("group",1);analysisGroup=saved.getLong("analysisGroup",0);mode=saved.getInt("mode",0);weekly=saved.getBoolean("weekly",true);selectedDate=LocalDate.parse(saved.getString("date",LocalDate.now().toString()));query=saved.getString("query","");archived=saved.getBoolean("archived",false);exportGroup=saved.getLong("exportGroup",0);exportWeek=LocalDate.parse(saved.getString("exportWeek",LocalDate.now().toString()));}
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(TimerService.CHANGED),Context.RECEIVER_NOT_EXPORTED);else registerLegacyUpdates();
        render(false);
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerLegacyUpdates(){
        // Called only below API 33; a signature permission protects this legacy receiver.
        registerReceiver(receiver,new IntentFilter(TimerService.CHANGED),"app.dayline.INTERNAL",null);
    }
    @Override protected void onResume(){super.onResume();resumed=true;render(true);syncService();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);unregisterReceiver(receiver);files.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(exportTasks!=null){long[] ids=new long[exportTasks.size()];int at=0;for(long id:exportTasks)ids[at++]=id;out.putLongArray("exportTasks",ids);}out.putBoolean("averageMode",averageMode);out.putInt("averageWeekday",averageWeekday);out.putLong("group",groupId);out.putLong("analysisGroup",analysisGroup);out.putInt("mode",mode);out.putBoolean("weekly",weekly);out.putString("date",selectedDate.toString());out.putString("query",query);out.putBoolean("archived",archived);out.putLong("exportGroup",exportGroup);out.putString("exportWeek",exportWeek.toString());}
    void changed(boolean preserve){render(preserve);syncService();}
    void syncService(){try{TimerService.sync(this);}catch(RuntimeException e){toast("Записи сохранены. Уведомление недоступно — проверьте настройки приложения.");}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void safe(Runnable action){try{action.run();}catch(IllegalArgumentException|android.database.SQLException e){new AlertDialog.Builder(this).setTitle("Проверьте запись").setMessage(e.getMessage()).setPositiveButton("Понятно",null).show();}}
    private void notificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(0).getBoolean("askedNotifications",false)){getPreferences(0).edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);}}
    void toggle(Task t){safe(()->{notificationPermission();store.toggle(t.id,System.currentTimeMillis());changed(true);});}
    void render(boolean preserveScroll){
        int oldY=preserveScroll&&scroll!=null?scroll.getScrollY():0;
        data=store.snapshot();if(data.group(groupId)==null&&!data.groups.isEmpty())groupId=data.groups.get(0).id;if(analysisGroup!=0&&data.group(analysisGroup)==null)analysisGroup=0;
        renderedToday=LocalDate.now();live.clear();taskLive.clear();timeline=null;selectionSummary=null;selectedTasks.removeIf(id->data.task(id)==null);
        root=column(this);root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets system=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(system.left,system.top,system.right,system.bottom);return insets;});}
        LinearLayout brand=row(this);pad(brand,22,12);
        LinearLayout title=column(this);add(title,text(this,"Dayline",28,INK,true),0);add(title,label(this,mode==0?"Ваш день, занятие за занятием":"Понятная картина вашего времени"),4);weight(brand,title);
        TextView options=button(this,"•••",false,this::settings);options.setContentDescription("Настройки и экспорт");brand.addView(options,new LinearLayout.LayoutParams(dp(this,48),dp(this,48)));root.addView(brand);
        if(mode==1&&selectionActive)addSelectionBar();
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        body=column(this);pad(body,20,8);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(mode==0)tracking();else analysis();
        LinearLayout nav=row(this);pad(nav,20,12);nav.setBackgroundColor(BG);
        TextView track=button(this,"◷  Трекинг",mode==0,()->{mode=0;render(false);});weight(nav,track);
        Space space=new Space(this);nav.addView(space,new LinearLayout.LayoutParams(dp(this,10),1));weight(nav,button(this,"▤  Анализ",mode==1,()->{mode=1;selectedDate=LocalDate.now();render(false);}));root.addView(nav);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null)bars.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
        root.requestApplyInsets();scroll.post(()->scroll.scrollTo(0,oldY));for(Runnable r:live)r.run();for(Runnable r:taskLive)r.run();
    }
    void tabs(LinearLayout parent,boolean forAnalysis){
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);LinearLayout tabs=row(this);
        if(forAnalysis)tab(tabs,"Все",analysisGroup==0,()->{analysisGroup=0;render(false);});
        for(Group g:data.groups){boolean active=forAnalysis?analysisGroup==g.id:groupId==g.id;String n=g.name+(data.runningCount(g.id)>0?" •":"");tab(tabs,n,active,()->{if(forAnalysis)analysisGroup=g.id;else{groupId=g.id;archived=false;getPreferences(0).edit().putLong("group",groupId).apply();}render(false);});}
        tab(tabs,"＋",false,()->new Editors(this).group(null));sc.addView(tabs);add(parent,sc,4);
    }
    private void tab(LinearLayout row,String s,boolean selected,Runnable action){TextView v=chip(this,s,selected,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=dp(this,7);row.addView(v,p);}
    private void tracking(){
        tabs(body,false);Group group=data.group(groupId);
        LinearLayout summary=column(this);pad(summary,20,18);summary.setBackground(bg(group.color,0,22,this));
        LinearLayout head=row(this);TextView title=text(this,group.name+"  ✎",21,WHITE,true);title.setOnClickListener(v->new Editors(this).group(group));title.setContentDescription("Изменить группу "+group.name);weight(head,title);
        TextView stop=button(this,"■ Стоп группы",false,()->safe(()->{store.stopGroup(groupId,System.currentTimeMillis());changed(true);}));stop.setTextSize(13);stop.setMinHeight(dp(this,44));stop.setTextColor(WHITE);stop.setBackground(bg(0x28ffffff,0,12,this));stop.setEnabled(data.runningCount(groupId)>0);stop.setAlpha(stop.isEnabled()?1f:.45f);head.addView(stop);add(summary,head,0);
        LinearLayout values=row(this);LinearLayout today=column(this),week=column(this);TextView dayValue=text(this,"",24,WHITE,true),weekValue=text(this,"",24,WHITE,true);
        add(today,text(this,"СЕГОДНЯ",11,0xcfffffff,true),0);add(today,dayValue,8);add(week,text(this,"ЭТА НЕДЕЛЯ",11,0xcfffffff,true),0);add(week,weekValue,8);weight(values,today);weight(values,week);add(summary,values,22);
        TextView state=text(this,"",12,0xe0ffffff,false);add(summary,state,18);
        live.add(()->{long now=System.currentTimeMillis();List<Session> all=data.forGroup(groupId);LocalDate d=TimeMath.day(now,zone);dayValue.setText(TimeMath.shortTime(TimeMath.sum(all,TimeMath.start(d,zone),TimeMath.start(d.plusDays(1),zone),now)));weekValue.setText(TimeMath.shortTime(TimeMath.sum(all,TimeMath.start(TimeMath.monday(d),zone),TimeMath.start(TimeMath.monday(d).plusDays(7),zone),now)));int n=data.runningCount(groupId);state.setText(n>0?"●  Сейчас идёт занятий: "+n:"Все таймеры этой группы остановлены");});
        add(body,summary,16);
        EditText search=input(this,"Поиск задач во всех группах",query,false);search.setContentDescription("Поиск задач");search.setCompoundDrawablePadding(dp(this,8));add(body,search,18);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();fillTasks();}public void afterTextChanged(Editable e){}});
        LinearLayout actions=row(this);weight(actions,text(this,"Задачи",20,INK,true));actions.addView(button(this,"＋ Задача",false,()->new Editors(this).task(null,groupId)));add(body,actions,18);
        long closed=data.tasks.stream().filter(t->t.groupId==groupId&&t.archived).count();
        TextView archive=button(this,archived?"← Открытые задачи":"Завершённые · "+closed,false,()->{archived=!archived;render(false);});archive.setBackgroundColor(android.graphics.Color.TRANSPARENT);archive.setTextSize(13);archive.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);add(body,archive,2);
        taskList=column(this);add(body,taskList,4);fillTasks();gap(body,10);
        if(data.runningCount(0)>0){TextView running=label(this,"● Сейчас работают таймеры: "+data.runningCount(0)+" · во всех группах");running.setTextColor(GREEN);add(body,running,12);}
    }
    private void fillTasks(){
        if(taskList==null)return;taskList.removeAllViews();taskLive.clear();List<Task> list=new ArrayList<>();String q=query.trim().toLowerCase(TimeMath.RU);
        for(Task t:data.tasks)if(t.archived==archived&&(q.isEmpty()?t.groupId==groupId:t.name.toLowerCase(TimeMath.RU).contains(q)))list.add(t);
        list.sort((a,b)->{boolean ar=data.running(a.id)!=null,br=data.running(b.id)!=null;if(ar!=br)return ar?-1:1;return Long.compare(a.id,b.id);});
        if(list.isEmpty()){
            LinearLayout empty=card(this);add(empty,text(this,q.isEmpty()?(archived?"Пока нет завершённых задач":"С чего начнём?"):"Ничего не найдено",20,INK,true),0);
            add(empty,label(this,q.isEmpty()?(archived?"Закрытые задачи сохранят свои записи и отчёты.":"Добавьте занятие и нажмите ▶. Можно запустить несколько таймеров."):"Попробуйте другое название или проверьте завершённые задачи."),10);
            if(q.isEmpty()&&!archived)add(empty,button(this,"＋ Добавить задачу",true,()->new Editors(this).task(null,groupId)),18);add(taskList,empty,4);return;
        }
        for(Task t:list){
            Session active=data.running(t.id);Group g=data.group(t.groupId);LinearLayout card=card(this);if(active!=null)card.setBackground(bg(0xffeef5e9,0xffbdd2b9,20,this));
            LinearLayout top=row(this);LinearLayout names=column(this);TextView name=text(this,t.name,18,INK,true);name.setMaxLines(3);add(names,name,0);
            if(!q.isEmpty())add(names,pill(this,g.name,g.color),7);
            name.setOnClickListener(v->new Editors(this).history(t));name.setContentDescription("История задачи "+t.name);weight(top,names);
            TextView more=button(this,"⋮",false,()->taskMenu(t));more.setBackgroundColor(android.graphics.Color.TRANSPARENT);more.setContentDescription("Действия с задачей "+t.name);top.addView(more,new LinearLayout.LayoutParams(dp(this,40),dp(this,52)));
            TextView play=button(this,t.archived?"↶":active==null?"▶":"■",active!=null,()->{if(t.archived)safe(()->{store.archive(t.id,false,System.currentTimeMillis());changed(true);});else toggle(t);});
            play.setTextSize(22);play.setContentDescription(t.archived?"Вернуть задачу "+t.name:active==null?"Запустить "+t.name:"Остановить "+t.name);top.addView(play,new LinearLayout.LayoutParams(dp(this,56),dp(this,56)));add(card,top,0);
            if(!t.note.isEmpty()){TextView description=label(this,"Описание ▾");description.setMinHeight(dp(this,36));description.setGravity(Gravity.CENTER_VERTICAL);TextView note=text(this,t.note,14,MUTED,false);note.setVisibility(View.GONE);description.setOnClickListener(v->{boolean open=note.getVisibility()==View.VISIBLE;note.setVisibility(open?View.GONE:View.VISIBLE);description.setText(open?"Описание ▾":"Описание ▴");});add(card,description,4);add(card,note,0);}
            TextView counts=text(this,"",13,MUTED,false);add(card,counts,14);TextView current=text(this,"",21,GREEN,true);current.setTypeface(Typeface.create("monospace",Typeface.NORMAL));if(active!=null)add(card,current,10);
            taskLive.add(()->{long now=System.currentTimeMillis();LocalDate day=TimeMath.day(now,zone);long a=TimeMath.start(day,zone),b=TimeMath.start(day.plusDays(1),zone),w=TimeMath.start(TimeMath.monday(day),zone),e=TimeMath.start(TimeMath.monday(day).plusDays(7),zone);List<Session> sessions=data.forTask(t.id);counts.setText("Сегодня  "+TimeMath.shortTime(TimeMath.sum(sessions,a,b,now))+"   ·   Неделя  "+TimeMath.shortTime(TimeMath.sum(sessions,w,e,now)));if(active!=null)current.setText("● "+TimeMath.clock(now-active.start));});
            add(taskList,card,10);
        }
        for(Runnable r:taskLive)r.run();
    }
    private void taskMenu(Task task){
        String[] items={"История и правки","Изменить задачу","Добавить время вручную",task.archived?"Вернуть в открытые":"Завершить задачу"};
        new AlertDialog.Builder(this).setTitle(task.name).setItems(items,(d,which)->{Editors ed=new Editors(this);if(which==0)ed.history(task);if(which==1)ed.task(task,task.groupId);if(which==2)ed.session(null,task.id);if(which==3)safe(()->{store.archive(task.id,!task.archived,System.currentTimeMillis());changed(true);toast(task.archived?"Задача снова открыта":"Задача завершена. История сохранена.");});}).show();
    }
    private void analysis(){new AnalysisScreen(this,body,live).render();}
    void setTimeline(TimelineView view){timeline=view;}
    Set<Long> selectionFilter(){return selectionActive?new LinkedHashSet<>(selectedTasks):null;}
    void saveSelection(){Set<String> ids=new HashSet<>();for(long id:selectedTasks)ids.add(Long.toString(id));getPreferences(0).edit().putBoolean("selectionActive",selectionActive).putStringSet("selectedTasks",ids).apply();}
    void toggleSelection(long taskId){selectionActive=true;if(!selectedTasks.add(taskId))selectedTasks.remove(taskId);saveSelection();render(true);}
    void clearSelection(){selectedTasks.clear();selectionActive=false;saveSelection();render(true);}
    private void addSelectionBar(){
        LinearLayout box=column(this);pad(box,20,8);box.setBackgroundColor(MINT);LinearLayout line=row(this);
        TextView title=text(this,"Выбрано задач: "+selectedTasks.size(),15,GREEN,true);weight(line,title);
        TextView edit=button(this,"Изменить",false,()->new AnalysisScreen(this,body,live).chooseTasks());edit.setTextSize(13);line.addView(edit);
        TextView close=button(this,"×",false,this::clearSelection);close.setContentDescription("Закрыть выбор задач");line.addView(close,new LinearLayout.LayoutParams(dp(this,48),dp(this,48)));add(box,line,0);
        selectionSummary=text(this,"",13,INK,true);selectionSummary.setContentDescription("Итоги выбранных задач");add(box,selectionSummary,3);root.addView(box);
    }
    void addMenu(){new AlertDialog.Builder(this).setTitle("Добавить").setItems(new String[]{"Задачу","Группу","Время вручную"},(d,w)->{Editors e=new Editors(this);if(w==0)e.task(null,analysisGroup==0?groupId:analysisGroup);if(w==1)e.group(null);if(w==2)e.session(null,0);}).show();}
    private void settings(){new AlertDialog.Builder(this).setTitle("Dayline").setItems(new String[]{"Экспорт недели в Excel","Сохранить резервную копию","Восстановить из копии","Настройки уведомлений","Как работает учёт"},(d,w)->{
        if(w==0)exportDialog();if(w==1)createDocument("application/json","dayline-backup-"+LocalDate.now()+".json",101);
        if(w==2){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,102);}
        if(w==3)startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        if(w==4)new AlertDialog.Builder(this).setTitle("Всё под вашим контролем").setMessage("▶ запускает таймер, ■ останавливает. Можно заниматься несколькими задачами одновременно.\n\n«Стоп группы» останавливает только текущую группу. Сон также запускается вручную.\n\nДень начинается в 00:00, неделя — в понедельник. История хранится на телефоне. Таймеры продолжают считать по сохранённому времени, даже если приложение закрыто. После перезагрузки работающие таймеры тоже продолжаются.\n\nДля правки нажмите на отрезок ленты или запись в истории. Перенесённое время попадёт в выбранную задачу, остальные пересекающиеся занятия не изменятся.\n\nНа vivo разрешите уведомления и фоновую работу Dayline в настройках батареи, чтобы управление таймерами было доступно в шторке.\n\nExcel — готовый снимок выбранной недели. Для сохранения всей истории используйте резервную копию. В копии работающие сеансы завершаются на момент сохранения; текущие таймеры телефона продолжаются.\n\nDayline 1.1.0 · без рекламы и доступа к интернету").setPositiveButton("Понятно",null).show();
    }).show();}
    void exportDialog(){
        LinearLayout form=column(this);pad(form,20,8);LocalDate week=TimeMath.monday(mode==0?LocalDate.now():selectedDate);
        add(form,text(this,"Неделя с "+week.format(DateTimeFormatter.ofPattern("d MMMM",TimeMath.RU)),17,INK,true),0);
        add(form,label(this,"Что включить в подробный отчёт"),16);Spinner scope=new Spinner(this);List<String> labels=new ArrayList<>();labels.add("Все группы");for(Group g:data.groups)labels.add(g.name);scope.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));add(form,scope,6);
        if(mode==1&&analysisGroup!=0)for(int i=0;i<data.groups.size();i++)if(data.groups.get(i).id==analysisGroup)scope.setSelection(i+1);
        CheckBox selectedOnly=new CheckBox(this);selectedOnly.setText("Только выбранные задачи: "+selectedTasks.size());selectedOnly.setTextColor(INK);selectedOnly.setChecked(mode==1&&selectionActive);
        if(mode==1&&selectionActive){add(form,selectedOnly,12);scope.setEnabled(false);selectedOnly.setOnCheckedChangeListener((b,checked)->scope.setEnabled(!checked));}
        add(form,label(this,"При выборе группы или задач остальные занятия в ленте будут объединены в «Другое». Их названия и описания в файл не попадут."),16);
        new AlertDialog.Builder(this).setTitle("Готовый отчёт Excel").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{exportWeek=week;exportTasks=selectedOnly.isChecked()?new LinkedHashSet<>(selectedTasks):null;exportGroup=scope.getSelectedItemPosition()==0?0:data.groups.get(scope.getSelectedItemPosition()-1).id;createDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","dayline-"+week+(exportGroup==0?"":"-group")+".xlsx",100);}).show();
    }
    private void createDocument(String mime,String name,int code){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,code);}
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;Uri uri=intent.getData();
        if(request==102){
            files.execute(()->{try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new IOException("Не удалось открыть файл");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){if(out.size()+n>25_000_000)throw new IOException("Файл слишком большой");out.write(b,0,n);}String json=out.toString(StandardCharsets.UTF_8.name());
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("Заменить историю?").setMessage("Задачи и записи на телефоне будут заменены содержимым копии. Сохраните текущую копию, если она нужна.").setNegativeButton("Отмена",null).setPositiveButton("Восстановить",(d,w)->files.execute(()->{try{store.restore(json,System.currentTimeMillis());runOnUiThread(()->{if(!isDestroyed()){changed(false);toast("История восстановлена");}});}catch(Exception e){fileError(e);}})).show();});
            }catch(Exception e){fileError(e);}});return;
        }
        if(request==100||request==101){
            long now=System.currentTimeMillis();Snapshot captured=store.snapshot();LocalDate week=exportWeek;long group=exportGroup;Set<Long> tasks=exportTasks==null?null:new LinkedHashSet<>(exportTasks);toast("Сохраняю файл…");
            files.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("Не удалось открыть место сохранения");if(request==100)ExcelReport.write(out,captured,week,zone,group,now,tasks);else out.write(store.backup(now).getBytes(StandardCharsets.UTF_8));
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("Файл сохранён").setMessage(request==100?"Готовые сводки, подробные записи и лента недели находятся в Excel-файле.":"Резервная копия содержит группы, задачи и всю историю.").setNegativeButton("Готово",null).setPositiveButton("Открыть",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,request==100?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/json").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException e){toast("Файл сохранён. Его можно открыть через приложение «Файлы».");}}).show();});
            }catch(Exception e){fileError(e);}});
        }
    }
    private void fileError(Exception e){runOnUiThread(()->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Не удалось обработать файл").setMessage(e.getMessage()==null?"Попробуйте другое место сохранения или проверьте файл.":e.getMessage()).setPositiveButton("Понятно",null).show();});}
    private float swipeX,swipeY;
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN){swipeX=event.getX();swipeY=event.getY();}
        if(event.getAction()==MotionEvent.ACTION_UP&&mode==0&&query.isEmpty()&&swipeY>dp(this,240)&&Math.abs(event.getX()-swipeX)>dp(this,85)&&Math.abs(event.getY()-swipeY)<dp(this,50)){
            int index=0;for(int i=0;i<data.groups.size();i++)if(data.groups.get(i).id==groupId)index=i;int next=index+(event.getX()<swipeX?1:-1);
            if(next>=0&&next<data.groups.size()){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();groupId=data.groups.get(next).id;getPreferences(0).edit().putLong("group",groupId).apply();archived=false;render(false);return true;}
        }
        return super.dispatchTouchEvent(event);
    }
}
