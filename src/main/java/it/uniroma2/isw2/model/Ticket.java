package it.uniroma2.isw2.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Ticket {
    public final String key;
    public final String created;
    public final String resolutionDate;
    /** Affected versions (version names) */
    public final List<String> affectedVersions;
    /** Fix versions (version names) */
    public final List<String> fixVersions;
    private final List<CommitInfo> fixCommits = new ArrayList<>();
    private static final DateTimeFormatter JIRA_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");


    public Ticket(String key, String created, String resolutionDate,
                  List<String> affectedVersions, List<String> fixVersions) {
        this.key              = key;
        this.created          = created;
        this.resolutionDate   = resolutionDate;
        this.affectedVersions = affectedVersions;
        this.fixVersions      = fixVersions;
    }

    public static class CommitInfo {
        public final String hash;
        public final long   timestamp;
        public CommitInfo(String hash, long ts) {
            this.hash = hash; this.timestamp = ts;
        }
    }

    public void addFixCommit(String hash, long timestamp) {
        fixCommits.add(new CommitInfo(hash, timestamp));
    }

    public List<CommitInfo> getFixCommits() { return fixCommits; }

    public long getCreationTime() {
        return OffsetDateTime.parse(created, JIRA_FORMATTER)
                .toEpochSecond();
    }

    public long getResolutionTime() {
        return OffsetDateTime.parse(resolutionDate, JIRA_FORMATTER)
                .toEpochSecond();
    }
    @Override public String toString() {
        return key + " AV=" + affectedVersions + " FV=" + fixVersions;
    }
}
