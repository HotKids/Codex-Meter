package dev.bennett.codexmeter;

/** Presentation adapter for stable labels owned by pure models and the frozen shared module. */
final class PhoneLabels {
    private PhoneLabels() { }
    static String translate(String value) {
        switch (value == null ? "" : value) {
            case "How to read the charts": return AppText.get(R.string.phone_model_label_0);
            case "Previous window list": return AppText.get(R.string.phone_model_label_1);
            case "Pace vs. typical": return AppText.get(R.string.phone_model_label_2);
            case "Projected exhaustion": return AppText.get(R.string.phone_model_label_3);
            case "Average completed window": return AppText.get(R.string.phone_model_label_4);
            case "Peak burn rate": return AppText.get(R.string.phone_model_label_5);
            case "Value estimates ($)": return AppText.get(R.string.phone_model_label_6);
            case "App settings": return AppText.get(R.string.phone_model_label_7);
            case "Notifications": return AppText.get(R.string.phone_model_label_8);
            case "Now Bar": return AppText.get(R.string.phone_model_label_9);
            case "Authentication": return AppText.get(R.string.phone_model_label_10);
            case "Theme, refresh, updates, and default widget look": return AppText.get(R.string.phone_model_label_11);
            case "Low-usage alerts and reset-credit reminders": return AppText.get(R.string.phone_model_label_12);
            case "Display mode, percentage mode, and auto-start": return AppText.get(R.string.phone_model_label_13);
            case "ChatGPT sign-in tokens (sensitive)": return AppText.get(R.string.phone_model_label_14);
            case "Every hour": return AppText.get(R.string.phone_model_label_15);
            case "Every 6 hours": return AppText.get(R.string.phone_model_label_16);
            case "Every 12 hours": return AppText.get(R.string.phone_model_label_17);
            case "Weekly": return AppText.get(R.string.phone_model_label_18);
            case "Daily": return AppText.get(R.string.phone_model_label_19);
            default: return value == null ? "" : value;
        }
    }
    static String updateLabel(int hours) { return translate(UpdateCheckFrequency.label(hours)); }
    static String updateSummary(int hours) { return AppText.get(R.string.phone_update_frequency, updateLabel(hours)); }
}
