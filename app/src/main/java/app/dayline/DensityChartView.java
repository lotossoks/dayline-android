package app.dayline;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import app.dayline.core.*;
import app.dayline.core.Model.*;
import java.util.*;
import static app.dayline.Ui.*;

/** Shared percent scale: overlapping groups stay independent and never stack. */
final class DensityChartView extends View {
    private final List<Group> groups;
    private final List<double[]> curves=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(),fill=new Path();
    private final PathEffect[] styles=new PathEffect[4];
    private final java.util.function.IntConsumer changed;
    private int selected=-1,minute=12*60;
    private float downX,downY;
    private boolean dragging;
    private final float density;
    DensityChartView(Context context,AnalysisMath.Average average,List<Group> groups,String outside,java.util.function.IntConsumer changed){
        super(context);this.groups=new ArrayList<>(groups);this.changed=changed;density=getResources().getDisplayMetrics().density;
        styles[1]=new DashPathEffect(new float[]{d(8),d(4)},0);styles[2]=new DashPathEffect(new float[]{d(2),d(4)},0);styles[3]=new DashPathEffect(new float[]{d(10),d(3),d(2),d(3)},0);
        for(Group g:groups)curves.add(AnalysisMath.density(average.occupiedBins.get(g.id),average.exposureBins,30));
        this.groups.add(new Group(0,outside,MUTED));curves.add(AnalysisMath.density(average.outsideBins,average.exposureBins,30));
        setFocusable(true);setClickable(true);updateDescription();
    }
    private float d(float value){return value*density;}
    double value(int group,int minute){double at=minute/(double)AnalysisMath.BIN_MINUTES;int index=(int)at;return index>=AnalysisMath.BINS?curves.get(group)[AnalysisMath.BINS]:curves.get(group)[index]*(1-(at-index))+curves.get(group)[index+1]*(at-index);}
    private void updateDescription(){StringBuilder desc=new StringBuilder("Кривые среднего дня, ").append(clock(minute));for(int i=0;i<groups.size();i++)desc.append(". ").append(groups.get(i).name).append(": ").append(Math.round(value(i,minute)*100)).append(" процентов");setContentDescription(desc);}
    static String clock(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
    void focus(int index){selected=selected==index?-1:index;invalidate();}
    private PathEffect dash(int index){return styles[index%styles.length];}
    private void stroke(Canvas canvas,int index,float x1,float y1,float x2,float y2){paint.setColor(groups.get(index).color);paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(2.3f));paint.setPathEffect(dash(index));canvas.drawLine(x1,y1,x2,y2,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(dp(getContext(),238),heightSpec));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float left=d(34),right=getWidth()-d(10),top=d(24),bottom=getHeight()-d(30),width=right-left,height=bottom-top;if(width<=0)return;
        paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(d(10));paint.setStyle(Paint.Style.FILL);paint.setColor(MUTED);canvas.drawText("Занятость, %",0,d(12),paint);
        for(int percent=0;percent<=100;percent+=25){float y=bottom-height*percent/100;paint.setColor(LINE);paint.setStrokeWidth(d(1));canvas.drawLine(left,y,right,y,paint);paint.setColor(MUTED);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(""+percent,left-d(6),y+d(3),paint);}
        for(int h=0;h<=24;h+=6){float x=left+width*h/24;paint.setColor(LINE);canvas.drawLine(x,top,x,bottom,paint);paint.setColor(MUTED);paint.setTextAlign(h==0?Paint.Align.LEFT:h==24?Paint.Align.RIGHT:Paint.Align.CENTER);canvas.drawText(String.format(Locale.ROOT,"%02d",h),x,bottom+d(18),paint);}paint.setTextAlign(Paint.Align.LEFT);
        canvas.save();canvas.clipRect(left-d(1),top-d(2),right+d(1),bottom+d(2));
        for(int pass=0;pass<2;pass++)for(int i=0;i<curves.size();i++){
            if((i==selected)!=(pass==1))continue;
            double[] curve=curves.get(i);path.rewind();for(int j=0;j<=AnalysisMath.BINS;j++){float x=left+width*j/AnalysisMath.BINS,y=bottom-height*(float)curve[j];if(j==0)path.moveTo(x,y);else path.lineTo(x,y);}
            paint.setColor(groups.get(i).color);paint.setAlpha(selected<0?20:i==selected?38:4);paint.setStyle(Paint.Style.FILL);fill.set(path);fill.lineTo(right,bottom);fill.lineTo(left,bottom);fill.close();canvas.drawPath(fill,paint);
            paint.setAlpha(selected<0||i==selected?255:45);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(i==selected?3:2));paint.setPathEffect(dash(i));canvas.drawPath(path,paint);paint.setPathEffect(null);
        }
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);float cursor=left+width*minute/1440;paint.setColor(INK);paint.setStrokeWidth(d(1));canvas.drawLine(cursor,top,cursor,bottom,paint);
        for(int i=0;i<curves.size();i++){if(selected>=0&&i!=selected)continue;float y=bottom-height*(float)value(i,minute);paint.setColor(WHITE);canvas.drawCircle(cursor,y,d(4),paint);paint.setColor(groups.get(i).color);canvas.drawCircle(cursor,y,d(2.5f),paint);}
        canvas.restore();
    }
    private void choose(float x){float width=getWidth()-d(44);if(width<=0)return;setMinute(Math.max(0,Math.min(1440,Math.round((x-d(34))/width*1440/15)*15)));}
    void setMinute(int minute){this.minute=minute;updateDescription();changed.accept(minute);invalidate();}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=event.getX();downY=event.getY();dragging=false;return true;
            case MotionEvent.ACTION_MOVE:if(!dragging&&Math.abs(event.getX()-downX)>ViewConfiguration.get(getContext()).getScaledTouchSlop()&&Math.abs(event.getX()-downX)>Math.abs(event.getY()-downY)){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);}if(dragging)choose(event.getX());return true;
            case MotionEvent.ACTION_UP:choose(event.getX());getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
            case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){setMinute(Math.max(0,Math.min(1440,minute+(keyCode==KeyEvent.KEYCODE_DPAD_LEFT?-15:15))));return true;}return super.onKeyDown(keyCode,event);}
    static void addTo(LinearLayout card,AnalysisMath.Average average,List<Group> groups,String outside){
        Context c=card.getContext();TextView time=text(c,"",15,INK,true);List<TextView> amounts=new ArrayList<>();List<LinearLayout> legendRows=new ArrayList<>();List<TextView> names=new ArrayList<>();
        SeekBar seek=new SeekBar(c);seek.setMax(96);seek.setProgress(48);
        DensityChartView[] holder=new DensityChartView[1];
        DensityChartView chart=new DensityChartView(c,average,groups,outside,minute->{seek.setProgress(minute/15);time.setText("Около "+clock(minute)+" · сглаженная занятость");for(int i=0;i<amounts.size();i++)amounts.get(i).setText(Math.round(holder[0].value(i,minute)*100)+"%");});holder[0]=chart;
        add(card,chart,12);add(card,label(c,"Коснитесь графика для выбора часа. Нажмите название ниже, чтобы выделить кривую."),6);add(card,time,14);
        for(int i=0;i<chart.groups.size();i++){
            final int index=i;Group group=chart.groups.get(i);LinearLayout line=row(c);pad(line,8,6);line.setMinimumHeight(dp(c,48));
            View sample=new View(c){@Override protected void onDraw(Canvas canvas){chart.stroke(canvas,index,0,getHeight()/2f,getWidth(),getHeight()/2f);}};line.addView(sample,new LinearLayout.LayoutParams(dp(c,28),dp(c,20)));
            LinearLayout title=column(c);pad(title,8,0);TextView name=text(c,group.name,14,INK,true);names.add(name);add(title,name,0);long duration=group.id==0?average.elapsed-average.union:average.groupUnion.getOrDefault(group.id,0L);add(title,label(c,TimeMath.shortTime(average.mean(duration))+" в день"),4);weight(line,title);
            TextView amount=text(c,"",16,group.color,true);line.addView(amount);amounts.add(amount);legendRows.add(line);line.setFocusable(true);line.setClickable(true);
            line.setContentDescription("Выделить кривую: "+group.name);line.setOnClickListener(v->{chart.focus(index);for(int k=0;k<legendRows.size();k++){boolean active=k==chart.selected;legendRows.get(k).setSelected(active);names.get(k).setText((active?"✓  ":"")+chart.groups.get(k).name);legendRows.get(k).setBackground(bg(active?MINT:WHITE,active?GREEN:0,12,c));}});add(card,line,4);
        }
        // Native seek bar also exposes time selection through accessibility services.
        seek.setContentDescription("Время на кривых среднего дня");seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int progress,boolean user){if(user)chart.setMinute(progress*15);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        // Keep a native alternative to dragging the Canvas, including TalkBack progress actions.
        add(card,seek,8);chart.setMinute(720);
    }
}
