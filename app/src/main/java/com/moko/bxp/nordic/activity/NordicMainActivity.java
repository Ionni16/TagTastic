package com.moko.bxp.nordic.activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.AppConstants;
import com.moko.bxp.nordic.adapter.BeaconXListAdapter;
import com.moko.bxp.nordic.BuildConfig;
import com.moko.bxp.nordic.R;
import com.moko.bxp.nordic.R2;
import com.moko.bxp.nordic.dialog.AlertMessageDialog;
import com.moko.bxp.nordic.dialog.LoadingDialog;
import com.moko.bxp.nordic.dialog.LoadingMessageDialog;
import com.moko.bxp.nordic.dialog.PasswordDialog;
import com.moko.bxp.nordic.dialog.ScanFilterDialog;
import com.moko.bxp.nordic.entity.BeaconXInfo;
import com.moko.bxp.nordic.utils.BeaconXInfoParseableImpl;
import com.moko.bxp.nordic.utils.ToastUtils;
import com.moko.support.nordic.MokoBleScanner;
import com.moko.support.nordic.MokoSupport;
import com.moko.support.nordic.OrderTaskAssembler;
import com.moko.support.nordic.callback.MokoScanDeviceCallback;
import com.moko.support.nordic.entity.DeviceInfo;
import com.moko.support.nordic.entity.OrderCHAR;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import butterknife.BindView;
import butterknife.ButterKnife;

// AP
import com.moko.bxp.nordic.BaseApplication;
import android.Manifest;
import android.content.pm.PackageManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.ArrayList;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;


public class NordicMainActivity extends BaseActivity implements MokoScanDeviceCallback, BaseQuickAdapter.OnItemChildClickListener {

    @BindView(R2.id.iv_refresh)
    ImageView ivRefresh;
    @BindView(R2.id.rv_devices)
    RecyclerView rvDevices;
    @BindView(R2.id.tv_device_num)
    TextView tvDeviceNum;
    @BindView(R2.id.rl_edit_filter)
    RelativeLayout rl_edit_filter;
    @BindView(R2.id.rl_filter)
    RelativeLayout rl_filter;
    @BindView(R2.id.tv_filter)
    TextView tv_filter;
    private boolean mReceiverTag = false;
    private ConcurrentHashMap<String, BeaconXInfo> beaconXInfoHashMap;
    private final List<String> macArrivalOrder = new ArrayList<>();

    private boolean mMitechAutoWriteRequested = false;
    private boolean mStartMitechAutoWriteFlow = false;   // serve per passare l’extra a DeviceInfoActivity
    private static final String MITECH_AUTOWRITE_PASSWORD = "20250430";

    // password di default del tag (serve per Verifying/Unlock)
    private static final String DEFAULT_PASSWORD = "Moko4321";


