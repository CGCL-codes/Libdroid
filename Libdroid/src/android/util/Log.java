/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;


import java.util.logging.Logger;

/**
 * API for sending log output.
 *
 * <p>Generally, use the Log.v() Log.d() Log.i() Log.w() and Log.e()
 * methods.
 *
 * <p>The order in terms of verbosity, from least to most is
 * ERROR, WARN, INFO, DEBUG, VERBOSE.  Verbose should never be compiled
 * into an application except during development.  Debug logs are compiled
 * in but stripped at runtime.  Error, warning and info logs are always kept.
 *
 * <p><b>Tip:</b> A good convention is to declare a <code>TAG</code> constant
 * in your class:
 *
 * <pre>private static final String TAG = "MyActivity";</pre>
 *
 * and use that in subsequent calls to the log methods.
 * </p>
 *
 * <p><b>Tip:</b> Don't forget that when you make a call like
 * <pre>Log.v(TAG, "index=" + i);</pre>
 * that when you're building the string to pass into Log.d, the compiler uses a
 * StringBuilder and at least three allocations occur: the StringBuilder
 * itself, the buffer, and the String object.  Realistically, there is also
 * another buffer allocation and copy, and even more pressure on the gc.
 * That means that if your log message is filtered out, you might be doing
 * significant work and incurring significant overhead.
 */
public final class Log {
    private static Logger log = Logger.getLogger(Log.class.getName());

    public static int v(String tag, String msg) {
        log.info(tag + ":" + msg);
        return 0;
    }

    public static int v(String tag, String msg, Exception e) {
        log.info(tag + ":" + msg);
        e.printStackTrace();
        return 0;
    }

    public static int d(String tag, String msg) {
        log.info(tag + ":" + msg);
        return 0;
    }

    public static int d(String tag, String msg, Exception e) {
        log.info(tag + ":" + msg);
        e.printStackTrace();
        return 0;
    }

    public static int i(String tag, String msg) {
        log.info(tag + ":" + msg);
        return 0;
    }


    public static int i(String tag, String msg, Throwable tr) {
        log.info(tag + ":" + msg);
        return 0;
    }


    public static int w(String tag, String msg) {
        log.info(tag + ":" + msg);
        return 0;
    }

    public static int w(String tag, String msg, Exception e) {
        log.info(tag + ":" + msg);
        e.printStackTrace();
        return 0;
    }

    public static int e(String tag, String msg) {
        log.info(tag + ":" + msg);
        return 0;
    }

    public static int e(String tag, String msg, Exception e) {
        log.info(tag + ":" + msg);
        e.printStackTrace();
        return 0;
    }

    public static int wtf(String tag, String msg) {
        log.info(tag + ":" + msg);
        return 0;
    }

    public static int wtf(String tag, String msg, Exception e) {
        log.info(tag + ":" + msg);
        e.printStackTrace();
        return 0;
    }
}
