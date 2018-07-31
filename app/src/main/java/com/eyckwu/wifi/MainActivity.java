package com.eyckwu.wifi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
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

import com.eyckwu.wifi.adapter.WifiAdapter;
import com.eyckwu.wifi.wifi.WifiHelper;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSION_REQUEST_CODE = 10000;

    private Switch switch_wifi;
    private RecyclerView rv_wifi;
    private Button scan_wifi;
    private WifiHelper wifiHelper;
    private List<ScanResult> scanResults;
    private WifiAdapter wifiAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiHelper = new WifiHelper(this);
        scanResults = new ArrayList<>();
        initView();
        initPermission();
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
                wifiHelper.startScan(MainActivity.this);
                refresh();
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
}
