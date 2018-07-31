package com.eyckwu.wifi.adapter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.net.wifi.ScanResult;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eyckwu.wifi.R;
import com.eyckwu.wifi.wifi.WifiHelper;

import java.util.List;

/**
 * Created by EyckWu on 2018/7/30.
 */

public class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.ViewHolder> {
    private static final String TAG = WifiAdapter.class.getSimpleName();
    private Context mContext;
    private List<ScanResult> scanResults;
    private WifiHelper wifiHelper;
    private String mSsid;
    private String mPsw = "";

    public WifiAdapter(Context mContext, List<ScanResult> scanResults, WifiHelper wifiHelper) {
        this.mContext = mContext;
        this.scanResults = scanResults;
        this.wifiHelper = wifiHelper;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.layout_wifi_item, null);
        ViewHolder viewHolder = new ViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Log.w(TAG, "scan::" + scanResults.toString());
        holder.ssid_wifi.setText("ssid::" + scanResults.get(position).SSID);
        holder.bssid_wifi.setText("bssid::" + scanResults.get(position).BSSID);
        holder.capabilities_wifi.setText("capabilities::" + scanResults.get(position).capabilities);
        holder.frequency_wifi.setText("frequency::" + scanResults.get(position).frequency + "");
        holder.lever_wifi.setText("lever::" + scanResults.get(position).level + "");
    }

    @Override
    public int getItemCount() {
        return scanResults.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder{
        private LinearLayout ll_wifi;
        private TextView bssid_wifi;
        private TextView ssid_wifi;
        private TextView capabilities_wifi;
        private TextView frequency_wifi;
        private TextView lever_wifi;


        public ViewHolder(View itemView) {
            super(itemView);
            bssid_wifi = (TextView) itemView.findViewById(R.id.bssid_wifi);
            ssid_wifi = (TextView) itemView.findViewById(R.id.ssid_wifi);
            capabilities_wifi = (TextView) itemView.findViewById(R.id.capabilities_wifi);
            frequency_wifi = (TextView) itemView.findViewById(R.id.frequency_wifi);
            lever_wifi = (TextView) itemView.findViewById(R.id.lever_wifi);
            ll_wifi = (LinearLayout) itemView.findViewById(R.id.ll_wifi);
            ll_wifi.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean free = isFree(scanResults.get(getAdapterPosition()).capabilities);
                    mSsid = scanResults.get(getAdapterPosition()).SSID;
                    if(!free) {
                        final EditText psw_input = new EditText(mContext);
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setTitle(mSsid)
                                .setMessage("请输入密码")
                                .setView(psw_input)
                                .setPositiveButton("连接", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mPsw = psw_input.getText().toString();
                                        wifiHelper.addNetwork(wifiHelper.createWifiConfiguration(mSsid,mPsw,3));
                                    }
                                })
                                .create()
                                .show();
                    }
                    wifiHelper.addNetwork(wifiHelper.createWifiConfiguration(mSsid,"",1));
                }
            });
            ll_wifi.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Log.w(TAG, "ssid" + wifiHelper.getConnectState());
                    Log.w(TAG, "curssid" + scanResults.get(getAdapterPosition()).SSID);

                    if(("\"" + scanResults.get(getAdapterPosition()).SSID + "\"").equals(wifiHelper.getConnectState())) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setTitle(mSsid)
                                .setMessage(wifiHelper.getSSID())
                                .setPositiveButton("断开连接", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        wifiHelper.disconnectWifi(wifiHelper.getNetworkId());
                                    }
                                })
                                .create()
                                .show();
                    }

                    return false;
                }
            });
        }
    }

    private boolean isFree(String capabilities) {
         if(capabilities.contains("[WPA2-PSK-CCMP]") || capabilities.contains("WPA-PSK-CCMP")) {
            return false;
         }
         return true;
    }


}
