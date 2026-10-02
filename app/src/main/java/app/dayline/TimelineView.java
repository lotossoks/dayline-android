package app.dayline;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import app.dayline.core.Model.*;
import app.dayline.core.TimeMath;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class TimelineView extends View {
    interface Listener { void open(Session s);default void empty(long from,long to){} default void selectTask(long id){} default void openTask(long id){} default boolean selectionMode(){return false;} default boolean selected(long id){return false;} }
    private final Snapshot data;private final LocalDate selected;private final boolean week;private final long group;private final Listener listener;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final List<Hit> hits=new ArrayList<>();private final ZoneId zone=ZoneId.systemDefault();
    private static final class Hit { RectF rect;Session session;long from,to,taskId;Hit(RectF rect,Session session){this.rect=rect;this.session=session;this.taskId=session.taskId;}Hit(RectF rect,long taskId){this.rect=rect;this.taskId=taskId;}Hit(RectF rect,long from,long to){this.rect=rect;this.from=from;this.to=to;} }
    TimelineView(Context c,Snapshot data,LocalDate day,boolean week,long group,Listener listener){
        super(c);this.data=data;selected=week?TimeMath.monday(day):day;this.week=week;this.group=group;this.listener=listener;
        setContentDescription("Временная лента. Точные интервалы доступны в списке записей под диаграммой.");setFocusable(true);
        setOnLongClickListener(v->{Hit hit=nearest(downX,downY);if(hit==null||hit.taskId==0)return false;longPressed=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);listener.selectTask(hit.taskId);return true;});
    }
    private float d(float n){return Ui.dp(getContext(),n);}
    private List<Task> tasks(LocalDate day,long now){
        long a=TimeMath.start(day,zone),b=TimeMath.start(day.plusDays(1),zone);List<Task> list=new ArrayList<>();
        for(Task t:data.tasks)if((group==0||t.groupId==group)&&TimeMath.sum(data.forTask(t.id),a,b,now)>0)list.add(t);
        list.sort(Comparator.comparingLong(t->t.groupId));return list;
    }
    private int height(){long now=System.currentTimeMillis();float n=38;for(int i=0;i<(week?7:1);i++)n+=42+Math.max(1,tasks(selected.plusDays(i),now).size())*36+34;return (int)d(n);}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),height());}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setColor(color);p.setTextSize(d(size));p.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.NORMAL):Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(s,x,y,p);}
    private String fit(String s,float width){p.setTextSize(d(11));if(p.measureText(s)<=width)return s;while(s.length()>1&&p.measureText(s+"…")>width)s=s.substring(0,s.length()-1);return s+"…";}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);hits.clear();float left=d(94),right=getWidth()-d(10),width=right-left,y=d(30);long now=System.currentTimeMillis();
        for(int h=0;h<=24;h+=6){float x=left+width*h/24f;text(c,String.format(Locale.ROOT,"%02d",h),x-d(h==24?12:3),d(18),10,Ui.MUTED,false);}
        for(int i=0;i<(week?7:1);i++){
            LocalDate day=selected.plusDays(i);long a=TimeMath.start(day,zone),b=TimeMath.start(day.plusDays(1),zone);List<Task> tasks=tasks(day,now);
            text(c,day.format(DateTimeFormatter.ofPattern("EEE, d MMM",TimeMath.RU)),d(2),y+d(18),13,Ui.INK,true);y+=d(32);
            float top=y;
            if(tasks.isEmpty()){text(c,day.isAfter(LocalDate.now())?"Впереди":"Нет записей",d(2),y+d(20),11,Ui.MUTED,false);track(c,left,right,y);y+=d(36);}
            for(Task task:tasks){
                hits.add(new Hit(new RectF(0,y,left,y+d(32)),task.id));
                text(c,fit((listener.selected(task.id)?"✓ ":"")+task.name,left-d(10)),d(2),y+d(19),11,Ui.INK,false);track(c,left,right,y);
                Group g=data.group(task.groupId);
                for(Session s:data.forTask(task.id))if(TimeMath.duration(s,a,b,now)>0){
                    float x1=left+width*(Math.max(a,s.start)-a)/(b-a),x2=left+width*(Math.min(Math.min(b,s.finish(now)),now)-a)/(b-a);
                    RectF r=new RectF(x1,y+d(5),Math.max(x1+d(3),x2),y+d(27));p.setColor(listener.selectionMode()&&!listener.selected(task.id)?(g.color&0x00ffffff)|0x55000000:g.color);c.drawRoundRect(r,d(5),d(5),p);hits.add(new Hit(r,s));
                    if(x2-x1>d(66))text(c,TimeMath.at(Math.max(a,s.start),zone)+"–"+TimeMath.at(Math.min(b,s.finish(now)),zone),x1+d(5),y+d(20),10,Ui.WHITE,false);
                }
                y+=d(36);
            }
            text(c,group==0?"Не учтено":"Вне группы",d(2),y+d(19),11,Ui.MUTED,false);track(c,left,right,y);
            for(long[] gap:TimeMath.gaps(data.forGroup(group),a,b,now)){
                p.setColor(0xffcfd6ce);float x1=left+width*(gap[0]-a)/(b-a),x2=left+width*(gap[1]-a)/(b-a);
                RectF r=new RectF(x1,y+d(7),x2,y+d(25));c.drawRoundRect(r,d(4),d(4),p);hits.add(new Hit(r,gap[0],gap[1]));
            }
            y+=d(34);
            for(int h=0;h<=24;h+=6){p.setColor(0x20809080);p.setStrokeWidth(d(1));float x=left+width*h/24f;c.drawLine(x,top,x,y-d(4),p);}
            if(now>=a&&now<b){float x=left+width*(now-a)/(b-a);p.setColor(Ui.RED);p.setStrokeWidth(d(1.3f));c.drawLine(x,top-d(3),x,y-d(4),p);c.drawCircle(x,top-d(3),d(3),p);}
            y+=d(10);
        }
    }
    private void track(Canvas c,float left,float right,float y){p.setColor(0xfff0f3ee);c.drawRoundRect(new RectF(left,y+d(5),right,y+d(27)),d(5),d(5),p);}
    private float downX,downY;private boolean longPressed;
    private final Runnable hold=()->performLongClick();
    private Hit nearest(float x,float y){
        Hit nearest=null;float distance=d(12);
        for(Hit h:hits)if(y>=h.rect.top-d(5)&&y<=h.rect.bottom+d(5)){
            float delta=Math.max(0,Math.max(h.rect.left-x,x-h.rect.right));if(delta<distance){nearest=h;distance=delta;}
        }
        return nearest;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();longPressed=false;postDelayed(hold,ViewConfiguration.getLongPressTimeout());return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE&&(Math.abs(e.getX()-downX)>d(12)||Math.abs(e.getY()-downY)>d(12)))removeCallbacks(hold);
        if(e.getAction()==MotionEvent.ACTION_CANCEL){removeCallbacks(hold);return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
            removeCallbacks(hold);
            if(!longPressed&&Math.abs(e.getX()-downX)<d(12)&&Math.abs(e.getY()-downY)<d(12)){
                performClick();Hit hit=nearest(e.getX(),e.getY());
                if(hit!=null){if(hit.taskId!=0&&listener.selectionMode())listener.selectTask(hit.taskId);else if(hit.session!=null)listener.open(hit.session);else if(hit.taskId!=0)listener.openTask(hit.taskId);else listener.empty(hit.from,hit.to);}
            }
        }
        return true;
    }
    @Override protected void onDetachedFromWindow(){removeCallbacks(hold);super.onDetachedFromWindow();}
    @Override public boolean performClick(){return super.performClick();}
}
