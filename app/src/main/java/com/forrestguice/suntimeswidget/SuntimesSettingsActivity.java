/**
    Copyright (C) 2014-2018 Forrest Guice
    This file is part of SuntimesWidget.

    SuntimesWidget is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    SuntimesWidget is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with SuntimesWidget.  If not, see <http://www.gnu.org/licenses/>.
*/ 

package com.forrestguice.suntimeswidget;

import android.annotation.SuppressLint;
import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import com.forrestguice.suntimeswidget.calculator.SuntimesCalculator;
import com.forrestguice.suntimeswidget.calculator.SuntimesCalculatorDescriptor;
import com.forrestguice.suntimeswidget.calendar.SuntimesCalendarTask;
import com.forrestguice.suntimeswidget.getfix.BuildPlacesTask;
import com.forrestguice.suntimeswidget.getfix.ExportPlacesTask;
import com.forrestguice.suntimeswidget.settings.AppSettings;
import com.forrestguice.suntimeswidget.settings.SummaryListPreference;
import com.forrestguice.suntimeswidget.settings.WidgetSettings;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A preferences activity for the main app;
 * @see SuntimesConfigActivity0 for widget configuration.
 */
public class SuntimesSettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener
{
    public static final String LOG_TAG = "SuntimesSettings";

    final static String ACTION_PREFS_GENERAL = "com.forrestguice.suntimeswidget.PREFS_GENERAL";
    final static String ACTION_PREFS_CALENDAR = "com.forrestguice.suntimeswidget.PREFS_CALENDAR";
    final static String ACTION_PREFS_LOCALE = "com.forrestguice.suntimeswidget.PREFS_LOCALE";
    final static String ACTION_PREFS_UI = "com.forrestguice.suntimeswidget.PREFS_UI";
    final static String ACTION_PREFS_WIDGETLIST = "com.forrestguice.suntimeswidget.PREFS_WIDGETLIST";
    final static String ACTION_PREFS_PLACES = "com.forrestguice.suntimeswidget.PREFS_PLACES";

    public static final int REQUEST_CALENDARPREFSFRAGMENT_ENABLED = 2;
    public static final int REQUEST_CALENDARPREFSFRAGMENT_DISABLED = 4;

    private Context context;
    private PlacesPrefsBase placesPrefBase = null;
    private String appTheme = null;

    public SuntimesSettingsActivity()
    {
        super();
    }

    @Override
    protected void attachBaseContext(Context newBase)
    {
        Context context = AppSettings.initLocale(newBase);
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        setResult(RESULT_OK);
        context = SuntimesSettingsActivity.this;
        appTheme = AppSettings.loadThemePref(this);
        setTheme(AppSettings.themePrefToStyleId(this, appTheme));

        super.onCreate(icicle);
        initLocale(icicle);
        initLegacyPrefs();

        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(onChangedNeedingRebuild);
    }

    /**
     * legacy pref api used for pre honeycomb devices, while honeycomb+ uses the fragment based api.
     */
    private void initLegacyPrefs()
    {
        String action = getIntent().getAction();
        if (action != null)
        {
            Log.i(LOG_TAG, "initLegacyPrefs: action: " + action);

            //noinspection IfCanBeSwitch
            if (action.equals(ACTION_PREFS_GENERAL))
            {
                //noinspection deprecation
                addPreferencesFromResource(R.xml.preference_general);
                initPref_general();

            } else if (action.equals(ACTION_PREFS_CALENDAR)) {
                addPreferencesFromResource(R.xml.preference_calendars);
                initPref_calendars();

            } else if (action.equals(ACTION_PREFS_LOCALE)) {
                //noinspection deprecation
                addPreferencesFromResource(R.xml.preference_locale);
                initPref_locale();

            } else if (action.equals(ACTION_PREFS_UI)) {
                //noinspection deprecation
                addPreferencesFromResource(R.xml.preference_userinterface);
                initPref_ui();

            } else if (action.equals(ACTION_PREFS_PLACES)) {
                //noinspection deprecation
                addPreferencesFromResource(R.xml.preference_places);
                initPref_places();

            } else if (action.equals(ACTION_PREFS_WIDGETLIST)) {
                Intent intent = new Intent(this, SuntimesWidgetListActivity.class);
                startActivity(intent);
                finish();

            } else {
                Log.w(LOG_TAG, "initLegacyPrefs: unhandled action: " + action);
            }

        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            //noinspection deprecation
            addPreferencesFromResource(R.xml.preference_headers_legacy);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        initLocale(null);
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);

        if (placesPrefBase != null)
        {
            placesPrefBase.onResume();
        }
    }

    @Override
    public void onPause()
    {
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        if (placesPrefBase != null)
        {
            placesPrefBase.onStop();
        }
    }

    @Override
    public void onDestroy()
    {
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(onChangedNeedingRebuild);
        super.onDestroy();
    }

