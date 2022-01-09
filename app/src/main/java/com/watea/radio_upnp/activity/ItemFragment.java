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

package com.watea.radio_upnp.activity;

import static com.watea.radio_upnp.activity.MainActivity.RADIO_ICON_SIZE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.watea.radio_upnp.R;
import com.watea.radio_upnp.adapter.PlayerAdapter;
import com.watea.radio_upnp.model.Radio;
import com.watea.radio_upnp.service.NetworkProxy;
import com.watea.radio_upnp.service.RadioURL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ItemFragment extends MainActivityFragment {
  private static final String LOG_TAG = ItemFragment.class.getName();
  private static final String DAR_FM_API = "http://api.dar.fm/";
  private static final String DAR_FM_PLAYLIST_REQUEST = DAR_FM_API + "playlist.php?q=@callsign%20";
  private static final String DAR_FM_STATIONS_REQUEST = DAR_FM_API + "darstations.php?station_id=";
  private static final String WILDCARD = "*";
  private static final String SPACE_FOR_SEARCH = "%20";
  private static final String COUNTRY_FOR_SEARCH = "@COUNTRY%20";
  private static final String DAR_FM_PARTNER_TOKEN = "&partner_token=6453742475";
  private static final String DAR_FM_BASE_URL = "http://stream.dar.fm/";
  private static final String DAR_FM_NAME = "name";
  private static final String DAR_FM_WEB_PAGE = "web_page";
  private static final String DAR_FM_ID = "id";
  private final Pattern PATTERN =
    Pattern.compile(".*(https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+\\.(png|jpg)).*");
  // <HMI assets
  protected EditText nameEditText;
  protected EditText urlEditText;
  protected EditText webPageEditText;
  protected UrlWatcher urlWatcher;
  protected UrlWatcher webPageWatcher;
  private NetworkProxy networkProxy;
  private EditText countryEditText;
  private RadioButton darFmRadioButton;
  private ImageButton searchImageButton;
  private ProgressBar progressBar;
  // />

  @NonNull
  private static String extractValue(@NonNull Element element, @NonNull String tag) {
    return element.getElementsByTag(tag).first().ownText();
  }

  @NonNull
  @Override
  public View.OnClickListener getFloatingActionButtonOnClickListener() {
    return v -> {
      if (networkProxy.isDeviceOffline()) {
        tell(R.string.no_internet);
      } else {
        if (urlWatcher.url == null) {
          tell(R.string.connection_test_aborted);
        } else {
          new UrlTester(urlWatcher.url);
        }
      }
    };
  }

  @Override
  public int getFloatingActionButtonResource() {
    return R.drawable.ic_radio_white_24dp;
  }

  @Override
  public int getMenuId() {
    return R.menu.menu_item_modify;
  }

  @Override
  protected void onActivityCreatedFiltered(@Nullable Bundle savedInstanceState) {
    // Context exists
    assert getContext() != null;
    // Network
    networkProxy = new NetworkProxy(getContext());
    // Order matters
    urlWatcher = new UrlWatcher(urlEditText);
    webPageWatcher = new UrlWatcher(webPageEditText);
    // Default icon
    setRadioIcon(DEFAULT_ICON);
    // Restore icon; may fail
    if (savedInstanceState != null) {
      try {
        String file = savedInstanceState.getString(getString(R.string.key_radio_icon_file));
        setRadioIcon(BitmapFactory.decodeFile(file));
      } catch (Exception exception) {
        Log.e(LOG_TAG, "onActivityCreatedFiltered: internal failure restoring context", exception);
      }
    }
  }

  @Nullable
  @Override
  protected View onCreateViewFiltered(
    @NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
    // Inflate the view so that graphical objects exists
    final View view = inflater.inflate(R.layout.content_item_modify, container, false);
    nameEditText = view.findViewById(R.id.name_edit_text);
    countryEditText = view.findViewById(R.id.country_edit_text);
    progressBar = view.findViewById(R.id.progress_bar);
    urlEditText = view.findViewById(R.id.url_edit_text);
    webPageEditText = view.findViewById(R.id.web_page_edit_text);
    darFmRadioButton = view.findViewById(R.id.dar_fm_radio_button);
    searchImageButton = view.findViewById(R.id.search_image_button);
    final View countryEditLinearlayout = view.findViewById(R.id.country_edit_linearlayout);
    // Order matters!
    ((RadioGroup) view.findViewById(R.id.search_radio_group)).setOnCheckedChangeListener(
      (group, checkedId) -> {
        boolean isDarFmSelected = (checkedId == R.id.dar_fm_radio_button);
        searchImageButton.setImageResource(
          isDarFmSelected ? R.drawable.ic_search_white_40dp : R.drawable.ic_image_white_40dp);
        webPageEditText.setEnabled(!isDarFmSelected);
        urlEditText.setEnabled(!isDarFmSelected);
        countryEditLinearlayout.setVisibility(isDarFmSelected ? View.VISIBLE : View.INVISIBLE);
      });
    searchImageButton.setOnClickListener(v -> {
      flushKeyboard();
      if (networkProxy.isDeviceOffline()) {
        tell(R.string.no_internet);
      } else {
        if (darFmRadioButton.isChecked()) {
          new DarFmSearcher();
        } else {
          URL webPageUrl = webPageWatcher.url;
          if (webPageUrl == null) {
            tell(R.string.no_icon_found);
          } else {
            new IconSearcher(webPageUrl);
          }
        }
      }
    });
    return view;
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    // Save icon; may fail
    try {
      File file = getRadioLibrary().bitmapToFile(getIcon(), Integer.toString(hashCode()));
      outState.putString(getString(R.string.key_radio_icon_file), file.getPath());
    } catch (Exception exception) {
      Log.e(LOG_TAG, "onSaveInstanceState: internal failure", exception);
    }
  }

  @Override
  public void onPause() {
    flushKeyboard();
    super.onPause();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    flushKeyboard();
    if (item.getItemId() == R.id.action_done)
      if (urlWatcher.url == null) {
        tell(R.string.radio_definition_error);
      } else {
        // Action shall be implemented by actual class
        return false;
      }
    else {
      // If we got here, the user's action was not recognized
      // Invoke the superclass to handle it
      return super.onOptionsItemSelected(item);
    }
    return true;
  }

  // Back to previous fragment
  protected void getBack() {
    assert getFragmentManager() != null;
    getFragmentManager().popBackStack();
  }

  @NonNull
  protected String getRadioName() {
    return nameEditText.getText().toString().toUpperCase();
  }

  protected Bitmap getIcon() {
    return (Bitmap) nameEditText.getTag();
  }

  protected void setRadioIcon(@NonNull Bitmap icon) {
    // Resize bitmap as a square
    int height = icon.getHeight();
    int width = icon.getWidth();
    int min = Math.min(height, width);
    icon = Bitmap.createBitmap(
      icon,
      (width - min) / 2,
      (height - min) / 2,
      min,
      min,
      null,
      false);
    nameEditText.setCompoundDrawablesRelativeWithIntrinsicBounds(
      null,
      new BitmapDrawable(
        getResources(),
        Bitmap.createScaledBitmap(icon, RADIO_ICON_SIZE, RADIO_ICON_SIZE, false)),
      null,
      null);
    // radioIcon stored as tag
    nameEditText.setTag(icon);
  }

  @NonNull
  private String getCountrySearch() {
    String countrySearch = countryEditText.getText().toString().toUpperCase().replace(" ", "");
    return (countrySearch.length() > 0) ? COUNTRY_FOR_SEARCH + countrySearch : "";
  }

  private void flushKeyboard() {
    assert getView() != null;
    assert getContext() != null;
    ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
      .hideSoftInputFromWindow(getView().getWindowToken(), 0);
  }

  private void showSearchButton(boolean isShowing) {
    searchImageButton.setVisibility(isShowing ? View.VISIBLE : View.INVISIBLE);
    progressBar.setVisibility(isShowing ? View.INVISIBLE : View.VISIBLE);
  }

  private void tellWait() {
    flushKeyboard();
    tell(R.string.wait_search);
  }

  private void tellSearch() {
    showSearchButton(false);
    tellWait();
  }

  // Utility class to listen for URL edition
  protected class UrlWatcher implements TextWatcher {
    private final int defaultColor;
    @NonNull
    private final EditText editText;
    @Nullable
    protected URL url;

    private UrlWatcher(@NonNull EditText editText) {
      this.editText = editText;
      this.editText.addTextChangedListener(this);
      defaultColor = this.editText.getCurrentTextColor();
      url = null;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
      // Context exists
      assert getContext() != null;
      boolean isError = false;
      try {
        url = new URL(s.toString());
      } catch (MalformedURLException malformedURLException) {
        if (url != null) {
          tell(R.string.malformed_url_error);
        }
        url = null;
        isError = true;
      }
      editText.setTextColor(
        isError ? ContextCompat.getColor(getContext(), R.color.dark_red) : defaultColor);
    }
  }

  // Abstract class to handle web search
  private abstract class Searcher extends Thread {

    @Override
    public void run() {
      onSearch();
      new Handler(Looper.getMainLooper()).post(() -> {
        if (isActuallyAdded()) {
          onPostSearch();
        }
      });
    }

    protected abstract void onSearch();

    protected abstract void onPostSearch();
  }

  private class IconSearcher extends Searcher {
    @NonNull
    private final URL url;
    @Nullable
    private Bitmap foundIcon = null;

    private IconSearcher(@NonNull URL url) {
      super();
      this.url = url;
      tellSearch();
      start();
    }

    @Override
    protected void onSearch() {
      try {
        Element head = Jsoup.connect(url.toString()).get().head();
        // Parse site data, try to accelerate
        for (Element element : head.getAllElements()) {
          String string;
          Bitmap bitmap;
          if ((element != head) && ((string = element.toString()).contains("http"))) {
            Matcher matcher = PATTERN.matcher(string);
            // Fetch largest icon
            if (matcher.find() &&
              ((bitmap = new RadioURL(new URL(matcher.group(1))).getBitmap()) != null) &&
              ((foundIcon == null) || (bitmap.getByteCount() > foundIcon.getByteCount()))) {
              foundIcon = bitmap;
            }
          }
        }
      } catch (Exception exception) {
        Log.i(LOG_TAG, "Error performing radio site search");
      }
    }

    @Override
    protected void onPostSearch() {
      showSearchButton(true);
      if (foundIcon == null) {
        tell(R.string.no_icon_found);
      } else {
        setRadioIcon(foundIcon);
        tell(R.string.icon_updated);
      }
    }
  }

  // Launch with no parameter: display the list result or fill content if only one result.
  // Launch with one parameter: fill content.
  private class DarFmSearcher extends Searcher {
    // List of radio datas
    @NonNull
    private final List<Map<String, String>> radios = new Vector<>();
    @Nullable
    private Bitmap foundIcon = null;

    private DarFmSearcher() {
      this(null);
    }

    private DarFmSearcher(@Nullable Map<String, String> radioDatas) {
      super();
      if (radioDatas != null) {
        radios.add(radioDatas);
      }
      tellSearch();
      start();
    }

    @Override
    protected void onSearch() {
      if (radios.isEmpty()) {
        try {
          Element search = Jsoup
            .connect(
              DAR_FM_PLAYLIST_REQUEST +
                getRadioName().replace(" ", SPACE_FOR_SEARCH) + WILDCARD +
                getCountrySearch() +
                DAR_FM_PARTNER_TOKEN)
            .get();
          // Parse data
          for (Element station : search.getElementsByTag("station")) {
            // As stated, may fail
            try {
              Map<String, String> map = new Hashtable<>();
              map.put(DAR_FM_ID, extractValue(station, "station_id"));
              map.put(DAR_FM_NAME, extractValue(station, "callsign"));
              radios.add(map);
            } catch (Exception exception) {
              Log.i(LOG_TAG, "Error performing DAR_FM_PLAYLIST_REQUEST extraction", exception);
            }
          }
        } catch (IOException iOexception) {
          Log.i(LOG_TAG, "Error performing DAR_FM_PLAYLIST_REQUEST search", iOexception);
        }
      }
      // When radio is known, fetch data
      if (radios.size() == 1) {
        Map<String, String> foundRadio = radios.get(0);
        try {
          Element station = Jsoup
            .connect(DAR_FM_STATIONS_REQUEST + foundRadio.get(DAR_FM_ID) + DAR_FM_PARTNER_TOKEN)
            .get();
          foundRadio.put(DAR_FM_WEB_PAGE, extractValue(station, "websiteurl"));
          foundIcon = new RadioURL(new URL(extractValue(station, "imageurl"))).getBitmap();
        } catch (MalformedURLException malformedURLException) {
          Log.i(LOG_TAG, "Error performing icon search");
        } catch (IOException iOexception) {
          Log.i(LOG_TAG, "Error performing DAR_FM_STATIONS_REQUEST search", iOexception);
        }
      }
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onPostSearch() {
      // Context exists
      assert getContext() != null;
      switch (radios.size()) {
        case 0:
          tell(R.string.dar_fm_failed);
          break;
        case 1:
          Map<String, String> foundRadio = radios.get(0);
          String radioName = foundRadio.get(DAR_FM_NAME);
          if (radioName != null) {
            nameEditText.setText(radioName.toUpperCase());
          }
          urlEditText.setText(DAR_FM_BASE_URL + foundRadio.get(DAR_FM_ID));
          boolean isDarFmWebPageFound = foundRadio.containsKey(DAR_FM_WEB_PAGE);
          boolean isIconFound = (foundIcon != null);
          if (isDarFmWebPageFound) {
            webPageEditText.setText(foundRadio.get(DAR_FM_WEB_PAGE));
          }
          if (isIconFound) {
            setRadioIcon(foundIcon);
          }
          tell((isIconFound && isDarFmWebPageFound) ?
            R.string.dar_fm_done : R.string.dar_fm_went_wrong);
          break;
        default:
          new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
            .setAdapter(
              new SimpleAdapter(
                getActivity(),
                radios,
                R.layout.row_darfm_radio,
                new String[]{DAR_FM_NAME},
                new int[]{R.id.row_darfm_radio_name_text_view}),
              // Call recursively for selected radio
              (dialogInterface, i) -> new DarFmSearcher(radios.get(i)))
            .setCancelable(true)
            .create()
            .show();
      }
      showSearchButton(true);
    }
  }

  private class UrlTester extends Searcher {
    @NonNull
    private final URL url;
    @Nullable
    private String streamContent = null;

    private UrlTester(@NonNull URL url) {
      super();
      this.url = url;
      tellWait();
      start();
    }

    @Override
    protected void onSearch() {
      URL uRL = Radio.getUrlFromM3u(url);
      streamContent = (uRL == null) ? null : new RadioURL(uRL).getStreamContentType();
    }

    @Override
    protected void onPostSearch() {
      if (streamContent == null) {
        tell(R.string.connection_test_failed);
      } else if (!PlayerAdapter.isHandling(streamContent)) {
        tell(getResources().getString(R.string.mime_not_authorized) + streamContent + ".");
      } else {
        tell(getResources().getString(R.string.connection_test_successful) + streamContent + ".");
      }
    }
  }
}