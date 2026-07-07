package it.uniroma2.isw2.retrievers;

import it.uniroma2.isw2.model.Ticket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves closed/resolved bug ticket IDs from Apache JIRA.
 * Based on the provided RetrieveTicketsID.java skeleton.
 */
public class RetrieveBugTickets {


    /**
     * Fetches all bug tickets (closed/resolved + fixed) for the project.
     * Mirrors the paging logic from the provided RetrieveTicketsID.java.
     */
    public static List<Ticket> fetchAll(String projName) throws IOException, JSONException {
        List<Ticket> result = new ArrayList<>();
        int i = 0, total = 1;

        do {
            int j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql="
                    + "project=%22" + projName + "%22"
                    + "AND%22issueType%22=%22Bug%22"
                    + "AND(%22status%22=%22closed%22OR%22status%22=%22resolved%22)"
                    + "AND%22resolution%22=%22fixed%22"
                    + "&fields=key,resolutiondate,versions,fixVersions,created"
                    + "&startAt=" + i
                    + "&maxResults=" + j;

            JSONObject json   = readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");

            for (; i < total && i < j; i++) {
                JSONObject issue  = issues.getJSONObject(i % 1000);
                JSONObject fields = issue.getJSONObject("fields");

                String key            = issue.getString("key");
                String created        = fields.optString("created", "");
                String resolutionDate = fields.optString("resolutiondate", "");

                List<String> av = extractVersionNames(fields.optJSONArray("versions"));
                List<String> fv = extractVersionNames(fields.optJSONArray("fixVersions"));

                result.add(new Ticket(key, created, resolutionDate, av, fv));
            }
        } while (i < total);

        return result;
    }

    private static List<String> extractVersionNames(JSONArray arr) throws JSONException {
        List<String> names = new ArrayList<>();
        if (arr == null) return names;
        for (int k = 0; k < arr.length(); k++) {
            String name = arr.getJSONObject(k).optString("name", "");
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    // ── JSON helpers ────────────────────────────────────────────────────────────

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(is, Charset.forName("UTF-8")));
            return new JSONObject(readAll(rd));
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

