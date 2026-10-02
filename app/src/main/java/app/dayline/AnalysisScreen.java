package app.dayline;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.*;
import android.widget.*;
import app.dayline.core.*;
import app.dayline.core.Model.*;
import app.dayline.core.AnalysisMath.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static app.dayline.Ui.*;

final class AnalysisScreen {
    private final MainActivity a;
    private final LinearLayout body;
    private final List<Runnable> live;
    private final Snapshot data;
    private static final String[] WEEKDAYS={"Все дни","Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье"};
    AnalysisScreen(MainActivity a,LinearLayout body,List<Runnable> live){this.a=a;this.body=body;this.live=live;data=a.data;}
    private void refresh(boolean preserve){a.render(preserve);}
    private void chipIn(LinearLayout row,String title,boolean selected,Runnable action){TextView v=chip(a,title,selected,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=dp(a,7);row.addView(v,p);}
    void render(){
        LinearLayout controls=row(a);
        chipIn(controls,"День",!a.averageMode&&!a.weekly,()->{a.averageMode=false;a.weekly=false;refresh(false);});
        chipIn(controls,"Неделя",!a.averageMode&&a.weekly,()->{a.averageMode=false;a.weekly=true;refresh(false);});
        chipIn(controls,"Среднее",a.averageMode,()->{a.averageMode=true;refresh(false);});
        HorizontalScrollView modes=new HorizontalScrollView(a);modes.setHorizontalScrollBarEnabled(false);modes.addView(controls);add(body,modes,0);
        if(a.averageMode)average();else range();
        gap(body,16);
    }
    private LocalDate first(){return a.weekly?TimeMath.monday(a.selectedDate):a.selectedDate;}
    private String outside(){return a.selectionActive?"Вне выбранных задач":a.analysisGroup==0?"Не учтено":"Вне этой группы";}
    private List<Session> selected(){return AnalysisMath.select(data,a.selectionFilter(),a.analysisGroup);}
    private void period(){
        LinearLayout date=row(a);date.addView(button(a,"‹",false,()->{a.selectedDate=a.selectedDate.minusDays(a.weekly?7:1);a.historyLimit=30;refresh(false);}));
        LocalDate start=first();String caption=a.weekly?start.format(DateTimeFormatter.ofPattern("d MMM",TimeMath.RU))+" — "+start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM",TimeMath.RU)):start.format(DateTimeFormatter.ofPattern("d MMMM, EEE",TimeMath.RU));
        TextView title=text(a,caption,17,INK,true);title.setGravity(Gravity.CENTER);weight(date,title);
        TextView next=button(a,"›",false,()->{a.selectedDate=a.selectedDate.plusDays(a.weekly?7:1);a.historyLimit=30;refresh(false);});
        boolean canNext=a.weekly?start.isBefore(TimeMath.monday(LocalDate.now())):start.isBefore(LocalDate.now());next.setEnabled(canNext);next.setAlpha(canNext?1:.35f);date.addView(next);add(body,date,16);
        if(a.weekly?!start.equals(TimeMath.monday(LocalDate.now())):!start.equals(LocalDate.now()))add(body,button(a,"К текущему периоду",false,()->{a.selectedDate=LocalDate.now();refresh(false);}),8);
    }
    private TextView[] stats(String title){
        LinearLayout cards=row(a),sumCard=card(a),unionCard=card(a);TextView sum=text(a,"",22,INK,true),union=text(a,"",22,GREEN,true);
        add(sumCard,label(a,title),0);add(sumCard,sum,9);add(unionCard,label(a,"Без пересечений"),0);add(unionCard,union,9);weight(cards,sumCard);Space gap=new Space(a);cards.addView(gap,new LinearLayout.LayoutParams(dp(a,8),1));weight(cards,unionCard);add(body,cards,16);return new TextView[]{sum,union};
    }
    private void selectionEntry(){
        add(body,button(a,a.selectionActive?"Изменить выбранные задачи":"Выбрать задачи для расчёта",false,this::chooseTasks),12);
        add(body,label(a,"Можно зажать задачу в ленте или списке, затем отметить остальные. Выбор общий для всех групп и сохраняется."),8);
    }
    private void range(){
        period();a.tabs(body,true);
        long from=TimeMath.start(first(),a.zone),to=TimeMath.start(first().plusDays(a.weekly?7:1),a.zone);List<Session> selected=selected();
        TextView[] stats=stats(a.selectionActive?"Выбранные задачи":"Время задач");TextView gap=label(a,"");add(body,gap,12);
        live.add(()->{long now=System.currentTimeMillis(),sum=TimeMath.sum(selected,from,to,now),union=TimeMath.union(selected,from,to,now);stats[0].setText(TimeMath.shortTime(sum));stats[1].setText(TimeMath.shortTime(union));gap.setText(outside()+": "+TimeMath.shortTime(Math.max(0,Math.min(to,now)-from)-union));if(a.selectionSummary!=null)a.selectionSummary.setText("Сумма: "+TimeMath.shortTime(sum)+"\nБез пересечений: "+TimeMath.shortTime(union));});
        selectionEntry();
        LinearLayout chart=card(a),head=row(a);weight(head,text(a,"Лента времени",18,INK,true));head.addView(button(a,a.zoom?"Уместить":"Крупнее",false,()->{a.zoom=!a.zoom;refresh(true);}));add(chart,head,0);
        add(chart,label(a,"Нажатие — правка. Долгое нажатие — выбор задачи. Пустой участок — добавить время."),8);
        TimelineView timeline=new TimelineView(a,data,a.selectedDate,a.weekly,a.analysisGroup,new TimelineView.Listener(){
            public void open(Session s){if(a.selectionActive)a.toggleSelection(s.taskId);else new Editors(a).sessionActions(s);}
            public void empty(long start,long end){if(!a.selectionActive)new Editors(a).addBetween(start,end);}
            public void selectTask(long id){a.toggleSelection(id);}
            public void openTask(long id){new Editors(a).history(data.task(id));}
            public boolean selectionMode(){return a.selectionActive;}
            public boolean selected(long id){return a.selectedTasks.contains(id);}
        });a.setTimeline(timeline);
        if(a.zoom){HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.addView(timeline,new android.widget.FrameLayout.LayoutParams(dp(a,1000),-2));add(chart,scroll,12);}else add(chart,timeline,12);
        add(body,chart,20);
        add(body,button(a,"Заполнить все пропущенные моменты",false,()->new GapFiller(a).show(from,to)),12);
        if(a.store.canUndo())add(body,button(a,"↶ Отменить последнюю правку",false,()->a.safe(()->{a.store.undo();a.changed(true);})),8);
        if(a.weekly){
            add(body,text(a,a.selectionActive?"Выбранное по дням":"По дням",20,INK,true),24);
            for(int i=0;i<7;i++){LocalDate day=first().plusDays(i);long start=TimeMath.start(day,a.zone),end=TimeMath.start(day.plusDays(1),a.zone);TextView line=button(a,"",false,()->{a.weekly=false;a.selectedDate=day;refresh(false);});line.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);line.setTextSize(14);add(body,line,6);
                live.add(()->{long now=System.currentTimeMillis();line.setText(day.format(DateTimeFormatter.ofPattern("EEE, d MMM",TimeMath.RU))+"\nСумма "+TimeMath.shortTime(TimeMath.sum(selected,start,end,now))+"  ·  без пересечений "+TimeMath.shortTime(TimeMath.union(selected,start,end,now)));});}
        }
        taskRows(from,to,null);
        add(body,button(a,"Экспорт недели в Excel",true,a::exportDialog),20);
        LinearLayout actions=row(a);weight(actions,button(a,"＋ Записать время",false,()->new Editors(a).session(null,0)));Space gapView=new Space(a);actions.addView(gapView,new LinearLayout.LayoutParams(dp(a,8),1));weight(actions,button(a,"＋ Задача",false,()->new Editors(a).task(null,a.analysisGroup==0?a.groupId:a.analysisGroup)));add(body,actions,10);
        if(a.analysisGroup==0)groupRows(selected,from,to,null);
        add(body,text(a,"Записи и исправления",20,INK,true),24);int count=0,total=0;long now=System.currentTimeMillis();
        for(Session s:selected)if(TimeMath.duration(s,from,to,now)>0){total++;if(count++>=a.historyLimit)continue;Task t=data.task(s.taskId);TextView record=button(a,t.name+"\n"+TimeMath.dateTime(s.start,a.zone)+" — "+(s.running()?"идёт":TimeMath.dateTime(s.end,a.zone)),false,()->new Editors(a).sessionActions(s));record.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);record.setTextSize(14);add(body,record,8);}
        if(total>a.historyLimit)add(body,button(a,"Показать ещё записи",false,()->{a.historyLimit+=50;refresh(true);}),10);
    }
    private void average(){
        add(body,text(a,"Средний день",24,INK,true),20);
        Spinner days=new Spinner(a);days.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,WEEKDAYS));days.setSelection(a.averageWeekday);days.setContentDescription("День недели для среднего");add(body,days,8);
        days.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position!=a.averageWeekday){a.averageWeekday=position;refresh(false);}}public void onNothingSelected(AdapterView<?> p){}});
        a.tabs(body,true);long now=System.currentTimeMillis();
        Averages calculated=AnalysisMath.averages(data,a.selectionFilter(),a.analysisGroup,null,a.zone,now);
        Average current=a.averageWeekday==0?calculated.all:calculated.weekdays[a.averageWeekday-1];
        String basis="Завершённых дней: "+current.days;
        if(calculated.all.days>0)basis+=" · "+calculated.first.format(DateTimeFormatter.ofPattern("d MMM yyyy",TimeMath.RU))+" — "+calculated.endExclusive.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy",TimeMath.RU));
        add(body,label(a,basis),12);add(body,label(a,"Вся история с первого дня записей. Сегодня исключён. Дни без записей внутри периода тоже входят в среднее."),8);
        TextView[] stats=stats("В среднем за день");stats[0].setText(TimeMath.shortTime(current.mean(current.sum)));stats[1].setText(TimeMath.shortTime(current.mean(current.union)));
        if(a.selectionSummary!=null)a.selectionSummary.setText("В среднем: "+TimeMath.shortTime(current.mean(current.sum))+"\nБез пересечений: "+TimeMath.shortTime(current.mean(current.union)));
        add(body,label(a,outside()+": "+TimeMath.shortTime(current.mean(current.elapsed-current.union))+" в день"),12);selectionEntry();
        if(current.days==0){add(body,text(a,"Для этого дня пока нет истории",19,INK,true),24);add(body,label(a,"Средние показатели появятся, когда закончится хотя бы один подходящий день."),8);return;}
        LinearLayout chart=card(a);add(chart,text(a,"Как обычно проходит день",19,INK,true),0);
        add(chart,label(a,"По одной линии на группу. Яркий участок — занятие чаще встречается в этот час, бледный — реже. Параллельные занятия остаются на своих линиях."),10);
        List<Group> groups=new ArrayList<>();for(Group g:data.groups)if(a.selectionActive||a.analysisGroup==0||a.analysisGroup==g.id)groups.add(g);
        add(chart,new AverageChartView(a,current,groups,a.selectionActive?"Вне выбора":a.analysisGroup==0?"Не учтено":"Вне группы",(name,hour,share)->new AlertDialog.Builder(a).setTitle(name+" · "+String.format(Locale.ROOT,"%02d:00–%02d:00",hour,hour+1)).setMessage("Занято в среднем "+Math.round(share*100)+"% этого часа (примерно "+Math.round(share*60)+" мин из 60).\n\nЭто частота по истории, а не запланированное занятие.").setPositiveButton("Понятно",null).show()),16);
        add(chart,button(a,"По часам текстом",false,()->hourlyText(current,groups)),12);add(body,chart,20);
        groupRows(Collections.emptyList(),0,0,current);
        // Keep unselected rows available so the user can add them without leaving selection mode.
        Averages allTasks=AnalysisMath.averages(data,null,0,null,a.zone,now);Average base=a.averageWeekday==0?allTasks.all:allTasks.weekdays[a.averageWeekday-1];taskRows(0,0,base);
        add(body,text(a,"Средняя неделя · по дням",20,INK,true),24);add(body,label(a,"Нажмите на день: откроются его средние длительности и почасовая лента."),8);
        for(int i=0;i<7;i++){int index=i+1;Average day=calculated.weekdays[i];String detail=day.days==0?"Пока нет завершённых дней":"Сумма "+TimeMath.shortTime(day.mean(day.sum))+"\nБез пересечений "+TimeMath.shortTime(day.mean(day.union))+" · дней: "+day.days;
            TextView line=button(a,WEEKDAYS[index]+"\n"+detail,false,()->{a.averageWeekday=index;refresh(false);});line.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);line.setTextSize(14);add(body,line,8);}
        add(body,button(a,"＋ Задача или группа",false,a::addMenu),16);
    }
    private void taskRows(long from,long to,Average average){
        add(body,text(a,average==null?"По задачам":"Средние длительности задач",20,INK,true),24);add(body,label(a,"Зажмите задачу, чтобы выбрать несколько. После выбора достаточно обычного нажатия."),8);
        long now=System.currentTimeMillis();List<Task> tasks=new ArrayList<>(data.tasks);
        java.util.function.ToLongFunction<Task> duration=t->average==null?TimeMath.sum(data.forTask(t.id),from,to,now):average.mean(average.taskSum.getOrDefault(t.id,0L));
        tasks.sort((x,y)->Long.compare(duration.applyAsLong(y),duration.applyAsLong(x)));int count=0;
        for(Task t:tasks){long n=duration.applyAsLong(t);if(n==0&&!a.selectedTasks.contains(t.id))continue;if(a.analysisGroup!=0&&t.groupId!=a.analysisGroup)continue;count++;
            LinearLayout card=card(a);boolean chosen=a.selectionActive&&a.selectedTasks.contains(t.id);if(chosen)card.setBackground(bg(MINT,GREEN,20,a));
            LinearLayout line=row(a);TextView title=text(a,(chosen?"✓  ":"")+t.name,16,INK,true);title.setMaxLines(3);weight(line,title);TextView value=text(a,TimeMath.shortTime(n),15,GREEN,true);line.addView(value);add(card,line,0);
            add(card,label(a,data.group(t.groupId).name+(t.archived?" · завершена":"")+(average==null?"":" · в среднем за день")),8);
            card.setContentDescription("Задача в анализе: "+t.name);card.setFocusable(true);card.setSelected(chosen);
            card.setOnLongClickListener(v->{a.toggleSelection(t.id);return true;});card.setOnClickListener(v->{if(a.selectionActive)a.toggleSelection(t.id);else new Editors(a).history(t);});add(body,card,8);
            if(average==null)live.add(()->value.setText(TimeMath.shortTime(TimeMath.sum(data.forTask(t.id),from,to,System.currentTimeMillis()))));
        }
        if(count==0)add(body,label(a,"Нет задач с записанным временем за этот период."),12);
    }
    private void groupRows(List<Session> selected,long from,long to,Average average){
        add(body,text(a,average==null?"По группам":"Средние длительности групп",20,INK,true),24);
        for(Group group:data.groups){if(average!=null&&!a.selectionActive&&a.analysisGroup!=0&&group.id!=a.analysisGroup)continue;
            LinearLayout card=card(a),line=row(a);weight(line,text(a,group.name,17,group.color,true));TextView amount=text(a,"",16,INK,true);line.addView(amount);add(card,line,0);TextView actual=label(a,"");add(card,actual,8);add(body,card,8);
            if(average!=null){amount.setText(TimeMath.shortTime(average.mean(average.groupSum.getOrDefault(group.id,0L))));actual.setText("Без пересечений: "+TimeMath.shortTime(average.mean(average.groupUnion.getOrDefault(group.id,0L)))+" в день");}
            else {List<Session> rows=new ArrayList<>();for(Session s:selected)if(data.task(s.taskId).groupId==group.id)rows.add(s);live.add(()->{long now=System.currentTimeMillis();amount.setText(TimeMath.shortTime(TimeMath.sum(rows,from,to,now)));actual.setText("Без пересечений: "+TimeMath.shortTime(TimeMath.union(rows,from,to,now)));});}
        }
    }
    void chooseTasks(){
        List<Task> tasks=new ArrayList<>(data.tasks);tasks.sort(Comparator.comparing((Task t)->data.group(t.groupId).name).thenComparing(t->t.name));
        Set<Long> chosen=new LinkedHashSet<>(a.selectedTasks);if(!a.selectionActive)for(Task t:tasks)if(a.analysisGroup==0||a.analysisGroup==t.groupId)chosen.add(t.id);
        LinearLayout form=column(a);pad(form,16,8);EditText search=input(a,"Найти задачу","",false);add(form,search,0);LinearLayout actions=row(a),list=column(a);
        TextView count=label(a,"");add(form,count,8);
        Runnable update=()->{list.removeAllViews();String query=search.getText().toString().trim().toLowerCase(TimeMath.RU);for(Task t:tasks)if(t.name.toLowerCase(TimeMath.RU).contains(query)){
            CheckBox box=new CheckBox(a);box.setText(t.name+"\n"+data.group(t.groupId).name+(t.archived?" · завершена":""));box.setTextSize(14);box.setTextColor(INK);box.setMinHeight(dp(a,56));box.setChecked(chosen.contains(t.id));box.setOnCheckedChangeListener((v,checked)->{if(checked)chosen.add(t.id);else chosen.remove(t.id);count.setText("Выбрано: "+chosen.size());});add(list,box,3);
        }count.setText("Выбрано: "+chosen.size());};
        weight(actions,button(a,"Все задачи",false,()->{for(Task t:tasks)chosen.add(t.id);update.run();}));weight(actions,button(a,"Снять все",false,()->{chosen.clear();update.run();}));add(form,actions,10);
        ScrollView scroll=new ScrollView(a);scroll.addView(list);LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(-1,dp(a,280));listParams.topMargin=dp(a,10);form.addView(scroll,listParams);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}public void afterTextChanged(Editable s){}});update.run();
        new AlertDialog.Builder(a).setTitle("Что учитывать в анализе").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Применить",(d,w)->{a.selectedTasks.clear();a.selectedTasks.addAll(chosen);a.selectionActive=true;a.saveSelection();refresh(true);}).show();
    }
    private void hourlyText(Average average,List<Group> groups){
        StringBuilder text=new StringBuilder();for(int h=0;h<24;h++){text.append(String.format(Locale.ROOT,"%02d:00–%02d:00",h,h+1)).append('\n');for(Group g:groups){double share=average.share(g.id,h);if(share>0)text.append(g.name).append(": ").append(Math.round(share*100)).append("% · ").append(Math.round(share*60)).append(" мин/час\n");}text.append(outside()).append(": ").append(Math.round(average.outsideShare(h)*100)).append("%\n\n");}
        new AlertDialog.Builder(a).setTitle("Средний день по часам").setMessage(text.toString()).setPositiveButton("Готово",null).show();
    }
}
