/*
 * Copyright (c) 2018. Stephane Treuchot
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.watea.radio_upnp.service;

import static android.content.Context.WIFI_SERVICE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class NetworkProxy {
  private static final String LOG_TAG = NetworkProxy.class.getName();
  private static final String SCHEME = "http";
  private static final int CONNECTION_TRY = 3;
  // Create the SSL connection for HTTPS
  private static final SSLSocketFactory sSLSocketFactory;

  static {
    SSLContext sSLContext = null;
    try {
      sSLContext = SSLContext.getInstance("TLS");
      sSLContext.init(
        null, new TrustManager[]{new EasyX509TrustManager()}, new java.security.SecureRandom());
    } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException exception) {
      Log.i(LOG_TAG, "Error handling SSL connection");
    }
    sSLSocketFactory = (sSLContext == null) ? null : sSLContext.getSocketFactory();
  }

  // Static class, no instance
  private NetworkProxy() {
  }

  @Nullable
  public static String getStreamContentType(@NonNull URL uRL) {
    String streamContent = null;
    HttpURLConnection httpURLConnection = null;
    try {
      httpURLConnection = getActualHttpURLConnection(uRL);
      streamContent = httpURLConnection.getHeaderField("Content-Type");
      // If we get there, connection has occurred
      Log.d(LOG_TAG, "Connection status/contentType: " +
        httpURLConnection.getResponseCode() + "/" + streamContent);
    } catch (IOException iOException) {
      // Fires also in case of timeout
      Log.i(LOG_TAG, "URL IO exception");
    } finally {
      if (httpURLConnection != null) {
        httpURLConnection.disconnect();
      }
    }
    return streamContent;
  }

  @Nullable
  private static String ipAddressToString(int ipAddress) {
    try {
      return InetAddress.getByAddress(
        BigInteger.valueOf(ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN) ?
          Integer.reverseBytes(ipAddress) : ipAddress).toByteArray()).getHostAddress();
    } catch (UnknownHostException unknownHostException) {
      Log.e(LOG_TAG, "Error decoding IP address", unknownHostException);
    }
    return null;
  }

  @NonNull
  private static Uri getUri(@NonNull String address, int port) {
    return new Uri
      .Builder()
      .scheme(SCHEME)
      .appendEncodedPath("/" + address + ":" + port)
      .build();
  }

  public static boolean isDeviceOffline(@NonNull Context context) {
    boolean result = true;
    try {
      ConnectivityManager connectivityManager
        = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
      result = (activeNetworkInfo == null) || !activeNetworkInfo.isConnected();
    } catch (Exception exception) {
      Log.e(LOG_TAG, "Error testing ConnectivityManager", exception);
    }
    return result;
  }

  @Nullable
  private static String getIpAddress(@NonNull Context context) {
    String result = null;
    try {
      WifiManager wifiManager =
        (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
      result = ipAddressToString(wifiManager.getConnectionInfo().getIpAddress());
    } catch (Exception exception) {
      Log.e(LOG_TAG, "Error getting IP address", exception);
    }
    return result;
  }

  public static boolean hasWifiIpAddress(@NonNull Context context) {
    return (getIpAddress(context) != null);
  }

  @NonNull
  public static Uri getLoopbackUri(int port) {
    return getUri("127.0.0.1", port);
  }

  public static Uri getUri(@NonNull Context context, int port) {
    String ipAddress = getIpAddress(context);
    return (ipAddress == null) ? null : getUri(ipAddress, port);
  }

  @NonNull
  public static HttpURLConnection getActualHttpURLConnection(@Nullable URL uRL) throws IOException {
    return getActualHttpURLConnection(uRL, null);
  }

  // Handle redirection
  // Consumer sets connection headers
  @NonNull
  public static HttpURLConnection getActualHttpURLConnection(
    @Nullable URL uRL,
    @Nullable HttpURLConnectionConsumer httpURLConnectionConsumer) throws IOException {
    if (uRL == null) {
      throw new IOException("getActualHttpURLConnection: URL is null");
    }
    HttpURLConnection httpURLConnection;
    int connectionTry = 0;
    do {
      // Set headers
      httpURLConnection = (HttpURLConnection) uRL.openConnection();
      httpURLConnection.setInstanceFollowRedirects(true);
      if (httpURLConnection instanceof HttpsURLConnection) {
        ((HttpsURLConnection) httpURLConnection).setSSLSocketFactory(sSLSocketFactory);
      }
      if (httpURLConnectionConsumer != null) {
        httpURLConnectionConsumer.accept(httpURLConnection);
      }
      // Get answer
      if (httpURLConnection.getResponseCode() / 100 == 3) {
        uRL = new URL(httpURLConnection.getHeaderField("Location"));
      } else {
        break;
      }
    } while (connectionTry++ < CONNECTION_TRY);
    return httpURLConnection;
  }

  @Nullable
  public static Bitmap getBitmapFromUrl(@NonNull URL uRL) {
    HttpURLConnection httpURLConnection = null;
    Bitmap bitmap = null;
    try {
      httpURLConnection = getActualHttpURLConnection(uRL);
      bitmap = BitmapFactory.decodeStream(httpURLConnection.getInputStream());
    } catch (IOException iOException) {
      Log.i(LOG_TAG, "Error decoding image: " + uRL);
    } finally {
      if (httpURLConnection != null) {
        httpURLConnection.disconnect();
      }
    }
    return bitmap;
  }

  public interface HttpURLConnectionConsumer {
    void accept(@NonNull HttpURLConnection httpURLConnection) throws IOException;
  }
}