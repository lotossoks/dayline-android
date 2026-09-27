package app.dayline.core;

import java.util.ArrayList;
import java.util.List;

public final class Model {
    private Model() {}
    public static final class Group {
        public final long id; public final String name; public final int color;
        public Group(long id, String name, int color) { this.id=id; this.name=name; this.color=color; }
        @Override public String toString() { return name; }
    }
    public static final class Task {
        public final long id, groupId; public final String name, note; public final boolean archived;
        public Task(long id,long groupId,String name,String note,boolean archived) {
            this.id=id; this.groupId=groupId; this.name=name; this.note=note; this.archived=archived;
        }
        @Override public String toString() { return name; }
    }
    public static final class Session {
        public final long id, taskId, start, end;
        public Session(long id,long taskId,long start,long end) { this.id=id; this.taskId=taskId; this.start=start; this.end=end; }
        public boolean running() { return end==0; }
        public long finish(long now) { return end==0 ? Math.max(start,now) : end; }
    }
    public static final class Snapshot {
        public final List<Group> groups=new ArrayList<>();
        public final List<Task> tasks=new ArrayList<>();
        public final List<Session> sessions=new ArrayList<>();
        public Group group(long id) { for(Group g:groups) if(g.id==id)return g; return null; }
        public Task task(long id) { for(Task t:tasks) if(t.id==id)return t; return null; }
        public Session session(long id) { for(Session s:sessions) if(s.id==id)return s; return null; }
        public Session running(long taskId) { for(Session s:sessions) if(s.taskId==taskId&&s.running())return s; return null; }
        public List<Session> forTask(long id) { List<Session> r=new ArrayList<>(); for(Session s:sessions)if(s.taskId==id)r.add(s); return r; }
        public List<Session> forGroup(long id) {
            if(id==0)return new ArrayList<>(sessions);
            List<Session> r=new ArrayList<>();
            for(Session s:sessions) { Task t=task(s.taskId); if(t!=null&&t.groupId==id)r.add(s); }
            return r;
        }
        public int runningCount(long groupId) { int n=0; for(Session s:forGroup(groupId))if(s.running())n++; return n; }
    }
}
