package com.genymobile.scrcpy;

import android.os.Build;

import com.genymobile.scrcpy.wrappers.ContentProvider;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import java.io.IOException;

public final class Settings {

  public static final String TABLE_SYSTEM = ContentProvider.TABLE_SYSTEM;
  public static final String TABLE_SECURE = ContentProvider.TABLE_SECURE;
  public static final String TABLE_GLOBAL = ContentProvider.TABLE_GLOBAL;

  private Settings() {
    /* not instantiable */
  }

  private static void execSettingsPut(String table, String key, String value) throws SettingsException {
    try {
      Command.exec("settings", "put", table, key, value);
    } catch (IOException | InterruptedException e) {
      throw new SettingsException("put", table, key, value, e);
    }
  }

  private static String execSettingsGet(String table, String key) throws SettingsException {
    try {
      return Command.execReadLine("settings", "get", table, key);
    } catch (IOException | InterruptedException e) {
      throw new SettingsException("get", table, key, null, e);
    }
  }

  public static String getValue(String table, String key) throws SettingsException {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
      // on Android >= 12, it always fails: <https://github.com/Genymobile/scrcpy/issues/2788>
      try (ContentProvider provider = ServiceManager.getActivityManager().createSettingsProvider()) {
        return provider.getValue(table, key);
      } catch (SettingsException e) {
        Ln.w("Could not get settings value via ContentProvider, fallback to settings process", e);
      }
    }

    return execSettingsGet(table, key);
  }

  public static void putValue(String table, String key, String value) throws SettingsException {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
      // on Android >= 12, it always fails: <https://github.com/Genymobile/scrcpy/issues/2788>
      try (ContentProvider provider = ServiceManager.getActivityManager().createSettingsProvider()) {
        provider.putValue(table, key, value);
      } catch (SettingsException e) {
        Ln.w("Could not put settings value via ContentProvider, fallback to settings process", e);
      }
    }

    execSettingsPut(table, key, value);
  }

  public static String getAndPutValue(String table, String key, String value) throws SettingsException {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
      // on Android >= 12, it always fails: <https://github.com/Genymobile/scrcpy/issues/2788>
      try (ContentProvider provider = ServiceManager.getActivityManager().createSettingsProvider()) {
        String oldValue = provider.getValue(table, key);
        if (!value.equals(oldValue)) {
          provider.putValue(table, key, value);
        }
        return oldValue;
      } catch (SettingsException e) {
        Ln.w("Could not get and put settings value via ContentProvider, fallback to settings process", e);
      }
    }

    String oldValue = getValue(table, key);
    if (!value.equals(oldValue)) {
      putValue(table, key, value);
    }
    return oldValue;
  }
}
