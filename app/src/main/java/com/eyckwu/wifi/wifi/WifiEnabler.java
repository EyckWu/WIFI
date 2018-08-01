package com.eyckwu.wifi.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.widget.Switch;
import android.widget.Toast;

import com.eyckwu.wifi.R;
import com.eyckwu.wifi.widget.SwitchBar;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by EyckWu on 2018/8/1.
 */

public class WifiEnabler implements SwitchBar.OnSwitchChangeListener {
    private WifiManager mWifiManager;
    private SwitchBar mSwitchBar;
    private Context mContext;
    private IntentFilter mIntentFilter;
    private AtomicBoolean mConnected = new AtomicBoolean(false);

    private boolean mListeningToOnSwitchChange = false;

    private boolean mStateMachineEvent;//避免由于其他原因改变switchBar的状态
    
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiStateChange(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
            }else if(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {//并没有实现
                if(!mConnected.get()) {
                    handleStateChanged(WifiInfo.getDetailedStateOf((SupplicantState) intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE)));
                }
            }else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {//并没有实现
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                mConnected.set(networkInfo.isConnected());
                handleStateChanged(networkInfo.getDetailedState());
            }
        }
    };

    private static final String EVENT_DATA_IS_WIFI_ON = "is_wifi_on";
    private static final int EVENT_UPDATE_INDEX = 0;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_UPDATE_INDEX :
                    boolean isWifiOn = msg.getData().getBoolean(EVENT_DATA_IS_WIFI_ON);
//                    Index
                    break;
            }
        }
    };

    @Override
    public void onSwitchChanged(Switch switchView, boolean isChecked) {
        if(mStateMachineEvent) {
            return;
        }
        // Disable tethering if enabling Wifi
        if (mayDisableTethering(isChecked)) {
//            mWifiManager.setWifiApEnabled(null, false);
        }
        if (!mWifiManager.setWifiEnabled(isChecked)) {
            // Error
            mSwitchBar.setEnabled(true);
            Toast.makeText(mContext, R.string.wifi_error, Toast.LENGTH_SHORT).show();
        }
    }

    public WifiEnabler(Context context, SwitchBar switchBar){
        mContext = context;
        mSwitchBar = switchBar;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mIntentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);//WiFi状态变化
        mIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);//网络状态变化

        setUpSwitchBar();

    }

    private void setUpSwitchBar() {
        int state = mWifiManager.getWifiState();
        handleWifiStateChange(state);
        if(!mListeningToOnSwitchChange) {
            mSwitchBar.addOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = true;
        }
        mSwitchBar.show();
    }

    public void teardownSwitchBar() {
        if (mListeningToOnSwitchChange) {
            mSwitchBar.removeOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = false;
        }
        mSwitchBar.hide();
    }

    public void resume(Context context){
        this.mContext = context;
        context.registerReceiver(receiver, mIntentFilter);
        if(!mListeningToOnSwitchChange) {
            mSwitchBar.addOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = true;
        }
    }

    public void pause(){
        mContext.unregisterReceiver(receiver);
        if(mListeningToOnSwitchChange) {
            mSwitchBar.removeOnSwitchChangeListener(this);
            mListeningToOnSwitchChange = false;
        }
    }

    private void handleWifiStateChange(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING ://正在开启
                mSwitchBar.setEnable(false);
                break;

            case WifiManager.WIFI_STATE_ENABLED://已经开启
                setSwitchBarChecked(true);
                mSwitchBar.setEnable(true);
                updateSearchIndex(true);
                break;

            case WifiManager.WIFI_STATE_DISABLING://正在关闭
                mSwitchBar.setEnable(false);
                break;

            case WifiManager.WIFI_STATE_DISABLED://已经关闭
            default://其他情况
                setSwitchBarChecked(false);
                mSwitchBar.setEnable(true);
                updateSearchIndex(false);
                break;
        }
        if(mayDisableTethering(!mSwitchBar.isChecked())) {
            mSwitchBar.setEnable(false);
        }
    }

    /**
     * 需要系统API
     * @param isChecked
     * @return
     */
    private boolean mayDisableTethering(boolean isChecked) {
//        int wifiApState = mWifiManager.getWifiApState();
//        return isChecked && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
//                (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED));
        return false;
    }

    private void updateSearchIndex(boolean isWiFiOn) {
        mHandler.removeMessages(EVENT_UPDATE_INDEX);

        Message msg = new Message();
        msg.what = EVENT_UPDATE_INDEX;
        msg.getData().putBoolean(EVENT_DATA_IS_WIFI_ON, isWiFiOn);
        mHandler.sendMessage(msg);
    }

    private void setSwitchBarChecked(boolean checked) {
        mStateMachineEvent = true;
        mSwitchBar.setChecked(checked);
        mStateMachineEvent = false;
    }

    private void handleStateChanged(@SuppressWarnings("unused") NetworkInfo.DetailedState state) {
        // After the refactoring from a CheckBoxPreference to a Switch, this method is useless since
        // there is nowhere to display a summary.
        // This code is kept in case a future change re-introduces an associated text.
        /*
        // WifiInfo is valid if and only if Wi-Fi is enabled.
        // Here we use the state of the switch as an optimization.
        if (state != null && mSwitch.isChecked()) {
            WifiInfo info = mWifiManager.getConnectionInfo();
            if (info != null) {
                //setSummary(Summary.get(mContext, info.getSSID(), state));
            }
        }
        */
    }
}
