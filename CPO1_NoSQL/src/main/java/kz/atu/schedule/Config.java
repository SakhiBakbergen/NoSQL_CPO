package kz.atu.schedule;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv env = Dotenv.configure().ignoreIfMissing().load();

    public static final String GROUP = "ИС 24-22";
    public static final String WEEK  = "2026-W43";
    public static final String MONGO_URI = env.get("MONGO_URI", "mongodb://localhost:27017");
    public static final String MONGO_DB  = env.get("MONGO_DB", "university");
    public static final String REDIS_HOST = env.get("REDIS_HOST", "localhost");
    public static final int REDIS_PORT = Integer.parseInt(env.get("REDIS_PORT", "6379"));
    public static final int CACHE_TTL = Integer.parseInt(env.get("CACHE_TTL_SECONDS", "60"));
}