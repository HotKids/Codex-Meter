package dev.bennett.codexmeter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small SemVer-compatible comparator for GitHub release tags. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final int MIN_NUMERIC_PARTS = 2;
    private static final int MAX_NUMERIC_PARTS = 4;
    /** Short versions such as {@code 2.1} are padded to {@code 2.1.0}. */
    private static final int PADDED_NUMERIC_PARTS = 3;

    private final List<BigInteger> numbers;
    /** Lower-case prerelease suffix without the leading '-', or empty for a stable release. */
    private final String prerelease;
    private final String normalized;

    private ReleaseVersion(List<BigInteger> numbers, String prerelease, String normalized) {
        this.numbers = numbers;
        this.prerelease = prerelease;
        this.normalized = normalized;
    }

    public static ReleaseVersion parse(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("v") || candidate.startsWith("V")) {
            candidate = candidate.substring(1);
        }
        int buildIndex = candidate.indexOf('+');
        if (buildIndex >= 0) {
            candidate = candidate.substring(0, buildIndex);
        }
        String prerelease = "";
        int prereleaseIndex = candidate.indexOf('-');
        if (prereleaseIndex >= 0) {
            prerelease = candidate.substring(prereleaseIndex + 1);
            candidate = candidate.substring(0, prereleaseIndex);
        }
        String[] parts = candidate.split("\\.", -1);
        if (parts.length < MIN_NUMERIC_PARTS || parts.length > MAX_NUMERIC_PARTS
                || !isValidPrerelease(prerelease)) {
            return null;
        }
        List<BigInteger> numbers = new ArrayList<>();
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            if (!isNumeric(part)) {
                return null;
            }
            BigInteger number = new BigInteger(part);
            numbers.add(number);
            if (normalized.length() > 0) {
                normalized.append('.');
            }
            normalized.append(number);
        }
        while (numbers.size() < PADDED_NUMERIC_PARTS) {
            numbers.add(BigInteger.ZERO);
            normalized.append(".0");
        }
        String lowerPrerelease = prerelease.toLowerCase(Locale.US);
        if (!lowerPrerelease.isEmpty()) {
            normalized.append('-').append(lowerPrerelease);
        }
        return new ReleaseVersion(numbers, lowerPrerelease, normalized.toString());
    }

    public static int compare(String left, String right) {
        ReleaseVersion leftVersion = parse(left);
        ReleaseVersion rightVersion = parse(right);
        if (leftVersion == null || rightVersion == null) {
            throw new IllegalArgumentException("Invalid release version.");
        }
        return leftVersion.compareTo(rightVersion);
    }

    public String normalized() {
        return normalized;
    }

    public boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int numeric = compareNumbers(numbers, other.numbers);
        if (numeric != 0) {
            return numeric;
        }
        // A stable release orders after any prerelease of the same version.
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) {
            return prerelease.isEmpty() ? 1 : -1;
        }
        if (prerelease.isEmpty()) {
            return 0;
        }
        return comparePrerelease(prerelease, other.prerelease);
    }

    /** Compares version components, treating missing trailing components as zero. */
    private static int compareNumbers(List<BigInteger> left, List<BigInteger> right) {
        int count = Math.max(left.size(), right.size());
        for (int index = 0; index < count; index++) {
            BigInteger leftNumber = index < left.size() ? left.get(index) : BigInteger.ZERO;
            BigInteger rightNumber = index < right.size() ? right.get(index) : BigInteger.ZERO;
            int compared = leftNumber.compareTo(rightNumber);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    /**
     * SemVer prerelease precedence: dot-separated identifiers compare numerically when both are
     * numeric, numeric identifiers sort before alphanumeric ones, and a shorter identifier list
     * sorts first when all shared identifiers are equal.
     */
    private static int comparePrerelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < count; index++) {
            if (index >= leftParts.length) {
                return -1;
            }
            if (index >= rightParts.length) {
                return 1;
            }
            int compared = compareIdentifiers(leftParts[index], rightParts[index]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static int compareIdentifiers(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPrerelease(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '.' && character != '-') {
                return false;
            }
        }
        return !value.startsWith(".") && !value.endsWith(".") && !value.contains("..");
    }
}
