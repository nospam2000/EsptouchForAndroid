package com.espressif.esptouch.android.v2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;

import com.espressif.esptouch.android.EspTouchActivityAbs;
import com.espressif.esptouch.android.EspTouchApp;
import com.espressif.esptouch.android.R;
import com.espressif.esptouch.android.databinding.ActivityEsptouch2Binding;
import com.espressif.iot.esptouch2.provision.EspProvisioner;
import com.espressif.iot.esptouch2.provision.EspProvisioningRequest;
import com.espressif.iot.esptouch2.provision.EspSyncListener;
import com.espressif.iot.esptouch2.provision.IEspProvisioner;
import com.espressif.iot.esptouch2.provision.TouchNetUtil;

import java.lang.ref.WeakReference;
import java.net.InetAddress;

public class EspTouch2Activity extends EspTouchActivityAbs {
    private static final String TAG = EspTouch2Activity.class.getSimpleName();

    private static final int REQUEST_PERMISSION = 0x01;

    private EspProvisioner mProvisioner;

    private ActivityEsptouch2Binding mBinding;

    private InetAddress mAddress;
    private String mSsid;
    private byte[] mSsidBytes;
    private String mBssid;
    private CharSequence mMessage;
    private int mMessageVisible;
    private int mControlVisible;

    private Context mContext;

