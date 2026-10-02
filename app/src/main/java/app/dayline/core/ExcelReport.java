package app.dayline.core;

import app.dayline.core.Model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.*;

/** Offline OOXML export. All numeric values are a consistent snapshot at capturedAt. */
public final class ExcelReport {
    private static final double DAY=86_400_000d;
    private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private ExcelReport() {}
    private static final class Cell {
        final Object value;final int style;
        Cell(Object value,int style){this.value=value;this.style=style;}
    }
    private static Cell text(String v){return new Cell(v,0);}
    private static Cell time(long v){return new Cell(v/DAY,3);}
    private static Cell hours(long v){return new Cell(v/(double)TimeMath.HOUR,4);}
    private static Cell number(long v){return new Cell(v,12);}
    private static Cell date(long ms,ZoneId zone){
        ZonedDateTime d=Instant.ofEpochMilli(ms).atZone(zone);
        return new Cell(ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),d.toLocalDate())+d.toLocalTime().toSecondOfDay()/86400d,5);
    }
    private static final class Sheet {
        final String name;final int[] widths;final List<Cell[]> rows=new ArrayList<>();
        int header=4;boolean filter=true;String bar="";boolean freezeLabels=false;
        Sheet(String name,int... widths){this.name=name;this.widths=widths;}
        void row(Cell... cells){rows.add(cells);}
        void title(String title,String subtitle){row(new Cell(title,1));row(new Cell(subtitle,8));row();}
        void headings(String... labels){Cell[] cells=new Cell[labels.length];for(int i=0;i<labels.length;i++)cells[i]=new Cell(labels[i],2);row(cells);}
        String xml(){
            StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"").append(NS).append("\">");
            b.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"><pane ySplit=\"").append(header).append(freezeLabels?"\" xSplit=\"2":"").append("\" topLeftCell=\"").append(freezeLabels?"C":"A").append(header+1).append("\" activePane=\"").append(freezeLabels?"bottomRight":"bottomLeft").append("\" state=\"frozen\"/></sheetView></sheetViews><cols>");
            for(int i=0;i<widths.length;i++)b.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
            b.append("</cols><sheetData>");
            for(int r=0;r<rows.size();r++){
                Cell[] row=rows.get(r);
                int height=30;
                if(r==0)height=36;else if(r==1||row.length>0&&row[0].style==2)height=42;
                else for(int c=0;c<row.length;c++)if(row[c].value instanceof String)height=Math.max(height,Math.min(160,15*(int)Math.ceil(((String)row[c].value).length()/(widths[c]*.95))));
                b.append("<row r=\"").append(r+1).append("\" ht=\"").append(height).append("\" customHeight=\"1\">");
                for(int c=0;c<row.length;c++){
                    Cell v=row[c];b.append("<c r=\"").append(col(c)).append(r+1).append("\" s=\"").append(v.style).append("\"");
                    if(v.value instanceof Number)b.append("><v>").append(v.value).append("</v></c>");
                    else b.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(String.valueOf(v.value))).append("</t></is></c>");
                }
                b.append("</row>");
            }
            b.append("</sheetData>");
            if(filter&&rows.size()>header)b.append("<autoFilter ref=\"A").append(header).append(":").append(col(widths.length-1)).append(rows.size()).append("\"/>");
            b.append("<mergeCells count=\"2\"><mergeCell ref=\"A1:").append(col(widths.length-1)).append("1\"/><mergeCell ref=\"A2:").append(col(widths.length-1)).append("2\"/></mergeCells>");
            if(!bar.isEmpty())b.append("<conditionalFormatting sqref=\"").append(bar).append("\"><cfRule type=\"dataBar\" priority=\"1\"><dataBar><cfvo type=\"min\"/><cfvo type=\"max\"/><color rgb=\"FFB8DACB\"/></dataBar></cfRule></conditionalFormatting>");
            return b.append("<pageMargins left=\"0.3\" right=\"0.3\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup orientation=\"landscape\" paperSize=\"9\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>").toString();
        }
    }
    private static boolean included(Task task,long group,Set<Long> ids){return ids!=null?ids.contains(task.id):group==0||task.groupId==group;}
    private static boolean includedGroup(Snapshot data,long group,long selectedGroup,Set<Long> ids){
        if(ids==null)return selectedGroup==0||group==selectedGroup;
        for(Task task:data.tasks)if(task.groupId==group&&ids.contains(task.id))return true;return false;
    }
    public static void write(OutputStream stream,Snapshot data,LocalDate anyDay,ZoneId zone,long groupId,long capturedAt)throws IOException{
        write(stream,data,anyDay,zone,groupId,capturedAt,null);
    }
    public static void write(OutputStream stream,Snapshot data,LocalDate anyDay,ZoneId zone,long groupId,long capturedAt,Set<Long> taskIds)throws IOException{
        boolean filtered=groupId!=0||taskIds!=null;
        LocalDate week=TimeMath.monday(anyDay);long from=TimeMath.start(week,zone),to=TimeMath.start(week.plusDays(7),zone);
        List<Session> rows=AnalysisMath.select(data,taskIds,groupId);rows.removeIf(s->TimeMath.duration(s,from,to,capturedAt)==0);
        rows.sort(Comparator.comparingLong(s->s.start));
        Group selected=data.group(groupId);String scope=taskIds!=null?"Выбранные задачи: "+taskIds.size():selected==null?"Все группы":selected.name;
        String period=week.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))+" — "+week.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String subtitle=period+" · "+scope+" · "+zone;
        long sum=TimeMath.sum(rows,from,to,capturedAt),union=TimeMath.union(rows,from,to,capturedAt);
        long elapsed=Math.max(0,Math.min(capturedAt,to)-from),gap=Math.max(0,elapsed-union);

        Sheet overview=new Sheet("Обзор недели",36,22,25,70);overview.filter=false;
        overview.title("DAYLINE / Обзор недели",subtitle);
        overview.headings("Показатель","Время, ч:мм:сс","Часы (числом)","Как читать");
        overview.row(text("Сумма времени задач"),time(sum),hours(sum),text("Параллельные задачи учитываются каждая целиком."));
        overview.row(text("Фактическая занятость"),time(union),hours(union),text("Пересечения объединены. Одна минута учитывается один раз."));
        overview.row(text(!filtered?"Не учтено":"Без записей выбранных задач"),time(gap),hours(gap),text("Только прошедшая часть недели. Будущее время исключено."));
        overview.row(text("Время параллельного учёта сверх занятости"),time(sum-union),hours(sum-union),text("Разница между суммой часов задач и фактической занятостью."));
        overview.row(text("Записей за неделю"),number(rows.size()));
        overview.row(text("Сформировано"),date(capturedAt,zone));
        overview.row();overview.headings("Группа","Время задач","Занятость без пересечений","Доля суммы задач");
        for(Group g:data.groups)if(includedGroup(data,g.id,groupId,taskIds)){
            List<Session> gs=new ArrayList<>();for(Session s:rows)if(data.task(s.taskId).groupId==g.id)gs.add(s);
            long n=TimeMath.sum(gs,from,to,capturedAt);overview.row(text(g.name),time(n),time(TimeMath.union(gs,from,to,capturedAt)),new Cell(sum==0?0:n/(double)sum,6));
        }
        overview.row();overview.row(text("Отчёт — снимок на момент экспорта."));
        overview.row(text("Записи на границах недели обрезаны только в этом отчёте."));
        overview.row(text("Суммы занятости отдельных групп могут пересекаться между собой."));

        Sheet days=new Sheet("По дням",18,19,23,25,24,18,18);
        days.title("Неделя по дням",subtitle);days.headings("Дата","День недели","Время задач","Фактическая занятость",!filtered?"Не учтено":"Вне выбранных задач","Часы задач","Часы занятости");
        for(int i=0;i<7;i++){
            LocalDate d=week.plusDays(i);long a=TimeMath.start(d,zone),b=TimeMath.start(d.plusDays(1),zone),n=TimeMath.sum(rows,a,b,capturedAt),u=TimeMath.union(rows,a,b,capturedAt);
            days.row(new Cell(ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),d),7),text(d.format(DateTimeFormatter.ofPattern("EEEE",TimeMath.RU))),time(n),time(u),time(Math.max(0,Math.min(b,capturedAt)-a)-u),hours(n),hours(u));
        }
        days.bar="C5:C11";

        Sheet groups=new Sheet("По группам",30,24,25,18,18,18);
        groups.title("Распределение по группам",subtitle);groups.headings("Группа","Время задач","Фактическая занятость","Часы задач","Доля задач","Записей");
        for(Group g:data.groups)if(includedGroup(data,g.id,groupId,taskIds)){
            List<Session> gs=new ArrayList<>();for(Session s:rows)if(data.task(s.taskId).groupId==g.id)gs.add(s);
            long n=TimeMath.sum(gs,from,to,capturedAt);groups.row(text(g.name),time(n),time(TimeMath.union(gs,from,to,capturedAt)),hours(n),new Cell(sum==0?0:n/(double)sum,6),number(gs.size()));
        }

        Sheet tasks=new Sheet("По задачам",23,38,23,18,18,18,22,65);
        tasks.title("На что ушло время",subtitle);tasks.headings("Группа","Задача","Время","Часы","Доля","Записей","Статус задачи","Описание");
        List<Task> sorted=new ArrayList<>(data.tasks);sorted.sort((a,b)->Long.compare(TimeMath.sum(data.forTask(b.id),from,to,capturedAt),TimeMath.sum(data.forTask(a.id),from,to,capturedAt)));
        for(Task t:sorted)if(included(t,groupId,taskIds)){
            List<Session> ts=new ArrayList<>();for(Session s:rows)if(s.taskId==t.id)ts.add(s);
            long n=TimeMath.sum(ts,from,to,capturedAt);if(n==0)continue;
            tasks.row(text(data.group(t.groupId).name),text(t.name),time(n),hours(n),new Cell(sum==0?0:n/(double)sum,6),number(ts.size()),text(t.archived?"Завершена":"Открыта"),text(t.note));
        }
        if(tasks.rows.size()>4)tasks.bar="C5:C"+tasks.rows.size();

        Sheet intervals=new Sheet("Интервалы",24,38,23,23,22,18,24,65,18);
        intervals.title("Подробные записи",subtitle+" · границы в пределах отчётной недели");
        intervals.headings("Группа","Задача","Начало","Окончание","Длительность","Часы","Состояние записи","Описание задачи","ID записи");
        for(Session s:rows){
            Task t=data.task(s.taskId);long a=Math.max(from,s.start),b=Math.min(Math.min(to,s.finish(capturedAt)),capturedAt);
            intervals.row(text(data.group(t.groupId).name),text(t.name),date(a,zone),date(b,zone),time(b-a),hours(b-a),text(s.running()?"Идёт · снимок":"Остановлена"),text(t.note),number(s.id));
        }
        int[] timelineWidths=new int[27];Arrays.fill(timelineWidths,6);timelineWidths[0]=17;timelineWidths[1]=38;timelineWidths[26]=19;
        Sheet timeline=new Sheet("Лента недели",timelineWidths);timeline.freezeLabels=true;
        timeline.title("Лента недели · 24 часа",subtitle+" · в часовых ячейках — учтённые минуты; пустое будущее время не заполняется");
        String[] labels=new String[27];labels[0]="Дата";labels[1]="Занятие";for(int h=0;h<24;h++)labels[h+2]=String.format(Locale.ROOT,"%02d:00",h);labels[26]="Всего";timeline.headings(labels);
        List<Session> others=new ArrayList<>();if(filtered)for(Session s:data.sessions)if(!included(data.task(s.taskId),groupId,taskIds))others.add(s);
        for(int d=0;d<7;d++){
            LocalDate day=week.plusDays(d);long dayStart=TimeMath.start(day,zone),dayEnd=TimeMath.start(day.plusDays(1),zone);
            for(Task t:data.tasks)if(included(t,groupId,taskIds)){List<Session> ts=data.forTask(t.id);if(TimeMath.sum(ts,dayStart,dayEnd,capturedAt)>0)timelineRow(timeline,day,groupId==0?data.group(t.groupId).name+" / "+t.name:t.name,ts,zone,capturedAt,9);}
            if(filtered&&TimeMath.union(others,dayStart,dayEnd,capturedAt)>0)timelineRow(timeline,day,"Другое",others,zone,capturedAt,10);
            List<Session> gaps=new ArrayList<>();for(long[] gapRange:TimeMath.gaps(data.sessions,dayStart,dayEnd,capturedAt))gaps.add(new Session(0,0,gapRange[0],gapRange[1]));
            if(!gaps.isEmpty())timelineRow(timeline,day,"Не учтено",gaps,zone,capturedAt,11);
            if(dayStart>capturedAt)timelineRow(timeline,day,"Впереди",Collections.emptyList(),zone,capturedAt,11);
        }
        List<Sheet> sheets=Arrays.asList(overview,timeline,days,groups,tasks,intervals);
        try(ZipOutputStream zip=new ZipOutputStream(stream)){
            StringBuilder types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder workbook=new StringBuilder("<workbook xmlns=\"").append(NS).append("\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><bookViews><workbookView/></bookViews><sheets>");
            StringBuilder rels=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for(int i=0;i<sheets.size();i++){
                int id=i+1;types.append("<Override PartName=\"/xl/worksheets/sheet").append(id).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                workbook.append("<sheet name=\"").append(escape(sheets.get(i).name)).append("\" sheetId=\"").append(id).append("\" r:id=\"rId").append(id).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(id).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(id).append(".xml\"/>");
                entry(zip,"xl/worksheets/sheet"+id+".xml",sheets.get(i).xml());
            }
            types.append("</Types>");workbook.append("</sheets></workbook>");rels.append("<Relationship Id=\"rId").append(sheets.size()+1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entry(zip,"[Content_Types].xml",types.toString());entry(zip,"xl/workbook.xml",workbook.toString());entry(zip,"xl/_rels/workbook.xml.rels",rels.toString());
            entry(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/styles.xml",styles());
        }
    }
    private static void timelineRow(Sheet sheet,LocalDate day,String label,List<Session> sessions,ZoneId zone,long now,int style){
        Cell[] cells=new Cell[27];cells[0]=new Cell(ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),day),7);cells[1]=text(label);
        for(int h=0;h<24;h++){
            long start=day.atTime(h,0).atZone(zone).toInstant().toEpochMilli();
            long end=(h==23?day.plusDays(1).atStartOfDay(zone):day.atTime(h+1,0).atZone(zone)).toInstant().toEpochMilli();
            long minutes=TimeMath.union(sessions,start,end,now);
            cells[h+2]=minutes==0?text(""):new Cell(Math.round(minutes/6000d)/10d,style);
        }
        cells[26]=time(TimeMath.union(sessions,TimeMath.start(day,zone),TimeMath.start(day.plusDays(1),zone),now));sheet.row(cells);
    }
    private static void entry(ZipOutputStream z,String name,String xml)throws IOException { z.putNextEntry(new ZipEntry(name));z.write(xml.getBytes(StandardCharsets.UTF_8));z.closeEntry(); }
    private static String col(int n){StringBuilder b=new StringBuilder();do{b.insert(0,(char)('A'+n%26));n=n/26-1;}while(n>=0);return b.toString();}
    private static String escape(String s){return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String styles(){return "<styleSheet xmlns=\""+NS+"\">"
        +"<numFmts count=\"4\"><numFmt numFmtId=\"164\" formatCode=\"[h]:mm:ss\"/><numFmt numFmtId=\"165\" formatCode=\"dd.mm.yyyy hh:mm\"/><numFmt numFmtId=\"166\" formatCode=\"dd.mm.yyyy\"/><numFmt numFmtId=\"167\" formatCode=\"0.0%\"/></numFmts>"
        +"<fonts count=\"4\"><font><sz val=\"11\"/><color rgb=\"FF243A33\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"22\"/><color rgb=\"FF216B58\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font><font><sz val=\"10\"/><color rgb=\"FF68786E\"/><name val=\"Calibri\"/></font></fonts>"
        +"<fills count=\"6\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF216B58\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD2E7D6\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF1E3CF\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE6E8E3\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
        +"<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"13\">"
        +xf(0,0,0)+xf(0,1,0)+xf(0,2,2)+xf(164,0,0)+xf(2,0,0)+xf(165,0,0)+xf(167,0,0)+xf(166,0,0)+xf(0,3,0)+xf(0,0,3)+xf(0,0,4)+xf(0,0,5)+xf(1,0,0)
        +"</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";}
    private static String xf(int format,int font,int fill){return "<xf numFmtId=\""+format+"\" fontId=\""+font+"\" fillId=\""+fill+"\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" horizontal=\""+(format!=0||fill>2?"right":"left")+"\" indent=\""+(font==1?0:1)+"\" wrapText=\"1\"/></xf>";}
}
