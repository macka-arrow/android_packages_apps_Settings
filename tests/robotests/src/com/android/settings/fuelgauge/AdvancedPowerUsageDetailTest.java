/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import static com.android.settings.SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.BatteryStats;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.preference.Preference;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.os.BatteryStatsHelper;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settings.testutils.shadow.ShadowActivityManager;
import com.android.settings.testutils.shadow.ShadowEntityHeaderController;
import com.android.settings.widget.EntityHeaderController;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.instantapps.InstantAppDataProvider;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.widget.LayoutPreference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowEntityHeaderController.class, ShadowActivityManager.class})
public class AdvancedPowerUsageDetailTest {
    private static final String APP_LABEL = "app label";
    private static final String SUMMARY = "summary";
    private static final String[] PACKAGE_NAME = {"com.android.app"};
    private static final String USAGE_PERCENT = "16%";
    private static final int ICON_ID = 123;
    private static final int UID = 1;
    private static final int POWER_MAH = 150;
    private static final long BACKGROUND_TIME_MS = 100;
    private static final long FOREGROUND_ACTIVITY_TIME_MS = 123;
    private static final long FOREGROUND_SERVICE_TIME_MS = 444;
    private static final long FOREGROUND_TIME_MS =
            FOREGROUND_ACTIVITY_TIME_MS + FOREGROUND_SERVICE_TIME_MS;
    private static final long PROCSTATE_TOP_TIME_MS = FOREGROUND_ACTIVITY_TIME_MS;
    private static final long BACKGROUND_TIME_US = BACKGROUND_TIME_MS * 1000;
    private static final long FOREGROUND_ACTIVITY_TIME_US = FOREGROUND_ACTIVITY_TIME_MS * 1000;
    private static final long FOREGROUND_SERVICE_TIME_US = FOREGROUND_SERVICE_TIME_MS * 1000;
    private static final long FOREGROUND_TIME_US = FOREGROUND_TIME_MS * 1000;
    private static final long PROCSTATE_TOP_TIME_US = PROCSTATE_TOP_TIME_MS * 1000;
    private static final long PHONE_FOREGROUND_TIME_MS = 250 * 1000;
    private static final long PHONE_BACKGROUND_TIME_MS = 0;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private FragmentActivity mActivity;
    @Mock
    private EntityHeaderController mEntityHeaderController;
    @Mock
    private LayoutPreference mHeaderPreference;
    @Mock
    private ApplicationsState mState;
    @Mock
    private ApplicationsState.AppEntry mAppEntry;
    @Mock
    private Bundle mBundle;
    @Mock
    private BatteryEntry mBatteryEntry;
    @Mock
    private BatteryStatsHelper mBatteryStatsHelper;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private LoaderManager mLoaderManager;
    @Mock
    private BatteryStats.Timer mForegroundActivityTimer;
    @Mock
    private BatteryUtils mBatteryUtils;
    private Context mContext;
    private Preference mForegroundPreference;
    private Preference mBackgroundPreference;
    private AdvancedPowerUsageDetail mFragment;
    private SettingsActivity mTestActivity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(RuntimeEnvironment.application);
        when(mContext.getPackageName()).thenReturn("foo");
        FakeFeatureFactory.setupForTest();

        mFragment = spy(new AdvancedPowerUsageDetail());
        doReturn(mContext).when(mFragment).getContext();
        doReturn(mActivity).when(mFragment).getActivity();
        doReturn(SUMMARY).when(mFragment).getString(anyInt());
        doReturn(APP_LABEL).when(mBundle).getString(nullable(String.class));
        when(mFragment.getArguments()).thenReturn(mBundle);
        doReturn(mLoaderManager).when(mFragment).getLoaderManager();

        ShadowEntityHeaderController.setUseMock(mEntityHeaderController);
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setRecyclerView(nullable(RecyclerView.class), nullable(Lifecycle.class));
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setButtonActions(anyInt(), anyInt());
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setIcon(nullable(Drawable.class));
        doReturn(mEntityHeaderController).when(mEntityHeaderController).setIcon(nullable(
                ApplicationsState.AppEntry.class));
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setLabel(nullable(String.class));
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setLabel(nullable(String.class));
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setLabel(nullable(ApplicationsState.AppEntry.class));
        doReturn(mEntityHeaderController).when(mEntityHeaderController)
                .setSummary(nullable(String.class));

