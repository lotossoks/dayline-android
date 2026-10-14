package app.dayline;

import android.app.Activity;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.dayline.core.Model.*;
import java.util.*;
import static app.dayline.Ui.*;

public final class WidgetConfigActivity extends Activity {
    private int widgetId;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(!TimerWidget.owns(this,widgetId)){finish();return;}
        Snapshot data=Store.get(this).widgetSnapshot();List<Group> groups=new ArrayList<>();groups.add(new Group(0,"Все группы",GREEN));groups.addAll(data.groups);
        long selected=saved==null?TimerWidget.group(this,widgetId,data):saved.getLong("group");
        LinearLayout content=column(this);pad(content,24,24);content.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);content.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(this,24)+bars.left,dp(this,24)+bars.top,dp(this,24)+bars.right,dp(this,24)+bars.bottom);return insets;});}
        add(content,text(this,"Виджет Dayline",26,INK,true),0);add(content,label(this,"Запускайте и останавливайте задачи прямо с рабочего стола. Активные таймеры всегда сверху."),12);
        add(content,text(this,"Какие задачи показывать",17,INK,true),28);Spinner choice=new Spinner(this);choice.setId(R.id.widget_group_choice);choice.setContentDescription("Группа задач для виджета");choice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,groups));for(int i=0;i<groups.size();i++)if(groups.get(i).id==selected)choice.setSelection(i);add(content,choice,12);
        add(content,label(this,"Можно разместить несколько виджетов с разными группами. Стрелки внизу переключают страницы задач; размер меняется долгим нажатием на рабочем столе."),22);
        add(content,button(this,"Сохранить виджет",true,()->{long group=((Group)choice.getSelectedItem()).id;TimerWidget.settings(this).edit().putLong(TimerWidget.key(widgetId),group).putInt("page_"+widgetId,0).apply();TimerWidget.refresh(this);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish();}),26);
        add(content,button(this,"Отмена",false,this::finish),10);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);setContentView(scroll);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);Spinner choice=findViewById(R.id.widget_group_choice);if(choice!=null&&choice.getSelectedItem()!=null)out.putLong("group",((Group)choice.getSelectedItem()).id);}
}
