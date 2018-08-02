package com.eyckwu.wifi.wifi;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by EyckWu on 2018/8/1.
 */

public class AccessPoint implements Comparable<AccessPoint>{
    static final String TAG = "wifi.AccessPoint";

    /**
     * Lower bound on the 2.4 GHz (802.11b/g/n) WLAN channels
     */
    public static final int LOWER_FREQ_24GHZ = 2400;

    /**
     * Upper bound on the 2.4 GHz (802.11b/g/n) WLAN channels
     */
    public static final int HIGHER_FREQ_24GHZ = 2500;

    /**
     * Lower bound on the 5.0 GHz (802.11a/h/j/n/ac) WLAN channels
     */
    public static final int LOWER_FREQ_5GHZ = 4900;

    /**
     * Upper bound on the 5.0 GHz (802.11a/h/j/n/ac) WLAN channels
     */
    public static final int HIGHER_FREQ_5GHZ = 5900;

    @IntDef({Speed.NONE, Speed.SLOW, Speed.MODERATE, Speed.FAST, Speed.VERY_FAST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Speed {
        /**
         * Constant value representing an unlabeled / unscored network.
         */
        int NONE = 0;
        /**
         * Constant value representing a slow speed network connection.
         */
        int SLOW = 5;
        /**
         * Constant value representing a medium speed network connection.
         */
        int MODERATE = 10;
        /**
         * Constant value representing a fast speed network connection.
         */
        int FAST = 20;
        /**
         * Constant value representing a very fast speed network connection.
         */
        int VERY_FAST = 30;
    }

    public interface AccessPointListener {
        void onAccessPointChanged(AccessPoint accessPoint);
        void onLevelChanged(AccessPoint accessPoint);
    }

    public static final int UNREACHABLE_RSSI = Integer.MIN_VALUE;

    private final Context mContext;
    private WifiConfiguration mConfig;
    /** Maximum age of scan results to hold onto while actively scanning. **/
    private static final long MAX_SCAN_RESULT_AGE_MILLIS = 25000;
    private String ssid;
    private String bssid;
    private int security;
    private int networkId = INVALID_NETWORK_ID;
//    private int networkId ;
    @Speed private int mSpeed = Speed.NONE;
    private int pskType = PSK_UNKNOWN;

    private int mRssi = UNREACHABLE_RSSI;
    private long mSeen = 0;

    private Object mTag;

    private WifiInfo mInfo;
    private NetworkInfo mNetworkInfo;
    AccessPointListener mAccessPointListener;

    private String mFqdn;
    private String mProviderFriendlyName;

    private boolean mIsCarrierAp = false;
    /**
     * The EAP type {@link WifiEnterpriseConfig.Eap} associated with this AP if it is a carrier AP.
     */
    private int mCarrierApEapType = WifiEnterpriseConfig.Eap.NONE;
    private String mCarrierName = null;

    /**
     * Experimental: we should be able to show the user the list of BSSIDs and bands
     *  for that SSID.
     *  For now this data is used only with Verbose Logging so as to show the band and number
     *  of BSSIDs on which that network is seen.
     */
    private final ConcurrentHashMap<String, ScanResult> mScanResultCache =
            new ConcurrentHashMap<String, ScanResult>(32);

    static final String KEY_NETWORKINFO = "key_networkinfo";
    static final String KEY_WIFIINFO = "key_wifiinfo";
    static final String KEY_SCANRESULT = "key_scanresult";
    static final String KEY_SSID = "key_ssid";
    static final String KEY_SECURITY = "key_security";
    static final String KEY_SPEED = "key_speed";
    static final String KEY_PSKTYPE = "key_psktype";
    static final String KEY_SCANRESULTCACHE = "key_scanresultcache";
    static final String KEY_SCOREDNETWORKCACHE = "key_scorednetworkcache";
    static final String KEY_CONFIG = "key_config";
    static final String KEY_FQDN = "key_fqdn";
    static final String KEY_PROVIDER_FRIENDLY_NAME = "key_provider_friendly_name";
    static final String KEY_IS_CARRIER_AP = "key_is_carrier_ap";
    static final String KEY_CARRIER_AP_EAP_TYPE = "key_carrier_ap_eap_type";
    static final String KEY_CARRIER_NAME = "key_carrier_name";
    static final AtomicInteger sLastId = new AtomicInteger(0);

    /**
     * These values are matched in string arrays -- changes must be kept in sync
     */
    public static final int SECURITY_NONE = 0;
    public static final int SECURITY_WEP = 1;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_EAP = 3;

    private static final int PSK_UNKNOWN = 0;
    private static final int PSK_WPA = 1;
    private static final int PSK_WPA2 = 2;
    private static final int PSK_WPA_WPA2 = 3;

    public static final int INVALID_NETWORK_ID = -1;//当前连接的AP是一个新的AP
    public static final int INVALID_RSSI = -127;

    // used to co-relate internal vs returned accesspoint.
    int mId;

    /**
     * The number of distinct wifi levels.
     *
     */
    public static final int SIGNAL_LEVELS = 5;


    public AccessPoint(Context context, Bundle savedState) {
        mContext = context;
        mConfig = savedState.getParcelable(KEY_CONFIG);
        if (mConfig != null) {
            loadConfig(mConfig);
        }
        if (savedState.containsKey(KEY_SSID)) {
            ssid = savedState.getString(KEY_SSID);
        }
        if (savedState.containsKey(KEY_SECURITY)) {
            security = savedState.getInt(KEY_SECURITY);
        }
        if (savedState.containsKey(KEY_SPEED)) {
            mSpeed = savedState.getInt(KEY_SPEED);
        }
        if (savedState.containsKey(KEY_PSKTYPE)) {
            pskType = savedState.getInt(KEY_PSKTYPE);
        }
        mInfo = savedState.getParcelable(KEY_WIFIINFO);
        if (savedState.containsKey(KEY_NETWORKINFO)) {
            mNetworkInfo = savedState.getParcelable(KEY_NETWORKINFO);
        }
        if (savedState.containsKey(KEY_SCANRESULTCACHE)) {
            ArrayList<ScanResult> scanResultArrayList =
                    savedState.getParcelableArrayList(KEY_SCANRESULTCACHE);
            mScanResultCache.clear();
            for (ScanResult result : scanResultArrayList) {
                mScanResultCache.put(result.BSSID, result);
            }
        }
//        if (savedState.containsKey(KEY_SCOREDNETWORKCACHE)) {
//            ArrayList<TimestampedScoredNetwork> scoredNetworkArrayList =
//                    savedState.getParcelableArrayList(KEY_SCOREDNETWORKCACHE);
//            for (TimestampedScoredNetwork timedScore : scoredNetworkArrayList) {
//                mScoredNetworkCache.put(timedScore.getScore().networkKey.wifiKey.bssid, timedScore);
//            }
//        }
        if (savedState.containsKey(KEY_FQDN)) {
            mFqdn = savedState.getString(KEY_FQDN);
        }
        if (savedState.containsKey(KEY_PROVIDER_FRIENDLY_NAME)) {
            mProviderFriendlyName = savedState.getString(KEY_PROVIDER_FRIENDLY_NAME);
        }
        if (savedState.containsKey(KEY_IS_CARRIER_AP)) {
            mIsCarrierAp = savedState.getBoolean(KEY_IS_CARRIER_AP);
        }
        if (savedState.containsKey(KEY_CARRIER_AP_EAP_TYPE)) {
            mCarrierApEapType = savedState.getInt(KEY_CARRIER_AP_EAP_TYPE);
        }
        if (savedState.containsKey(KEY_CARRIER_NAME)) {
            mCarrierName = savedState.getString(KEY_CARRIER_NAME);
        }
        update(mConfig, mInfo, mNetworkInfo);

        // Do not evict old scan results on initial creation
        updateRssi();
        updateSeen();
        mId = sLastId.incrementAndGet();
    }

    public AccessPoint(Context context, WifiConfiguration config) {
        mContext = context;
        loadConfig(config);
        mId = sLastId.incrementAndGet();
    }

//    public AccessPoint(Context context, PasspointConfiguration config) {
//        mContext = context;
//        mFqdn = config.getHomeSp().getFqdn();
//        mProviderFriendlyName = config.getHomeSp().getFriendlyName();
//        mId = sLastId.incrementAndGet();
//    }

    AccessPoint(Context context, AccessPoint other) {
        mContext = context;
        copyFrom(other);
    }

    AccessPoint(Context context, ScanResult result) {
        mContext = context;
        initWithScanResult(result);
        mId = sLastId.incrementAndGet();
    }

    /**
     * Returns a negative integer, zero, or a positive integer if this AccessPoint is less than,
     * equal to, or greater than the other AccessPoint.
     *
     * Sort order rules for AccessPoints:
     *   1. Active before inactive
     *   2. Reachable before unreachable
     *   3. Saved before unsaved
     *   4. Network speed value
     *   5. Stronger signal before weaker signal
     *   6. SSID alphabetically
     *
     * Note that AccessPoints with a signal are usually also Reachable,
     * and will thus appear before unreachable saved AccessPoints.
     */
    @Override
    public int compareTo(@NonNull AccessPoint other) {
        // Active one goes first.
        if (isActive() && !other.isActive()) return -1;
        if (!isActive() && other.isActive()) return 1;

        // Reachable one goes before unreachable one.
        if (isReachable() && !other.isReachable()) return -1;
        if (!isReachable() && other.isReachable()) return 1;

        // Configured (saved) one goes before unconfigured one.
        if (isSaved() && !other.isSaved()) return -1;
        if (!isSaved() && other.isSaved()) return 1;

        // Faster speeds go before slower speeds - but only if visible change in speed label
        if (getSpeed() != other.getSpeed()) {
            return other.getSpeed() - getSpeed();
        }

        // Sort by signal strength, bucketed by level
        int difference = WifiManager.calculateSignalLevel(other.mRssi, SIGNAL_LEVELS)
                - WifiManager.calculateSignalLevel(mRssi, SIGNAL_LEVELS);
        if (difference != 0) {
            return difference;
        }

        // Sort by ssid.
        difference = getSsidStr().compareToIgnoreCase(other.getSsidStr());
        if (difference != 0) {
            return difference;
        }

        // Do a case sensitive comparison to distinguish SSIDs that differ in case only
        return getSsidStr().compareTo(other.getSsidStr());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AccessPoint)) return false;
        return (this.compareTo((AccessPoint) other) == 0);
    }