    private ArrayList<BeaconXInfo> beaconXInfos;
    private BeaconXListAdapter adapter;
    private boolean mInputPassword;
    private MokoBleScanner mokoBleScanner;
    private Handler mHandler;
    private boolean isPasswordError;
    public static String PATH_LOGCAT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1001
                );
            }
        }


        // 初始化Xlog
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // 优先保存到SD卡中
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PATH_LOGCAT = getExternalFilesDir(null).getAbsolutePath() + File.separator + (BuildConfig.IS_LIBRARY ? "mokoBeaconXPro" : "TagTastic");
            } else {
                PATH_LOGCAT = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + (BuildConfig.IS_LIBRARY ? "mokoBeaconXPro" : "TagTastic");
            }
        } else {
            // 如果SD卡不存在，就保存到本应用的目录下
            PATH_LOGCAT = getFilesDir().getAbsolutePath() + File.separator + (BuildConfig.IS_LIBRARY ? "mokoBeaconXPro" : "TagTastic");
        }
        MokoSupport.getInstance().init(getApplicationContext());
        beaconXInfoHashMap = new ConcurrentHashMap<>();
        beaconXInfos = new ArrayList<>();
        adapter = new BeaconXListAdapter();
        adapter.replaceData(beaconXInfos);
        adapter.setOnItemChildClickListener(this);
        adapter.openLoadAnimation();
        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.shape_recycleview_divider));
        rvDevices.addItemDecoration(itemDecoration);
        rvDevices.setAdapter(adapter);

        mHandler = new Handler(Looper.getMainLooper());
        mokoBleScanner = new MokoBleScanner(this);
        EventBus.getDefault().register(this);
        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
        mReceiverTag = true;
        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            // 蓝牙未打开，开启蓝牙
            MokoSupport.getInstance().enableBluetooth();
        } else {
            if (animation == null) {
                startScan();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveMacListToFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private String unLockResponse;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    switch (blueState) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            if (animation != null) {
                                mHandler.removeMessages(0);
                                mokoBleScanner.stopScanDevice();
                                onStopScan();
                            }
                            break;
                        case BluetoothAdapter.STATE_ON:
                            if (animation == null) {
                                startScan();
                            }
                            break;

                    }
                }
            }
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            mPassword = "";
            // 设备断开，通知页面更新
            dismissLoadingProgressDialog();
            dismissLoadingMessageDialog();
            if (!mInputPassword && animation == null) {
                if (isPasswordError) {
                    isPasswordError = false;
                } else {
                    ToastUtils.showToast(NordicMainActivity.this, "Connection failed");
                }
                startScan();
            } else {
                mInputPassword = false;
            }
        }
        if (MokoConstants.ACTION_DISCOVER_SUCCESS.equals(action)) {
            // 设备连接成功，通知页面更新
            dismissLoadingProgressDialog();
//            BluetoothGattCharacteristic modelNumberChar = MokoSupport.getInstance().getCharacteristic(OrderCHAR.CHAR_MODEL_NUMBER);
//            BluetoothGattCharacteristic deviceTypeChar = MokoSupport.getInstance().getCharacteristic(OrderCHAR.CHAR_DEVICE_TYPE);
//            if (modelNumberChar != null && deviceTypeChar != null) {
            showLoadingMessageDialog();
            mHandler.postDelayed(() -> {
                if (TextUtils.isEmpty(mPassword)) {
                    ArrayList<OrderTask> orderTasks = new ArrayList<>();
                    orderTasks.add(OrderTaskAssembler.getLockState());
                    MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
                } else {
                    XLog.i("锁定状态，获取unLock，解锁");
                    ArrayList<OrderTask> orderTasks = new ArrayList<>();
                    orderTasks.add(OrderTaskAssembler.getUnLock());
                    MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
                }
            }, 500);

//            } else {
//                MokoSupport.getInstance().disConnectBle();
//                AlertMessageDialog dialog = new AlertMessageDialog();
//                dialog.setMessage("The firmware version selected is incorrect. Please back to the firmware version options and select again.");
//                dialog.setConfirm(R.string.ok);
//                dialog.setOnAlertConfirmListener(() -> {
//                    back();
//                });
//                dialog.show(getSupportFragmentManager());
//            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        final String action = event.getAction();
        if (MokoConstants.ACTION_ORDER_TIMEOUT.equals(action)) {
        }
        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
        }
        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            int responseType = response.responseType;
            byte[] value = response.responseValue;
            switch (orderCHAR) {
                case CHAR_LOCK_STATE: {
                    String valueStr = MokoUtils.bytesToHexString(value);
                    dismissLoadingMessageDialog();

                    if ("00".equals(valueStr)) {
                        // Serve password
                        MokoSupport.getInstance().disConnectBle();

                        if (TextUtils.isEmpty(unLockResponse)) {
                            mInputPassword = true;

                            // ✅ AUTOWRITE: usa password di default
                            if (mMitechAutoWriteRequested || ((BaseApplication) getApplication()).GetMTAutoWriteStatus() == 1) {
                                mSavedPassword = DEFAULT_PASSWORD;
                            } else {
                                mPassword = "";
                                mSavedPassword = "";
                            }

                            PasswordDialog dialog = new PasswordDialog();
                            dialog.setPassword(mSavedPassword);
                            dialog.setOnPasswordClicked(new PasswordDialog.PasswordClickListener() {
                                @Override
                                public void onEnsureClicked(String password) {
                                    if (!MokoSupport.getInstance().isBluetoothOpen()) {
                                        MokoSupport.getInstance().enableBluetooth();
                                        return;
                                    }
                                    mPassword = password;

                                    if (animation != null) {
                                        mHandler.removeMessages(0);
                                        mokoBleScanner.stopScanDevice();
                                    }
                                    showLoadingProgressDialog();
                                    ivRefresh.postDelayed(() -> MokoSupport.getInstance().connDevice(mSelectedBeaconXMac), 500);
                                }

                                @Override
                                public void onDismiss() {
                                    unLockResponse = "";
                                    if (animation == null) startScan();
                                }
                            });

                            // ✅ AUTOWRITE: premi OK da solo
                            if (mMitechAutoWriteRequested || ((BaseApplication) getApplication()).GetMTAutoWriteStatus() == 1) {
                                dialog.setAutomaticOk();
                            }

                            dialog.show(NordicMainActivity.this.getSupportFragmentManager());

                        } else {
                            isPasswordError = true;
                            unLockResponse = "";
                            ToastUtils.showToast(NordicMainActivity.this, "Password incorrect!");
                        }

                    } else if ("02".equals(valueStr)) {
                        // Non serve password
                        BluetoothGattCharacteristic modelNumberChar =
                                MokoSupport.getInstance().getCharacteristic(OrderCHAR.CHAR_MODEL_NUMBER);

                        Intent deviceInfoIntent = new Intent(NordicMainActivity.this, DeviceInfoActivity.class);
                        deviceInfoIntent.putExtra(AppConstants.EXTRA_KEY_PASSWORD, mPassword);
                        deviceInfoIntent.putExtra(AppConstants.IS_NEW_VERSION, null == modelNumberChar);

                        // ✅ passa flag autowrite
                        deviceInfoIntent.putExtra(AppConstants.EXTRA_KEY_MITECH_AUTOWRITE,
                                mMitechAutoWriteRequested || ((BaseApplication) getApplication()).GetMTAutoWriteStatus() == 1);

                        mMitechAutoWriteRequested = false; // ✅ evita rientri
                        startActivityForResult(deviceInfoIntent, AppConstants.REQUEST_CODE_DEVICE_INFO);

                    } else {
                        // Unlock OK
                        unLockResponse = "";
                        mSavedPassword = mPassword;

                        BluetoothGattCharacteristic modelNumberChar =
                                MokoSupport.getInstance().getCharacteristic(OrderCHAR.CHAR_MODEL_NUMBER);

                        Intent deviceInfoIntent = new Intent(NordicMainActivity.this, DeviceInfoActivity.class);
                        deviceInfoIntent.putExtra(AppConstants.EXTRA_KEY_PASSWORD, mPassword);
                        deviceInfoIntent.putExtra(AppConstants.IS_NEW_VERSION, null == modelNumberChar);

                        // ✅ passa flag autowrite
                        deviceInfoIntent.putExtra(AppConstants.EXTRA_KEY_MITECH_AUTOWRITE,
                                mMitechAutoWriteRequested || ((BaseApplication) getApplication()).GetMTAutoWriteStatus() == 1);

                        mMitechAutoWriteRequested = false; // ✅ evita rientri
                        startActivityForResult(deviceInfoIntent, AppConstants.REQUEST_CODE_DEVICE_INFO);
                    }
                    break;
                }


                case CHAR_UNLOCK:
                    if (responseType == OrderTask.RESPONSE_TYPE_READ) {
                        unLockResponse = MokoUtils.bytesToHexString(value);
                        XLog.i("返回的随机数：" + unLockResponse);
                        MokoSupport.getInstance().sendOrder(OrderTaskAssembler.setUnLock(mPassword, value));
                    }
                    if (responseType == OrderTask.RESPONSE_TYPE_WRITE) {
                        MokoSupport.getInstance().sendOrder(OrderTaskAssembler.getLockState());
                    }
                    break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case AppConstants.REQUEST_CODE_DEVICE_INFO:
                    mPassword = "";
                    if (animation == null) {
                        startScan();
                    }
                    break;

            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiverTag) {
            mReceiverTag = false;
            // 注销广播
            unregisterReceiver(mReceiver);
        }
        EventBus.getDefault().unregister(this);
    }


    @Override
    public void onStartScan() {
        beaconXInfoHashMap.clear();
        macArrivalOrder.clear();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (animation != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.replaceData(beaconXInfos);
                            tvDeviceNum.setText(String.format("DEVICE(%d)", beaconXInfos.size()));
                        }
                    });
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    updateDevices();
                }
            }
        }).start();
    }

    private BeaconXInfoParseableImpl beaconXInfoParseable;

    @Override
    public void onScanDevice(DeviceInfo deviceInfo) {
        BeaconXInfo beaconXInfo = beaconXInfoParseable.parseDeviceInfo(deviceInfo);
        if (beaconXInfo == null)
            return;
        String mac = beaconXInfo.mac;

// se è un MAC nuovo, aggiungilo alla lista dell’ordine
        if (!beaconXInfoHashMap.containsKey(mac)) {
            macArrivalOrder.add(mac);
        }

// aggiorna comunque i dati dell’hashmap
        beaconXInfoHashMap.put(mac, beaconXInfo);
    }

    @Override
    public void onStopScan() {
        findViewById(R.id.iv_refresh).clearAnimation();
        animation = null;
    }

    private void updateDevices() {
        beaconXInfos.clear();
        if (!TextUtils.isEmpty(filterName)
                || !TextUtils.isEmpty(filterMac)
                || filterRssi != -100) {
            ArrayList<BeaconXInfo> beaconXInfosFilter = new ArrayList<>(beaconXInfoHashMap.values());
            Iterator<BeaconXInfo> iterator = beaconXInfosFilter.iterator();
            while (iterator.hasNext()) {
                BeaconXInfo beaconXInfo = iterator.next();
                if (beaconXInfo.rssi > filterRssi) {
                    if (TextUtils.isEmpty(filterName) && TextUtils.isEmpty(filterMac)) {
                        continue;
                    } else {
                        if (!TextUtils.isEmpty(filterMac) && TextUtils.isEmpty(beaconXInfo.mac)) {
                            iterator.remove();
                        } else if (!TextUtils.isEmpty(filterMac) && beaconXInfo.mac.toLowerCase().replaceAll(":", "").contains(filterMac.toLowerCase())) {
                            continue;
                        } else if (!TextUtils.isEmpty(filterName) && TextUtils.isEmpty(beaconXInfo.name)) {
                            iterator.remove();
                        } else if (!TextUtils.isEmpty(filterName) && beaconXInfo.name.toLowerCase().contains(filterName.toLowerCase())) {
                            continue;
                        } else {
                            iterator.remove();
                        }
                    }
                } else {
                    iterator.remove();
                }
            }
            beaconXInfos.addAll(beaconXInfosFilter);
        } else {
            beaconXInfos.addAll(beaconXInfoHashMap.values());
        }
        System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        Collections.sort(beaconXInfos, new Comparator<BeaconXInfo>() {
            @Override
            public int compare(BeaconXInfo lhs, BeaconXInfo rhs) {
                if (lhs.rssi > rhs.rssi) {
                    return -1;
                } else if (lhs.rssi < rhs.rssi) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private Animation animation = null;
    public String filterName;
    public String filterMac;
    public int filterRssi = -100;

    private void startScan() {
        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            // 蓝牙未打开，开启蓝牙
            MokoSupport.getInstance().enableBluetooth();
            return;
        }
        animation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
        findViewById(R.id.iv_refresh).startAnimation(animation);
        beaconXInfoParseable = new BeaconXInfoParseableImpl();
        mokoBleScanner.startScanDevice(this);
    }


    private LoadingDialog mLoadingDialog;

    private void showLoadingProgressDialog() {
        mLoadingDialog = new LoadingDialog();
        mLoadingDialog.show(getSupportFragmentManager());

    }

    private void dismissLoadingProgressDialog() {
        if (mLoadingDialog != null)
            mLoadingDialog.dismissAllowingStateLoss();
    }

    private LoadingMessageDialog mLoadingMessageDialog;

    private void showLoadingMessageDialog() {
        mLoadingMessageDialog = new LoadingMessageDialog();
        mLoadingMessageDialog.setMessage("Verifying..");
        mLoadingMessageDialog.show(getSupportFragmentManager());

    }

    private void dismissLoadingMessageDialog() {
        if (mLoadingMessageDialog != null)
            mLoadingMessageDialog.dismissAllowingStateLoss();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private String mPassword;
    private String mSavedPassword;
    private String mSelectedBeaconXMac;


    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {

        int viewId = view.getId();

        if (viewId == R.id.tv_connect) {
            Toast.makeText(this, "tv_connect cliccato!", Toast.LENGTH_SHORT).show();
            ((BaseApplication) getApplication()).SetMTAutoWriteStatus(0);
            mMitechAutoWriteRequested = false;
            mStartMitechAutoWriteFlow = false;
        } else if (viewId == R.id.tv_mtautowrite) {
            Toast.makeText(this, "tv_mtautowrite cliccato!", Toast.LENGTH_SHORT).show();
            ((BaseApplication)getApplication()).SetMTAutoWriteStatus(1);
            mMitechAutoWriteRequested = true; // ✅ serve per auto inserire la password di default
        }


        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            MokoSupport.getInstance().enableBluetooth();
            return;
        }

        final BeaconXInfo beaconXInfo = (BeaconXInfo) adapter.getItem(position);
        if (beaconXInfo != null && !isFinishing()) {
            if (animation != null) {
                mHandler.removeMessages(0);
                mokoBleScanner.stopScanDevice();
            }
            mSelectedBeaconXMac = beaconXInfo.mac;
            showLoadingProgressDialog();
            ivRefresh.postDelayed(() -> MokoSupport.getInstance().connDevice(beaconXInfo.mac), 500);
        }
    }


    public void onBack(View view) {
        if (isWindowLocked())
            return;
        back();
    }

    public void onAbout(View view) {
        if (isWindowLocked())
            return;
        startActivity(new Intent(this, AboutActivity.class));
    }

    public void onFilter(View view) {
        if (isWindowLocked())
            return;
        if (animation != null) {
            mHandler.removeMessages(0);
            mokoBleScanner.stopScanDevice();
        }
        ScanFilterDialog scanFilterDialog = new ScanFilterDialog(this);
        scanFilterDialog.setFilterName(filterName);
        scanFilterDialog.setFilterMac(filterMac);
        scanFilterDialog.setFilterRssi(filterRssi);
        scanFilterDialog.setOnScanFilterListener((filterName, filterMac, filterRssi) -> {
            NordicMainActivity.this.filterName = filterName;
            NordicMainActivity.this.filterMac = filterMac;
            String showFilterMac = "";
            if (filterMac.length() == 12) {
                StringBuffer stringBuffer = new StringBuffer(filterMac);
                stringBuffer.insert(2, ":");
                stringBuffer.insert(5, ":");
                stringBuffer.insert(8, ":");
                stringBuffer.insert(11, ":");
                stringBuffer.insert(14, ":");
                showFilterMac = stringBuffer.toString();
            } else {
                showFilterMac = filterMac;
            }
            NordicMainActivity.this.filterRssi = filterRssi;
            if (!TextUtils.isEmpty(filterName)
                    || !TextUtils.isEmpty(showFilterMac)
                    || filterRssi != -100) {
                rl_filter.setVisibility(View.VISIBLE);
                rl_edit_filter.setVisibility(View.GONE);
                StringBuilder stringBuilder = new StringBuilder();
                if (!TextUtils.isEmpty(filterName)) {
                    stringBuilder.append(filterName);
                    stringBuilder.append(";");
                }
                if (!TextUtils.isEmpty(showFilterMac)) {
                    stringBuilder.append(showFilterMac);
                    stringBuilder.append(";");
                }
                if (filterRssi != -100) {
                    stringBuilder.append(String.format("%sdBm", filterRssi + ""));
                    stringBuilder.append(";");
                }
                tv_filter.setText(stringBuilder.toString());
            } else {
                rl_filter.setVisibility(View.GONE);
                rl_edit_filter.setVisibility(View.VISIBLE);
            }
            if (isWindowLocked())
                return;
            if (animation == null) {
                startScan();
            }
        });
        scanFilterDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (isWindowLocked())
                    return;
                if (animation == null) {
                    startScan();
                }
            }
        });
        scanFilterDialog.show();
    }

    private void back() {
        if (animation != null) {
            mHandler.removeMessages(0);
            mokoBleScanner.stopScanDevice();
        }
        if (BuildConfig.IS_LIBRARY) {
            finish();
        } else {
            AlertMessageDialog dialog = new AlertMessageDialog();
            dialog.setMessage(R.string.main_exit_tips);
            dialog.setOnAlertConfirmListener(() -> NordicMainActivity.this.finish());
            dialog.show(getSupportFragmentManager());
        }
    }

    public void onRefresh(View view) {
        if (isWindowLocked())
            return;
        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            // 蓝牙未打开，开启蓝牙
            MokoSupport.getInstance().enableBluetooth();
            return;
        }
        if (animation == null) {
            startScan();
        } else {
            mHandler.removeMessages(0);
            mokoBleScanner.stopScanDevice();
        }
    }

    public void onFilterDelete(View view) {
        if (animation != null) {
            mHandler.removeMessages(0);
            mokoBleScanner.stopScanDevice();
        }
        rl_filter.setVisibility(View.GONE);
        rl_edit_filter.setVisibility(View.VISIBLE);
        filterName = "";
        filterMac = "";
        filterRssi = -100;
        if (isWindowLocked())
            return;
        if (animation == null) {
            startScan();
        }
    }


    private void saveMacListToFile() {
        try {
            // cartella Download
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();

            // nome file con data (solo giorno) – ad es. 2025-12-10_MAC_LIST.txt
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            File file = new File(dir, today + "_MAC_LIST.txt");

            // 1) costruiamo il blocco NUOVO da mettere in alto
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            StringBuilder newBlock = new StringBuilder();
            newBlock.append("---- Scansione del ").append(timestamp).append(" ----\n");

            // lista MAC in ordine: più recenti in alto
            List<String> macList = new ArrayList<>(macArrivalOrder);
            Collections.reverse(macList);

            for (String mac : macList) {
                newBlock.append(mac).append("\n");
            }
            newBlock.append("\n");

            // 2) leggiamo il vecchio contenuto (se esiste)
            StringBuilder oldContent = new StringBuilder();
            if (file.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    oldContent.append(line).append("\n");
                }
                br.close();
            }

            // 3) riscriviamo il file: prima il blocco nuovo, poi il vecchio contenuto
            FileWriter writer = new FileWriter(file, false); // false = sovrascrivi
            writer.write(newBlock.toString());
            writer.write(oldContent.toString());
            writer.close();

            Toast.makeText(this,
                    "Mac salvati in: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Errore durante il salvataggio", Toast.LENGTH_SHORT).show();
        }
    }




    public void onSave(View view) {
        if (isWindowLocked())
            return;
        saveMacListToFile();
    }

}


