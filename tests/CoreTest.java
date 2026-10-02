import app.dayline.core.*;
import app.dayline.core.Model.*;
import java.time.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class CoreTest {
    static int checks=0;static final ZoneId Z=ZoneId.of("Europe/Moscow");
    static long at(String s){return LocalDateTime.parse(s).atZone(Z).toInstant().toEpochMilli();}
    static void eq(long a,long b,String name){checks++;if(a!=b)throw new AssertionError(name+": expected "+b+", got "+a);}
    static void yes(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
    static void throwsError(Runnable r,String name){checks++;try{r.run();throw new AssertionError(name);}catch(IllegalArgumentException good){}}
    public static void main(String[] args)throws Exception {
        long h=TimeMath.HOUR,day=at("2026-09-21T00:00"),now=day+24*h;
        List<Session> overlap=Arrays.asList(new Session(1,1,day+10*h,day+12*h),new Session(2,2,day+11*h,day+13*h));
        eq(TimeMath.sum(overlap,day,now,now),4*h,"parallel sum");eq(TimeMath.union(overlap,day,now,now),3*h,"parallel union");
        eq(TimeMath.gaps(overlap,day,now,now).stream().mapToLong(x->x[1]-x[0]).sum(),21*h,"gaps");
        eq(TimeMath.union(overlap,day+11*h,day+12*h,now),h,"clipping both ends");
        Session sleep=new Session(3,3,day-h,day+7*h);eq(TimeMath.duration(sleep,day,now,now),7*h,"overnight after midnight");eq(TimeMath.duration(sleep,day-24*h,day,now),h,"overnight before midnight");
        Session running=new Session(4,1,day+14*h,0);eq(TimeMath.duration(running,day,now,day+16*h),2*h,"running snapshot");
        eq(TimeMath.gaps(Collections.emptyList(),day,now,day+6*h).get(0)[1],day+6*h,"future excluded");
        eq(TimeMath.gaps(Collections.emptyList(),now,now+24*h,day).size(),0,"future day excluded");
        eq(TimeMath.sum(Arrays.asList(new Session(1,1,day,day+24*h),new Session(2,2,day,day+24*h)),day,now,now),48*h,"more than 24 hours");
        eq(TimeMath.start(TimeMath.monday(LocalDate.of(2026,9,27)),Z),day,"Monday starts week");
        List<long[]> pieces=TimeMath.subtract(day,day+3*h,day+h,day+2*h);eq(pieces.size(),2,"middle cut creates two pieces");eq(pieces.get(0)[1],day+h,"left remainder");eq(pieces.get(1)[0],day+2*h,"right remainder");
        eq(TimeMath.subtract(day,day+h,day,day+h).size(),0,"whole cut");
        eq(TimeMath.subtract(day,day+2*h,day,day+h).size(),1,"prefix cut");
        throwsError(()->TimeMath.subtract(day,day+h,day-h,day),"outside cut rejected");
        throwsError(()->TimeMath.validate(day,day-1,now),"backwards rejected");
        throwsError(()->TimeMath.validate(day,now+1,now),"future rejected");
        ZoneId ny=ZoneId.of("America/New_York");eq(TimeMath.start(LocalDate.of(2026,3,9),ny)-TimeMath.start(LocalDate.of(2026,3,8),ny),23*h,"DST spring day");
        eq(TimeMath.start(LocalDate.of(2026,11,2),ny)-TimeMath.start(LocalDate.of(2026,11,1),ny),25*h,"DST fall day");
        yes(!TimeMath.overlaps(0,10,10,20),"touching endpoints are not overlap");
        Random random=new Random(7);
        for(int trial=0;trial<500;trial++){
            List<Session> spans=new ArrayList<>();boolean[] expected=new boolean[1440];long expectedSum=0;
            int cutoff=1+random.nextInt(1440);
            for(int i=0;i<20;i++){int a=random.nextInt(1440),b=a+1+random.nextInt(1440-a);spans.add(new Session(i,i,day+a*60000L,day+b*60000L));int stop=Math.min(b,cutoff);expectedSum+=Math.max(0,stop-a)*60000L;for(int m=a;m<stop;m++)expected[m]=true;}
            long actual=0;for(boolean x:expected)if(x)actual+=60000;
            eq(TimeMath.union(spans,day,now,day+cutoff*60000L),actual,"random union "+trial);
            eq(TimeMath.sum(spans,day,now,day+cutoff*60000L),expectedSum,"random sum "+trial);
            long gaps=TimeMath.gaps(spans,day,now,day+cutoff*60000L).stream().mapToLong(x->x[1]-x[0]).sum();eq(gaps+actual,cutoff*60000L,"coverage partition "+trial);
        }
        Snapshot data=new Snapshot();data.groups.add(new Group(1,"Работа",0xff216b58));data.groups.add(new Group(2,"Личное SECRET_GROUP",0xff7270b1));
        data.tasks.add(new Task(1,1,"TGPA-123","Описание проекта",false));data.tasks.add(new Task(2,1,"=TEST & <Пример>","Завершённая задача",true));
        data.tasks.add(new Task(3,2,"SECRET_TASK","SECRET_NOTE",false));data.tasks.add(new Task(4,2,"SECOND_SECRET","HIDDEN_NOTE",false));
        data.sessions.addAll(overlap);data.sessions.add(new Session(3,3,day+12*h,day+14*h));data.sessions.add(new Session(4,4,day+13*h,day+15*h));
        data.sessions.add(new Session(5,1,day-h,day+h));data.sessions.add(new Session(6,1,day+6*24*h+15*h,0));
        long captured=day+6*24*h+16*h;Path out=Paths.get(args.length==0?"build/test-reports":args[0]);Files.createDirectories(out);
        for(long scope:new long[]{0,1}){File file=out.resolve(scope==0?"all.xlsx":"work.xlsx").toFile();try(OutputStream stream=new FileOutputStream(file)){ExcelReport.write(stream,data,LocalDate.of(2026,9,23),Z,scope,captured);}
            try(ZipFile zip=new ZipFile(file)){
                StringBuilder contents=new StringBuilder();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();contents.append(new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
                String xml=contents.toString();yes(xml.contains("Лента недели"),"timeline sheet exists");yes(xml.contains("inlineStr"),"text literal escaped");
                if(scope==1){yes(!xml.contains("SECRET")&&!xml.contains("HIDDEN_NOTE"),"all private names and descriptions redacted");yes(xml.contains("Другое"),"other activities aggregated");yes(xml.contains("Не учтено"),"gaps separately labeled");}
                yes(xml.contains("&amp; &lt;Пример&gt;"),"XML escaping");
            }
        }
        Snapshot empty=new Snapshot();empty.groups.add(new Group(1,"Работа",0xff216b58));try(OutputStream stream=new FileOutputStream(out.resolve("empty.xlsx").toFile())){ExcelReport.write(stream,empty,LocalDate.of(2026,10,5),Z,0,captured);}
        try(OutputStream stream=Files.newOutputStream(out.resolve("selected.xlsx"))){ExcelReport.write(stream,data,LocalDate.of(2026,9,23),Z,0,captured,Collections.singleton(1L));}
        try(ZipFile zip=new ZipFile(out.resolve("selected.xlsx").toFile())){
            StringBuilder content=new StringBuilder();Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements())content.append(new String(zip.getInputStream(entries.nextElement()).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            yes(!content.toString().contains("SECRET")&&!content.toString().contains("TEST")&&!content.toString().contains("Завершённая задача"),"selected export omits unselected names and descriptions");
            yes(content.toString().contains("Другое")&&content.toString().contains("TGPA-123"),"selected export retains chosen detail and anonymous other lane");
        }
        System.out.println("PASS: "+checks+" assertions; reports in "+out.toAbsolutePath());
    }
}
