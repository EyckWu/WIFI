package com.eyckwu.preferencefragmentdemo;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;

/**
 * Created by EyckWu on 2018/7/31.
 */

public class PrefFragment extends PreferenceFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);

    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if("select_linkage".equals(preference.getKey())) {
            CheckBoxPreference checkbox = (CheckBoxPreference) findPreference("select_linkage");
            ListPreference editBox = (ListPreference) findPreference("select_city");
            editBox.setEnabled(checkbox.isChecked());
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
