package com.eyckwu.wifi.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.widget.Toast;

import com.eyckwu.wifi.R;

import net.jcip.annotations.GuardedBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
//import android.net.NetworkScoreManager;

/**
 * Created by EyckWu on 2018/8/1.
 */

public class WifiTracker {
    private NetworkRequest mNetworkRequest;
    private IntentFilter mFilter;
    private boolean mIncludeScans;
    private boolean mIncludeSave;
    private WifiManager mWifiManager;
    private Context mContext;
    private MainHandler mMainHandler;
    private WorkHandler mWorkHandler;
    private WifiListener mWifiListener;
    private boolean mIncludePasspoints;
    private ConnectivityManager mConnectivityManager;
    private Scanner mScanner;
    @GuardedBy("mLock")
    private boolean mRegistered;
    private WifiTrackerNetworkCallback mNetworkCallback;

    private final HashMap<String, Integer> mSeenBssids = new HashMap<>();
    private final HashMap<String, ScanResult> mScanResultCache = new HashMap<>();
    private Integer mScanId = 0;

    private NetworkInfo mLastNetworkInfo;
    private WifiInfo mLastInfo;

    // TODO: Allow control of this?
    // Combo scans can take 5-6s to complete - set to 10s.
    private static final int WIFI_RESCAN_INTERVAL_MS = 10 * 1000;

    private final AtomicBoolean mConnected = new AtomicBoolean(false);
    /**
     * The internal list of access points, synchronized on itself.
     *
     * Never exposed outside this class.
     */
    @GuardedBy("mLock")
    private final List<AccessPoint> mInternalAccessPoints = new ArrayList<>();