    @Override
    public int hashCode() {
        int result = 0;
        if (mInfo != null) result += 13 * mInfo.hashCode();
        result += 19 * mRssi;
        result += 23 * networkId;
        result += 29 * ssid.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append("AccessPoint(")
                .append(ssid);
        if (bssid != null) {
            builder.append(":").append(bssid);
        }
        if (isSaved()) {
            builder.append(',').append("saved");
        }
        if (isActive()) {
            builder.append(',').append("active");
        }
//        if (isEphemeral()) {
//            builder.append(',').append("ephemeral");
//        }
        if (isConnectable()) {
            builder.append(',').append("connectable");
        }
        if (security != SECURITY_NONE) {
            builder.append(',').append(securityToString(security, pskType));
        }
        builder.append(",level=").append(getLevel());
        if (mSpeed != Speed.NONE) {
            builder.append(",speed=").append(mSpeed);
        }
//        builder.append(",metered=").append(isMetered());

//        if (WifiTracker.sVerboseLogging) {
//            builder.append(",rssi=").append(mRssi);
//            builder.append(",scan cache size=").append(mScanResultCache.size());
//        }

        return builder.append(')').toString();
    }

    public static String securityToString(int security, int pskType) {
        if (security == SECURITY_WEP) {
            return "WEP";
        } else if (security == SECURITY_PSK) {
            if (pskType == PSK_WPA) {
                return "WPA";
            } else if (pskType == PSK_WPA2) {
                return "WPA2";
            } else if (pskType == PSK_WPA_WPA2) {
                return "WPA_WPA2";
            }
            return "PSK";
        } else if (security == SECURITY_EAP) {
            return "EAP";
        }
        return "NONE";
    }

