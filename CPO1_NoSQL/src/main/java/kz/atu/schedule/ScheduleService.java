package kz.atu.schedule;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Supplier;

public class ScheduleService {
    private static final Type LIST = new TypeToken<List<Lesson>>() {}.getType();
    private final LessonRepository repo;
    private final CacheService cache;
    private final Gson gson = new Gson();
    private int ttl = Config.CACHE_TTL;
    private boolean invalidation = true; // false нужен для эксперимента «только TTL»

    public ScheduleService(LessonRepository repo, CacheService cache) {
        this.repo = repo; this.cache = cache;
    }
    public void setTtl(int ttl) { this.ttl = ttl; }
    public void setInvalidation(boolean on) { this.invalidation = on; }

    public static String groupKey(String g, String week) { return "schedule:group:" + g + ":" + week; }
    public static String teacherKey(String t, String week) { return "schedule:teacher:" + t + ":" + week; }
    public static String groupDayKey(String g, String date) { return "schedule:group:" + g + ":day:" + date; }

    // ---------- чтение (cache-aside) ----------
    public List<Lesson> getGroupSchedule(String groupId, String week) {
        return cached(groupKey(groupId, week), "tag:group:" + groupId,
                () -> repo.findByGroupWeek(groupId, week));
    }
    public List<Lesson> getGroupDay(String groupId, String date) {
        return cached(groupDayKey(groupId, date), "tag:group:" + groupId,
                () -> repo.findByGroupDate(groupId, date));
    }
    public List<Lesson> getTeacherSchedule(String teacherId, String week) {
        return cached(teacherKey(teacherId, week), "tag:teacher:" + teacherId,
                () -> repo.findByTeacherWeek(teacherId, week));
    }

    private List<Lesson> cached(String key, String tag, Supplier<List<Lesson>> loader) {
        String json = cache.get(key);
        if (json != null) {
            cache.incr("stats:hits");
            return gson.fromJson(json, LIST);
        }
        cache.incr("stats:misses");
        List<Lesson> data = loader.get();
        cache.setex(key, ttl, gson.toJson(data));
        cache.sadd(tag, key);
        return data;
    }

    // ---------- запись: сначала БД, потом инвалидация ----------
    public void createLesson(Lesson l) {
        repo.insert(l);
        invalidate(l);
    }
    public Lesson updateLesson(String id, Map<String, Object> changes) {
        Lesson old = repo.findById(id);
        Lesson now = repo.update(id, changes);
        invalidate(old, now); // и старая, и новая группа/преподаватель
        return now;
    }
    public void deleteLesson(String id) {
        Lesson old = repo.findById(id);
        repo.delete(id);
        invalidate(old);
    }

    private void invalidate(Lesson... lessons) {
        if (!invalidation) return;
        for (Lesson l : lessons) {
            if (l == null) continue;
            cache.invalidateTag("tag:group:" + l.groupId);
            cache.invalidateTag("tag:teacher:" + l.teacherId);
        }
    }

    // ---------- статистика ----------
    public double hitRatio() {
        long h = cache.counter("stats:hits"), m = cache.counter("stats:misses");
        return (h + m) == 0 ? 0 : 100.0 * h / (h + m);
    }
    public String stats() {
        return String.format("hits=%d, misses=%d, hit ratio=%.1f%%",
                cache.counter("stats:hits"), cache.counter("stats:misses"), hitRatio());
    }
}