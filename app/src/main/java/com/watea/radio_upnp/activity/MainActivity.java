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

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.watea.radio_upnp.BuildConfig;
import com.watea.radio_upnp.R;
import com.watea.radio_upnp.model.Radio;
import com.watea.radio_upnp.model.RadioLibrary;
import com.watea.radio_upnp.service.RadioService;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.Registry;

import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity
  extends
  AppCompatActivity
  implements
  NavigationView.OnNavigationItemSelectedListener {
  private static final String LOG_TAG = MainActivity.class.getSimpleName();
  private static final DefaultRadio[] DEFAULT_RADIOS = {
    new DefaultRadio(
      "FRANCE INTER",
      R.drawable.logo_france_inter,
      "http://direct.franceinter.fr/live/franceinter-midfi.mp3",
      "https://www.franceinter.fr/"),
    new DefaultRadio(
      "RFM",
      R.drawable.logo_rfm,
      "http://rfm-live-mp3-128.scdn.arkena.com/rfm.mp3",
      "http://www.rfm.fr/"),
    new DefaultRadio(
      "SKYROCK",
      R.drawable.logo_skyrock,
      "http://icecast.skyrock.net/s/natio_mp3_128k",
      "https://www.skyrock.com/"),
    new DefaultRadio(
      "VIRGIN",
      R.drawable.logo_virgin,
      "http://vr-live-mp3-128.scdn.arkena.com/virginradio.mp3",
      "https://www.virginradio.fr/"),
    new DefaultRadio(
      "FUN",
      R.drawable.logo_fun,
      "http://streaming.radio.funradio.fr/fun-1-48-192",
      "https://www.funradio.fr/")
  };
  // <HMI assets
  private DrawerLayout mDrawerLayout;
  private ActionBarDrawerToggle mDrawerToggle;
  private ActionBar mActionBar;
  private FloatingActionButton mFloatingActionButton;
  private Menu mNavigationMenu;
  private AlertDialog mAboutAlertDialog;
  // />
  private RadioLibrary mRadioLibrary;
  private int mNavigationMenuCheckedId;
  private MainFragment mMainFragment;
  private AndroidUpnpService mAndroidUpnpService = null;
  private final ServiceConnection mUpnpConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      mAndroidUpnpService = (AndroidUpnpService) service;
      // Add all devices to the list we already know about
      new Thread() {
        @Override
        public void run() {
          super.run();
          Registry registry = mAndroidUpnpService.getRegistry();
          MainFragment.RegistryListener listener = mMainFragment.mBrowseRegistryListener;
          listener.init(mAndroidUpnpService);
          for (Device device : registry.getDevices()) {
            if (device instanceof RemoteDevice) {
              listener.remoteDeviceAdded(registry, (RemoteDevice) device);
            }
          }
          // Get ready for future device advertisements
          registry.addListener(listener);
          mAndroidUpnpService.getControlPoint().search();
        }
      }.start();
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
      releaseUpnpServiceResource();
    }
  };

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // MainActivityFragment instantiates the menu
    getCurrentFragment().onCreateOptionsMenu(getMenuInflater(), menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Pass the event to ActionBarDrawerToggle, if it returns
    // true, then it has handled the app icon touch event
    return
      mDrawerToggle.onOptionsItemSelected(item) ||
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        getCurrentFragment().onOptionsItemSelected(item) ||
        // If we got here, the user's action was not recognized
        // Invoke the superclass to handle it
        super.onOptionsItemSelected(item);
  }

  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
    switch (menuItem.getItemId()) {
      case R.id.action_home:
        setFragment(R.layout.content_main);
        break;
      case R.id.action_add_item:
        // Default mode: ADD
        setFragment(R.layout.content_item_modify);
        break;
      case R.id.action_modify:
        setFragment(R.layout.content_modify);
        break;
      case R.id.action_donate:
        setFragment(R.layout.content_donation);
        break;
      case R.id.action_about:
        mAboutAlertDialog.show();
        break;
      default:
        // Should not happen
        Log.e(LOG_TAG, "onNavigationItemSelected: internal failure, wrong navigation menu id");
    }
    mDrawerLayout.closeDrawers();
    return true;
  }

  @NonNull
  public Object getFragmentCallback(@NonNull Fragment fragment) {
    if (fragment instanceof MainFragment) {
      return new MainFragmentCallback();
    } else if (fragment instanceof ModifyFragment) {
      return new ModifyFragmentCallback();
    } else if (fragment instanceof ItemModifyFragment) {
      return new ItemModifyFragmentCallback();
    } else if (fragment instanceof DonationFragment) {
      return new DonationFragmentCallback();
    } else {
      // Should not happen
      Log.e(LOG_TAG, "fragmentSet: internal failure, wrong fragment");
      throw new RuntimeException();
    }
  }

  public RadioLibrary getRadioLibrary() {
    return mRadioLibrary;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    // Must be done for Donation
    Fragment fragment = getCurrentFragment();
    if (fragment != null) {
      fragment.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    // Stop MediaBrowser
    mMainFragment.mMediaBrowserConnectionCallback.releaseMediaBrowser();
    // Release UPNP service
    if (mAndroidUpnpService != null) {
      releaseUpnpServiceResource();
      unbindService(mUpnpConnection);
    }
    // Close radios database
    mRadioLibrary.close();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Shared preferences
    SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
    // Open radios database
    mRadioLibrary = new RadioLibrary(this);
    // Create default radios on first start
    if (sharedPreferences.getBoolean(getString(R.string.key_first_start), true) &&
      setDefaultRadios()) {
      // To do just one time, store a flag
      sharedPreferences
        .edit()
        .putBoolean(getString(R.string.key_first_start), false)
        .apply();
    }
    // Retrieve main fragment
    mMainFragment = (MainFragment) ((getCurrentFragment() == null) ?
      setFragment(R.layout.content_main) :
      // Shall exists as MainFragment always created
      getFragmentManager().findFragmentByTag(getTagFromId(R.layout.content_main)));
    // MediaBrowser creation
    MainFragment.MediaBrowserCompatConnectionCallback mediaBrowserConnectionCallback =
      mMainFragment.mMediaBrowserConnectionCallback;
    if (mediaBrowserConnectionCallback.getMediaBrowser() == null) {
      mediaBrowserConnectionCallback
        .setMediaBrowser(
          new MediaBrowserCompat(
            this,
            new ComponentName(this, RadioService.class),
            mediaBrowserConnectionCallback,
            null))
        // Launch the media service, will create a MediaController
        .connect();
    }
    // Start the UPNP service
    if (!bindService(
      new Intent(this, AndroidUpnpServiceImpl.class),
      mUpnpConnection,
      BIND_AUTO_CREATE)) {
      Log.e(LOG_TAG, "onResume: internal failure; AndroidUpnpService not bound");
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Inflate view
    setContentView(R.layout.activity_main);
    mDrawerLayout = findViewById(R.id.main_activity);
    // Toolbar
    setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
    mActionBar = getSupportActionBar();
    if (mActionBar == null) {
      // Should not happen
      Log.e(LOG_TAG, "onCreate: ActionBar is null");
    } else {
      mActionBar.setDisplayHomeAsUpEnabled(true);
      mActionBar.setHomeButtonEnabled(true);
    }
    // Set navigation drawer toggle (according to documentation)
    mDrawerToggle = new ActionBarDrawerToggle(
      this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close);
    // Set the drawer toggle as the DrawerListener
    mDrawerLayout.addDrawerListener(mDrawerToggle);
    // Navigation drawer
    NavigationView navigationView = findViewById(R.id.navigation_view);
    mNavigationMenu = navigationView.getMenu();
    navigationView.setNavigationItemSelectedListener(this);
    // Build alert about dialog
    @SuppressLint("InflateParams")
    View aboutView = getLayoutInflater().inflate(R.layout.view_about, null);
    ((TextView) aboutView.findViewById(R.id.version_name)).setText(BuildConfig.VERSION_NAME);
    mAboutAlertDialog = new AlertDialog.Builder(this)
      .setView(aboutView)
      .setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialogInterface) {
          // Restore checked item
          checkNavigationMenu(mNavigationMenuCheckedId);
        }
      })
      .create();
    // FAB
    mFloatingActionButton = findViewById(R.id.fab);
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Sync the toggle state after onRestoreInstanceState has occurred.
    mDrawerToggle.syncState();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    mDrawerToggle.onConfigurationChanged(newConfig);
  }

  private boolean setDefaultRadios() {
    boolean result = false;
    for (DefaultRadio defaultRadio : DEFAULT_RADIOS) {
      try {
        //noinspection ConstantConditions
        result = (mRadioLibrary.insertAndSaveIcon(
          new Radio(
            defaultRadio.name,
            null, // Filename not known yet
            Radio.Type.MISC,
            Radio.Language.OTHER,
            new URL(defaultRadio.uRL),
            new URL(defaultRadio.webPageURL),
            Radio.Quality.LOW),
          mRadioLibrary.resourceToBitmap(defaultRadio.drawable)) >= 0) || result;
      } catch (MalformedURLException malformedURLException) {
        Log.e(LOG_TAG, "setDefaultRadios: internal error, bad URL definition", malformedURLException);
      }
    }
    return result;
  }

  private MainActivityFragment getCurrentFragment() {
    return (MainActivityFragment) getFragmentManager().findFragmentById(R.id.content_frame);
  }

  // Utility to retrieve fragment
  // Set mMainFragment
  @NonNull
  private Fragment getFragmentFromId(int fragmentId) {
    // MODIFY or ADD: always a new fragment
    Fragment fragment = (fragmentId == R.layout.content_item_modify) ?
      new ItemModifyFragment() :
      getFragmentManager().findFragmentByTag(getTagFromId(fragmentId));
    if (fragment == null) {
      switch (fragmentId) {
        case R.layout.content_main:
          fragment = new MainFragment();
          break;
        case R.layout.content_modify:
          fragment = new ModifyFragment();
          break;
        case R.layout.content_donation:
          fragment = new DonationFragment();
          break;
        default:
          // Should not happen
          Log.e(LOG_TAG, "setFragment: internal failure, wrong fragment id");
          throw new RuntimeException();
      }
    }
    return fragment;
  }

  // Utility to change content fragment
  @NonNull
  private Fragment setFragment(int fragmentId) {
    return setFragment(getFragmentFromId(fragmentId), fragmentId);
  }

  // Utility to change content fragment
  @NonNull
  private Fragment setFragment(@NonNull Fragment fragment, int fragmentId) {
    // Replace fragment setting tag to retrieve it later
    FragmentTransaction fragmentTransaction = getFragmentManager()
      .beginTransaction()
      .replace(R.id.content_frame, fragment, getTagFromId(fragmentId));
    // First fragment transaction not saved to enable back leaving the app
    if (getCurrentFragment() != null) {
      // Works properly only with AndroidManifest options:
      // android:configChanges="keyboardHidden|orientation|screenSize".
      // Otherwise back state added in case of orientation change
      fragmentTransaction.addToBackStack(null);
    }
    fragmentTransaction.commit();
    return fragment;
  }

  @NonNull
  private String getTagFromId(int fragmentId) {
    return Integer.toString(fragmentId);
  }

  private void checkNavigationMenu(int id) {
    mNavigationMenuCheckedId = id;
    mNavigationMenu.findItem(mNavigationMenuCheckedId).setChecked(true);
  }

  private void releaseUpnpServiceResource() {
    if (mAndroidUpnpService != null) {
      MainFragment.RegistryListener listener = mMainFragment.mBrowseRegistryListener;
      listener.release();
      mAndroidUpnpService.getRegistry().removeListener(listener);
      mAndroidUpnpService = null;
    }
  }

  private static class DefaultRadio {
    @NonNull
    private final String name;
    private final int drawable;
    @NonNull
    private final String uRL;
    @NonNull
    private final String webPageURL;

    private DefaultRadio(
      @NonNull String name,
      int drawable,
      @NonNull String uRL,
      @NonNull String webPageURL) {
      this.name = name;
      this.drawable = drawable;
      this.uRL = uRL;
      this.webPageURL = webPageURL;
    }
  }

  private class MainFragmentCallback implements MainFragment.Callback {
    // Shall decorate
    @Override
    public void onResume(
      @NonNull View.OnClickListener floatingActionButtonOnClickListener,
      @NonNull View.OnLongClickListener floatingActionButtonOnLongClickListener,
      int floatingActionButtonResource) {
      invalidateOptionsMenu();
      mActionBar.setTitle(R.string.title_main);
      mFloatingActionButton.setOnClickListener(floatingActionButtonOnClickListener);
      mFloatingActionButton.setOnLongClickListener(floatingActionButtonOnLongClickListener);
      mFloatingActionButton.setImageResource(floatingActionButtonResource);
      checkNavigationMenu(R.id.action_home);
    }
  }

  private class DonationFragmentCallback implements DonationFragment.Callback {
    // Shall decorate
    // FloatingAction defined by fragment
    @Override
    public void onResume(
      @NonNull View.OnClickListener floatingActionButtonOnClickListener,
      int floatingActionButtonResource) {
      invalidateOptionsMenu();
      mActionBar.setTitle(R.string.title_donate);
      mFloatingActionButton.setOnClickListener(floatingActionButtonOnClickListener);
      mFloatingActionButton.setImageResource(floatingActionButtonResource);
      checkNavigationMenu(R.id.action_donate);
    }
  }

  private class ItemModifyFragmentCallback implements ItemModifyFragment.Callback {
    // Shall decorate
    // FloatingAction defined by fragment
    @Override
    public void onResume(
      boolean isAddMode,
      @NonNull View.OnClickListener floatingActionButtonOnClickListener,
      int floatingActionButtonResource) {
      invalidateOptionsMenu();
      mActionBar.setTitle(isAddMode ? R.string.title_item_add : R.string.title_item_modify);
      mFloatingActionButton.setOnClickListener(floatingActionButtonOnClickListener);
      mFloatingActionButton.setImageResource(floatingActionButtonResource);
      checkNavigationMenu(isAddMode ? R.id.action_add_item : R.id.action_modify);
    }
  }

  private class ModifyFragmentCallback implements ModifyFragment.Callback {
    // Shall decorate
    @Override
    public void onResume() {
      invalidateOptionsMenu();
      mActionBar.setTitle(R.string.title_modify);
      mFloatingActionButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            setFragment(R.layout.content_item_modify);
          }
        });
      mFloatingActionButton.setImageResource(R.drawable.ic_playlist_add_black_24dp);
      checkNavigationMenu(R.id.action_modify);
    }

    @Override
    public void onModifyRequest(@NonNull Long radioId) {
      //noinspection ConstantConditions
      setFragment(
        ((ItemModifyFragment) getFragmentFromId(R.layout.content_item_modify))
          .set(mRadioLibrary.getFrom(radioId)),
        R.layout.content_item_modify);
    }
  }
}