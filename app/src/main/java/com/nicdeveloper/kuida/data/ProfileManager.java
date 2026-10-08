package com.nicdeveloper.kuida.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityManager;

public final class ProfileManager {
    public static final String PROFILE_AUTO = "auto";
    public static final String PROFILE_BLIND = "blind";
    public static final String PROFILE_DEAF = "deaf";
    public static final String PROFILE_MUTE = "mute";
    private static final String LEGACY_COMBINED_PROFILE = "deaf_mute";
    public static final String PROFILE_ALL = "all";

    private static final String PREFS = "kuida_profile";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_ONBOARDED = "onboarded";
    private static final String KEY_VOICE_PROMPT = "voice_prompt_done";

    private ProfileManager() {}

    public static boolean hasCompletedOnboarding(Context context) {
        return prefs(context).getBoolean(KEY_ONBOARDED, false);
    }

    public static void completeOnboarding(Context context, String profile) {
        prefs(context).edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_PROFILE, profile)
                .apply();
    }

    public static String selectedProfile(Context context) {
        String profile = prefs(context).getString(KEY_PROFILE, PROFILE_AUTO);
        // Migrate preferences saved by an older build that offered a combined profile.
        return LEGACY_COMBINED_PROFILE.equals(profile) ? PROFILE_ALL : profile;
    }

    public static void setProfile(Context context, String profile) {
        prefs(context).edit().putString(KEY_PROFILE, profile).apply();
    }

    /** La locucion inicial del asistente solo debe sonar UNA vez en la vida de la app. */
    public static boolean isVoicePromptDone(Context context) {
        return prefs(context).getBoolean(KEY_VOICE_PROMPT, false);
    }

    public static void markVoicePromptDone(Context context) {
        prefs(context).edit().putBoolean(KEY_VOICE_PROMPT, true).apply();
    }

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";
    private static final String KEY_THEME = "theme_mode";

    public static String getThemeMode(Context context) {
        return prefs(context).getString(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_THEME, mode).apply();
    }

    private static final String KEY_READER = "reader_enabled";

    /** Lector Kuida propio (para ciegos sin TalkBack). Activado por defecto. */
    public static boolean isReaderEnabled(Context context) {
        return prefs(context).getBoolean(KEY_READER, true);
    }

    public static void setReaderEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_READER, enabled).apply();
    }

    public static boolean isTalkBackActive(Context context) {
        AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        return manager != null && manager.isTouchExplorationEnabled();
    }

    public static boolean needsBlindTools(Context context) {
        String profile = selectedProfile(context);
        return isTalkBackActive(context)
                || PROFILE_BLIND.equals(profile)
                || PROFILE_ALL.equals(profile);
    }

    public static boolean needsHearingTools(Context context) {
        String profile = selectedProfile(context);
        return PROFILE_DEAF.equals(profile)
                || PROFILE_ALL.equals(profile);
    }

    public static boolean needsSpeechTools(Context context) {
        String profile = selectedProfile(context);
        return PROFILE_MUTE.equals(profile)
                || PROFILE_ALL.equals(profile);
    }

    /** Perfiles que pueden beneficiarse del catálogo visual de LSN. */
    public static boolean canUseSignLanguageCatalog(Context context) {
        String profile = selectedProfile(context);
        return PROFILE_DEAF.equals(profile)
                || PROFILE_MUTE.equals(profile)
                || PROFILE_ALL.equals(profile);
    }

    public static boolean showVision(Context context) {
        return needsBlindTools(context) || PROFILE_ALL.equals(selectedProfile(context));
    }

    public static boolean showCommunication(Context context) {
        return needsHearingTools(context) || needsSpeechTools(context) || !showVision(context);
    }

    public static String label(Context context) {
        if (isTalkBackActive(context)) return "Ceguera - TalkBack activo";
        switch (selectedProfile(context)) {
            case PROFILE_BLIND:
                return "Perfil visual - lector y voz";
            case PROFILE_DEAF:
                return "Perfil auditivo - transcripcion y avisos visuales";
            case PROFILE_MUTE:
                return "Perfil de comunicacion del habla - texto y voz";
            case PROFILE_ALL:
                return "Todas las herramientas activas";
            case PROFILE_AUTO:
            default:
                return "Perfil automatico";
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
