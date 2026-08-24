package it.uniroma2.isw2.refactoring;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RefactoringImpactService {


    public List<Map<String, String>> compute(
            List<Map<String, String>> refactoredRows,
            List<Map<String, String>> correlations) {

        List<Map<String, String>> results = new ArrayList<>();

        /*
         * Process each class independently.
         *
         * Example:
         *
         * LRUMap  -> C0, C1, C2, C3
         * Filters -> C0, C1, C2, C3, C4
         */
        Map<String, List<Map<String, String>>> byClass =
                groupByClass(refactoredRows);

        for (Map.Entry<String, List<Map<String, String>>> entry
                : byClass.entrySet()) {

            String className = entry.getKey();

            List<Map<String, String>> classRows =
                    entry.getValue();

            /*
             * Find C0.
             */
            Map<String, String> c0 = findVersion(classRows, "C0");

            if (c0 == null) {
                continue;
            }

            /*
             * For every feature having a correlation with bugginess.
             */
            for (Map<String, String> correlation : correlations) {

                String feature =
                        correlation.get("Feature");

                String correlationString =
                        correlation.get("Defectiveness");

                if (feature == null
                        || correlationString == null) {
                    continue;
                }

                double correlationValue;

                try {
                    correlationValue =
                            Double.parseDouble(correlationString);
                } catch (NumberFormatException e) {
                    continue;
                }

                /*
                 * Only numeric features can be compared.
                 */
                if (!isNumeric(c0.get(feature))) {
                    continue;
                }

                Map<String, String> result =
                        new LinkedHashMap<>();

                result.put("class_name", className);
                result.put("feature", feature);

                result.put(
                        "correlation",
                        String.format(
                                java.util.Locale.US,
                                "%.4f",
                                correlationValue
                        )
                );

                /*
                 * Positive / negative correlation.
                 */
                if (correlationValue > 0) {
                    result.put(
                            "correlation_sign",
                            "POSITIVE"
                    );
                } else if (correlationValue < 0) {
                    result.put(
                            "correlation_sign",
                            "NEGATIVE"
                    );
                } else {
                    result.put(
                            "correlation_sign",
                            "NONE"
                    );
                }

                /*
                 * C0 value.
                 */
                double c0Value =
                        Double.parseDouble(c0.get(feature));

                result.put(
                        "C0",
                        format(c0Value)
                );

                /*
                 * Compare every refactored version
                 * against C0.
                 */
                for (Map<String, String> row : classRows) {

                    String version =
                            row.get("version");

                    if ("C0".equals(version)) {
                        continue;
                    }

                    String valueString =
                            row.get(feature);

                    if (!isNumeric(valueString)) {
                        continue;
                    }

                    double value =
                            Double.parseDouble(valueString);

                    double delta =
                            value - c0Value;

                    double relativeChange = 0.0;

                    if (c0Value != 0.0) {
                        relativeChange = (value - c0Value) / c0Value;
                    }

                    result.put(
                            version,
                            format(value)
                    );

                    result.put(
                            "delta_" + version,
                            format(delta)
                    );

                    result.put(
                            "relative_change_" + version,
                            format(relativeChange)
                    );

                    /*
                     * Determine whether the change is potentially
                     * good or bad according to the correlation.
                     */
                    String impact =
                            determineImpact(
                                    correlationValue,
                                    delta
                            );

                    result.put(
                            "impact_" + version,
                            impact
                    );
                }

                results.add(result);
            }
        }

        return results;
    }

    /**
     * Groups rows by class_name.
     */
    private Map<String, List<Map<String, String>>> groupByClass(
            List<Map<String, String>> rows) {

        Map<String, List<Map<String, String>>> grouped =
                new LinkedHashMap<>();

        for (Map<String, String> row : rows) {

            String className =
                    row.get("class_name");

            if (className == null) {
                continue;
            }

            grouped
                    .computeIfAbsent(
                            className,
                            k -> new ArrayList<>()
                    )
                    .add(row);
        }

        return grouped;
    }

    /**
     * Finds a specific version of a class.
     */
    private Map<String, String> findVersion(
            List<Map<String, String>> rows,
            String version) {

        for (Map<String, String> row : rows) {

            if (version.equals(row.get("version"))) {
                return row;
            }
        }

        return null;
    }

    /**
     * Determines whether a feature change is potentially
     * positive or negative for maintainability.
     *
     * Positive correlation:
     *
     *     feature increases -> potentially worse
     *     feature decreases -> potentially better
     *
     * Negative correlation:
     *
     *     feature increases -> potentially better
     *     feature decreases -> potentially worse
     */
    private String determineImpact(
            double correlation,
            double delta) {

        if (delta == 0.0) {
            return "UNCHANGED";
        }

        if (correlation > 0) {

            if (delta > 0) {
                return "POTENTIALLY_WORSE";
            } else {
                return "POTENTIALLY_BETTER";
            }
        }

        if (correlation < 0) {

            if (delta > 0) {
                return "POTENTIALLY_BETTER";
            } else {
                return "POTENTIALLY_WORSE";
            }
        }

        return "NO_CORRELATION";
    }

    private boolean isNumeric(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String format(double value) {

        return String.format(
                java.util.Locale.US,
                "%.2f",
                value
        );
    }
}
