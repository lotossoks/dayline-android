package app.dayline;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import app.dayline.core.Model.*;
import app.dayline.core.TimeMath;
import org.json.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    private static Store instance;
    private List<Session> undoSessions;
    public static synchronized Store get(Context c) { if(instance==null)instance=new Store(c.getApplicationContext());return instance; }
    public Store(Context c) { this(c,"dayline.db"); }
    public Store(Context c,String name) { super(c,name,null,1);setWriteAheadLoggingEnabled(true); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE groups (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,color INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT,group_id INTEGER NOT NULL REFERENCES groups(id),name TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',archived INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER NOT NULL REFERENCES tasks(id),started INTEGER NOT NULL CHECK(started>0),ended INTEGER CHECK(ended IS NULL OR ended>started))");
        db.execSQL("CREATE UNIQUE INDEX one_running_per_task ON sessions(task_id) WHERE ended IS NULL");
        db.execSQL("CREATE INDEX session_task_time ON sessions(task_id,started)");
        db.execSQL("CREATE INDEX session_time ON sessions(started,ended)");
        insertGroup(db,"Работа",0xff216b58);insertGroup(db,"Жизнь",0xffbd713f);long sleep=insertGroup(db,"Сон",0xff7270b1);
        ContentValues v=new ContentValues();v.put("group_id",sleep);v.put("name","Сон");db.insertOrThrow("tasks",null,v);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Неизвестная версия базы. Сохраните резервную копию."); }
    private static long insertGroup(SQLiteDatabase db,String name,int color) { ContentValues v=new ContentValues();v.put("name",name);v.put("color",color);return db.insertOrThrow("groups",null,v); }
    public synchronized Snapshot snapshot() {
        SQLiteDatabase db=getReadableDatabase();Snapshot s=new Snapshot();
        try(Cursor c=db.rawQuery("SELECT id,name,color FROM groups ORDER BY id",null)) { while(c.moveToNext())s.groups.add(new Group(c.getLong(0),c.getString(1),c.getInt(2))); }
        try(Cursor c=db.rawQuery("SELECT id,group_id,name,note,archived FROM tasks ORDER BY id",null)) { while(c.moveToNext())s.tasks.add(new Task(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getInt(4)!=0)); }
        try(Cursor c=db.rawQuery("SELECT id,task_id,started,ended FROM sessions ORDER BY started DESC,id DESC",null)) { while(c.moveToNext())s.sessions.add(new Session(c.getLong(0),c.getLong(1),c.getLong(2),c.isNull(3)?0:c.getLong(3))); }
        return s;
    }
    private static String name(String s) { String n=s.trim();if(n.isEmpty())throw new IllegalArgumentException("Введите название");if(n.length()>120)throw new IllegalArgumentException("Название должно быть не длиннее 120 символов");return n; }
    public synchronized long saveGroup(long id,String n,int color) {
        n=name(n);undoSessions=null;SQLiteDatabase db=getWritableDatabase();
        if(id==0)return insertGroup(db,n,color);
        ContentValues v=new ContentValues();v.put("name",n);v.put("color",color);db.update("groups",v,"id=?",new String[]{""+id});return id;
    }
    public synchronized long saveTask(long id,long groupId,String n,String note) {
        n=name(n);if(note.length()>4000)throw new IllegalArgumentException("Описание слишком длинное");
        if(snapshot().group(groupId)==null)throw new IllegalArgumentException("Группа не найдена");
        undoSessions=null;ContentValues v=new ContentValues();v.put("name",n);v.put("note",note.trim());v.put("group_id",groupId);
        SQLiteDatabase db=getWritableDatabase();if(id==0)return db.insertOrThrow("tasks",null,v);
        db.update("tasks",v,"id=?",new String[]{""+id});return id;
    }
    public synchronized void toggle(long taskId,long now) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Snapshot s=snapshot();Task t=s.task(taskId);if(t==null||t.archived)throw new IllegalArgumentException("Сначала верните задачу из завершённых");
            Session current=s.running(taskId);undoSessions=null;
            if(current!=null)stop(db,current,now);else {
                checkOverlap(s,taskId,now,Long.MAX_VALUE,0);
                insertSession(db,0,taskId,now,0);
            }
            db.setTransactionSuccessful();
        }finally { db.endTransaction(); }
    }
    private static void stop(SQLiteDatabase db,Session s,long now) {
        if(now<=s.start) { db.delete("sessions","id=?",new String[]{""+s.id});return; }
        ContentValues v=new ContentValues();v.put("ended",now);db.update("sessions",v,"id=?",new String[]{""+s.id});
    }
    public synchronized void stopGroup(long groupId,long now) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try { undoSessions=null;for(Session s:snapshot().forGroup(groupId))if(s.running())stop(db,s,now);db.setTransactionSuccessful(); }finally { db.endTransaction(); }
    }
    public synchronized void archive(long taskId,boolean archived,long now) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            undoSessions=null;Session s=snapshot().running(taskId);if(archived&&s!=null)stop(db,s,now);
            ContentValues v=new ContentValues();v.put("archived",archived?1:0);db.update("tasks",v,"id=?",new String[]{""+taskId});db.setTransactionSuccessful();
        }finally { db.endTransaction(); }
    }
    private static long insertSession(SQLiteDatabase db,long id,long taskId,long from,long to) {
        ContentValues v=new ContentValues();if(id>0)v.put("id",id);v.put("task_id",taskId);v.put("started",from);
        if(to==0)v.putNull("ended");else v.put("ended",to);
        return db.insertOrThrow("sessions",null,v);
    }
    private static void checkOverlap(Snapshot s,long taskId,long from,long to,long ignore) {
        for(Session x:s.sessions)if(x.id!=ignore&&x.taskId==taskId&&TimeMath.overlaps(from,to,x.start,x.running()?Long.MAX_VALUE:x.end))
            throw new IllegalArgumentException("В этой задаче уже есть запись на выбранное время. Измените границы или выберите другую задачу.");
    }
    public synchronized long saveSession(long id,long taskId,long from,long to,long now) {
        TimeMath.validate(from,to,now);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Snapshot s=snapshot();if(s.task(taskId)==null)throw new IllegalArgumentException("Выберите задачу");
            Session old=s.session(id);if(id!=0&&old==null)throw new IllegalArgumentException("Запись уже изменена");
            if(old!=null&&old.running())throw new IllegalArgumentException("Сначала остановите этот таймер");
            checkOverlap(s,taskId,from,to,id);
            if(id!=0)db.delete("sessions","id=?",new String[]{""+id});
            long result=insertSession(db,id,taskId,from,to);db.setTransactionSuccessful();undoSessions=s.sessions;return result;
        }finally { db.endTransaction(); }
    }
    public synchronized void cut(long id,long from,long to,long targetTaskId,long now) {
        TimeMath.validate(from,to,now);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Snapshot s=snapshot();Session original=s.session(id);
            if(original==null||original.running())throw new IllegalArgumentException("Сначала остановите таймер");
            List<long[]> pieces=TimeMath.subtract(original.start,original.end,from,to);
            if(targetTaskId==original.taskId)throw new IllegalArgumentException("Выберите другую задачу");
            if(targetTaskId!=0) {
                if(s.task(targetTaskId)==null)throw new IllegalArgumentException("Задача не найдена");
                checkOverlap(s,targetTaskId,from,to,0);
            }
            db.delete("sessions","id=?",new String[]{""+id});
            for(long[] p:pieces)insertSession(db,0,original.taskId,p[0],p[1]);
            if(targetTaskId!=0)insertSession(db,0,targetTaskId,from,to);
            db.setTransactionSuccessful();undoSessions=s.sessions;
        }finally { db.endTransaction(); }
    }
    public synchronized void deleteSession(long id) {
        Snapshot s=snapshot();Session x=s.session(id);if(x==null||x.running())throw new IllegalArgumentException("Сначала остановите таймер");
        getWritableDatabase().delete("sessions","id=?",new String[]{""+id});undoSessions=s.sessions;
    }
    public synchronized int fillGaps(long taskId,Set<Long> coverageTasks,long from,long to,long now,long maxDuration) {
        if(from<=0||to<=from||from>=now)throw new IllegalArgumentException("Выберите прошедший день или неделю");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Snapshot before=snapshot();if(before.task(taskId)==null)throw new IllegalArgumentException("Задача не найдена");
            List<long[]> gaps=app.dayline.core.AnalysisMath.fillable(before,taskId,coverageTasks,from,to,now,maxDuration);
            for(long[] gap:gaps){checkOverlap(before,taskId,gap[0],gap[1],0);insertSession(db,0,taskId,gap[0],gap[1]);}
            db.setTransactionSuccessful();if(!gaps.isEmpty())undoSessions=before.sessions;return gaps.size();
        } finally { db.endTransaction(); }
    }
    public synchronized boolean canUndo() { return undoSessions!=null; }
    public synchronized void undo() {
        if(undoSessions==null)return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try { db.delete("sessions",null,null);for(Session s:undoSessions)insertSession(db,s.id,s.taskId,s.start,s.end);db.setTransactionSuccessful();undoSessions=null; }finally { db.endTransaction(); }
    }
    public synchronized String backup(long now) throws JSONException {
        Snapshot s=snapshot();JSONObject root=new JSONObject();root.put("format","dayline");root.put("version",1);root.put("exportedAt",now);
        JSONArray groups=new JSONArray(),tasks=new JSONArray(),sessions=new JSONArray();
        for(Group g:s.groups)groups.put(new JSONObject().put("id",g.id).put("name",g.name).put("color",g.color));
        for(Task t:s.tasks)tasks.put(new JSONObject().put("id",t.id).put("groupId",t.groupId).put("name",t.name).put("note",t.note).put("archived",t.archived));
        for(Session x:s.sessions)sessions.put(new JSONObject().put("id",x.id).put("taskId",x.taskId).put("start",x.start).put("end",x.running()?Math.max(x.start+1,now):x.end));
        root.put("groups",groups);root.put("tasks",tasks);root.put("sessions",sessions);return root.toString(2);
    }
    public synchronized void restore(String json,long now) throws JSONException {
        JSONObject root=new JSONObject(json);
        if(!"dayline".equals(root.optString("format"))||root.optInt("version")!=1)throw new IllegalArgumentException("Это не резервная копия Dayline");
        JSONArray gg=root.getJSONArray("groups"),tt=root.getJSONArray("tasks"),ss=root.getJSONArray("sessions");
        if(gg.length()==0||gg.length()>1000||tt.length()>50000||ss.length()>200000)throw new IllegalArgumentException("Неподдерживаемый размер копии");
        Snapshot parsed=new Snapshot();Set<Long> ids=new HashSet<>();
        for(int i=0;i<gg.length();i++) { JSONObject g=gg.getJSONObject(i);long id=g.getLong("id");if(id<=0||!ids.add(id))throw new IllegalArgumentException("Повторяющаяся группа в копии");parsed.groups.add(new Group(id,name(g.getString("name")),g.getInt("color"))); }
        ids.clear();
        for(int i=0;i<tt.length();i++) { JSONObject t=tt.getJSONObject(i);long id=t.getLong("id"),group=t.getLong("groupId");String note=t.optString("note","");if(id<=0||!ids.add(id)||parsed.group(group)==null||note.length()>4000)throw new IllegalArgumentException("Повреждённая задача в копии");parsed.tasks.add(new Task(id,group,name(t.getString("name")),note,t.optBoolean("archived"))); }
        ids.clear();
        for(int i=0;i<ss.length();i++) {
            JSONObject x=ss.getJSONObject(i);long id=x.getLong("id"),task=x.getLong("taskId"),from=x.getLong("start"),to=x.getLong("end");
            TimeMath.validate(from,to,now);
            if(id<=0||!ids.add(id)||parsed.task(task)==null)throw new IllegalArgumentException("Повреждённая запись в копии");
            parsed.sessions.add(new Session(id,task,from,to));
        }
        Map<Long,List<Session>> perTask=new HashMap<>();
        for(Session x:parsed.sessions)perTask.computeIfAbsent(x.taskId,k->new ArrayList<>()).add(x);
        for(List<Session> rows:perTask.values()) { rows.sort(Comparator.comparingLong(x->x.start));long lastEnd=0;for(Session x:rows) { if(x.start<lastEnd)throw new IllegalArgumentException("Пересечение записей одной задачи в копии");lastEnd=x.end; } }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("sessions",null,null);db.delete("tasks",null,null);db.delete("groups",null,null);
            for(Group g:parsed.groups) { ContentValues v=new ContentValues();v.put("id",g.id);v.put("name",g.name);v.put("color",g.color);db.insertOrThrow("groups",null,v); }
            for(Task t:parsed.tasks) { ContentValues v=new ContentValues();v.put("id",t.id);v.put("group_id",t.groupId);v.put("name",t.name);v.put("note",t.note);v.put("archived",t.archived?1:0);db.insertOrThrow("tasks",null,v); }
            for(Session x:parsed.sessions)insertSession(db,x.id,x.taskId,x.start,x.end);
            db.setTransactionSuccessful();undoSessions=null;
        }finally { db.endTransaction(); }
    }
}
