package kz.atu.schedule;

import org.bson.Document;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

public class Lesson {
    public String id, groupId, teacherId, teacherName, subject, type, room, date, week, start, end;
    public long updatedAt;

    public Lesson() {}

    public Lesson(String id, String groupId, String teacherId, String teacherName, String subject,
                  String type, String room, String date, String start, String end) {
        this.id = id; this.groupId = groupId; this.teacherId = teacherId;
        this.teacherName = teacherName; this.subject = subject; this.type = type;
        this.room = room; this.date = date; this.week = weekOf(date);
        this.start = start; this.end = end;
        this.updatedAt = System.currentTimeMillis();
    }

    public static String weekOf(String date) {
        LocalDate d = LocalDate.parse(date);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W"
                + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public Document toDocument() {
        return new Document("_id", id).append("groupId", groupId).append("teacherId", teacherId)
                .append("teacherName", teacherName).append("subject", subject).append("type", type)
                .append("room", room).append("date", date).append("week", week)
                .append("start", start).append("end", end).append("updatedAt", updatedAt);
    }

    public static Lesson fromDocument(Document d) {
        Lesson l = new Lesson();
        l.id = d.getString("_id"); l.groupId = d.getString("groupId");
        l.teacherId = d.getString("teacherId"); l.teacherName = d.getString("teacherName");
        l.subject = d.getString("subject"); l.type = d.getString("type");
        l.room = d.getString("room"); l.date = d.getString("date");
        l.week = d.getString("week"); l.start = d.getString("start");
        l.end = d.getString("end"); l.updatedAt = d.getLong("updatedAt");
        return l;
    }

    @Override
    public String toString() {
        return date + " " + start + "-" + end + " | " + subject + " (" + type + ", "
                + teacherName + ", ауд. " + room + ")";
    }
}