    public WifiConfiguration getConfig() {
        return mConfig;
    }

    public String getPasspointFqdn() {
        return mFqdn;
    }

    public void clearConfig() {
        mConfig = null;
        networkId = INVALID_NETWORK_ID;
    }

    public WifiInfo getInfo() {
        return mInfo;
    }

    public int getRssi() {
        return mRssi;
    }

    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    public int getSecurity() {
        return security;
    }

    public String getBssid() {
        return bssid;
    }

    public CharSequence getSsid() {
        final SpannableString str = new SpannableString(ssid);
        str.setSpan(new TtsSpan.TelephoneBuilder(ssid).build(), 0, ssid.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return str;
    }

    public String getConfigName() {
        if (mConfig != null && mConfig.isPasspoint()) {
            return mConfig.providerFriendlyName;
        } else if (mFqdn != null) {
            return mProviderFriendlyName;
        } else {
            return ssid;
        }
    }

    public boolean isCarrierAp() {
        return mIsCarrierAp;
    }

    public String getCarrierName() {
        return mCarrierName;
    }

    public boolean isConnectable() {
        return getLevel() != -1 && getDetailedState() == null;
    }

    public NetworkInfo.DetailedState getDetailedState() {
        if (mNetworkInfo != null) {
            return mNetworkInfo.getDetailedState();
        }
        Log.w(TAG, "NetworkInfo is null, cannot return detailed state");
        return null;
    }

    /** Return true if the current RSSI is reachable, and false otherwise. */
    public boolean isReachable() {
        return mRssi != UNREACHABLE_RSSI;
    }

    public boolean isSaved() {
        return networkId != INVALID_NETWORK_ID;
    }

    int getSpeed() { return mSpeed;}

    public String getSsidStr() {
        return ssid;
    }

    /**
     * Copy accesspoint information. NOTE: We do not copy tag information because that is never
     * set on the internal copy.
     * @param that
     */
    void copyFrom(AccessPoint that) {
        that.evictOldScanResults();
        this.ssid = that.ssid;
        this.bssid = that.bssid;
        this.security = that.security;
        this.networkId = that.networkId;
        this.pskType = that.pskType;
        this.mConfig = that.mConfig; //TODO: Watch out, this object is mutated.
        this.mRssi = that.mRssi;
        this.mSeen = that.mSeen;
        this.mInfo = that.mInfo;
        this.mNetworkInfo = that.mNetworkInfo;
        this.mScanResultCache.clear();
        this.mScanResultCache.putAll(that.mScanResultCache);
//        this.mScoredNetworkCache.clear();
//        this.mScoredNetworkCache.putAll(that.mScoredNetworkCache);
        this.mId = that.mId;
        this.mSpeed = that.mSpeed;
//        this.mIsScoredNetworkMetered = that.mIsScoredNetworkMetered;
        this.mIsCarrierAp = that.mIsCarrierAp;
        this.mCarrierApEapType = that.mCarrierApEapType;
        this.mCarrierName = that.mCarrierName;
    }

    private void initWithScanResult(ScanResult result) {
        ssid = result.SSID;
        bssid = result.BSSID;
        security = getSecurity(result);
        if (security == SECURITY_PSK)
            pskType = getPskType(result);

        mScanResultCache.put(result.BSSID, result);
        updateRssi();
        mSeen = result.timestamp; // even if the timestamp is old it is still valid
//        mIsCarrierAp = result.isCarrierAp;
//        mCarrierApEapType = result.carrierApEapType;
//        mCarrierName = result.carrierName;
    }

    public Object getTag() {
        return mTag;
    }

    public void setTag(Object tag) {
        mTag = tag;
    }

    private static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        }
        return SECURITY_NONE;
    }

    private static int getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
        if (wpa2 && wpa) {
            return PSK_WPA_WPA2;
        } else if (wpa2) {
            return PSK_WPA2;
        } else if (wpa) {
            return PSK_WPA;
        } else {
            Log.w(TAG, "Received abnormal flag string: " + result.capabilities);
            return PSK_UNKNOWN;
        }
    }

    /**
     * 信号强度
     */
    private void updateRssi() {
        if (this.isActive()) {
            return;
        }

        int rssi = UNREACHABLE_RSSI;
        for (ScanResult result : mScanResultCache.values()) {
            if (result.level > rssi) {
                rssi = result.level;
            }
        }

        if (rssi != UNREACHABLE_RSSI && mRssi != UNREACHABLE_RSSI) {
            mRssi = (mRssi + rssi) / 2; // half-life previous value
        } else {
            mRssi = rssi;
        }
    }

    /** Updates {@link #mSeen} based on the scan result cache. */
    private void updateSeen() {
        long seen = 0;
        for (ScanResult result : mScanResultCache.values()) {
            if (result.timestamp > seen) {
                seen = result.timestamp;
            }
        }

        // Only replace the previous value if we have a recent scan result to use
        if (seen != 0) {
            mSeen = seen;
        }
    }

    public boolean isActive() {
        return mNetworkInfo != null &&
                (networkId != INVALID_NETWORK_ID ||
                        mNetworkInfo.getState() != NetworkInfo.State.DISCONNECTED);
    }

    private boolean update(WifiConfiguration config, WifiInfo info, NetworkInfo networkInfo) {
        boolean updated = false;
        final int oldLevel = getLevel();
        if (info != null && isInfoForThisAccessPoint(config, info)) {
            updated = (mInfo == null);
            if (mConfig != config) {
                // We do not set updated = true as we do not want to increase the amount of sorting
                // and copying performed in WifiTracker at this time. If issues involving refresh
                // are still seen, we will investigate further.
                update(config); // Notifies the AccessPointListener of the change
            }
            if (mRssi != info.getRssi() && info.getRssi() != INVALID_RSSI) {
                mRssi = info.getRssi();
                updated = true;
            } else if (mNetworkInfo != null && networkInfo != null
                    && mNetworkInfo.getDetailedState() != networkInfo.getDetailedState()) {
                updated = true;
            }
            mInfo = info;
            mNetworkInfo = networkInfo;
        } else if (mInfo != null) {
            updated = true;
            mInfo = null;
            mNetworkInfo = null;
        }
        if (updated && mAccessPointListener != null) {
            mAccessPointListener.onAccessPointChanged(this);

            if (oldLevel != getLevel() /* current level */) {
                mAccessPointListener.onLevelChanged(this);
            }
        }

        return updated;
    }

    /**
     * Notifies the AccessPointListener of the change
     * @param config
     */
    void update(@Nullable WifiConfiguration config) {
        mConfig = config;
        networkId = config != null ? config.networkId : INVALID_NETWORK_ID;
        if (mAccessPointListener != null) {
            mAccessPointListener.onAccessPointChanged(this);
        }
    }

    /**
     * Return whether the given {@link WifiInfo} is for this access point.
     * If the current AP does not have a network Id then the config is used to
     * match based on SSID and security.
     */
    private boolean isInfoForThisAccessPoint(WifiConfiguration config, WifiInfo info) {
        if (isPasspoint() == false && networkId != INVALID_NETWORK_ID) {
            return networkId == info.getNetworkId();
        } else if (config != null) {
            return matches(config);
        }
        else {
            // Might be an ephemeral connection with no WifiConfiguration. Try matching on SSID.
            // (Note that we only do this if the WifiConfiguration explicitly equals INVALID).
            // TODO: Handle hex string SSIDs.
            return ssid.equals(removeDoubleQuotes(info.getSSID()));
        }
    }

    public boolean matches(WifiConfiguration config) {
        if (config.isPasspoint() && mConfig != null && mConfig.isPasspoint()) {
            return ssid.equals(removeDoubleQuotes(config.SSID)) && config.FQDN.equals(mConfig.FQDN);
        } else {
            return ssid.equals(removeDoubleQuotes(config.SSID))
                    && security == getSecurity(config)
//                    && (mConfig == null || mConfig.shared == config.shared); //shared用于同一设备多用户共享
                    && (mConfig == null );
        }
    }

    private void evictOldScanResults() {
        long nowMs = SystemClock.elapsedRealtime();
        for (Iterator<ScanResult> iter = mScanResultCache.values().iterator(); iter.hasNext(); ) {
            ScanResult result = iter.next();
            // result timestamp is in microseconds
            if (nowMs - result.timestamp / 1000 > MAX_SCAN_RESULT_AGE_MILLIS) {
                iter.remove();
            }
        }
    }

    /**
     * Return true if this AccessPoint represents a Passpoint AP.
     */
    public boolean isPasspoint() {
        return mConfig != null && mConfig.isPasspoint();
    }

    /**
     * Returns the number of levels to show for a Wifi icon, from 0 to {@link #SIGNAL_LEVELS}-1.
     *
     * <p>Use {@#isReachable()} to determine if an AccessPoint is in range, as this method will
     * always return at least 0.
     */
    public int getLevel() {
        return WifiManager.calculateSignalLevel(mRssi, SIGNAL_LEVELS);
    }

    void loadConfig(WifiConfiguration config){
        ssid = (config.SSID == null ? "" : removeDoubleQuotes(config.SSID));
        bssid = config.BSSID;
        security = getSecurity(config);
        networkId = config.networkId;
        mConfig = config;
    }

    private int getSecurity(WifiConfiguration config) {
        if(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)){
            return SECURITY_PSK;
        }
        if(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
            return SECURITY_EAP;
        }
        return (config.wepKeys[0] != null) ? SECURITY_WEP : SECURITY_NONE;
    }

    /**
     * 去掉双引号
     * @param string
     * @return
     */
    static String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }
}
