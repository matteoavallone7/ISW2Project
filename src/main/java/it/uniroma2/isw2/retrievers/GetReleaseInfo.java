package it.uniroma2.isw2.retrievers;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class GetReleaseInfo {

    public static HashMap<LocalDateTime, String> releaseNames = new HashMap<>();
    public static HashMap<LocalDateTime, String> releaseID    = new HashMap<>();
    public static ArrayList<LocalDateTime> releases     = new ArrayList<>();
    public static Integer numVersions = 0;

    /** Populate releases, releaseNames, releaseID for the given project. */
    public static void load(String projName) throws IOException, JSONException {
        releases.clear();
        releaseNames.clear();
        releaseID.clear();

        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        for (int i = 0; i < versions.length(); i++) {
            JSONObject v = versions.getJSONObject(i);
            if (v.has("releaseDate")) {
                String name = v.optString("name", "");
                String id   = v.optString("id",   "");
                addRelease(v.getString("releaseDate"), name, id);
            }
        }

        // Sort releases by date ascending
        releases.sort(Comparator.naturalOrder());
        numVersions = releases.size();
    }

    public static void addRelease(String strDate, String name, String id) {
        LocalDate date     = LocalDate.parse(strDate);
        LocalDateTime dateTime = date.atStartOfDay();
        if (!releases.contains(dateTime)) {
            releases.add(dateTime);
        }
        releaseNames.put(dateTime, name);
        releaseID.put(dateTime, id);
    }

    // ── JSON helpers (identical pattern to provided code) ──────────────────────

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        } finally {
            is.close();
        }
    }

    public static JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            return new JSONArray(jsonText);
        } finally {
            is.close();
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) sb.append((char) cp);
        return sb.toString();
    }
}
