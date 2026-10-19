package kz.atu.schedule;

import org.bson.Document;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;

public class DemoQueries {
    static LessonRepository repo;
    static ScheduleService service;

    public static void main(String[] args) {
        repo = new LessonRepository(Config.MONGO_URI, Config.MONGO_DB);
        CacheService cache = new CacheService(Config.REDIS_HOST, Config.REDIS_PORT);
        service = new ScheduleService(repo, cache);
        cache.flushAll();
        String g = Config.GROUP, week = Config.WEEK;

        show("Q1. Расписание группы на неделю (miss, MongoDB)", () -> service.getGroupSchedule(g, week));
        show("Q2. То же самое повторно (hit, Redis)", () -> service.getGroupSchedule(g, week));
        show("Q3. Расписание группы на день (понедельник 2026-10-19)", () -> service.getGroupDay(g, "2026-10-19"));
        show("Q4. Расписание преподавателя T01 (Рашидинов Д.Р.) на неделю", () -> service.getTeacherSchedule("T01", week));
        show("Q5. Занятость аудитории 404-С во вторник 2026-10-20", () -> repo.findByRoom("404-С", "2026-10-20"));
        show("Q6. Проверка конфликта: аудитория 401-С, пн 13:00-14:00",
                () -> repo.findConflicts("401-С", "T99", "2026-10-19", "13:00", "14:00", "NEW"));

        // Нетривиальный 1: агрегация $match + $group + $sort
        show("Q7 (нетривиальный). Число занятий по предметам за неделю",
                () -> toLines(repo.countBySubject(g, week), "занятий"));

        // Нетривиальный 2: нагрузка преподавателей по всем группам
        show("Q8 (нетривиальный). Нагрузка преподавателей за неделю",
                () -> toLines(repo.workloadByTeacher(week), "занятий"));

        show("Q9. «Окна» группы в понедельник между занятиями", DemoQueries::gaps);

        System.out.println("\nСтатистика кэша: " + service.stats());
    }

    private static List<String> toLines(List<Document> docs, String unit) {
        return docs.stream().map(d -> d.getString("_id") + ": " + d.getInteger("count") + " " + unit).toList();
    }

    private static List<String> gaps() {
        List<Lesson> day = repo.findByGroupDate(Config.GROUP, "2026-10-19");
        java.util.ArrayList<String> res = new java.util.ArrayList<>();
        for (int i = 1; i < day.size(); i++) {
            long min = Duration.between(LocalTime.parse(day.get(i - 1).end), LocalTime.parse(day.get(i).start)).toMinutes();
            if (min > 0) res.add(day.get(i - 1).end + " - " + day.get(i).start + ": перерыв " + min + " мин");
        }
        return res;
    }

    private static <T> void show(String title, Supplier<List<T>> query) {
        long t = System.nanoTime();
        List<T> result = query.get();
        double ms = (System.nanoTime() - t) / 1e6;
        System.out.printf("%n%s  [%.2f мс, строк: %d]%n", title, ms, result.size());
        for (T r : result) System.out.println("   " + r);
    }
}