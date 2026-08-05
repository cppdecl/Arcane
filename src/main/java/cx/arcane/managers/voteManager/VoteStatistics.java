package cx.arcane.managers.voteManager;

import cx.arcane.Arcane;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class VoteStatistics {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static File dataFile;
    private static ScheduledTask resetTask;

    static final AtomicLong totalVotesAllTime = new AtomicLong(0);
    static final AtomicLong totalVotesMonthly = new AtomicLong(0);
    static final AtomicLong totalVotesWeekly = new AtomicLong(0);
    static final AtomicLong totalVotesDaily = new AtomicLong(0);

    static final AtomicLong votePartyVotes = new AtomicLong(0);
    static final AtomicLong votePartyRequired = new AtomicLong(50);

    private static volatile int lastResetDay = -1;
    private static volatile int lastResetWeek = -1;
    private static volatile int lastResetMonth = -1;

    static void onEnable(File pluginDataFolder) {
        dataFile = new File(pluginDataFolder, "votes.json");
        load();
        checkRollovers();
        scheduleResetTask();
    }

    static void onDisable() {
        if (resetTask != null) resetTask.cancel();
        save();
    }

    private static void scheduleResetTask() {
        resetTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                Arcane.getPlugin(),
                task -> checkRollovers(),
                1, 1, TimeUnit.MINUTES
        );
    }

    static void checkRollovers() {
        LocalDate now = LocalDate.now(ZONE);
        int day = now.getDayOfYear();
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        int month = now.getMonthValue();

        if (lastResetDay != day) {
            lastResetDay = day;
            totalVotesDaily.set(0);
        }

        if (lastResetWeek != week) {
            lastResetWeek = week;
            totalVotesWeekly.set(0);
        }

        if (lastResetMonth != month) {
            lastResetMonth = month;
            totalVotesMonthly.set(0);
        }
    }

    private static void load() {
        if (!dataFile.exists()) {
            LocalDate now = LocalDate.now(ZONE);
            lastResetDay = now.getDayOfYear();
            lastResetWeek = now.get(WeekFields.ISO.weekOfWeekBasedYear());
            lastResetMonth = now.getMonthValue();
            return;
        }
        try {
            Map<String, Long> data = MAPPER.readValue(dataFile, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            totalVotesAllTime.set(data.getOrDefault("totalVotesAllTime", 0L));
            totalVotesMonthly.set(data.getOrDefault("totalVotesMonthly", 0L));
            totalVotesWeekly.set(data.getOrDefault("totalVotesWeekly", 0L));
            totalVotesDaily.set(data.getOrDefault("totalVotesDaily", 0L));
            votePartyVotes.set(data.getOrDefault("votePartyVotes", 0L));
            votePartyRequired.set(data.getOrDefault("votePartyRequired", 50L));
            lastResetDay = data.getOrDefault("lastResetDay", -1L).intValue();
            lastResetWeek = data.getOrDefault("lastResetWeek", -1L).intValue();
            lastResetMonth = data.getOrDefault("lastResetMonth", -1L).intValue();
        } catch (Exception e) {}
    }

    static void save() {
        try {
            dataFile.getParentFile().mkdirs();
            Map<String, Long> data = new LinkedHashMap<>();
            data.put("totalVotesAllTime", totalVotesAllTime.get());
            data.put("totalVotesMonthly", totalVotesMonthly.get());
            data.put("totalVotesWeekly", totalVotesWeekly.get());
            data.put("totalVotesDaily", totalVotesDaily.get());
            data.put("votePartyVotes", votePartyVotes.get());
            data.put("votePartyRequired", votePartyRequired.get());
            data.put("lastResetDay", (long) lastResetDay);
            data.put("lastResetWeek", (long) lastResetWeek);
            data.put("lastResetMonth", (long) lastResetMonth);
            MAPPER.writeValue(dataFile, data);
        } catch (Exception e) {}
    }

    public static long getTotalVotesAllTime() { return totalVotesAllTime.get(); }
    public static long getTotalVotesMonthly() { return totalVotesMonthly.get(); }
    public static long getTotalVotesWeekly() { return totalVotesWeekly.get(); }
    public static long getTotalVotesDaily() { return totalVotesDaily.get(); }
    public static long getVotePartyVotes() { return votePartyVotes.get(); }
    public static long getVotePartyRequired() { return votePartyRequired.get(); }
    public static void setVotePartyRequired(long value) { votePartyRequired.set(value); }
}