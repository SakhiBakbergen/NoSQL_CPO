package kz.atu.schedule;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;

public class StaleExperiment {
    static final String G = Config.GROUP, WEEK = Config.WEEK, LESSON = "L001";
    static LessonRepository repo;
    static CacheService cache;
    static ScheduleService service;

    public static void main(String[] args) throws Exception {
        repo = new LessonRepository(Config.MONGO_URI, Config.MONGO_DB);
        cache = new CacheService(Config.REDIS_HOST, Config.REDIS_PORT);
        service = new ScheduleService(repo, cache);

        System.out.println("=== Эксперимент 1: окно устаревания (TTL = 3 с) ===");
        long a = staleWindow(false, 3);
        long b = staleWindow(true, 3);
        System.out.println("Только TTL:        данные устарели на " + a + " мс");
        System.out.println("TTL + инвалидация: данные устарели на " + b + " мс");

        System.out.println("\n=== Эксперимент 2: разные TTL (60 чтений по 100 мс, запись на 20-м чтении) ===");
        System.out.printf("%-6s | %-18s | %-9s | %-12s%n", "TTL,с", "режим", "hit ratio", "устаревших чтений");
        for (int ttl : new int[]{2, 5, 10}) {
            for (boolean inv : new boolean[]{false, true}) simulate(ttl, inv);
        }

        System.out.println("\n=== Эксперимент 3: race condition (воспроизведение) ===");
        race();
        System.out.println("\nГотово.");
    }

    /** Сколько мс клиент видит старую аудиторию после изменения в MongoDB. */
    static long staleWindow(boolean invalidation, int ttl) throws Exception {
        prepare(ttl, invalidation);
        String orig = repo.findById(LESSON).room;
        service.getGroupSchedule(G, WEEK);                         // кэш заполнен
        service.updateLesson(LESSON, Map.of("room", "NEW-ROOM"));  // изменение
        long t0 = System.currentTimeMillis();
        while (!service.getGroupSchedule(G, WEEK).get(0).room.equals("NEW-ROOM")
                && System.currentTimeMillis() - t0 < ttl * 1000L + 3000) Thread.sleep(100);
        long window = System.currentTimeMillis() - t0;
        restore(orig);
        return window;
    }

    static void simulate(int ttl, boolean invalidation) throws Exception {
        prepare(ttl, invalidation);
        String orig = repo.findById(LESSON).room;
        int stale = 0;
        for (int i = 0; i < 60; i++) {
            if (i == 20) service.updateLesson(LESSON, Map.of("room", "NEW-ROOM"));
            String room = service.getGroupSchedule(G, WEEK).get(0).room;
            if (i >= 20 && !room.equals("NEW-ROOM")) stale++;
            Thread.sleep(100);
        }
        System.out.printf("%-6d | %-18s | %8.1f%% | %d%n", ttl,
                invalidation ? "TTL + инвалидация" : "только TTL", service.hitRatio(), stale);
        restore(orig);
    }

    /** Гонка: читатель успевает положить в кэш старые данные уже после инвалидации. */
    static void race() throws Exception {
        prepare(2, true);
        String orig = repo.findById(LESSON).room;
        Gson gson = new Gson();
        String key = ScheduleService.groupKey(G, WEEK);

        List<Lesson> old = repo.findByGroupWeek(G, WEEK);                 // 1. читатель: miss, прочитал старое из БД
        service.updateLesson(LESSON, Map.of("room", "RACE"));             // 2. писатель: обновил БД и сбросил кэш
        cache.setex(key, 2, gson.toJson(old));                            // 3. читатель: записал СТАРОЕ в кэш
        cache.sadd("tag:group:" + G, key);

        System.out.println("В MongoDB: RACE, из кэша читаем: " + service.getGroupSchedule(G, WEEK).get(0).room
                + "  <- устаревшие данные после гонки");
        Thread.sleep(2500);
        System.out.println("Через 2.5 с (TTL истёк): " + service.getGroupSchedule(G, WEEK).get(0).room
                + "  <- TTL исправил ситуацию");
        System.out.println("Вывод: короткий TTL ограничивает ущерб от гонки; можно также сравнивать updatedAt.");
        restore(orig);
    }

    static void prepare(int ttl, boolean invalidation) {
        cache.flushAll();
        service.setTtl(ttl);
        service.setInvalidation(invalidation);
    }

    static void restore(String room) {
        service.setInvalidation(true);
        service.updateLesson(LESSON, Map.of("room", room));
        cache.flushAll();
    }
}