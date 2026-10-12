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
        densityTests(base);
        System.out.println("PASS: "+checks+" selection, averages and gap planning assertions");
    }
    static void densityTests(long base){
        double[] exposure=new double[AnalysisMath.BINS],all=new double[AnalysisMath.BINS],night=new double[AnalysisMath.BINS];Arrays.fill(exposure,300000);Arrays.fill(all,300000);
        for(int i=0;i<12;i++){night[i]=300000;night[AnalysisMath.BINS-1-i]=300000;}
        double[] full=AnalysisMath.density(all,exposure,30),none=AnalysisMath.density(null,exposure,30),curve=AnalysisMath.density(night,exposure,30);
        double integral=0;for(int i=0;i<AnalysisMath.BINS;i++){near(full[i],1,"full occupancy stays 100 percent");near(none[i],0,"missing group stays zero");if(curve[i]<0||curve[i]>1)throw new AssertionError("KDE out of range");integral+=(curve[i]+curve[i+1])/2*5;}
        near(curve[0],curve[AnalysisMath.BINS],"24-hour seam continuous");near(curve[6],curve[AnalysisMath.BINS-6],"sleep symmetric across midnight");near(integral,120,"kernel preserves two hours of occupied time");near(curve[144],0,"sleep does not leak to midday");
        near(AnalysisMath.density(all,new double[AnalysisMath.BINS],30)[0],0,"no exposure no division by zero");
        Snapshot shortSession=data();add(shortSession,1,base+9*H+15*60000,base+9*H+45*60000);add(shortSession,2,base+9*H+20*60000,base+9*H+40*60000);
        Average avg=AnalysisMath.averages(shortSession,null,0,null,Z,base+24*H).all;
        near(avg.occupiedBins.get(1L)[9*12+3],300000,"five-minute resolution retains quarter-hour boundary");near(avg.occupiedBins.get(1L)[9*12+2],0,"no hourly spreading before start");near(avg.occupiedBins.get(1L)[9*12+4],300000,"same-group overlap counted once in KDE");
        double[] shortCurve=AnalysisMath.density(avg.occupiedBins.get(1L),avg.exposureBins,30);
        near(shortCurve[9*12],shortCurve[10*12],"KDE centred on actual 09:30 session");
        Average picked=AnalysisMath.averages(shortSession,Collections.singleton(2L),0,null,Z,base+24*H).all;near(picked.occupiedBins.get(1L)[9*12+3],0,"task exclusion also applies to density");
        Snapshot partial=data();add(partial,1,base+9*H,base+10*H);Average two=AnalysisMath.averages(partial,null,0,null,Z,base+48*H).all;
        double[] half=AnalysisMath.density(two.occupiedBins.get(1L),two.exposureBins,30);Average one=AnalysisMath.averages(partial,null,0,null,Z,base+24*H).all;double[] single=AnalysisMath.density(one.occupiedBins.get(1L),one.exposureBins,30);near(half[114],single[114]/2,"empty dates lower density without renormalizing each group");
        ZoneId ny=ZoneId.of("America/New_York");LocalDate fall=LocalDate.of(2026,11,1);long start=TimeMath.start(fall,ny),end=TimeMath.start(fall.plusDays(1),ny);Snapshot dst=data();add(dst,1,start,end);Average repeated=AnalysisMath.averages(dst,null,0,null,ny,end).all;
        near(repeated.exposureBins[12],600000,"repeated DST bins counted twice");double[] dstCurve=AnalysisMath.density(repeated.occupiedBins.get(1L),repeated.exposureBins,30);near(dstCurve[18],1,"DST numerator/exposure smooth together");
        LocalDate spring=LocalDate.of(2026,3,8);start=TimeMath.start(spring,ny);end=TimeMath.start(spring.plusDays(1),ny);Snapshot missing=data();add(missing,1,start,end);Average skipped=AnalysisMath.averages(missing,null,0,null,ny,end).all;
        near(skipped.exposureBins[24],0,"nonexistent clock bins have no exposure");near(AnalysisMath.density(skipped.occupiedBins.get(1L),skipped.exposureBins,30)[30],1,"missing DST hour is not a false gap");
    }

}
