package com.eyckwu.wifi.wifi;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

/**
 * Created by EyckWu on 2018/7/30.
 */

public class WifiHelper {
    private static final String TAG = WifiHelper.class.getSimpleName();
    private WifiManager wifiManager;
    private WifiInfo wifiInfo;
    private List<ScanResult> scanResults;
    private List<WifiConfiguration> wifiConfigurations;
    private int netId = -1;

    public WifiHelper(Context context) {
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.wifiInfo = wifiManager.getConnectionInfo();
    }

    public void openWifi(Context context){
        if(!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }else if(wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
            Toast.makeText(context, "Wifi正在开启", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(context, "Wifi已经开启", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void closeWifi(Context context){
        if(wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(false);
        }else if(wifiManager.getWifiState() == WifiManager.WIFI_STATE_DISABLING) {
            Toast.makeText(context, "Wifi正在关闭", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(context, "Wifi已经关闭", Toast.LENGTH_SHORT).show();
        }
    }

    public List<WifiConfiguration> getWifiConfigurations() {
        return wifiConfigurations;
    }

    public List<ScanResult> getScanResults() {
        return scanResults;
    }

    public void connectConfiguration(int index){
        if (index >= wifiConfigurations.size()){
            return;
        }
        wifiManager.enableNetwork(wifiConfigurations.get(index).networkId, true);
    }

    public void startScan(Context context){
        int wifiState = wifiManager.getWifiState();
        Log.w(TAG, "wifiState==" + wifiState);
        wifiManager.startScan();
        scanResults = wifiManager.getScanResults();
        for (ScanResult s: scanResults){
            Log.d(TAG, s.toString());
        }
        wifiConfigurations = wifiManager.getConfiguredNetworks();
        if(scanResults == null) {
            Log.w(TAG, "scanResults == null" + scanResults.toString());
            switch (wifiManager.getWifiState()) {
                case WifiManager.WIFI_STATE_ENABLED :
                    Toast.makeText(context, "当前区域没有网络", Toast.LENGTH_SHORT).show();
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    Toast.makeText(context, "wifi正在开启", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(context, "wifi没有开启", Toast.LENGTH_SHORT).show();
            }
        }
        Log.w(TAG, "scanResults != null" + scanResults.toString());
    }

    public String getMacAddress(){
        return wifiInfo == null ? "NULL" : wifiInfo.getMacAddress();
    }

    public String getBSSID(){
        return wifiInfo == null ? "NULL" : wifiInfo.getBSSID();
    }

    public String getSSID(){
        return wifiInfo == null ? "NULL" : wifiInfo.getSSID();
    }

    public int getIP(){
        return wifiInfo == null ? 0 : wifiInfo.getIpAddress();
    }

    public int getNetworkId(){
        return wifiInfo == null ? 0 : wifiInfo.getNetworkId();
    }

    public void addNetwork(WifiConfiguration wcf){
        netId = wifiManager.addNetwork(wcf);
        wifiManager.enableNetwork(netId, true);
    }

    public void disconnectWifi(int id){
        wifiManager.disableNetwork(id);
        wifiManager.disconnect();
    }

    public void removeWifi(int id){
        disconnectWifi(id);
        wifiManager.removeNetwork(id);
    }

    public String getConnectState(){
        return wifiManager.getConnectionInfo().getSSID();
    }

    public WifiConfiguration createWifiConfiguration(String SSID, String password, int type){
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.allowedAuthAlgorithms.clear();
        wifiConfiguration.allowedGroupCiphers.clear();
        wifiConfiguration.allowedKeyManagement.clear();
        wifiConfiguration.allowedPairwiseCiphers.clear();
        wifiConfiguration.allowedProtocols.clear();
        wifiConfiguration.SSID = SSID;

        WifiConfiguration tempConfig = exsits(SSID);
        if(tempConfig != null) {
            wifiManager.removeNetwork(tempConfig.networkId);
        }
        if(1 == type) {
            wifiConfiguration.wepKeys[0] = "";
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfiguration.wepTxKeyIndex = 0;
        }else if(2 == type) {
            wifiConfiguration.hiddenSSID = true;
            wifiConfiguration.wepKeys[0]= "\""+password+"\"";
            wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wifiConfiguration.wepTxKeyIndex = 0;
        }else if(3 == type) {
            wifiConfiguration.preSharedKey = "\""+password+"\"";
            wifiConfiguration.hiddenSSID = true;
            wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            //config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wifiConfiguration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wifiConfiguration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wifiConfiguration.status = WifiConfiguration.Status.ENABLED;
        }
        return wifiConfiguration;
    }

    private WifiConfiguration exsits(String SSID)
    {
        List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs)
        {
            if (existingConfig.SSID.equals("\""+SSID+"\""))
            {
                return existingConfig;
            }
        }
        return null;
    }


}