    private ActivityResultLauncher<Intent> mProvisionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = ActivityEsptouch2Binding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mProvisionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> mBinding.confirmBtn.setEnabled(true)
        );

        mBinding.controlGroup.setVisibility(View.INVISIBLE);
        mBinding.confirmBtn.setOnClickListener(v -> {
            if (launchProvisioning()) {
                mBinding.confirmBtn.setEnabled(false);
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        EspTouchApp.getInstance().observeBroadcast(this, action -> check());

        check();

        mContext = this;
        String wifiPassword = PreferenceManager.getString(mContext, "wifi_password");
        mBinding.apPasswordEdit.setText(wifiPassword);
        String wifiAES = PreferenceManager.getString(mContext, "wifi_aes");
        mBinding.aesKeyEdit.setText(wifiAES);
        String mqttHost = PreferenceManager.getString(mContext, "mqtt_host");
        mBinding.mqttHostEdit.setText(mqttHost);
        String mqttPort = PreferenceManager.getString(mContext, "mqtt_port");
        mBinding.mqttPortEdit.setText(mqttPort);
        String mqttUser = PreferenceManager.getString(mContext, "mqtt_user");
        mBinding.mqttUserEdit.setText(mqttUser);
        String mqttPassword = PreferenceManager.getString(mContext, "mqtt_password");
        mBinding.mqttPasswordEdit.setText(mqttPassword);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mProvisioner = new EspProvisioner(getApplicationContext());
        SyncListener syncListener = new SyncListener(mProvisioner);
        mProvisioner.startSync(syncListener);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mProvisioner != null) {
            mProvisioner.stopSync();
            mProvisioner.close();
            mProvisioner = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            check();
            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected String getEspTouchVersion() {
        return getString(R.string.esptouch2_about_version, IEspProvisioner.ESPTOUCH_VERSION);
    }

    private boolean launchProvisioning() {
        EspProvisioningRequest request = genRequest();
        if (request == null) {
            return false;
        }
        if (mProvisioner != null) {
            mProvisioner.close();
        }

        Intent intent = new Intent(EspTouch2Activity.this, EspProvisioningActivity.class);
        intent.putExtra(EspProvisioningActivity.KEY_PROVISION_REQUEST, request);
        intent.putExtra(EspProvisioningActivity.KEY_DEVICE_COUNT, getDeviceCount());
        mProvisionLauncher.launch(intent);

        return true;
    }

    private boolean checkEnable() {
        StateResult stateResult = checkState();
        if (!stateResult.permissionGranted) {
            mMessage = stateResult.message;
            mBinding.messageView.setOnClickListener(v -> {
                String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
                ActivityCompat.requestPermissions(EspTouch2Activity.this, permissions, REQUEST_PERMISSION);
            });
            return false;
        }

        mBinding.messageView.setOnClickListener(null);

        mSsid = stateResult.ssid;
        mSsidBytes = stateResult.ssidBytes;
        mBssid = stateResult.bssid;
        mMessage = stateResult.message;
        mAddress = stateResult.address;

        if (stateResult.wifiConnected && stateResult.is5G) {
            mBinding.hintView.setText(R.string.esptouch_message_wifi_frequency);
        } else {
            mBinding.hintView.setText("");
        }

        return stateResult.wifiConnected;
    }

    private byte[] getBssidBytes() {
        return mBssid == null ? null : TouchNetUtil.convertBssid2Bytes(mBssid);
    }

    private void invalidateAll() {
        mBinding.controlGroup.setVisibility(mControlVisible);
        mBinding.apSsidText.setText(mSsid);
        mBinding.apBssidText.setText(mBssid);
        mBinding.ipText.setText(mAddress == null ? "" : mAddress.getHostAddress());
        mBinding.messageView.setText(mMessage);
        mBinding.messageView.setVisibility(mMessageVisible);
    }

    private void check() {
        if (checkEnable()) {
            mControlVisible = View.VISIBLE;
            mMessageVisible = View.GONE;
//        } else {
//            mControlVisible = View.GONE;
//            mMessageVisible = View.VISIBLE;
//
//            if (mProvisioner != null) {
//                if (mProvisioner.isSyncing()) {
//                    mProvisioner.stopSync();
//                }
//                if (mProvisioner.isProvisioning()) {
//                    mProvisioner.stopProvisioning();
//                }
//            }
        }
        invalidateAll();
    }

    private EspProvisioningRequest genRequest() {
        mBinding.aesKeyEdit.setError(null);
//        mBinding.customDataEdit.setError(null);

        CharSequence aesKeyChars = mBinding.aesKeyEdit.getText();
        byte[] aesKey = null;
        if (aesKeyChars != null && aesKeyChars.length() > 0) {
            aesKey = aesKeyChars.toString().getBytes();
        }
        if (aesKey != null && aesKey.length != 16) {
            mBinding.aesKeyEdit.setError(getString(R.string.esptouch2_aes_key_error));
            return null;
        }

//        CharSequence customDataChars = mBinding.customDataEdit.getText();
        CharSequence mqttHostChars = mBinding.mqttHostEdit.getText();
        CharSequence mqttPortChars = mBinding.mqttPortEdit.getText();
        CharSequence mqttUserChars = mBinding.mqttUserEdit.getText();
        CharSequence mqttPasswordChars = mBinding.mqttPasswordEdit.getText();
        CharSequence customDataChars = mqttHostChars + "/" + mqttPortChars + "/" + mqttUserChars + "/" + mqttPasswordChars;
        byte[] customData = null;
        if (customDataChars != null && customDataChars.length() > 0) {
            customData = customDataChars.toString().getBytes();
        }
        int customDataMaxLen = EspProvisioningRequest.RESERVED_LENGTH_MAX;
        if (customData != null && customData.length > customDataMaxLen) {
            mBinding.hintView.setText(getString(R.string.esptouch2_custom_data_error, customDataMaxLen));
            return null;
        }

        CharSequence password = mBinding.apPasswordEdit.getText();

        PreferenceManager.setString(mContext, "wifi_password", password.toString());
        PreferenceManager.setString(mContext, "wifi_aes", aesKeyChars.toString());
        PreferenceManager.setString(mContext, "mqtt_host", mqttHostChars.toString());
        PreferenceManager.setString(mContext, "mqtt_port", mqttPortChars.toString());
        PreferenceManager.setString(mContext, "mqtt_user", mqttUserChars.toString());
        PreferenceManager.setString(mContext, "mqtt_password", mqttPasswordChars.toString());

        return new EspProvisioningRequest.Builder(getApplicationContext())
                .setSSID(mSsidBytes)
                .setBSSID(getBssidBytes())
                .setPassword(password == null ? null : password.toString().getBytes())
                .setAESKey(aesKey)
                .setReservedData(customData)
                .build();
    }

    private int getDeviceCount() {
        CharSequence deviceCountStr = mBinding.deviceCountEdit.getText();
        int deviceCount = -1;
        if (deviceCountStr != null && deviceCountStr.length() > 0) {
            try {
                deviceCount = Integer.parseInt(deviceCountStr.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return deviceCount;
    }

    private static class SyncListener implements EspSyncListener {
        private final WeakReference<EspProvisioner> provisioner;

        SyncListener(EspProvisioner provisioner) {
            this.provisioner = new WeakReference<>(provisioner);
        }

        @Override
        public void onStart() {
            Log.d(TAG, "SyncListener onStart");
        }

        @Override
        public void onStop() {
            Log.d(TAG, "SyncListener onStop");
        }

        @Override
        public void onError(Exception e) {
            e.printStackTrace();
            EspProvisioner provisioner = this.provisioner.get();
            if (provisioner != null) {
                provisioner.stopSync();
            }
        }
    }
}
