package app.dayline.core;

import app.dayline.core.Model.*;
import java.time.*;
import java.util.*;

/** Calendar-based averages. All completed dates in the observation window count, including empty ones. */
public final class AnalysisMath {
    private AnalysisMath() {}
    public static List<Session> select(Snapshot data, Set<Long> taskIds, long group) {
        List<Session> result=new ArrayList<>();
        for(Session s:data.sessions) {
            Task task=data.task(s.taskId);
            if(task!=null&&(taskIds!=null?taskIds.contains(s.taskId):group==0||task.groupId==group))result.add(s);
        }
        return result;
    }
    public static final class Average {
        public int days;
        public long sum,union,elapsed,untracked;
        public final Map<Long,Long> taskSum=new LinkedHashMap<>(),groupSum=new LinkedHashMap<>(),groupUnion=new LinkedHashMap<>();
        public final Map<Long,double[]> hourly=new LinkedHashMap<>();
        public final double[] exposure=new double[24],outside=new double[24];
        public long mean(long total){return days==0?0:Math.round((double)total/days);}
        public double share(long group,int hour){double[] values=hourly.get(group);return values==null||exposure[hour]==0?0:values[hour]/exposure[hour];}
        public double outsideShare(int hour){return exposure[hour]==0?0:outside[hour]/exposure[hour];}
    }
    public static final class Averages {
        public final Average all=new Average();
        public final Average[] weekdays=new Average[7];
        public LocalDate first,endExclusive;
        Averages(){for(int i=0;i<7;i++)weekdays[i]=new Average();}
    }
    public static Averages averages(Snapshot data,Set<Long> taskIds,long group,LocalDate week,ZoneId zone,long now) {
        Averages out=new Averages();LocalDate today=TimeMath.day(now,zone),first=null;
        for(Session s:data.sessions)if(s.start<now){LocalDate d=TimeMath.day(s.start,zone);if(first==null||d.isBefore(first))first=d;}
        if(first==null){out.first=today;out.endExclusive=today;return out;}
        LocalDate end=today;
        if(week!=null){LocalDate monday=TimeMath.monday(week);if(monday.isAfter(first))first=monday;if(monday.plusDays(7).isBefore(end))end=monday.plusDays(7);}
        out.first=first;out.endExclusive=end;
        List<Session> selected=select(data,taskIds,group);
        Map<LocalDate,List<Session>> allByDay=splitByDay(data.sessions,first,end,zone,now);
        Map<LocalDate,List<Session>> selectedByDay=splitByDay(selected,first,end,zone,now);
        Map<Long,Long> groups=new HashMap<>();for(Task t:data.tasks)groups.put(t.id,t.groupId);
        for(LocalDate day=first;day.isBefore(end);day=day.plusDays(1)) {
            long start=TimeMath.start(day,zone),finish=TimeMath.start(day.plusDays(1),zone);
            List<Session> rows=selectedByDay.getOrDefault(day,Collections.emptyList());
            List<Session> all=allByDay.getOrDefault(day,Collections.emptyList());
            Map<Long,List<Session>> perGroup=new LinkedHashMap<>();Map<Long,Long> tasks=new LinkedHashMap<>();
            for(Session s:rows){tasks.merge(s.taskId,s.end-s.start,Long::sum);perGroup.computeIfAbsent(groups.get(s.taskId),k->new ArrayList<>()).add(s);}
            long sum=TimeMath.sum(rows,start,finish,now),union=TimeMath.union(rows,start,finish,now),untracked=finish-start-TimeMath.union(all,start,finish,now);
            Average[] targets={out.all,out.weekdays[day.getDayOfWeek().getValue()-1]};
            for(Average target:targets){target.days++;target.sum+=sum;target.union+=union;target.elapsed+=finish-start;target.untracked+=untracked;for(Map.Entry<Long,Long> t:tasks.entrySet())target.taskSum.merge(t.getKey(),t.getValue(),Long::sum);}
            for(Map.Entry<Long,List<Session>> entry:perGroup.entrySet())for(Average target:targets){target.groupSum.merge(entry.getKey(),TimeMath.sum(entry.getValue(),start,finish,now),Long::sum);target.groupUnion.merge(entry.getKey(),TimeMath.union(entry.getValue(),start,finish,now),Long::sum);}
            // Walk real hours: repeated DST hours contribute exposure twice; a missing hour contributes none.
            for(ZonedDateTime cursor=day.atStartOfDay(zone);cursor.toInstant().toEpochMilli()<finish;cursor=cursor.plusHours(1)) {
                long a=cursor.toInstant().toEpochMilli(),b=Math.min(finish,cursor.plusHours(1).toInstant().toEpochMilli());int hour=cursor.getHour();
                for(Average target:targets){target.exposure[hour]+=b-a;target.outside[hour]+=b-a-TimeMath.union(rows,a,b,now);}
                for(Map.Entry<Long,List<Session>> entry:perGroup.entrySet()) {
                    long occupied=TimeMath.union(entry.getValue(),a,b,now);
                    for(Average target:targets)target.hourly.computeIfAbsent(entry.getKey(),k->new double[24])[hour]+=occupied;
                }
            }
        }
        return out;
    }
    private static Map<LocalDate,List<Session>> splitByDay(List<Session> sessions,LocalDate first,LocalDate end,ZoneId zone,long now) {
        Map<LocalDate,List<Session>> map=new HashMap<>();if(!first.isBefore(end))return map;
        long start=TimeMath.start(first,zone),finish=TimeMath.start(end,zone);
        for(Session s:sessions){long a=Math.max(start,s.start),b=Math.min(finish,Math.min(now,s.finish(now)));if(b<=a)continue;
            for(LocalDate d=TimeMath.day(a,zone);TimeMath.start(d,zone)<b;d=d.plusDays(1)){
                long lo=Math.max(a,TimeMath.start(d,zone)),hi=Math.min(b,TimeMath.start(d.plusDays(1),zone));
                if(hi>lo)map.computeIfAbsent(d,k->new ArrayList<>()).add(new Session(s.id,s.taskId,lo,hi));
            }
        }
        return map;
    }
    /** Target intervals always remain occupied, even when the coverage filter excludes the target. */
    public static List<long[]> fillable(Snapshot data,long targetTask,Set<Long> coverageTasks,long from,long to,long now,long maxDuration) {
        List<Session> occupied=new ArrayList<>();
        for(Session s:data.sessions)if(coverageTasks==null||coverageTasks.contains(s.taskId)||s.taskId==targetTask)occupied.add(s);
        List<long[]> result=new ArrayList<>();
        for(long[] gap:TimeMath.gaps(occupied,from,to,now))if(maxDuration<=0||gap[1]-gap[0]<=maxDuration)result.add(gap);
        return result;
    }
}
