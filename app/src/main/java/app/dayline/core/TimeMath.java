package app.dayline.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import app.dayline.core.Model.Session;

public final class TimeMath {
    public static final Locale RU=new Locale("ru","RU");
    public static final long HOUR=3_600_000L;
    private TimeMath() {}
    public static long start(LocalDate d,ZoneId z) { return d.atStartOfDay(z).toInstant().toEpochMilli(); }
    public static LocalDate day(long t,ZoneId z) { return Instant.ofEpochMilli(t).atZone(z).toLocalDate(); }
    public static LocalDate monday(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    public static long duration(Session s,long from,long to,long now) { return Math.max(0,Math.min(Math.min(s.finish(now),now),to)-Math.max(s.start,from)); }
    public static long sum(List<Session> sessions,long from,long to,long now) {
        long n=0; for(Session s:sessions)n+=duration(s,from,to,now); return n;
    }
    public static List<long[]> merged(List<Session> sessions,long from,long to,long now) {
        List<long[]> r=new ArrayList<>();
        for(Session s:sessions) { long a=Math.max(from,s.start),b=Math.min(Math.min(to,s.finish(now)),now);if(b>a)r.add(new long[]{a,b}); }
        r.sort(Comparator.comparingLong(a->a[0]));
        List<long[]> out=new ArrayList<>();
        for(long[] a:r) { if(out.isEmpty()||out.get(out.size()-1)[1]<a[0])out.add(a.clone());else out.get(out.size()-1)[1]=Math.max(out.get(out.size()-1)[1],a[1]); }
        return out;
    }
    public static long union(List<Session> sessions,long from,long to,long now) {
        long n=0; for(long[] a:merged(sessions,from,to,now))n+=a[1]-a[0];return n;
    }
    public static List<long[]> gaps(List<Session> sessions,long from,long to,long now) {
        long finish=Math.min(to,now),cursor=from;List<long[]> r=new ArrayList<>();
        if(finish<=from)return r;
        for(long[] a:merged(sessions,from,to,now)) { if(a[0]>cursor)r.add(new long[]{cursor,a[0]});cursor=a[1]; }
        if(cursor<finish)r.add(new long[]{cursor,finish});return r;
    }
    public static String clock(long ms) { long sec=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,sec/60%60,sec%60); }
    public static String shortTime(long ms) { long m=Math.max(0,ms)/60000;return (m/60)+" ч "+String.format(Locale.ROOT,"%02d",m%60)+" м"; }
    public static String at(long ms,ZoneId z) { return Instant.ofEpochMilli(ms).atZone(z).format(DateTimeFormatter.ofPattern("HH:mm",RU)); }
    public static String dateTime(long ms,ZoneId z) { return Instant.ofEpochMilli(ms).atZone(z).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm",RU)); }
    public static boolean overlaps(long a,long b,long c,long d) { return a<d&&c<b; }
    public static void validate(long from,long to,long now) {
        if(from<=0||to<=from)throw new IllegalArgumentException("Окончание должно быть позже начала");
        if(to>now)throw new IllegalArgumentException("Нельзя записать время в будущем");
    }
    public static List<long[]> subtract(long start,long end,long cutStart,long cutEnd) {
        if(cutStart<start||cutEnd>end||cutEnd<=cutStart)throw new IllegalArgumentException("Выберите участок внутри записи");
        List<long[]> r=new ArrayList<>();
        if(cutStart>start)r.add(new long[]{start,cutStart});
        if(cutEnd<end)r.add(new long[]{cutEnd,end});
        return r;
    }
}