    /**
     * Synchronization lock for managing concurrency between main and worker threads.
     *
     * <p>This lock should be held for all background work.
     * TODO(b/37674366): Remove the worker thread so synchronization is no longer necessary.
     */
    private final Object mLock = new Object();

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(TextUtils.isEmpty(action)) {
                return;
            }
            switch (action) {
                case  WifiManager.WIFI_STATE_CHANGED_ACTION:
                    updateWifiState(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN));
                    break;
                case  WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                    mWorkHandler.obtainMessage(WorkHandler.MSG_UPDATE_ACCESS_POINTS,
                            WorkHandler.CLEAR_STALE_SCAN_RESULTS,
                            0
                            ).sendToTarget();
                    break;
//                case  WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION:
//                case  WifiManager.LINK_CONFIGURATION_CHANGED_ACTION:
//
//                    break;
                case  WifiManager.NETWORK_STATE_CHANGED_ACTION:
                    NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if(mConnected.get() != info.isConnected()) {
                        mConnected.set(info.isConnected());
                        mMainHandler.sendEmptyMessage(MainHandler.MSG_CONNECTED_CHANGED);
                    }
                    mWorkHandler.obtainMessage(WorkHandler.MSG_UPDATE_NETWORK_INFO, info).sendToTarget();
                    mWorkHandler.sendEmptyMessage(WorkHandler.MSG_UPDATE_ACCESS_POINTS);
                    break;
                case  WifiManager.RSSI_CHANGED_ACTION://强度变化
                    try {
                        Class<?> wm = mWifiManager.getClass();
                        Method getCurrentNetwork = wm.getDeclaredMethod("getCurrentNetwork");
                        getCurrentNetwork.setAccessible(true);
                        Network currentNetwork = (Network) getCurrentNetwork.invoke(mWifiManager);
                        NetworkInfo info1 = mConnectivityManager.getNetworkInfo(currentNetwork);
                        mWorkHandler.obtainMessage(WorkHandler.MSG_UPDATE_NETWORK_INFO, info1).sendToTarget();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

            }
        }
    };

    private void updateWifiState(int wifiState) {
        mWorkHandler.obtainMessage(WorkHandler.MSG_UPDATE_WIFI_STATE, wifiState, 0).sendToTarget();
        if(!mWifiManager.isWifiEnabled()) {
            clearAccessPointsAndConditionallyUpdate();
        }
    }

    private void clearAccessPointsAndConditionallyUpdate() {
        synchronized (mLock) {
            if (!mInternalAccessPoints.isEmpty()) {
                mInternalAccessPoints.clear();
                if (!mMainHandler.hasMessages(MainHandler.MSG_ACCESS_POINT_CHANGED)) {
                    mMainHandler.sendEmptyMessage(MainHandler.MSG_ACCESS_POINT_CHANGED);
                }
            }
        }
    }

    @GuardedBy("mLock")
    private boolean mStaleScanResults = true;

    public WifiTracker(Context context, WifiListener wifiListener,
                       boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, null, includeSaved, includeScans);
    }

    public WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper,
                       boolean includeSaved, boolean includeScans) {
        this(context, wifiListener, workerLooper, includeSaved, includeScans, false);
    }

    public WifiTracker(Context context, WifiListener wifiListener,
                       boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        this(context, wifiListener, null, includeSaved, includeScans, includePasspoints);
    }

    public WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper,
                       boolean includeSaved, boolean includeScans, boolean includePasspoints) {
        this(context, wifiListener, workerLooper, includeSaved, includeScans, includePasspoints,
                context.getSystemService(WifiManager.class),
                context.getSystemService(ConnectivityManager.class),
                Looper.myLooper()
        );
    }

    @VisibleForTesting
    WifiTracker(Context context, WifiListener wifiListener, Looper workerLooper,
                boolean includeSaved, boolean includeScans, boolean includePasspoints,
                WifiManager wifiManager, ConnectivityManager connectivityManager,
                Looper currentLooper) {
        if (!includeSaved && !includeScans) {
            throw new IllegalArgumentException("Must include either saved or scans");
        }
        mContext = context;
        if(currentLooper == null) {
            // When we aren't on a looper thread, default to the main.
            currentLooper = Looper.getMainLooper();
        }
        mMainHandler = new MainHandler(currentLooper);
        mWorkHandler = new WorkHandler(workerLooper != null ? workerLooper : currentLooper);
        mWifiManager = wifiManager;
        mIncludeSave = includeSaved;
        mIncludeScans = includeScans;
        mWifiListener = wifiListener;
        mIncludePasspoints = includePasspoints;
        mConnectivityManager = connectivityManager;

        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
//        mFilter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
//        mFilter.addAction(WifiManager.LINK_CONFIGURATION_CHANGED_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);

        mNetworkRequest = new NetworkRequest.Builder()
//                .clearCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
    }

    /**
     * Start tracking wifi networks and scores.
     *
     * <p>Registers listeners and starts scanning for wifi networks. If this is not called
     * then forceUpdate() must be called to populate getAccessPoints().
     */
    @MainThread
    public void startTracking(){
        resumeScanning();
        if(!mRegistered) {
            mContext.registerReceiver(mReceiver, mFilter);
            mNetworkCallback = new WifiTrackerNetworkCallback();
            mConnectivityManager.registerNetworkCallback(mNetworkRequest, mNetworkCallback);
            mRegistered = true;
        }
    }

    @MainThread
    public void stopTracking() {
        synchronized (mLock){
            if (mRegistered) {
                mContext.unregisterReceiver(mReceiver);
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
                mRegistered = false;
            }
            pauseScanning();

            mWorkHandler.removePendingMessages();
            mMainHandler.removePendingMessages();
            mStaleScanResults = true;
        }
    }

    public void pauseScanning() {
        if (mScanner != null) {
            mScanner.pause();
            mScanner = null;
        }
    }

    private void resumeScanning() {
        if(mScanner == null) {
            mScanner = new Scanner();
        }
        mWorkHandler.sendEmptyMessage(WorkHandler.MSG_RESUME);
        if(mWifiManager.isWifiEnabled()) {
            mScanner.resume();
        }
    }

    class WifiTrackerNetworkCallback extends ConnectivityManager.NetworkCallback{
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            Class<?> c = mWifiManager.getClass();
            Method getCurrentNetwork = null;
            try {
                getCurrentNetwork = c.getDeclaredMethod("getCurrentNetwork");
                getCurrentNetwork.setAccessible(true);
                Network wifiNetwork = (Network) getCurrentNetwork.invoke(mWifiManager);
                if(network.equals(wifiNetwork)) {
                    mWorkHandler.sendEmptyMessage(WorkHandler.MSG_UPDATE_NETWORK_INFO);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @VisibleForTesting
    class Scanner extends Handler{
        static final int MSG_SCAN = 0;

        private int mRetry = 0;

        public void resume() {
            if(!hasMessages(MSG_SCAN)) {
                sendEmptyMessage(MSG_SCAN);
            }
        }

        public void forceScan(){
            removeMessages(MSG_SCAN);
            sendEmptyMessage(MSG_SCAN);
        }

        public void pause() {
            mRetry = 0;
            removeMessages(MSG_SCAN);
        }

        boolean isScanning(){
            return hasMessages(MSG_SCAN);
        }

        /**
         * 1、 亮屏情况下，在Wifi settings界面，固定扫描，时间间隔为10s。

         2、 亮屏情况下，非Wifi settings界面，二进制指数退避扫描，退避算法：interval*(2^n), 最小间隔min=20s, 最大间隔max=160s.

         3、 灭屏情况下，有保存网络时，若已连接，不扫描，否则，PNO扫描，即只扫描已保存的网络。最小间隔min=20s，最大间隔max=60s. （详见Android wifi PNO扫描流程(Android O)）

         4、 无保存网络情况下，固定扫描，间隔为5分钟，用于通知用户周围存在可用开放网络。
         * @param msg
         */
        @Override
        public void handleMessage(Message msg) {
            if(msg.what != MSG_SCAN) {
                return;
            }
            if(mWifiManager.startScan()) {
                mRetry = 0;
            }else if(++mRetry >= 3) {
                mRetry = 0;
                if(mContext != null){
                    Toast.makeText(mContext, R.string.wifi_fail_to_scan, Toast.LENGTH_LONG).show();
                }
            }
            sendEmptyMessageDelayed(MSG_SCAN, WIFI_RESCAN_INTERVAL_MS);// Combo scans can take 5-6s to complete - set to 10s.一直扫描
        }
    }

    @VisibleForTesting
    final class WorkHandler extends Handler{
        private static final int MSG_UPDATE_ACCESS_POINTS = 0;
        private static final int MSG_UPDATE_NETWORK_INFO = 1;
        private static final int MSG_RESUME = 2;
        private static final int MSG_UPDATE_WIFI_STATE = 3;

        private static final int CLEAR_STALE_SCAN_RESULTS = 1;

        public WorkHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            synchronized (mLock){
                processMessage(msg);
            }
        }

        void processMessage(Message msg) {
            if (!mRegistered) return;
            switch (msg.what) {
                case MSG_UPDATE_ACCESS_POINTS :
                    if (msg.arg1 == CLEAR_STALE_SCAN_RESULTS) {
                        mStaleScanResults = false;
                    }
                    updateAccessPoints();
                    break;

                case MSG_UPDATE_NETWORK_INFO :
                    updateNetworkInfo((NetworkInfo) msg.obj);
                    break;

                case MSG_RESUME :
                    handleResume();
                    break;

                case MSG_UPDATE_WIFI_STATE :
                    if (msg.arg1 == WifiManager.WIFI_STATE_ENABLED) {
                        if (mScanner != null) {
                            // We only need to resume if mScanner isn't null because
                            // that means we want to be scanning.
                            mScanner.resume();
                        }
                    } else {
                        mLastInfo = null;
                        mLastNetworkInfo = null;
                        if (mScanner != null) {
                            mScanner.pause();
                        }
                        synchronized (mLock) {
                            mStaleScanResults = true;
                        }
                    }
                    mMainHandler.obtainMessage(MainHandler.MSG_WIFI_STATE_CHANGED, msg.arg1, 0)
                            .sendToTarget();
                    break;

            }
        }

        void removePendingMessages(){
            removeMessages(MSG_UPDATE_ACCESS_POINTS);
            removeMessages(MSG_UPDATE_NETWORK_INFO);
            removeMessages(MSG_RESUME);
            removeMessages(MSG_UPDATE_WIFI_STATE);
        }
    }

    private void updateNetworkInfo(NetworkInfo obj) {

    }

    private void handleResume() {
        mScanResultCache.clear();
        mSeenBssids.clear();
        mScanId = 0;
    }

    private void updateAccessPoints() {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        final List<ScanResult> newScanResults = mWifiManager.getScanResults();

        synchronized (mLock) {
            if(!mStaleScanResults) {
                updateAccessPointsLocked(newScanResults, configs);
            }
        }
    }

    private void updateAccessPointsLocked(List<ScanResult> newScanResults, List<WifiConfiguration> configs) {

    }

    public WifiManager getManager() {
        return mWifiManager;
    }


    @VisibleForTesting
    final class MainHandler extends Handler{
        @VisibleForTesting static final int MSG_CONNECTED_CHANGED = 0;
        @VisibleForTesting static final int MSG_WIFI_STATE_CHANGED = 1;
        @VisibleForTesting static final int MSG_ACCESS_POINT_CHANGED = 2;
        private static final int MSG_RESUME_SCANNING = 3;
        private static final int MSG_PAUSE_SCANNING = 4;
        public MainHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if(mWifiListener == null) {
                return;
            }
            switch (msg.what) {
                case MSG_CONNECTED_CHANGED :
                    mWifiListener.onConnectedChanged();
                    break;

                case MSG_WIFI_STATE_CHANGED :
                    mWifiListener.onWifiStateChanged(msg.arg1);
                    break;

                case MSG_ACCESS_POINT_CHANGED :
                    if (mStaleScanResults) {
                        copyAndNotifyListeners(false /*notifyListeners*/);
                    } else {
                        copyAndNotifyListeners(true /*notifyListeners*/);
                        mWifiListener.onAccessPointsChanged();
                    }
                    break;

                case MSG_RESUME_SCANNING :
                    if(mScanner != null) {
                        mScanner.resume();
                    }
                    break;

                case MSG_PAUSE_SCANNING :
                    if(mScanner != null) {
                        mScanner.pause();
                    }
                    synchronized (mLock){
                        mStaleScanResults = true;
                    }
                    break;
            }
        }

        void removePendingMessages(){
            removeMessages(MSG_ACCESS_POINT_CHANGED);
            removeMessages(MSG_CONNECTED_CHANGED);
            removeMessages(MSG_WIFI_STATE_CHANGED);
            removeMessages(MSG_PAUSE_SCANNING);
            removeMessages(MSG_RESUME_SCANNING);
        }
    }

    private void copyAndNotifyListeners(boolean notifyListeners) {

    }

    public interface WifiListener {
        /**
         * Called when the state of Wifi has changed, the state will be one of
         * the following.
         *
         * <li>{@link WifiManager#WIFI_STATE_DISABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLED}</li>
         * <li>{@link WifiManager#WIFI_STATE_DISABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_ENABLING}</li>
         * <li>{@link WifiManager#WIFI_STATE_UNKNOWN}</li>
         * <p>
         *
         * @param state The new state of wifi.
         */
        void onWifiStateChanged(int state);

        /**
         * Called when the connection state of wifi has changed and isConnected
         * should be called to get the updated state.
         */
        void onConnectedChanged();

        /**
         * Called to indicate the list of AccessPoints has been updated and
         * getAccessPoints should be called to get the latest information.
         */
        void onAccessPointsChanged();
    }
}
