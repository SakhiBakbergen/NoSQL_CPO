package kz.atu.schedule;

import com.mongodb.client.*;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.*;

public class LessonRepository {
    private final MongoCollection<Document> col;

    public LessonRepository(String uri, String dbName) {
        col = MongoClients.create(uri).getDatabase(dbName).getCollection("lessons");
    }

    public void ensureIndexes() {
        col.createIndex(Indexes.ascending("groupId", "week"));
        col.createIndex(Indexes.ascending("groupId", "date"));
        col.createIndex(Indexes.ascending("teacherId", "week"));
        col.createIndex(Indexes.ascending("room", "date", "start"));
    }

    // ---------- CRUD ----------
    public void insert(Lesson l) { col.insertOne(l.toDocument()); }
    public void deleteAll() { col.deleteMany(new Document()); }
    public long count() { return col.countDocuments(); }
    public void delete(String id) { col.deleteOne(Filters.eq("_id", id)); }

    public Lesson findById(String id) {
        Document d = col.find(Filters.eq("_id", id)).first();
        return d == null ? null : Lesson.fromDocument(d);
    }

    public Lesson update(String id, Map<String, Object> changes) {
        Document set = new Document(changes);
        if (changes.containsKey("date")) set.put("week", Lesson.weekOf((String) changes.get("date")));
        set.put("updatedAt", System.currentTimeMillis());
        col.updateOne(Filters.eq("_id", id), new Document("$set", set));
        return findById(id);
    }

    // ---------- выборки ----------
    private List<Lesson> find(Bson filter) {
        List<Lesson> res = new ArrayList<>();
        for (Document d : col.find(filter).sort(Sorts.ascending("date", "start")))
            res.add(Lesson.fromDocument(d));
        return res;
    }

    public List<Lesson> findByGroupWeek(String g, String week) {
        return find(Filters.and(Filters.eq("groupId", g), Filters.eq("week", week)));
    }
    public List<Lesson> findByGroupDate(String g, String date) {
        return find(Filters.and(Filters.eq("groupId", g), Filters.eq("date", date)));
    }
    public List<Lesson> findByTeacherWeek(String t, String week) {
        return find(Filters.and(Filters.eq("teacherId", t), Filters.eq("week", week)));
    }
    public List<Lesson> findByRoom(String room, String date) {
        return find(Filters.and(Filters.eq("room", room), Filters.eq("date", date)));
    }
    /** Занятия, пересекающиеся по времени и по аудитории или преподавателю. */
    public List<Lesson> findConflicts(String room, String teacherId, String date,
                                      String start, String end, String excludeId) {
        return find(Filters.and(
                Filters.eq("date", date), Filters.ne("_id", excludeId),
                Filters.lt("start", end), Filters.gt("end", start),
                Filters.or(Filters.eq("room", room), Filters.eq("teacherId", teacherId))));
    }

    // ---------- агрегации ----------
    public List<Document> countBySubject(String groupId, String week) {
        return col.aggregate(List.of(
                Aggregates.match(Filters.and(Filters.eq("groupId", groupId), Filters.eq("week", week))),
                Aggregates.group("$subject", Accumulators.sum("count", 1)),
                Aggregates.sort(Sorts.orderBy(Sorts.descending("count"), Sorts.ascending("_id")))
        )).into(new ArrayList<>());
    }

    public List<Document> workloadByTeacher(String week) {
        return col.aggregate(List.of(
                Aggregates.match(Filters.eq("week", week)),
                Aggregates.group("$teacherName", Accumulators.sum("count", 1)),
                Aggregates.sort(Sorts.orderBy(Sorts.descending("count"), Sorts.ascending("_id")))
        )).into(new ArrayList<>());
    }
}