    private void initLocale(Bundle icicle)
    {
        WidgetSettings.initDefaults(context);

        AppSettings.initDisplayStrings(context);
        WidgetSettings.initDisplayStrings(context);

        boolean themeChanged = false;
        if (icicle != null)
        {
            String prevTheme = icicle.getString(AppSettings.PREF_KEY_APPEARANCE_THEME);
            if (prevTheme == null) {
                prevTheme = appTheme;
            }
            themeChanged = !prevTheme.equals(appTheme);
        }

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) && (themeChanged))
        {
            invalidateHeaders();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (grantResults.length > 0 && permissions.length > 0)
        {
            switch (requestCode)
            {
                case REQUEST_CALENDARPREFSFRAGMENT_ENABLED:
                case REQUEST_CALENDARPREFSFRAGMENT_DISABLED:
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    {
                        boolean enabled = requestCode == (REQUEST_CALENDARPREFSFRAGMENT_ENABLED);
                        runCalendarTask(SuntimesSettingsActivity.this, enabled);

                        SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(context).edit();
                        pref.putBoolean(AppSettings.PREF_KEY_CALENDARS_ENABLED, enabled);
                        pref.apply();

                        if (tmp_calendarPref != null)
                        {
                            tmp_calendarPref.setChecked(enabled);
                            tmp_calendarPref = null;
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putString(AppSettings.PREF_KEY_APPEARANCE_THEME, appTheme);
    }

    /**
     * @param target the target list to place headers into
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            loadHeadersFromResource(R.xml.preference_headers, target);

            TypedValue typedValue = new TypedValue();   // force styled icons on headers
            int[] icActionAttr = new int[] { R.attr.icActionSettings };
            TypedArray a = obtainStyledAttributes(typedValue.data, icActionAttr);
            int settingsIcon = a.getResourceId(0, R.drawable.ic_action_settings);
            a.recycle();

            for (Header header : target)
            {
                if (header.iconRes == 0)
                {
                    header.iconRes = settingsIcon;
                }
            }
        }
    }

    /**
     * @param fragmentName reference to some fragment (by name)
     * @return true is a PreferenceFragment allowed by this activity
     */
    @Override
    protected boolean isValidFragment(String fragmentName)
    {
        return GeneralPrefsFragment.class.getName().equals(fragmentName) ||
               CalendarPrefsFragment.class.getName().equals(fragmentName) ||
               LocalePrefsFragment.class.getName().equals(fragmentName) ||
               UIPrefsFragment.class.getName().equals(fragmentName) ||
               PlacesPrefsFragment.class.getName().equals(fragmentName);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener onChangedNeedingRebuild = new SharedPreferences.OnSharedPreferenceChangeListener()
    {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
        {
            if (key.equals(AppSettings.PREF_KEY_LOCALE) || key.equals(AppSettings.PREF_KEY_LOCALE_MODE)
                    || key.equals(AppSettings.PREF_KEY_APPEARANCE_THEME))
            {
                //Log.d("SettingsActivity", "Locale change detected; restarting activity");
                updateLocale();
                rebuildActivity();
            }
        }
    };

    @SuppressWarnings("UnnecessaryReturnStatement")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        Log.i(LOG_TAG, "onSharedPreferenceChanged: key: " + key);

        if (key.endsWith(AppSettings.PREF_KEY_PLUGINS_ENABLESCAN))
        {
            SuntimesCalculatorDescriptor.reinitCalculators(this);
            rebuildActivity();
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_CALCULATOR))
        {
            try {
                // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
                // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
                String calcName = sharedPreferences.getString(key, null);
                SuntimesCalculatorDescriptor descriptor = SuntimesCalculatorDescriptor.valueOf(this, calcName);
                WidgetSettings.saveCalculatorModePref(this, 0, descriptor);
                Log.i(LOG_TAG, "onSharedPreferenceChanged: value: " + calcName + " :: " + descriptor);

            } catch (InvalidParameterException e) {
                Log.e(LOG_TAG, "onPreferenceChanged: Failed to persist sun calculator pref! " + e);
            }
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_CALCULATOR + "_moon"))
        {
            try {
                // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
                // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
                String calcName = sharedPreferences.getString(key, null);
                SuntimesCalculatorDescriptor descriptor = SuntimesCalculatorDescriptor.valueOf(this, calcName);
                WidgetSettings.saveCalculatorModePref(this, 0, "moon", descriptor);
                Log.i(LOG_TAG, "onSharedPreferenceChanged: value: " + calcName + " :: " + descriptor);

            } catch (InvalidParameterException e) {
                Log.e(LOG_TAG, "onPreferenceChanged: Failed to persist moon calculator pref! " + e);
            }
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_LOCATION_ALTITUDE_ENABLED))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveLocationAltitudeEnabledPref(this, 0, sharedPreferences.getBoolean(key, WidgetSettings.PREF_DEF_LOCATION_ALTITUDE_ENABLED));
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_APPEARANCE_TIMEFORMATMODE))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveTimeFormatModePref(this, 0, WidgetSettings.TimeFormatMode.valueOf(sharedPreferences.getString(key, WidgetSettings.PREF_DEF_APPEARANCE_TIMEFORMATMODE.name())));
            updateLocale();
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_TRACKINGMODE))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveTrackingModePref(this, 0, WidgetSettings.TrackingMode.valueOf(sharedPreferences.getString(key, WidgetSettings.PREF_DEF_GENERAL_TRACKINGMODE.name())));
	        return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_SHOWSECONDS))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveShowSecondsPref(this, 0, sharedPreferences.getBoolean(key, WidgetSettings.PREF_DEF_GENERAL_SHOWSECONDS));
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_SHOWTIMEDATE))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveShowTimeDatePref(this, 0, sharedPreferences.getBoolean(key, WidgetSettings.PREF_DEF_GENERAL_SHOWTIMEDATE));
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_SHOWWEEKS))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveShowWeeksPref(this, 0, sharedPreferences.getBoolean(key, WidgetSettings.PREF_DEF_GENERAL_SHOWWEEKS));
            return;
        }

        if (key.endsWith(WidgetSettings.PREF_KEY_GENERAL_SHOWHOURS))
        {
            // the pref activity saves to: com.forrestguice.suntimeswidget_preferences.xml,
            // ...but this is a widget setting (belongs in com.forrestguice.suntimeswidget.xml)
            WidgetSettings.saveShowHoursPref(this, 0, sharedPreferences.getBoolean(key, WidgetSettings.PREF_DEF_GENERAL_SHOWHOURS));
            return;
        }
    }

    protected void updateLocale()
    {
        AppSettings.initLocale(this);

        SuntimesWidget0.triggerWidgetUpdate(this, SuntimesWidget0.class);
        SuntimesWidget0.triggerWidgetUpdate(this, SuntimesWidget0_2x1.class);

        SuntimesWidget0.triggerWidgetUpdate(this, SuntimesWidget1.class);

        SuntimesWidget0.triggerWidgetUpdate(this, SolsticeWidget0.class);

        SuntimesWidget0.triggerWidgetUpdate(this, SuntimesWidget2.class);
        SuntimesWidget0.triggerWidgetUpdate(this, SuntimesWidget2_3x1.class);

        SuntimesWidget0.triggerWidgetUpdate(this, MoonWidget0.class);
        SuntimesWidget0.triggerWidgetUpdate(this, MoonWidget0_2x1.class);
        SuntimesWidget0.triggerWidgetUpdate(this, MoonWidget0_3x1.class);
    }

    protected void rebuildActivity()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            invalidateHeaders();
            recreate();

        } else {
            finish();
            startActivity(getIntent());
        }
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    /**
     * General Prefs
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPrefsFragment extends PreferenceFragment
    {
        private SummaryListPreference sunCalculatorPref, moonCalculatorPref;
        private CheckBoxPreference useAltitudePref;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            AppSettings.initLocale(getActivity());
            Log.i(LOG_TAG, "GeneralPrefsFragment: Arguments: " + getArguments());

            PreferenceManager.setDefaultValues(getActivity(), R.xml.preference_general, false);
            addPreferencesFromResource(R.xml.preference_general);

            sunCalculatorPref = (SummaryListPreference) findPreference(WidgetSettings.keyCalculatorModePref(0));
            moonCalculatorPref = (SummaryListPreference) findPreference(WidgetSettings.keyCalculatorModePref(0, "moon"));

            initPref_general(GeneralPrefsFragment.this);
        }

        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onAttach(Context context)
        {
            super.onAttach(context);
            loadPref_calculator(context, sunCalculatorPref);
            loadPref_calculator(context, moonCalculatorPref, "moon");
        }

        @Override
        public void onAttach(Activity activity)
        {
            super.onAttach(activity);
            loadPref_calculator(activity, sunCalculatorPref);
            loadPref_calculator(activity, moonCalculatorPref, "moon");
        }
    }

    /**
     * init legacy prefs
     */
    private void initPref_general()
    {
        Log.i(LOG_TAG, "initPref_general (legacy)");

        String key_timeFormat = WidgetSettings.PREF_PREFIX_KEY + "0" + WidgetSettings.PREF_PREFIX_KEY_APPEARANCE + WidgetSettings.PREF_KEY_APPEARANCE_TIMEFORMATMODE;
        ListPreference timeformatPref = (ListPreference)findPreference(key_timeFormat);
        initPref_timeFormat(this, timeformatPref);

        String key_altitudePref = WidgetSettings.PREF_PREFIX_KEY + "0" + WidgetSettings.PREF_PREFIX_KEY_LOCATION + WidgetSettings.PREF_KEY_LOCATION_ALTITUDE_ENABLED;
        CheckBoxPreference altitudePref = (CheckBoxPreference)findPreference(key_altitudePref);
        initPref_altitude(this, altitudePref);

        String key_sunCalc = WidgetSettings.keyCalculatorModePref(0);
        //noinspection deprecation
        SummaryListPreference sunCalculatorPref = (SummaryListPreference)findPreference(key_sunCalc);
        if (sunCalculatorPref != null)
        {
            initPref_calculator(this, sunCalculatorPref, WidgetSettings.PREF_DEF_GENERAL_CALCULATOR);
            loadPref_calculator(this, sunCalculatorPref);
        }

        String key_moonCalc = WidgetSettings.keyCalculatorModePref(0, "moon");
        //noinspection deprecation
        SummaryListPreference moonCalculatorPref = (SummaryListPreference)findPreference(key_moonCalc);
        if (moonCalculatorPref != null)
        {
            initPref_calculator(this, moonCalculatorPref, new int[] {SuntimesCalculator.FEATURE_MOON}, WidgetSettings.PREF_DEF_GENERAL_CALCULATOR_MOON);
            loadPref_calculator(this, moonCalculatorPref,"moon");
        }
    }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void initPref_general(PreferenceFragment fragment)
    {
        Log.i(LOG_TAG, "initPref_general (fragment)");
        Context context = fragment.getActivity();

        String key_timeFormat = WidgetSettings.PREF_PREFIX_KEY + "0" + WidgetSettings.PREF_PREFIX_KEY_APPEARANCE + WidgetSettings.PREF_KEY_APPEARANCE_TIMEFORMATMODE;
        ListPreference timeformatPref = (ListPreference)fragment.findPreference(key_timeFormat);
        if (timeformatPref != null)
        {
            initPref_timeFormat(fragment.getActivity(), timeformatPref);
            loadPref_timeFormat(fragment.getActivity(), timeformatPref);
        }

        String key_altitudePref = WidgetSettings.PREF_PREFIX_KEY + "0" + WidgetSettings.PREF_PREFIX_KEY_LOCATION + WidgetSettings.PREF_KEY_LOCATION_ALTITUDE_ENABLED;
        CheckBoxPreference altitudePref = (CheckBoxPreference)fragment.findPreference(key_altitudePref);
        if (altitudePref != null)
        {
            initPref_altitude(fragment.getActivity(), altitudePref);
            loadPref_altitude(fragment.getActivity(), altitudePref);
        }

        String key_sunCalc = WidgetSettings.keyCalculatorModePref(0);
        SummaryListPreference calculatorPref = (SummaryListPreference) fragment.findPreference(key_sunCalc);
        if (calculatorPref != null)
        {
            initPref_calculator(context, calculatorPref, WidgetSettings.PREF_DEF_GENERAL_CALCULATOR);
            loadPref_calculator(context, calculatorPref);
        }

        String key_moonCalc = WidgetSettings.keyCalculatorModePref(0, "moon");
        SummaryListPreference moonCalculatorPref = (SummaryListPreference) fragment.findPreference(key_moonCalc);
        if (moonCalculatorPref != null)
        {
            initPref_calculator(context, moonCalculatorPref, new int[] {SuntimesCalculator.FEATURE_MOON}, WidgetSettings.PREF_DEF_GENERAL_CALCULATOR_MOON);
            loadPref_calculator(context, moonCalculatorPref, "moon");
        }
    }


    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class CalendarPrefsFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            AppSettings.initLocale(getActivity());
            Log.i("CalendarPrefsFragment", "Arguments: " + getArguments());

            PreferenceManager.setDefaultValues(getActivity(), R.xml.preference_calendars, false);
            addPreferencesFromResource(R.xml.preference_calendars);

            initPref_calendars(CalendarPrefsFragment.this);
        }
    }

    private void initPref_calendars()
    {
        CheckBoxPreference calendarsEnabledPref = (CheckBoxPreference) findPreference(AppSettings.PREF_KEY_CALENDARS_ENABLED);
        initPref_calendars(this, calendarsEnabledPref);
    }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void initPref_calendars(PreferenceFragment fragment)
    {
        CheckBoxPreference calendarsEnabledPref = (CheckBoxPreference) fragment.findPreference(AppSettings.PREF_KEY_CALENDARS_ENABLED);
        initPref_calendars(fragment.getActivity(), calendarsEnabledPref);
    }
    private static void initPref_calendars(final Activity activity, final CheckBoxPreference enabledPref )
    {
        final Preference.OnPreferenceChangeListener onPreferenceChanged0 = new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                boolean enabled = (Boolean)newValue;
                int calendarPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CALENDAR);
                if (calendarPermission != PackageManager.PERMISSION_GRANTED)
                {
                    final int requestCode = (enabled ? REQUEST_CALENDARPREFSFRAGMENT_ENABLED : REQUEST_CALENDARPREFSFRAGMENT_DISABLED);
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_CALENDAR))
                    {
                        String permissionMessage = activity.getString(R.string.privacy_permission_calendar);
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setTitle(activity.getString(R.string.privacy_permissiondialog_title))
                                .setMessage(SuntimesUtils.fromHtml(permissionMessage))
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.WRITE_CALENDAR }, requestCode);
                                        tmp_calendarPref = enabledPref;
                                    }
                                });

                        if (Build.VERSION.SDK_INT >= 11)
                            builder.setIconAttribute(R.attr.icActionWarning);
                        else builder.setIcon(R.drawable.ic_action_warning);

                        builder.show();
                        return false;

                    } else {
                        ActivityCompat.requestPermissions(activity, new String[] { Manifest.permission.WRITE_CALENDAR }, requestCode);
                        tmp_calendarPref = enabledPref;
                        return false;
                    }

                } else {
                    runCalendarTask(activity, enabled);
                    return true;
                }
            }
        };
        enabledPref.setOnPreferenceChangeListener(onPreferenceChanged0);
    }
    private static CheckBoxPreference tmp_calendarPref = null;

    private static void runCalendarTask(final Activity activity, boolean enabled)
    {
        SuntimesCalendarTask calendarTask = new SuntimesCalendarTask(activity);
        if (!enabled) {
            calendarTask.setFlagClearCalendars(true);
        }
        calendarTask.execute();
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    /**
     * Locale Prefs
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class LocalePrefsFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            AppSettings.initLocale(getActivity());
            Log.i(LOG_TAG, "LocalePrefsFragment: Arguments: " + getArguments());

            PreferenceManager.setDefaultValues(getActivity(), R.xml.preference_locale, false);
            addPreferencesFromResource(R.xml.preference_locale);

            initPref_locale(LocalePrefsFragment.this);
        }
    }

    /**
     * init legacy prefs
     */
    private void initPref_locale()
    {
        //String key = AppSettings.PREF_KEY_LOCALE_MODE;
        //ListPreference modePref = (ListPreference)findPreference(key);
        //legacyPrefs.put(key, new LegacyListPref(modePref));

        String key = AppSettings.PREF_KEY_LOCALE;
        //noinspection deprecation
        ListPreference localePref = (ListPreference)findPreference(key);
        //legacyPrefs.put(key, new LegacyListPref(localePref));

        initPref_locale(this, localePref);
    }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void initPref_locale(PreferenceFragment fragment)
    {
        ListPreference localePref = (ListPreference)fragment.findPreference(AppSettings.PREF_KEY_LOCALE);
        initPref_locale(fragment.getActivity(), localePref);
    }
    private static void initPref_locale(Activity activity, ListPreference localePref)
    {
        final String[] localeDisplay = activity.getResources().getStringArray(R.array.locale_display);
        final String[] localeDisplayNative = activity.getResources().getStringArray(R.array.locale_display_native);
        final String[] localeValues = activity.getResources().getStringArray(R.array.locale_values);

        Integer[] index = getSortedOrder(localeDisplayNative);
        CharSequence[] entries = new CharSequence[localeDisplay.length];
        CharSequence[] values = new CharSequence[localeValues.length];
        for (int i=0; i<index.length; i++)
        {
            int j = index[i];
            CharSequence formattedDisplayString;
            if (localeDisplay[j].equals(localeDisplayNative[j]))
            {
                formattedDisplayString = localeDisplayNative[j];

            } else {
                String localizedName = "(" + localeDisplay[j] + ")";
                String displayString = localeDisplayNative[j] + " " + localizedName;
                formattedDisplayString = SuntimesUtils.createRelativeSpan(null, displayString, localizedName, 0.7f);
            }

            entries[i] = formattedDisplayString;
            values[i] = localeValues[j];
        }

        localePref.setEntries(entries);
        localePref.setEntryValues(values);

        AppSettings.LocaleMode localeMode = AppSettings.loadLocaleModePref(activity);
        localePref.setEnabled(localeMode == AppSettings.LocaleMode.CUSTOM_LOCALE);
    }

    /**
     * @param stringArray array to perform sort on
     * @return a sorted array of indices pointing into stringArray
     */
    private static Integer[] getSortedOrder(final String[] stringArray)
    {
        Integer[] index = new Integer[stringArray.length];
        for (int i=0; i < index.length; i++)
        {
            index[i] = i;
        }
        Arrays.sort(index, new Comparator<Integer>()
        {
            public int compare(Integer i1, Integer i2)
            {
                return stringArray[i1].compareTo(stringArray[i2]);
            }
        });
        return index;
    }
    
    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    /**
     * Places Prefs
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class PlacesPrefsFragment extends PreferenceFragment
    {
        private PlacesPrefsBase base;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            AppSettings.initLocale(getActivity());
            Log.i(LOG_TAG, "PlacesPrefsFragment: Arguments: " + getArguments());
            setRetainInstance(true);

            PreferenceManager.setDefaultValues(getActivity(), R.xml.preference_places, false);
            addPreferencesFromResource(R.xml.preference_places);

            Preference clearPlacesPref = findPreference("places_clear");
            Preference exportPlacesPref = findPreference("places_export");
            Preference buildPlacesPref = findPreference("places_build");
            base = new PlacesPrefsBase(getActivity(), buildPlacesPref, clearPlacesPref, exportPlacesPref);
        }

        @Override
        public void onStop()
        {
            super.onStop();
            base.onStop();
        }

        @Override
        public void onResume()
        {
            super.onResume();
            base.onResume();
        }

        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onAttach(Context context)
        {
            super.onAttach(context);
            if (base != null)
            {
                base.setParent(context);
            }
        }

        @Override
        public void onAttach(Activity activity)
        {
            super.onAttach(activity);
            if (base != null)
            {
                base.setParent(activity);
            }
        }
    }

    /**
     * Places Prefs - Base
     */
    private static class PlacesPrefsBase
    {
        //public static final String KEY_ISBUILDING = "isbuilding";
        //public static final String KEY_ISCLEARING = "isclearing";
        //public static final String KEY_ISEXPORTING = "isexporting";

        private Context myParent;
        private ProgressDialog progress;

        private BuildPlacesTask buildPlacesTask = null;
        private boolean isBuilding = false;

        private BuildPlacesTask clearPlacesTask = null;
        private boolean isClearing = false;

        private ExportPlacesTask exportPlacesTask = null;
        private boolean isExporting = false;

        public PlacesPrefsBase(Context context, Preference buildPref, Preference clearPref, Preference exportPref)
        {
            myParent = context;

            if (buildPref != null)
                buildPref.setOnPreferenceClickListener(onClickBuildPlaces);

            if (clearPref != null)
                clearPref.setOnPreferenceClickListener(onClickClearPlaces);

            if (exportPref != null)
                exportPref.setOnPreferenceClickListener(onClickExportPlaces);
        }

        public void setParent( Context context )
        {
            myParent = context;
        }

        public void showProgressBuilding()
        {
            progress = ProgressDialog.show(myParent, myParent.getString(R.string.locationbuild_dialog_title), myParent.getString(R.string.locationbuild_dialog_message), true);
        }

        public void showProgressClearing()
        {
            progress = ProgressDialog.show(myParent, myParent.getString(R.string.locationcleared_dialog_title), myParent.getString(R.string.locationcleared_dialog_message), true);
        }

        public void showProgressExporting()
        {
            progress = ProgressDialog.show(myParent, myParent.getString(R.string.locationexport_dialog_title), myParent.getString(R.string.locationexport_dialog_message), true);
        }

        public void dismissProgress()
        {
            if (progress != null && progress.isShowing())
            {
                progress.dismiss();
            }
        }

        /**
         * Build Places (click handler)
         */
        private Preference.OnPreferenceClickListener onClickBuildPlaces = new Preference.OnPreferenceClickListener()
        {
            public boolean onPreferenceClick(Preference preference)
            {
                if (myParent != null)
                {
                    buildPlacesTask = new BuildPlacesTask(myParent);
                    buildPlacesTask.setTaskListener(buildPlacesListener);
                    buildPlacesTask.execute();
                    return true;
                }
                return false;
            }
        };

        /**
         * Build Places (task handler)
         */
        private BuildPlacesTask.TaskListener buildPlacesListener = new BuildPlacesTask.TaskListener()
        {
            @Override
            public void onStarted()
            {
                isBuilding = true;
                showProgressBuilding();
            }

            @Override
            public void onFinished(Integer result)
            {
                buildPlacesTask = null;
                isBuilding = false;
                dismissProgress();
                if (result > 0) {
                    Toast.makeText(myParent, myParent.getString(R.string.locationbuild_toast_success, result.toString()), Toast.LENGTH_LONG).show();
                } // else // TODO: fail msg
            }
        };

        /**
         * Export Places (click handler)
         */
        private Preference.OnPreferenceClickListener onClickExportPlaces = new Preference.OnPreferenceClickListener()
        {
            public boolean onPreferenceClick(Preference preference)
            {
                if (myParent != null)
                {
                    exportPlacesTask = new ExportPlacesTask(myParent, "SuntimesPlaces", true, true);  // export to external cache
                    exportPlacesTask.setTaskListener(exportPlacesListener);
                    exportPlacesTask.execute();
                    return true;
                }
                return false;
            }
        };

        /**
         * Export Places (task handler)
         */
        private ExportPlacesTask.TaskListener exportPlacesListener = new ExportPlacesTask.TaskListener()
        {
            @Override
            public void onStarted()
            {
                isExporting = true;
                showProgressExporting();
            }

            @Override
            public void onFinished(ExportPlacesTask.ExportResult results)
            {
                exportPlacesTask = null;
                isExporting = false;
                dismissProgress();

                if (results.getResult())
                {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.setType("text/csv");
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    try {
                        //Uri shareURI = Uri.fromFile(results.getExportFile());  // this URI works until api26 (throws FileUriExposedException)
                        Uri shareURI = FileProvider.getUriForFile(myParent, "com.forrestguice.suntimeswidget.fileprovider", results.getExportFile());
                        shareIntent.putExtra(Intent.EXTRA_STREAM, shareURI);

                        String successMessage = myParent.getString(R.string.msg_export_success, results.getExportFile().getAbsolutePath());
                        Toast.makeText(myParent.getApplicationContext(), successMessage, Toast.LENGTH_LONG).show();

                        myParent.startActivity(Intent.createChooser(shareIntent, myParent.getResources().getText(R.string.msg_export_to)));
                        return;   // successful export ends here...

                    } catch (Exception e) {
                        Log.e("ExportPlaces", "Failed to share file URI! " + e);
                    }
                }

                File file = results.getExportFile();    // export failed
                String path = ((file != null) ? file.getAbsolutePath() : "<path>");
                String failureMessage = myParent.getString(R.string.msg_export_failure, path);
                Toast.makeText(myParent.getApplicationContext(), failureMessage, Toast.LENGTH_LONG).show();
            }
        };

        /**
         * Clear Places (click handler)
         */
        private Preference.OnPreferenceClickListener onClickClearPlaces = new Preference.OnPreferenceClickListener()
        {
            public boolean onPreferenceClick(Preference preference)
            {
                if (myParent != null)
                {
                    AlertDialog.Builder confirm = new AlertDialog.Builder(myParent)
                            .setTitle(myParent.getString(R.string.locationclear_dialog_title))
                            .setMessage(myParent.getString(R.string.locationclear_dialog_message))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(myParent.getString(R.string.locationclear_dialog_ok), new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int whichButton)
                                {
                                    clearPlacesTask = new BuildPlacesTask(myParent);
                                    clearPlacesTask.setTaskListener(clearPlacesListener);
                                    clearPlacesTask.execute(true);   // clearFlag set to true
                                }
                            })
                            .setNegativeButton(myParent.getString(R.string.locationclear_dialog_cancel), null);

                    confirm.show();
                    return true;
                }
                return false;
            }
        };

        /**
         * Clear Places (task handler)
         */
        private BuildPlacesTask.TaskListener clearPlacesListener = new BuildPlacesTask.TaskListener()
        {
            @Override
            public void onStarted()
            {
                isClearing = true;
                showProgressClearing();
            }

            @Override
            public void onFinished(Integer result)
            {
                clearPlacesTask = null;
                isClearing = false;
                dismissProgress();
                Toast.makeText(myParent, myParent.getString(R.string.locationcleared_toast_success), Toast.LENGTH_LONG).show();
            }
        };

        private void onStop()
        {
            if (isClearing && clearPlacesTask != null)
            {
                clearPlacesTask.pauseTask();
                clearPlacesTask.clearTaskListener();
            }

            if (isExporting && exportPlacesTask != null)
            {
                exportPlacesTask.pauseTask();
                exportPlacesTask.clearTaskListener();
            }

            if (isBuilding && buildPlacesTask != null)
            {
                buildPlacesTask.pauseTask();
                buildPlacesTask.clearTaskListener();
            }

            dismissProgress();
        }

        private void onResume()
        {

            if (isClearing && clearPlacesTask != null && clearPlacesTask.isPaused())
            {
                clearPlacesTask.setTaskListener(clearPlacesListener);
                showProgressClearing();
                clearPlacesTask.resumeTask();
            }

            if (isExporting && exportPlacesTask != null)
            {
                exportPlacesTask.setTaskListener(exportPlacesListener);
                showProgressExporting();
                exportPlacesTask.resumeTask();
            }

            if (isBuilding && buildPlacesTask != null)
            {
                buildPlacesTask.setTaskListener(buildPlacesListener);
                showProgressBuilding();
                buildPlacesTask.resumeTask();
            }
        }
    }

    /**
     * init legacy prefs
     */
    private void initPref_places()
    {
        //noinspection deprecation
        Preference buildPlacesPref = findPreference("places_build");
        //noinspection deprecation
        Preference clearPlacesPref = findPreference("places_clear");
        //noinspection deprecation
        Preference exportPlacesPref = findPreference("places_export");
        placesPrefBase = new PlacesPrefsBase(this, buildPlacesPref, clearPlacesPref, exportPlacesPref);
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    /**
     * User Interface Prefs
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class UIPrefsFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            AppSettings.initLocale(getActivity());
            Log.i(LOG_TAG, "UIPrefsFragment: Arguments: " + getArguments());

            PreferenceManager.setDefaultValues(getActivity(), R.xml.preference_userinterface, false);
            addPreferencesFromResource(R.xml.preference_userinterface);

            initPref_ui(UIPrefsFragment.this);
        }
    }

    /**
     * init legacy prefs
     */
    private void initPref_ui()
    {
        boolean[] showFields = AppSettings.loadShowFieldsPref(this);
        for (int i = 0; i<AppSettings.NUM_FIELDS; i++)
        {
            CheckBoxPreference field = (CheckBoxPreference)findPreference(AppSettings.PREF_KEY_UI_SHOWFIELDS + "_" + i);
            if (field != null) {
                initPref_ui_field(field, this, i, showFields[i]);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void initPref_ui(final PreferenceFragment fragment)
    {
        boolean[] showFields = AppSettings.loadShowFieldsPref(fragment.getActivity());
        for (int i = 0; i<AppSettings.NUM_FIELDS; i++)
        {
            CheckBoxPreference field = (CheckBoxPreference)fragment.findPreference(AppSettings.PREF_KEY_UI_SHOWFIELDS + "_" + i);
            if (field != null) {
                initPref_ui_field(field, fragment.getActivity(), i, showFields[i]);
            }
        }
    }

    private static void initPref_ui_field(CheckBoxPreference field, final Context context, final int k, boolean value)
    {
        field.setChecked(value);
        field.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o)
            {
                if (context != null) {
                    AppSettings.saveShowFieldsPref(context, k, (Boolean) o);
                    return true;
                } else return false;
            }
        });
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    private static void initPref_altitude(final Activity context, final CheckBoxPreference altitudePref)
    {
        TypedArray a = context.obtainStyledAttributes(new int[]{R.attr.icActionAltitude});
        int drawableID = a.getResourceId(0, R.drawable.baseline_terrain_black_18);
        a.recycle();

        String title = context.getString(R.string.configLabel_general_altitude_enabled) + "  [i]";
        ImageSpan altitudeIcon = SuntimesUtils.createImageSpan(context, drawableID, 32, 32, 0);
        SpannableStringBuilder altitudeSpan = SuntimesUtils.createSpan(context, title, "[i]", altitudeIcon);
        altitudePref.setTitle(altitudeSpan);
    }

    private static void loadPref_altitude(Context context, CheckBoxPreference altitudePref)
    {
        boolean useAltitude = WidgetSettings.loadLocationAltitudeEnabledPref(context, 0);
        altitudePref.setChecked(useAltitude);
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////


    private static void initPref_timeFormat(final Activity context, final Preference timeformatPref)
    {
        timeformatPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o)
            {
                timeformatPref.setSummary(timeFormatPrefSummary(WidgetSettings.TimeFormatMode.valueOf((String)o), context));
                return true;
            }
        });
    }

    private static void loadPref_timeFormat(final Activity context, final ListPreference timeformatPref)
    {
        WidgetSettings.TimeFormatMode mode = WidgetSettings.loadTimeFormatModePref(context, 0);
        int index = timeformatPref.findIndexOfValue(mode.name());
        if (index < 0)
        {
            index = 0;
            WidgetSettings.TimeFormatMode mode0 = mode;
            mode = WidgetSettings.TimeFormatMode.values()[index];
            Log.w("loadPref", "timeFormat not found (" + mode0 + ") :: loading " + mode.name() + " instead..");
        }
        timeformatPref.setValueIndex(index);
        timeformatPref.setSummary(timeFormatPrefSummary(mode, context));
    }

    public static String timeFormatPrefSummary(WidgetSettings.TimeFormatMode mode, Context context)
    {
        String summary = "%s";
        if (mode == WidgetSettings.TimeFormatMode.MODE_SYSTEM)
        {
            String sysPref = android.text.format.DateFormat.is24HourFormat(context)
                    ? WidgetSettings.TimeFormatMode.MODE_24HR.getDisplayString()
                    : WidgetSettings.TimeFormatMode.MODE_12HR.getDisplayString();
            summary = context.getString(R.string.configLabel_timeFormatMode_systemsummary, "%s", sysPref);
        }
        return summary;
    }

    //////////////////////////////////////////////////
    //////////////////////////////////////////////////

    private static void initPref_calculator(Context context, final SummaryListPreference calculatorPref, String defaultCalculator)
    {
        initPref_calculator(context, calculatorPref, null, defaultCalculator);
    }
    private static void initPref_calculator(Context context, final SummaryListPreference calculatorPref, int[] requestedFeatures, String defaultCalculator)
    {
        String tagDefault = context.getString(R.string.configLabel_tagDefault);
        String tagPlugin = context.getString(R.string.configLabel_tagPlugin);

        int[] colorAttrs = { R.attr.text_accentColor, R.attr.tagColor_warning };
        TypedArray typedArray = context.obtainStyledAttributes(colorAttrs);
        int colorDefault = ContextCompat.getColor(context, typedArray.getResourceId(0, R.color.text_accent_dark));
        @SuppressLint("ResourceType") int colorPlugin = ContextCompat.getColor(context, typedArray.getResourceId(1, R.color.warningTag_dark));
        typedArray.recycle();

        SuntimesCalculatorDescriptor[] calculators = (requestedFeatures == null ? SuntimesCalculatorDescriptor.values(context)
                                                                                : SuntimesCalculatorDescriptor.values(context, requestedFeatures));
        String[] calculatorEntries = new String[calculators.length];
        String[] calculatorValues = new String[calculators.length];
        CharSequence[] calculatorSummaries = new CharSequence[calculators.length];

        int i = 0;
        for (SuntimesCalculatorDescriptor calculator : calculators)
        {
            calculator.initDisplayStrings(context);
            calculatorEntries[i] = calculatorValues[i] = calculator.getName();

            String displayString = (calculator.getName().equalsIgnoreCase(defaultCalculator))
                                 ? context.getString(R.string.configLabel_prefSummaryTagged, calculator.getDisplayString(), tagDefault)
                                 : calculator.getDisplayString();

            if (calculator.isPlugin()) {
                displayString = context.getString(R.string.configLabel_prefSummaryTagged, displayString, tagPlugin);
            }

            SpannableString styledSummary = SuntimesUtils.createBoldColorSpan(null, displayString, tagDefault, colorDefault);
            styledSummary = SuntimesUtils.createRelativeSpan(styledSummary, displayString, tagDefault, 1.15f);

            styledSummary = SuntimesUtils.createBoldColorSpan(styledSummary, displayString, tagPlugin, colorPlugin);
            styledSummary = SuntimesUtils.createRelativeSpan(styledSummary, displayString, tagPlugin, 1.15f);

            calculatorSummaries[i] = styledSummary;
            i++;
        }

        calculatorPref.setEntries(calculatorEntries);
        calculatorPref.setEntryValues(calculatorValues);
        calculatorPref.setEntrySummaries(calculatorSummaries);
    }
    private static void loadPref_calculator(Context context, SummaryListPreference calculatorPref)
    {
        loadPref_calculator(context, calculatorPref, "");
    }
    private static void loadPref_calculator(Context context, SummaryListPreference calculatorPref, String calculatorName)
    {
        if (context != null && calculatorPref != null)
        {
            SuntimesCalculatorDescriptor currentMode = WidgetSettings.loadCalculatorModePref(context, 0, calculatorName);
            int currentIndex = ((currentMode != null) ? calculatorPref.findIndexOfValue(currentMode.getName()) : -1);
            if (currentIndex >= 0)
            {
                calculatorPref.setValueIndex(currentIndex);

            } else {
                Log.w(LOG_TAG, "loadPref: Unable to load calculator preference! The list is missing an entry for the descriptor: " + currentMode);
                calculatorPref.setValue(null);  // reset to null (so subsequent selection by user gets saved and fixes this condition)
            }
        }
    }


}
