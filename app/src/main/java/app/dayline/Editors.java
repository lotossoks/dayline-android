package app.dayline;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import app.dayline.core.*;
import app.dayline.core.Model.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static app.dayline.Ui.*;

final class Editors {
    private final MainActivity a;
    Editors(MainActivity activity){a=activity;}
    private LinearLayout form(){LinearLayout f=column(a);pad(f,20,12);return f;}
    private void saveDialog(String title,LinearLayout form,String action,Runnable save){
        TextView error=text(a,"",14,RED,false);error.setVisibility(View.GONE);add(form,error,14);ScrollView sc=new ScrollView(a);sc.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(sc).setNegativeButton("Отмена",null).setPositiveButton(action,null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{save.run();dialog.dismiss();}catch(IllegalArgumentException|android.database.SQLException e){error.setText(e.getMessage());error.setVisibility(View.VISIBLE);sc.post(()->sc.fullScroll(View.FOCUS_DOWN));}}));dialog.show();
    }
    void group(Group current){
        LinearLayout f=form();EditText name=input(a,"Название группы",current==null?"":current.name,false);add(f,name,0);add(f,label(a,"Цвет группы"),18);
        int[] colors={GREEN,0xffbd713f,0xff7270b1,0xff467f9d,0xffad6275,0xff918333};int[] selected={current==null?colors[a.data.groups.size()%colors.length]:current.color};
        LinearLayout choices=row(a);List<TextView> items=new ArrayList<>();
        for(int color:colors){TextView swatch=button(a,selected[0]==color?"✓":"",true,()->{});swatch.setTextSize(20);swatch.setBackground(bg(color,0,12,a));swatch.setContentDescription("Выбрать цвет "+Integer.toHexString(color));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(a,48),1);p.rightMargin=dp(a,5);choices.addView(swatch,p);items.add(swatch);swatch.setOnClickListener(v->{selected[0]=color;for(int i=0;i<items.size();i++)items.get(i).setText(colors[i]==color?"✓":"");});}
        add(f,choices,10);saveDialog(current==null?"Новая группа":"Изменить группу",f,"Сохранить",()->{long id=a.store.saveGroup(current==null?0:current.id,name.getText().toString(),selected[0]);a.groupId=id;if(a.mode==1)a.analysisGroup=id;a.changed(false);});
    }
    void task(Task current,long groupId){
        LinearLayout f=form();EditText name=input(a,"Например, Математический анализ",current==null?"":current.name,false);add(f,label(a,"Короткое название"),0);add(f,name,6);
        add(f,label(a,"Группа"),18);Spinner group=new Spinner(a);group.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,a.data.groups));for(int i=0;i<a.data.groups.size();i++)if(a.data.groups.get(i).id==groupId)group.setSelection(i);add(f,group,5);
        add(f,label(a,"Описание · необязательно"),18);EditText note=input(a,"Что входит в это занятие",current==null?"":current.note,true);add(f,note,6);
        if(current!=null)add(f,label(a,"При смене группы вместе с задачей переносится её история."),12);
        saveDialog(current==null?"Новая задача":"Изменить задачу",f,"Сохранить",()->{long target=((Group)group.getSelectedItem()).id;a.store.saveTask(current==null?0:current.id,target,name.getText().toString(),note.getText().toString());a.groupId=target;a.archived=current!=null&&current.archived;a.query="";a.changed(false);});
    }
    void history(Task task){history(task,60);}
    private void history(Task task,int limit){
        Snapshot snapshot=a.store.snapshot();List<Session> sessions=snapshot.forTask(task.id);LinearLayout f=form();
        if(!task.note.isEmpty())add(f,text(a,task.note,15,INK,false),0);
        LinearLayout actions=row(a);weight(actions,button(a,"＋ Добавить время",false,()->session(null,task.id)));add(f,actions,12);
        if(sessions.isEmpty())add(f,label(a,"Записей пока нет. Запустите таймер или добавьте время вручную."),20);
        ScrollView scroll=new ScrollView(a);scroll.addView(f);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(task.name).setView(scroll).setPositiveButton("Готово",null).create();
        int n=0;for(Session s:sessions){if(n++>=limit)break;String subtitle=TimeMath.dateTime(s.start,a.zone)+"\n"+(s.running()?"Сейчас идёт · ":"До "+TimeMath.dateTime(s.end,a.zone)+" · ")+TimeMath.shortTime(s.finish(System.currentTimeMillis())-s.start);TextView line=button(a,subtitle,false,()->{dialog.dismiss();sessionActions(s);});line.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);line.setTextSize(14);add(f,line,10);}
        if(sessions.size()>limit)add(f,button(a,"Показать ещё",false,()->{dialog.dismiss();history(task,limit+100);}),12);
        dialog.show();
    }
    void sessionActions(Session source){
        Session s=a.store.snapshot().session(source.id);if(s==null){a.toast("Запись уже изменена");return;}
        Task task=a.store.snapshot().task(s.taskId);String subtitle=TimeMath.dateTime(s.start,a.zone)+" — "+(s.running()?"сейчас":TimeMath.dateTime(s.end,a.zone));
        if(s.running())new AlertDialog.Builder(a).setTitle(task.name).setMessage(subtitle+"\n\nОстановите таймер, чтобы исправить запись.").setNegativeButton("Назад",null).setPositiveButton("Остановить и исправить",(d,w)->a.safe(()->{a.store.toggle(s.taskId,System.currentTimeMillis());a.changed(true);Session stopped=a.store.snapshot().session(s.id);if(stopped!=null)session(stopped,stopped.taskId);})).show();
        else new AlertDialog.Builder(a).setTitle(task.name+"\n"+subtitle).setItems(new String[]{"Изменить границы или задачу","Вырезать или перенести участок","Удалить эту запись"},(d,w)->{
            if(w==0)session(s,s.taskId);if(w==1)cut(s);if(w==2)new AlertDialog.Builder(a).setTitle("Удалить запись?").setMessage(subtitle+"\n"+TimeMath.shortTime(s.end-s.start)).setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dd,ww)->a.safe(()->{a.store.deleteSession(s.id);a.changed(true);a.toast("Запись удалена. Правку можно отменить в анализе.");})).show();
        }).show();
    }
    private List<Task> tasks(){List<Task> ts=new ArrayList<>(a.store.snapshot().tasks);ts.sort((x,y)->{if(x.archived!=y.archived)return x.archived?1:-1;int g=Long.compare(x.groupId,y.groupId);return g==0?x.name.compareToIgnoreCase(y.name):g;});return ts;}
    private Spinner taskChoice(LinearLayout form,List<Task> tasks,long selected,boolean withDelete){
        Spinner spinner=new Spinner(a);List<String> labels=new ArrayList<>();if(withDelete)labels.add("Не учитывать · вырезать участок");
        for(Task t:tasks)labels.add(a.data.group(t.groupId).name+" / "+t.name+(t.archived?" · завершена":""));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,labels);spinner.setAdapter(adapter);for(int i=0;i<tasks.size();i++)if(tasks.get(i).id==selected)spinner.setSelection(i+(withDelete?1:0));add(form,spinner,6);return spinner;
    }
    void session(Session s,long selectedTask){sessionForm(s,selectedTask,0,0);}
    void addBetween(long from,long to){sessionForm(null,0,from,to);}
    private void sessionForm(Session s,long selectedTask,long presetStart,long presetEnd){
        List<Task> tasks=tasks();if(tasks.isEmpty()){new AlertDialog.Builder(a).setTitle("Сначала нужна задача").setMessage("Создайте занятие, для которого хотите записать время.").setNegativeButton("Отмена",null).setPositiveButton("Создать задачу",(d,w)->task(null,a.groupId)).show();return;}
        if(s!=null&&s.running()){sessionActions(s);return;}
        if(selectedTask==0){long preferred=a.mode==0?a.groupId:a.analysisGroup;for(Task t:tasks)if(t.groupId==preferred&&!t.archived){selectedTask=t.id;break;}}
        LinearLayout f=form();add(f,label(a,"Задача"),0);Spinner task=taskChoice(f,tasks,selectedTask,false);
        long now=System.currentTimeMillis();long[] start={s==null?(presetStart>0?presetStart:now-30*60000):s.start},end={s==null?(presetEnd>0?presetEnd:now):s.end};
        TextView preview=text(a,"",15,GREEN,true);Runnable update=()->preview.setText(end[0]>start[0]?"Длительность: "+TimeMath.shortTime(end[0]-start[0]):"Окончание должно быть позже начала");
        moment(f,"Начало",start,update);moment(f,"Окончание",end,update);add(f,preview,18);update.run();
        if(s==null)add(f,label(a,"Можно записать пропущенное занятие за любой прошедший день."),12);
        saveDialog(s==null?"Добавить время":"Изменить запись",f,"Сохранить",()->{a.store.saveSession(s==null?0:s.id,tasks.get(task.getSelectedItemPosition()).id,start[0],end[0],System.currentTimeMillis());a.changed(true);a.toast("Запись сохранена");});
    }
    void cut(Session s){
        List<Task> tasks=tasks();tasks.removeIf(t->t.id==s.taskId);LinearLayout f=form();Task source=a.store.snapshot().task(s.taskId);
        add(f,text(a,source.name,18,INK,true),0);add(f,label(a,"Исходная запись: "+TimeMath.dateTime(s.start,a.zone)+" — "+TimeMath.dateTime(s.end,a.zone)),8);
        long[] from={s.start},to={s.end};TextView preview=text(a,"",15,GREEN,true);
        Runnable update=()->{long removed=to[0]-from[0],remaining=s.end-s.start-removed;preview.setText(from[0]>=s.start&&to[0]<=s.end&&removed>0?"Участок: "+TimeMath.shortTime(removed)+"\nОстанется в исходной задаче: "+TimeMath.shortTime(remaining):"Выберите участок внутри исходной записи");};
        moment(f,"Вырезать с",from,update);moment(f,"До",to,update);add(f,label(a,"Куда перенести"),18);Spinner target=taskChoice(f,tasks,0,true);add(f,preview,18);update.run();
        add(f,label(a,"Остальные задачи, которые шли параллельно, не изменятся."),12);
        saveDialog("Исправить участок",f,"Применить",()->{int pos=target.getSelectedItemPosition();a.store.cut(s.id,from[0],to[0],pos==0?0:tasks.get(pos-1).id,System.currentTimeMillis());a.changed(true);a.toast(pos==0?"Участок вырезан":"Время перенесено");});
    }
    private void moment(LinearLayout f,String label,long[] value,Runnable update){
        add(f,label(a,label),18);LinearLayout row=row(a);TextView date=button(a,"",false,()->{}),time=button(a,"",false,()->{});
        date.setContentDescription(label+": дата");time.setContentDescription(label+": время");
        Runnable refresh=()->{ZonedDateTime d=Instant.ofEpochMilli(value[0]).atZone(a.zone);date.setText(d.format(DateTimeFormatter.ofPattern("d MMM yyyy",TimeMath.RU)));time.setText(d.format(DateTimeFormatter.ofPattern("HH:mm",TimeMath.RU)));update.run();};
        date.setOnClickListener(v->{ZonedDateTime d=Instant.ofEpochMilli(value[0]).atZone(a.zone);DatePickerDialog picker=new DatePickerDialog(a,(view,y,m,day)->{LocalDate chosen=LocalDate.of(y,m+1,day);value[0]=chosen.atTime(Instant.ofEpochMilli(value[0]).atZone(a.zone).toLocalTime()).atZone(a.zone).toInstant().toEpochMilli();refresh.run();},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
        time.setOnClickListener(v->{ZonedDateTime d=Instant.ofEpochMilli(value[0]).atZone(a.zone);new TimePickerDialog(a,(view,h,m)->{value[0]=Instant.ofEpochMilli(value[0]).atZone(a.zone).withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli();refresh.run();},d.getHour(),d.getMinute(),true).show();});
        weight(row,date);Space gap=new Space(a);row.addView(gap,new LinearLayout.LayoutParams(dp(a,8),1));weight(row,time);add(f,row,6);refresh.run();
    }
}
