package kz.atu.schedule;

import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class ScheduleServiceTest {
    static final String G = "TEST-GROUP", WEEK = "2026-W43";
    static LessonRepository repo;
    static CacheService cache;
    ScheduleService service;

    @BeforeAll
    static void init() {
        repo = new LessonRepository(Config.MONGO_URI, "university_test");
        cache = new CacheService(Config.REDIS_HOST, Config.REDIS_PORT, 1);
    }

    @BeforeEach
    void reset() {
        repo.deleteAll();
        cache.flushAll();
        service = new ScheduleService(repo, cache);
        service.setTtl(30);
        repo.insert(new Lesson("T01", G, "P1", "Преподаватель 1", "NoSQL", "лекция", "101", "2026-10-19", "09:00", "10:30"));
        repo.insert(new Lesson("T02", G, "P1", "Преподаватель 1", "Java", "лабораторная", "102", "2026-10-19", "10:40", "12:10"));
    }

    @AfterAll
    static void cleanup() { repo.deleteAll(); cache.flushAll(); }

    @Test
    void firstRequestIsMissSecondIsHit() {
        service.getGroupSchedule(G, WEEK);
        assertEquals(1, cache.counter("stats:misses"));
        service.getGroupSchedule(G, WEEK);
        assertEquals(1, cache.counter("stats:hits"));
        assertTrue(cache.exists(ScheduleService.groupKey(G, WEEK)));
    }

    @Test
    void updateInvalidatesCacheAndReturnsFreshData() {
        service.getGroupSchedule(G, WEEK);
        service.updateLesson("T01", Map.of("room", "999"));
        assertFalse(cache.exists(ScheduleService.groupKey(G, WEEK)));
        assertEquals("999", service.getGroupSchedule(G, WEEK).get(0).room);
    }

    @Test
    void keyExpiresAfterTtl() throws Exception {
        service.setTtl(1);
        service.getGroupSchedule(G, WEEK);
        assertTrue(cache.exists(ScheduleService.groupKey(G, WEEK)));
        Thread.sleep(1500);
        assertFalse(cache.exists(ScheduleService.groupKey(G, WEEK)));
    }

    @Test
    void changingTeacherInvalidatesBothTeachers() {
        service.getTeacherSchedule("P1", WEEK);
        service.getTeacherSchedule("P2", WEEK);
        service.updateLesson("T01", Map.of("teacherId", "P2"));
        assertFalse(cache.exists(ScheduleService.teacherKey("P1", WEEK)));
        assertFalse(cache.exists(ScheduleService.teacherKey("P2", WEEK)));
    }

    @Test
    void withoutInvalidationDataIsStale() {
        service.setInvalidation(false);
        service.getGroupSchedule(G, WEEK);
        service.updateLesson("T01", Map.of("room", "999"));
        assertEquals("101", service.getGroupSchedule(G, WEEK).get(0).room); // устаревшее значение
    }

    @Test
    void fallbackToMongoWhenRedisIsDown() {
        CacheService dead = new CacheService("localhost", 6390, 0); // порт, на котором Redis нет
        ScheduleService s = new ScheduleService(repo, dead);
        List<Lesson> res = s.getGroupSchedule(G, WEEK);
        assertEquals(2, res.size());
    }

    @Test
    void conflictDetection() {
        List<Lesson> c = repo.findConflicts("101", "PX", "2026-10-19", "10:00", "11:00", "NEW");
        assertEquals(1, c.size());
        assertEquals("T01", c.get(0).id);
    }
}