package kz.atu.schedule;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Main {
    public static void main(String[] args) {
        LessonRepository repo = new LessonRepository(Config.MONGO_URI, Config.MONGO_DB);
        CacheService cache = new CacheService(Config.REDIS_HOST, Config.REDIS_PORT);
        ScheduleService service = new ScheduleService(repo, cache);
        cache.flushAll();
        String g = Config.GROUP, week = Config.WEEK;

        System.out.println("=== Чтение: cache-aside ===");
        timed("1-й запрос (miss, MongoDB)", () -> service.getGroupSchedule(g, week));
        timed("2-й запрос (hit, Redis)   ", () -> service.getGroupSchedule(g, week));

        System.out.println("\n=== UPDATE: смена аудитории L001 ===");
        service.updateLesson("L001", Map.of("room", "999"));
        List<Lesson> after = timed("Запрос после изменения (miss)", () -> service.getGroupSchedule(g, week));
        System.out.println("Первое занятие: " + after.get(0));

        System.out.println("\n=== CREATE: новое занятие L900 ===");
        service.createLesson(new Lesson("L900", g, "T01", "Рашидинов Д.Р.", "Консультация",
                "лекция", "501-Б", "2026-10-21", "10:00", "10:50"));
        System.out.println("Занятий в среду: " + service.getGroupDay(g, "2026-10-21").size());

        System.out.println("\n=== DELETE: удаление L900 ===");
        service.deleteLesson("L900");
        System.out.println("Занятий в среду: " + service.getGroupDay(g, "2026-10-21").size());

        service.updateLesson("L001", Map.of("room", "501-Б")); // вернуть исходное значение
        System.out.println("\n" + service.stats());
    }

    private static <T> T timed(String label, Supplier<T> s) {
        long t = System.nanoTime();
        T r = s.get();
        System.out.printf("%s: %.2f мс%n", label, (System.nanoTime() - t) / 1e6);
        return r;
    }
}