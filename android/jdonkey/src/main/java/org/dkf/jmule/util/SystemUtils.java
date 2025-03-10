/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dkf.jmule.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

import org.dkf.jmule.Platforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP;

import androidx.core.content.ContextCompat;
import androidx.core.os.EnvironmentCompat;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SystemUtils.class);

    private static final int VERSION_CODE_KITKAT = 19;
    private static final int VERSION_SDK_NOUGAT_7_0 = 24;

    private SystemUtils() {
    }

    public static File getCacheDir(Context context, String directory) {
        File cache;

        if (isPrimaryExternalStorageMounted()) {
            cache = context.getExternalCacheDir();
        } else {
            cache = context.getCacheDir();
        }

        return new File(cache, directory);
    }

    public static int calculateMemoryCacheSize(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        boolean largeHeap = (context.getApplicationInfo().flags & FLAG_LARGE_HEAP) != 0;
        int memoryClass = am.getMemoryClass();
        if (largeHeap) {
            memoryClass = am.getLargeMemoryClass();
        }
        // Target ~15% of the available heap.
        return 1024 * 1024 * memoryClass / 7;
    }

    public static long calculateDiskCacheSize(File dir, int minSize, int maxSize) {
        long size = minSize;

        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long available = ((long) statFs.getBlockCount()) * statFs.getBlockSize();
            // Target 2% of the total space.
            size = available / 50;
        } catch (IllegalArgumentException ignored) {
        }

        // Bound inside min/max size for disk cache.
        return Math.max(Math.min(size, maxSize), minSize);
    }

    /**
     * returns true if the media is present
     * and mounted at its mount point with read/write access.
     *
     * @see android.os.Environment#MEDIA_MOUNTED
     */
    public static boolean isPrimaryExternalStorageMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Use this instead of EnvironmentCompat.
     * <p/>
     * returns true if the media is present
     * and mounted at its mount point with read/write access.
     *
     * @see android.os.Environment#MEDIA_MOUNTED
     */
    public static boolean isSecondaryExternalStorageMounted(File path) {
        if (path == null) { // fast precondition
            return false;
        }

        boolean result = false;

        if (hasKitKatOrNewer()) {
            result = Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(path));
        } else {
            try {
                String[] l = path.list();
                result = l != null && l.length > 0;
            } catch (Exception e) {
                LOG.error("Error detecting secondary external storage state {}", e);
            }
        }

        return result;
    }

    public static boolean isPrimaryExternalPath(File path) {
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        return path != null ? path.getAbsolutePath().startsWith(primary) : null;
    }

    public static long getAvailableStorageSize(File dir) {
        long size;
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            size = ((long) stat.getAvailableBlocks()) * stat.getBlockSize();
        } catch (Exception e) {
            size = -1; // system error computing the available storage size
        }

        return size;
    }

    /**
     * Use this instead ContextCompat
     *
     */
    public static File[] getExternalFilesDirs(Context context) {
        if (hasKitKatOrNewer()) {
            List<File> dirs = new LinkedList<>();

            for (File f : ContextCompat.getExternalFilesDirs(context, null)) {
                if (f != null) {
                    dirs.add(f);
                }
            }

            return dirs.toArray(new File[dirs.size()]);
        } else {
            List<File> dirs = new LinkedList<>();

            dirs.add(context.getExternalFilesDir(null));

            try {
                String secondaryStorages = System.getenv("SECONDARY_STORAGE");
                if (secondaryStorages != null) {
                    String[] storages = secondaryStorages.split(File.pathSeparator);
                    for (String s : storages) {
                        dirs.add(new File(s));
                    }
                }
            } catch (Exception e) {
                LOG.error("Unable to get secondary external storages", e);
            }

            return dirs.toArray(new File[dirs.size()]);
        }
    }

    /**
     * Used to determine if the device is running Android11 or greater
     */
    public static boolean hasAndroid10OrNewer() {
        return hasSdkOrNewer(Build.VERSION_CODES.Q);
    }

    private static boolean hasSdkOrNewer(int versionCode) {
        return Build.VERSION.SDK_INT >= versionCode;
    }

    /**
     * Used to determine if the device is running
     * KitKat (Android 4.4) or greater
     *
     * @return True if the device is running KitKat or greater,
     * false otherwise
     */
    public static boolean hasKitKatOrNewer() {
        return hasSdkOrNewer(VERSION_CODE_KITKAT);
    }

    public static void postToUIThread(Runnable runnable) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(runnable);
        } catch (Throwable t) {
            LOG.error("UIUtils.postToUIThread error: " + t.getMessage());
        }
    }

    /**
     * Used to determine if the device is running
     * Nougat (Android 7.0) or greater.
     *
     * @return {@code true} if the device is running KitKat or greater,
     * {@code false} otherwise
     */
    public static boolean hasNougatOrNewer() {
        return hasSdkOrNewer(VERSION_SDK_NOUGAT_7_0);
    }

    /**
     * Used to determine if the device is running Android11 or greater
     */
    public static boolean hasAndroid11OrNewer() {
        return hasSdkOrNewer(30); //Build.VERSION_CODES.R
    }

    /**
     * We call it "safe" because if any exceptions are thrown,
     * they are caught in order to not crash the handler thread.
     */
    public static void exceptionSafePost(Handler handler, Runnable r) {
        if (handler != null) {
            // We are already in the Handler thread, just go!
            if (Thread.currentThread() == handler.getLooper().getThread()) {
                try {
                    r.run();
                } catch (Throwable t) {
                    LOG.error("safePost() " + t.getMessage(), t);
                }
            } else {
                handler.post(() -> {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        LOG.error("safePost() " + t.getMessage(), t);
                    }
                });
            }
        }
    }

    public static boolean isUIThread() {
        return Platforms.get().isUIThread();
    }
}
