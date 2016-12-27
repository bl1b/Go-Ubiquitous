package com.example.android.sunshine.common.utilities;

/**
 * Created by Jan-2 on 25.12.2016.
 */

public class LogHelper {
    /**
     * Generates a log-tag using the class-name of the class that logs. Log-Tags should no
     * be longer than 22 characters so the class-name will be truncated to that length.
     *
     * @param clazz the class the log-tag should be based on.
     * @return a string which can be used as log-tag.
     */
    public static String LOG_TAG(Class clazz) {
        return clazz.getSimpleName().substring(0, Math.min(clazz.getSimpleName().length(), 22));
    }
}
