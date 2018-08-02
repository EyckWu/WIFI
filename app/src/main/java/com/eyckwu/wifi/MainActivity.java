package com.eyckwu.wifi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import com.eyckwu.wifi.adapter.WifiAdapter;
import com.eyckwu.wifi.widget.SwitchBar;
import com.eyckwu.wifi.wifi.WifiEnabler;
import com.eyckwu.wifi.wifi.WifiHelper;
import com.eyckwu.wifi.wifi.WifiTracker;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WifiTracker.WifiListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSION_REQUEST_CODE = 10000;

    private Switch switch_wifi;
    private RecyclerView rv_wifi;
    private Button scan_wifi;
    private SwitchBar switchbar_wifi;
    private WifiHelper wifiHelper;
    private List<ScanResult> scanResults;
    private WifiAdapter wifiAdapter;
    private WifiTracker mWifiTracker;
    private HandlerThread mBgThread;
    private WifiManager mWifiManager;
    private WifiEnabler mWifiEnabler;
    private List<WifiConfiguration> wifiConfigurations;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiHelper = new WifiHelper(this);
        scanResults = new ArrayList<>();
        initView();
        initPermission();
        mBgThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mWifiTracker = new WifiTracker(MainActivity.this, this,mBgThread.getLooper(), true,true,false);
        mWifiManager = mWifiTracker.getManager();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mWifiEnabler = createWifiEnabler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mWifiEnabler != null) {
            mWifiEnabler.resume(MainActivity.this);
        }
        mWifiTracker.startTracking();

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mWifiEnabler != null) {
            mWifiEnabler.pause();
            mWifiEnabler.teardownSwitchBar();
        }
        mWifiTracker.stopTracking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBgThread.quit();
    }

    private WifiEnabler createWifiEnabler() {
        return new WifiEnabler(MainActivity.this,switchbar_wifi);
    }

    private void initPermission() {
        boolean permissionAllGranted = checkPermissionAllGranted(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
        if(permissionAllGranted) {
            return;
        }
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == MY_PERMISSION_REQUEST_CODE) {
            boolean isAllGranted = true;
            for (int grant:grantResults){
                if(grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }
        }
    }

    private void initView() {
        switchbar_wifi = (SwitchBar)findViewById(R.id.switchbar_wifi);
        initSwitch();
        initBtn();
        initRecyclerView();
    }

    private void initRecyclerView() {
        rv_wifi = (RecyclerView)findViewById(R.id.rv_wifi);
        rv_wifi.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        wifiAdapter = new WifiAdapter(MainActivity.this, scanResults,wifiHelper);
        rv_wifi.setAdapter(wifiAdapter);
    }

    private void initBtn() {
        scan_wifi = (Button)findViewById(R.id.scan_wifi);
        scan_wifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w(TAG, "scan_wifi");
                wifiHelper.startScan(MainActivity.this);
//                refresh();
            }
        });
    }

    private void refresh( ) {
        Log.w(TAG, wifiHelper.getScanResults().toString());
        wifiAdapter = new WifiAdapter(MainActivity.this, wifiHelper.getScanResults(),wifiHelper);
        rv_wifi.setAdapter(wifiAdapter);
        wifiAdapter.notifyDataSetChanged();
    }

    private void initSwitch() {
        switch_wifi = (Switch)findViewById(R.id.switch_wifi);
        switch_wifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean checked = switch_wifi.isChecked();
                if(checked) {
                    wifiHelper.openWifi(MainActivity.this);
                    wifiHelper.startScan(MainActivity.this);
                    refresh();
                }else{
                    wifiHelper.closeWifi(MainActivity.this);
                    refresh();
                }

            }
        });
    }

    /**
     * 检查是否拥有指定的所有权限
     */
    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }

    @Override
    public void onWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_DISABLING :
                Log.w(TAG, "WIFI_STATE_DISABLING");
                break;


            case WifiManager.WIFI_STATE_DISABLED :
                Log.w(TAG, "WIFI_STATE_DISABLED");

                break;


            case WifiManager.WIFI_STATE_ENABLING :
                Log.w(TAG, "WIFI_STATE_ENABLING");

                break;


            case WifiManager.WIFI_STATE_ENABLED :
                Log.w(TAG, "WIFI_STATE_ENABLED");

                break;


            case WifiManager.WIFI_STATE_UNKNOWN :
                Log.w(TAG, "WIFI_STATE_UNKNOWN");

                break;


        }
    }

    @Override
    public void onConnectedChanged() {

    }

    @Override
    public void onAccessPointsChanged() {

    }

    @Override
    public void onScanCompleted() {
        scanResults = mWifiManager.getScanResults();
        for (ScanResult s: scanResults){
            Log.d(TAG, s.toString());
        }
        wifiConfigurations = mWifiManager.getConfiguredNetworks();
        if(scanResults == null) {
            Log.w(TAG, "scanResults == null" + scanResults.toString());
            switch (mWifiManager.getWifiState()) {
                case WifiManager.WIFI_STATE_ENABLED :
                    Toast.makeText(MainActivity.this, "当前区域没有网络", Toast.LENGTH_SHORT).show();
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    Toast.makeText(MainActivity.this, "wifi正在开启", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(MainActivity.this, "wifi没有开启", Toast.LENGTH_SHORT).show();
            }
        }
        wifiAdapter = new WifiAdapter(MainActivity.this, scanResults,wifiHelper);
        rv_wifi.setAdapter(wifiAdapter);
        wifiAdapter.notifyDataSetChanged();
    }
}
