package kz.atu.schedule;

import java.time.LocalDate;
import java.util.Map;

public class SeedData {
    public static void main(String[] args) {
        LessonRepository repo = new LessonRepository(Config.MONGO_URI, Config.MONGO_DB);
        CacheService cache = new CacheService(Config.REDIS_HOST, Config.REDIS_PORT);
        repo.deleteAll();
        cache.flushAll();
        repo.ensureIndexes();

        Map<String, String> teachers = Map.of(
                "T01", "Рашидинов Д.Р.", "T02", "Сексембаева Н.С.",
                "T03", "Титова Н.В.", "T04", "Казангапова Б.А.");

        final String NOSQL = "Проектирование и применение NoSQL";
        final String PHP = "Программирование на PHP";
        final String VIS = "Визуальное программирование";
        final String NET = "Компьютерные сети";
        final String SEC = "Основы информационной безопасности";
        final String OR = "Основы исследования операций";

        // день (0=Пн), начало, конец, предмет, преподаватель, тип, аудитория
        String[][] plan = {
                {"0", "12:00", "12:50", NOSQL, "T01", "лекция", "501-Б"},
                {"0", "13:05", "13:55", NOSQL, "T01", "лабораторная", "401-С"},
                {"0", "14:10", "15:00", NOSQL, "T01", "лабораторная", "401-С"},
                {"0", "15:15", "16:05", PHP, "T01", "лабораторная", "404-С"},
                {"0", "16:20", "17:10", PHP, "T01", "лабораторная", "404-С"},

                {"1", "12:00", "12:50", VIS, "T01", "лекция", "501-Б"},
                {"1", "13:05", "13:55", VIS, "T01", "лабораторная", "404-С"},
                {"1", "14:10", "15:00", VIS, "T01", "лабораторная", "404-С"},
                {"1", "16:10", "17:00", NET, "T02", "лабораторная", "510-1"},
                {"1", "17:10", "18:00", NET, "T02", "лабораторная", "510-1"},

                {"2", "18:30", "19:20", NET, "T03", "лекция", "онлайн"},

                {"3", "08:30", "09:20", PHP, "T01", "лекция", "501-Б"},
                {"3", "09:35", "10:25", SEC, "T04", "лекция", "501-Б"},
                {"3", "10:40", "11:30", SEC, "T04", "лабораторная", "316-Б"},
                {"3", "12:00", "12:50", SEC, "T04", "лабораторная", "316-Б"},

                {"4", "15:10", "16:00", OR, "T04", "лекция", "504-С"},
                {"4", "16:10", "17:00", OR, "T04", "лабораторная", "503-С"},
                {"4", "17:10", "18:00", OR, "T04", "лабораторная", "503-С"},
        };

        LocalDate firstMonday = LocalDate.of(2026, 10, 19); // неделя 2026-W43
        int n = 0;
        for (int week = 0; week < 3; week++) {
            for (String[] p : plan) {
                String date = firstMonday.plusWeeks(week).plusDays(Integer.parseInt(p[0])).toString();
                String id = String.format("L%03d", ++n);
                repo.insert(new Lesson(id, Config.GROUP, p[4], teachers.get(p[4]),
                        p[3], p[5], p[6], date, p[1], p[2]));
            }
        }
        System.out.println("Загружено занятий: " + repo.count() + " (группа " + Config.GROUP + ")");
    }
}