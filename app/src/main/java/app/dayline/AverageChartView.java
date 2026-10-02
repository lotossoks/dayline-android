package app.dayline;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import app.dayline.core.AnalysisMath.Average;
import app.dayline.core.Model.Group;
import java.util.*;

/** Hour-by-hour coverage, one independent lane per group so concurrent classes stay visible. */
final class AverageChartView extends View {
    interface Listener { void hour(String group,int hour,double share); }
    private final Average average;
    private final List<Group> groups;
    private final String outsideLabel;
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float downX,downY;
    AverageChartView(Context context,Average average,List<Group> groups,String outsideLabel,Listener listener) {
        super(context);this.average=average;this.groups=groups;this.outsideLabel=outsideLabel;this.listener=listener;
        setContentDescription("Средний день по часам. Чем ярче участок, тем чаще в этот час было занятие. Точные значения доступны кнопкой «По часам текстом».");
        setFocusable(true);
    }
    private float dp(float n){return Ui.dp(getContext(),n);}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),Ui.dp(getContext(),40+(groups.size()+1)*48));}
    private void text(Canvas canvas,String value,float x,float y,float size,int color) {
        paint.setColor(color);paint.setTextSize(dp(size));paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));canvas.drawText(value,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float left=dp(88),width=getWidth()-left-dp(8),cell=width/24;
        for(int hour=0;hour<=24;hour+=6)text(canvas,String.format(Locale.ROOT,"%02d",hour),left+cell*hour-dp(hour==24?12:3),dp(18),10,Ui.MUTED);
        for(int row=0;row<=groups.size();row++) {
            boolean outside=row==groups.size();Group group=outside?null:groups.get(row);float y=dp(32+row*48);
            String title=outside?outsideLabel:group.name;paint.setTextSize(dp(11));while(title.length()>1&&paint.measureText(title)>left-dp(8))title=title.substring(0,title.length()-2)+"…";
            text(canvas,title,0,y+dp(22),11,outside?Ui.MUTED:Ui.INK);
            for(int hour=0;hour<24;hour++) {
                double share=outside?average.outsideShare(hour):average.share(group.id,hour);
                int color=outside?0xff929e91:group.color;paint.setColor(blend(Ui.BG,color,share));
                canvas.drawRoundRect(left+hour*cell+dp(.5f),y,left+(hour+1)*cell-dp(.5f),y+dp(32),dp(2),dp(2),paint);
            }
        }
    }
    private int blend(int background,int color,double share){double p=Math.max(0,Math.min(1,share));return Color.rgb((int)(Color.red(background)*(1-p)+Color.red(color)*p),(int)(Color.green(background)*(1-p)+Color.green(color)*p),(int)(Color.blue(background)*(1-p)+Color.blue(color)*p));}
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP&&Math.abs(e.getX()-downX)<dp(12)&&Math.abs(e.getY()-downY)<dp(12)) {
            performClick();float left=dp(88),width=getWidth()-left-dp(8);int hour=(int)((e.getX()-left)*24/width),row=(int)((e.getY()-dp(32))/dp(48));
            if(e.getX()>=left&&e.getY()>=dp(32)&&hour>=0&&hour<24&&row>=0&&row<=groups.size()) {
                Group g=row==groups.size()?null:groups.get(row);listener.hour(g==null?outsideLabel:g.name,hour,g==null?average.outsideShare(hour):average.share(g.id,hour));
            }
        }
        return true;
    }
    @Override public boolean performClick(){return super.performClick();}
}
