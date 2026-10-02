package app.dayline;

import android.app.AlertDialog;
import android.view.View;
import android.widget.*;
import app.dayline.core.*;
import app.dayline.core.Model.*;
import java.util.*;
import static app.dayline.Ui.*;

final class GapFiller {
    private final MainActivity a;
    GapFiller(MainActivity a){this.a=a;}
    void show(long from,long to) {
        Snapshot snapshot=a.store.snapshot();List<Task> tasks=new ArrayList<>(snapshot.tasks);
        tasks.sort(Comparator.comparing((Task t)->t.archived).thenComparing(t->snapshot.group(t.groupId).name).thenComparing(t->t.name));
        if(tasks.isEmpty()){a.toast("Сначала создайте задачу для пропущенного времени");return;}
        long captured=System.currentTimeMillis(),end=Math.min(to,captured);
        Set<Long> selection=new LinkedHashSet<>(a.selectedTasks);
        LinearLayout form=column(a);pad(form,20,12);
        add(form,label(a,TimeMath.dateTime(from,a.zone)+" — "+TimeMath.dateTime(end,a.zone)),0);
        add(form,label(a,"Записать пропуски в задачу"),16);
        Spinner target=new Spinner(a);List<String> names=new ArrayList<>();for(Task t:tasks)names.add(snapshot.group(t.groupId).name+" / "+t.name+(t.archived?" · завершена":""));
        target.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,names));target.setContentDescription("Задача для пропусков");add(form,target,4);
        add(form,label(a,"Какие промежутки заполнить"),16);
        Spinner length=new Spinner(a);length.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"Короткие · до 2 минут","До 5 минут","До 15 минут","Все пропуски"}));length.setContentDescription("Длина пропусков");add(form,length,4);
        long[] limits={120000,300000,900000,0};
        CheckBox filtered=new CheckBox(a);filtered.setText("Искать пробелы только в выбранных задачах");filtered.setTextColor(INK);filtered.setTextSize(14);
        if(a.selectionActive&&!selection.isEmpty())add(form,filtered,12);
        TextView explanation=label(a,"");add(form,explanation,10);TextView preview=text(a,"",18,GREEN,true);add(form,preview,16);
        add(form,label(a,"Все записи добавятся одним действием. Его можно отменить в анализе. Работающие таймеры продолжатся."),12);
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Заполнить пропуски").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Заполнить",null).create();
        Runnable refresh=()->{
            Set<Long> coverage=filtered.isChecked()?selection:null;
            List<long[]> gaps=AnalysisMath.fillable(snapshot,tasks.get(target.getSelectedItemPosition()).id,coverage,from,end,captured,limits[length.getSelectedItemPosition()]);
            long total=0;for(long[] g:gaps)total+=g[1]-g[0];
            preview.setText("Промежутков: "+gaps.size()+"\nВсего: "+TimeMath.clock(total));
            explanation.setText(filtered.isChecked()?"Другие задачи могут идти параллельно. Уже записанное время целевой задачи пропуском не считается.":"Пропуск — время, когда не было ни одной задачи, во всех группах. Фильтр анализа на это не влияет.");
            if(dialog.isShowing())dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!gaps.isEmpty());
        };
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){refresh.run();}public void onNothingSelected(AdapterView<?> p){}};
        target.setOnItemSelectedListener(listener);length.setOnItemSelectedListener(listener);filtered.setOnCheckedChangeListener((button,checked)->refresh.run());
        dialog.setOnShowListener(d->{refresh.run();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->a.safe(()->{
            int count=a.store.fillGaps(tasks.get(target.getSelectedItemPosition()).id,filtered.isChecked()?selection:null,from,end,captured,limits[length.getSelectedItemPosition()]);
            dialog.dismiss();a.changed(true);a.toast("Заполнено промежутков: "+count+". Доступна отмена.");
        }));});dialog.show();
    }
}