        when(mBatteryEntry.getUid()).thenReturn(UID);
        when(mBatteryEntry.getLabel()).thenReturn(APP_LABEL);
        when(mBatteryEntry.getTimeInBackgroundMs()).thenReturn(BACKGROUND_TIME_MS);
        when(mBatteryEntry.getTimeInForegroundMs()).thenReturn(FOREGROUND_TIME_MS);
        mBatteryEntry.iconId = ICON_ID;

        mFragment.mHeaderPreference = mHeaderPreference;
        mFragment.mState = mState;
        mFragment.mBatteryUtils = new BatteryUtils(RuntimeEnvironment.application);
        mAppEntry.info = mock(ApplicationInfo.class);

        mTestActivity = spy(new SettingsActivity());
        doReturn(mPackageManager).when(mTestActivity).getPackageManager();
        doReturn(mPackageManager).when(mActivity).getPackageManager();
        doReturn(mAppOpsManager).when(mTestActivity).getSystemService(Context.APP_OPS_SERVICE);

        mBatteryUtils = spy(new BatteryUtils(mContext));
        doReturn(FOREGROUND_SERVICE_TIME_US).when(mBatteryUtils).getForegroundServiceTotalTimeUs(
                any(BatteryStats.Uid.class), anyLong());

        final ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);

        Answer<Void> callable = invocation -> {
            mBundle = captor.getValue().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
            System.out.println("mBundle = " + mBundle);
            return null;
        };
        doAnswer(callable).when(mActivity).startActivityAsUser(captor.capture(),
                nullable(UserHandle.class));
        doAnswer(callable).when(mActivity).startActivity(captor.capture());

        mForegroundPreference = new Preference(mContext);
        mBackgroundPreference = new Preference(mContext);
        mFragment.mForegroundPreference = mForegroundPreference;
        mFragment.mBackgroundPreference = mBackgroundPreference;
    }

    @After
    public void reset() {
        ShadowEntityHeaderController.reset();
    }

    @Test
    public void testInitHeader_NoAppEntry_BuildByBundle() {
        mFragment.mAppEntry = null;
        mFragment.initHeader();

        verify(mEntityHeaderController).setIcon(nullable(Drawable.class));
        verify(mEntityHeaderController).setLabel(APP_LABEL);
    }

    @Test
    public void testInitHeader_HasAppEntry_BuildByAppEntry() {
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                new InstantAppDataProvider() {
                    @Override
                    public boolean isInstantApp(ApplicationInfo info) {
                        return false;
                    }
                });
        mFragment.mAppEntry = mAppEntry;
        mFragment.initHeader();

        verify(mEntityHeaderController).setIcon(mAppEntry);
        verify(mEntityHeaderController).setLabel(mAppEntry);
        verify(mEntityHeaderController).setIsInstantApp(false);
    }

    @Test
    public void testInitHeader_HasAppEntry_InstantApp() {
        ReflectionHelpers.setStaticField(AppUtils.class, "sInstantAppDataProvider",
                new InstantAppDataProvider() {
                    @Override
                    public boolean isInstantApp(ApplicationInfo info) {
                        return true;
                    }
                });
        mFragment.mAppEntry = mAppEntry;
        mFragment.initHeader();

        verify(mEntityHeaderController).setIcon(mAppEntry);
        verify(mEntityHeaderController).setLabel(mAppEntry);
        verify(mEntityHeaderController).setIsInstantApp(true);
    }

    @Test
    public void testStartBatteryDetailPage_hasBasicData() {
        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment,
                mBatteryEntry, USAGE_PERCENT);

        assertThat(mBundle.getInt(AdvancedPowerUsageDetail.EXTRA_UID)).isEqualTo(UID);
        assertThat(mBundle.getLong(AdvancedPowerUsageDetail.EXTRA_BACKGROUND_TIME))
            .isEqualTo(BACKGROUND_TIME_MS);
        assertThat(mBundle.getLong(AdvancedPowerUsageDetail.EXTRA_FOREGROUND_TIME))
            .isEqualTo(FOREGROUND_TIME_MS);
        assertThat(mBundle.getString(AdvancedPowerUsageDetail.EXTRA_POWER_USAGE_PERCENT))
            .isEqualTo(USAGE_PERCENT);
    }

    @Test
    public void testStartBatteryDetailPage_NormalApp() {
        when(mBatteryEntry.getDefaultPackageName()).thenReturn(PACKAGE_NAME[0]);

        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment,
                mBatteryEntry, USAGE_PERCENT);

        assertThat(mBundle.getString(AdvancedPowerUsageDetail.EXTRA_PACKAGE_NAME)).isEqualTo(
                PACKAGE_NAME[0]);
    }

    @Test
    public void testStartBatteryDetailPage_SystemApp() {
        when(mBatteryEntry.getDefaultPackageName()).thenReturn(null);

        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment,
                mBatteryEntry, USAGE_PERCENT);

        assertThat(mBundle.getString(AdvancedPowerUsageDetail.EXTRA_LABEL)).isEqualTo(APP_LABEL);
        assertThat(mBundle.getInt(AdvancedPowerUsageDetail.EXTRA_ICON_ID)).isEqualTo(ICON_ID);
        assertThat(mBundle.getString(AdvancedPowerUsageDetail.EXTRA_PACKAGE_NAME)).isNull();
    }

    @Test
    public void testStartBatteryDetailPage_WorkApp() {
        final int appUid = 1010019;
        doReturn(appUid).when(mBatteryEntry).getUid();

        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment,
                mBatteryEntry, USAGE_PERCENT);

        verify(mActivity).startActivityAsUser(any(Intent.class), eq(new UserHandle(10)));
    }

    @Test
    public void testStartBatteryDetailPage_typeUser_startByCurrentUser() {
        when(mBatteryEntry.isUserEntry()).thenReturn(true);

        final int currentUser = 20;
        ShadowActivityManager.setCurrentUser(currentUser);
        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment,
                mBatteryEntry, USAGE_PERCENT);

        verify(mActivity).startActivityAsUser(any(Intent.class), eq(new UserHandle(currentUser)));
    }

    @Test
    public void testStartBatteryDetailPage_noBatteryUsage_hasBasicData() {
        final ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);

        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment, PACKAGE_NAME[0]);

        verify(mActivity).startActivity(captor.capture());

        assertThat(captor.getValue().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS)
            .getString(AdvancedPowerUsageDetail.EXTRA_PACKAGE_NAME))
            .isEqualTo(PACKAGE_NAME[0]);

        assertThat(captor.getValue().getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS)
            .getString(AdvancedPowerUsageDetail.EXTRA_POWER_USAGE_PERCENT))
            .isEqualTo("0%");
    }

    @Test
    public void testStartBatteryDetailPage_batteryEntryNotExisted_extractUidFromPackageName() throws
            PackageManager.NameNotFoundException {
        doReturn(UID).when(mPackageManager).getPackageUid(PACKAGE_NAME[0], 0 /* no flag */);

        AdvancedPowerUsageDetail.startBatteryDetailPage(mActivity, mFragment, PACKAGE_NAME[0]);

        assertThat(mBundle.getInt(AdvancedPowerUsageDetail.EXTRA_UID)).isEqualTo(UID);
    }

    @Test
    public void testInitPreference_hasCorrectSummary() {
        Bundle bundle = new Bundle(4);
        bundle.putLong(AdvancedPowerUsageDetail.EXTRA_BACKGROUND_TIME, BACKGROUND_TIME_MS);
        bundle.putLong(AdvancedPowerUsageDetail.EXTRA_FOREGROUND_TIME, FOREGROUND_TIME_MS);
        bundle.putString(AdvancedPowerUsageDetail.EXTRA_POWER_USAGE_PERCENT, USAGE_PERCENT);
        bundle.putInt(AdvancedPowerUsageDetail.EXTRA_POWER_USAGE_AMOUNT, POWER_MAH);
        when(mFragment.getArguments()).thenReturn(bundle);

        doReturn(mContext.getText(R.string.battery_used_for)).when(mFragment).getText(
                R.string.battery_used_for);
        doReturn(mContext.getText(R.string.battery_active_for)).when(mFragment).getText(
                R.string.battery_active_for);

        mFragment.initPreference();

        assertThat(mForegroundPreference.getSummary().toString()).isEqualTo("Used for 0 min");
        assertThat(mBackgroundPreference.getSummary().toString()).isEqualTo("Active for 0 min");
    }
}
