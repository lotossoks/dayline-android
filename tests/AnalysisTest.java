import app.dayline.core.*;
import app.dayline.core.Model.*;
import app.dayline.core.AnalysisMath.*;
import java.time.*;
import java.util.*;

public final class AnalysisTest {
    static int checks;
    static final ZoneId Z=ZoneId.of("Europe/Moscow");static final long H=TimeMath.HOUR;
    static void eq(long actual,long expected,String message){checks++;if(actual!=expected)throw new AssertionError(message+": "+actual+" != "+expected);}
    static void near(double actual,double expected,String message){checks++;if(Math.abs(actual-expected)>.0000001)throw new AssertionError(message+": "+actual+" != "+expected);}
    static Snapshot data(){Snapshot s=new Snapshot();s.groups.add(new Group(1,"Работа",0));s.groups.add(new Group(2,"Жизнь",0));s.tasks.add(new Task(1,1,"Задача 1","",false));s.tasks.add(new Task(2,1,"Задача 2","",true));s.tasks.add(new Task(3,1,"В офисе","",false));s.tasks.add(new Task(4,2,"Другое","",false));return s;}
    static void add(Snapshot s,long task,long start,long end){s.sessions.add(new Session(s.sessions.size()+1,task,start,end));}
    public static void main(String[] args){
        LocalDate monday=LocalDate.of(2026,9,21);long base=TimeMath.start(monday,Z),now=TimeMath.start(monday.plusDays(14),Z)+12*H;
        Snapshot s=data();
        add(s,1,base+9*H,base+11*H);add(s,2,base+10*H,base+12*H);add(s,3,base+8*H,base+17*H);
        long second=TimeMath.start(monday.plusWeeks(1),Z);add(s,1,second+9*H,second+13*H);add(s,2,second+10*H,second+11*H);add(s,3,second+8*H,second+17*H);
        add(s,1,TimeMath.start(monday.plusDays(14),Z),0);
        Set<Long> picked=new HashSet<>(Arrays.asList(1L,2L));
        Averages a=AnalysisMath.averages(s,picked,0,null,Z,now);
        eq(a.all.days,14,"completed calendar days include empty days and exclude today");eq(a.all.sum,9*H,"selected sum excludes office and today");eq(a.all.union,7*H,"selected union");eq(a.all.mean(a.all.union),H/2,"mean over all observed dates");
        eq(a.weekdays[0].days,2,"two Mondays");eq(a.weekdays[0].mean(a.weekdays[0].sum),9*H/2,"mean Monday task hours");eq(a.weekdays[0].mean(a.weekdays[0].union),7*H/2,"mean Monday union");
        eq(a.weekdays[1].days,2,"empty Tuesdays count");eq(a.weekdays[1].sum,0,"empty weekday means zero");
        near(a.weekdays[0].share(1,10),1,"parallel same-group sessions do not double heatmap");near(a.weekdays[0].share(1,12),.5,"hourly recurrence");near(a.weekdays[0].share(1,8),0,"office excluded from heatmap");near(a.weekdays[0].outsideShare(8),1,"outside selected covers office");
        eq(a.all.untracked,14*24*H-18*H,"actual untracked stays distinct from selection");
        eq(AnalysisMath.select(s,picked,2).size(),5,"explicit task selection spans groups");eq(AnalysisMath.select(s,Collections.emptySet(),0).size(),0,"empty selection is not all tasks");
        Averages week=AnalysisMath.averages(s,picked,0,monday,Z,now);eq(week.all.days,7,"optional week denominator");eq(week.all.sum,4*H,"week clipped");
        Averages empty=AnalysisMath.averages(new Snapshot(),null,0,null,Z,now);eq(empty.all.days,0,"no data no divide by zero");
        Snapshot today=data();add(today,1,now-H,0);eq(AnalysisMath.averages(today,null,0,null,Z,now).all.days,0,"only current day has no finished sample");
        Snapshot overnight=data();long midnight=TimeMath.start(monday.plusDays(1),Z);add(overnight,1,midnight-H,midnight+2*H);Averages night=AnalysisMath.averages(overnight,null,0,null,Z,midnight+24*H);eq(night.all.days,2,"overnight dates");eq(night.all.sum,3*H,"overnight duration");near(night.all.share(1,23),.5,"late night wall time");near(night.all.share(1,0),.5,"early night wall time");
        ZoneId ny=ZoneId.of("America/New_York");LocalDate fall=LocalDate.of(2026,11,1);long fallStart=TimeMath.start(fall,ny),fallEnd=TimeMath.start(fall.plusDays(1),ny);Snapshot dst=data();add(dst,1,fallStart+H,fallStart+2*H);Average d=AnalysisMath.averages(dst,null,0,null,ny,fallEnd).all;eq(d.elapsed,25*H,"DST 25-hour calendar day");near(d.exposure[1],2*H,"repeated hour denominator");near(d.share(1,1),.5,"repeated hour occupancy");
        Snapshot gaps=data();add(gaps,1,base+9*H,base+10*H);add(gaps,2,base+10*H+60000,base+11*H);add(gaps,3,base+8*H,base+18*H);
        eq(AnalysisMath.fillable(gaps,4,null,base+9*H,base+12*H,now,120000).size(),0,"global gaps respect office coverage");
        List<long[]> small=AnalysisMath.fillable(gaps,4,picked,base+9*H,base+12*H,now,120000);eq(small.size(),1,"filtered short gap");eq(small.get(0)[1]-small.get(0)[0],60000,"one minute gap");
        eq(AnalysisMath.fillable(gaps,4,picked,base+9*H,base+12*H,now,0).size(),2,"all gaps includes long trailing gap");
        add(gaps,4,base+10*H,base+10*H+30000);small=AnalysisMath.fillable(gaps,4,picked,base+9*H,base+12*H,now,120000);eq(small.get(0)[0],base+10*H+30000,"target time always protected");
        List<long[]> future=AnalysisMath.fillable(data(),4,null,base,base+24*H,base+H,0);eq(future.get(0)[1],base+H,"filler excludes future");
        System.out.println("PASS: "+checks+" selection, averages and gap planning assertions");
    }
}
