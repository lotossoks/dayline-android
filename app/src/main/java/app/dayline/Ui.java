package app.dayline;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=0xfff5f6f2,INK=0xff203b33,MUTED=0xff697a70,GREEN=0xff216b58,MINT=0xffe6eee4,LIME=0xffe1f2a9,LINE=0xffdfe5dc,WHITE=0xffffffff,RED=0xffb74d46;
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static TextView text(Context c,String s,float size,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    static TextView label(Context c,String s){return text(c,s,13,MUTED,false);}
    static GradientDrawable bg(int color,int stroke,float radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(stroke!=0)d.setStroke(dp(c,1),stroke);return d;}
    static void pad(View v,int x,int y){v.setPadding(dp(v.getContext(),x),dp(v.getContext(),y),dp(v.getContext(),x),dp(v.getContext(),y));}
    static LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    static void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=match();p.topMargin=dp(parent.getContext(),top);parent.addView(child,p);}
    static void weight(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(0,-2,1));}
    static void gap(LinearLayout parent,int height){Space s=new Space(parent.getContext());parent.addView(s,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));}
    static TextView button(Context c,String name,boolean filled,Runnable action){
        TextView t=text(c,name,15,filled?WHITE:GREEN,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(c,48));pad(t,14,12);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25216b58),bg(filled?GREEN:MINT,0,14,c),null));
        t.setClickable(true);t.setFocusable(true);t.setOnClickListener(v->action.run());t.setContentDescription(name);return t;
    }
    static TextView chip(Context c,String name,boolean selected,Runnable action){TextView t=button(c,name,selected,action);t.setTextSize(14);t.setMinHeight(dp(c,44));return t;}
    static LinearLayout card(Context c){LinearLayout l=column(c);pad(l,16,16);l.setBackground(bg(WHITE,LINE,20,c));return l;}
    static EditText input(Context c,String hint,String value,boolean multiline){
        EditText e=new EditText(c);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setText(value);pad(e,14,12);
        e.setBackground(bg(WHITE,LINE,12,c));e.setSingleLine(!multiline);e.setMinHeight(dp(c,50));
        if(multiline){e.setMinLines(3);e.setGravity(Gravity.TOP);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}
        else e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }
    static TextView pill(Context c,String s,int color){TextView t=text(c,s,12,color,true);pad(t,10,6);t.setBackground(bg((color&0x00ffffff)|0x15000000,0,9,c));return t;}
}
