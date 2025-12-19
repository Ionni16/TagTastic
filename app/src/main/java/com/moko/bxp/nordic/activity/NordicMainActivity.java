package com.moko.bxp.nordic.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.elvishew.xlog.XLog;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.AppConstants;
import com.moko.bxp.nordic.BaseApplication;
import com.moko.bxp.nordic.BuildConfig;
import com.moko.bxp.nordic.R;
import com.moko.bxp.nordic.R2;
import com.moko.bxp.nordic.adapter.BeaconXListAdapter;
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
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import butterknife.BindView;
import butterknife.ButterKnife;

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

    // --- BLE / UI ---
    private boolean mReceiverTag = false;
    private MokoBleScanner mokoBleScanner;
    private Handler mHandler;
    private Animation animation = null;

    private LoadingDialog mLoadingDialog;
    private LoadingMessageDialog mLoadingMessageDialog;

    // --- Devices list ---
    private ConcurrentHashMap<String, BeaconXInfo> beaconXInfoHashMap;
    private final List<String> macArrivalOrder = new ArrayList<>();
    private ArrayList<BeaconXInfo> beaconXInfos;
    private BeaconXListAdapter adapter;
    private BeaconXInfoParseableImpl beaconXInfoParseable;

    // --- Filters ---
    public String filterName;
    public String filterMac;
    public int filterRssi = -100;

    // --- Connection / password flow ---
    private String mPassword;
    private String mSavedPassword;
    private String mSelectedBeaconXMac;
    private String unLockResponse;
    private boolean mInputPassword;
    private boolean isPasswordError;

    // --- Autowrite flags ---
    private boolean mMitechAutoWriteRequested = false;
    private static final String DEFAULT_PASSWORD = "Moko4321";

    // --- IMPORTANT: pause BLE handling when child activities control BLE ---
    private volatile boolean mBlePausedForChild = false;

    // --- Robust reconnect after forced disconnect (password verify) ---
    private String mPendingConnectMac = null;
    private boolean mWaitingDisconnectToConnect = false;

    public static String PATH_LOGCAT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            }
        }

        // PATH_LOGCAT (come avevi tu)
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PATH_LOGCAT = getExternalFilesDir(null).getAbsolutePath() + File.separator
                        + (BuildConfig.IS_LIBRARY ? "mokoBeaconXPro" : "TagTastic");
            } else {
                PATH_LOGCAT = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator
                        + (BuildConfig.IS_LIBRARY ? "mokoBeaconXPro" : "TagTastic");
            }
        } else {
            PATH_LOGCAT = getFilesDir().getAbsolutePath() + File.separator
                    + (BuildConfig.IS_LIBRARY ? "mokoBeaconXPro" : "TagTastic");
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

        // Receiver BT state
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
        mReceiverTag = true;

        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            MokoSupport.getInstance().enableBluetooth();
        } else {
            if (animation == null) startScan();
        }
    }

    // --- MENU save ---
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

    // --- Receiver ---
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) return;

            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (blueState == BluetoothAdapter.STATE_TURNING_OFF) {
                if (animation != null) {
                    mHandler.removeCallbacksAndMessages(null);
                    mokoBleScanner.stopScanDevice();
                    onStopScan();
                }
            } else if (blueState == BluetoothAdapter.STATE_ON) {
                if (animation == null) startScan();
            }
        }
    };

    // =========================================================
    // EventBus BLE events
    // =========================================================

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        if (mBlePausedForChild) return;

        String action = event.getAction();

        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {

            // ✅ Se stavamo aspettando la disconnessione per riconnettere (verifica password),
            // riconnetti QUI e non fare altro (non azzerare password / non startScan).
            if (mWaitingDisconnectToConnect && !TextUtils.isEmpty(mPendingConnectMac)) {
                String mac = mPendingConnectMac;
                mPendingConnectMac = null;
                mWaitingDisconnectToConnect = false;

                dismissLoadingMessageDialog();
                dismissLoadingProgressDialog();

                showLoadingProgressDialog();
                ivRefresh.postDelayed(() -> MokoSupport.getInstance().connDevice(mac), 200);
                return;
            }

            // normale disconnect
            mPassword = "";
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
            dismissLoadingProgressDialog();
            showLoadingMessageDialog();

            mHandler.postDelayed(() -> {
                if (mBlePausedForChild) return;

                if (TextUtils.isEmpty(mPassword)) {
                    ArrayList<OrderTask> orderTasks = new ArrayList<>();
                    orderTasks.add(OrderTaskAssembler.getLockState());
                    MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
                } else {
                    ArrayList<OrderTask> orderTasks = new ArrayList<>();
                    orderTasks.add(OrderTaskAssembler.getUnLock());
                    MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
                }
            }, 500);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        if (mBlePausedForChild) return;

        final String action = event.getAction();
        if (MokoConstants.ACTION_ORDER_TIMEOUT.equals(action)) {
            // utile per debug
            // ToastUtils.showToast(this, "BLE timeout");
            return;
        }
        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
            return;
        }
        if (!MokoConstants.ACTION_ORDER_RESULT.equals(action)) return;

        OrderTaskResponse response = event.getResponse();
        OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
        int responseType = response.responseType;
        byte[] value = response.responseValue;

        switch (orderCHAR) {
            case CHAR_LOCK_STATE: {
                String valueStr = MokoUtils.bytesToHexString(value);
                dismissLoadingMessageDialog();

                if ("00".equals(valueStr)) {
                    // Serve password → facciamo disconnect e poi reconnect SOLO su DISCONNECTED
                    MokoSupport.getInstance().disConnectBle();

                    if (TextUtils.isEmpty(unLockResponse)) {
                        mInputPassword = true;

                        boolean isAuto = (mMitechAutoWriteRequested || ((BaseApplication) getApplication()).GetMTAutoWriteStatus() == 1);
                        mSavedPassword = isAuto ? DEFAULT_PASSWORD : "";

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
                                    mHandler.removeCallbacksAndMessages(null);
                                    mokoBleScanner.stopScanDevice();
                                }

                                // ✅ aspetta DISCONNECTED e poi connetti
                                mPendingConnectMac = mSelectedBeaconXMac;
                                mWaitingDisconnectToConnect = true;

                                showLoadingProgressDialog();

                                // failsafe: se DISCONNECTED non arriva, riprova dopo 2.5s
                                ivRefresh.postDelayed(() -> {
                                    if (mWaitingDisconnectToConnect && !TextUtils.isEmpty(mPendingConnectMac)) {
                                        String mac = mPendingConnectMac;
                                        mPendingConnectMac = null;
                                        mWaitingDisconnectToConnect = false;
                                        MokoSupport.getInstance().connDevice(mac);
                                    }
                                }, 2500);
                            }

                            @Override
                            public void onDismiss() {
                                unLockResponse = "";
                                mInputPassword = false;
                                if (animation == null) startScan();
                            }
                        });

                        if (isAuto) dialog.setAutomaticOk();
                        dialog.show(getSupportFragmentManager());

                    } else {
                        isPasswordError = true;
                        unLockResponse = "";
                        ToastUtils.showToast(NordicMainActivity.this, "Password incorrect!");
                    }

                } else if ("02".equals(valueStr)) {
                    // Non serve password
                    openDeviceInfoAndPauseBle();

                } else {
                    // Unlock OK (già sbloccato)
                    unLockResponse = "";
                    mSavedPassword = mPassword;
                    openDeviceInfoAndPauseBle();
                }
                break;
            }

            case CHAR_UNLOCK:
                if (responseType == OrderTask.RESPONSE_TYPE_READ) {
                    unLockResponse = MokoUtils.bytesToHexString(value);
                    MokoSupport.getInstance().sendOrder(OrderTaskAssembler.setUnLock(mPassword, value));
                }
                if (responseType == OrderTask.RESPONSE_TYPE_WRITE) {
                    MokoSupport.getInstance().sendOrder(OrderTaskAssembler.getLockState());
                }
                break;
        }
    }

    private void openDeviceInfoAndPauseBle() {
        BluetoothGattCharacteristic modelNumberChar =
                MokoSupport.getInstance().getCharacteristic(OrderCHAR.CHAR_MODEL_NUMBER);

        Intent deviceInfoIntent = new Intent(NordicMainActivity.this, DeviceInfoActivity.class);
        deviceInfoIntent.putExtra(AppConstants.EXTRA_KEY_PASSWORD, mPassword);
        deviceInfoIntent.putExtra(AppConstants.IS_NEW_VERSION, null == modelNumberChar);

        boolean isAuto = (mMitechAutoWriteRequested || ((BaseApplication) getApplication()).GetMTAutoWriteStatus() == 1);
        deviceInfoIntent.putExtra(AppConstants.EXTRA_KEY_MITECH_AUTOWRITE, isAuto);

        // ✅ evita rientri
        mMitechAutoWriteRequested = false;

        // ✅ pausa NordicMainActivity: da qui in poi comanda DeviceInfoActivity/SlotDataActivity
        mBlePausedForChild = true;

        // stop scan/handler/ui
        try {
            if (animation != null) {
                mHandler.removeCallbacksAndMessages(null);
                mokoBleScanner.stopScanDevice();
                onStopScan();
            }
        } catch (Exception ignore) {}

        dismissLoadingProgressDialog();
        dismissLoadingMessageDialog();

        mHandler.removeCallbacksAndMessages(null);

        startActivityForResult(deviceInfoIntent, AppConstants.REQUEST_CODE_DEVICE_INFO);
    }

    // =========================================================
    // Activity result: resume BLE control here
    // =========================================================
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AppConstants.REQUEST_CODE_DEVICE_INFO) {
            mBlePausedForChild = false;

            dismissLoadingProgressDialog();
            dismissLoadingMessageDialog();

            mPassword = "";
            unLockResponse = "";
            mInputPassword = false;
            isPasswordError = false;

            if (animation == null) startScan();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiverTag) {
            mReceiverTag = false;
            unregisterReceiver(mReceiver);
        }
        EventBus.getDefault().unregister(this);
    }

    // =========================================================
    // Scanner callbacks
    // =========================================================
    @Override
    public void onStartScan() {
        beaconXInfoHashMap.clear();
        macArrivalOrder.clear();
        new Thread(() -> {
            while (animation != null) {
                runOnUiThread(() -> {
                    adapter.replaceData(beaconXInfos);
                    tvDeviceNum.setText(String.format(Locale.getDefault(), "DEVICE(%d)", beaconXInfos.size()));
                });
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                updateDevices();
            }
        }).start();
    }

    @Override
    public void onScanDevice(DeviceInfo deviceInfo) {
        if (beaconXInfoParseable == null) beaconXInfoParseable = new BeaconXInfoParseableImpl();
        BeaconXInfo beaconXInfo = beaconXInfoParseable.parseDeviceInfo(deviceInfo);
        if (beaconXInfo == null) return;

        String mac = beaconXInfo.mac;
        if (!beaconXInfoHashMap.containsKey(mac)) macArrivalOrder.add(mac);
        beaconXInfoHashMap.put(mac, beaconXInfo);
    }

    @Override
    public void onStopScan() {
        findViewById(R.id.iv_refresh).clearAnimation();
        animation = null;
    }

    private void updateDevices() {
        beaconXInfos.clear();
        if (!TextUtils.isEmpty(filterName) || !TextUtils.isEmpty(filterMac) || filterRssi != -100) {
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
                        } else if (!TextUtils.isEmpty(filterMac) && beaconXInfo.mac.toLowerCase().replace(":", "").contains(filterMac.toLowerCase())) {
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

        Collections.sort(beaconXInfos, (lhs, rhs) -> {
            if (lhs.rssi > rhs.rssi) return -1;
            if (lhs.rssi < rhs.rssi) return 1;
            return 0;
        });
    }

    private void startScan() {
        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            MokoSupport.getInstance().enableBluetooth();
            return;
        }
        animation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh);
        findViewById(R.id.iv_refresh).startAnimation(animation);
        beaconXInfoParseable = new BeaconXInfoParseableImpl();
        mokoBleScanner.startScanDevice(this);
    }

    // =========================================================
    // Dialog helpers
    // =========================================================
    private void showLoadingProgressDialog() {
        mLoadingDialog = new LoadingDialog();
        mLoadingDialog.show(getSupportFragmentManager());
    }

    private void dismissLoadingProgressDialog() {
        if (mLoadingDialog != null) mLoadingDialog.dismissAllowingStateLoss();
    }

    private void showLoadingMessageDialog() {
        mLoadingMessageDialog = new LoadingMessageDialog();
        mLoadingMessageDialog.setMessage("Verifying..");
        mLoadingMessageDialog.show(getSupportFragmentManager());
    }

    private void dismissLoadingMessageDialog() {
        if (mLoadingMessageDialog != null) mLoadingMessageDialog.dismissAllowingStateLoss();
    }

    // =========================================================
    // UI actions
    // =========================================================
    @Override
    public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
        int viewId = view.getId();

        if (viewId == R.id.tv_connect) {
            ((BaseApplication) getApplication()).SetMTAutoWriteStatus(0);
            mMitechAutoWriteRequested = false;
        } else if (viewId == R.id.tv_mtautowrite) {
            ((BaseApplication) getApplication()).SetMTAutoWriteStatus(1);
            mMitechAutoWriteRequested = true;
        }

        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            MokoSupport.getInstance().enableBluetooth();
            return;
        }

        final BeaconXInfo beaconXInfo = (BeaconXInfo) adapter.getItem(position);
        if (beaconXInfo != null && !isFinishing()) {
            if (animation != null) {
                mHandler.removeCallbacksAndMessages(null);
                mokoBleScanner.stopScanDevice();
                onStopScan();
            }
            mSelectedBeaconXMac = beaconXInfo.mac;

            showLoadingProgressDialog();
            ivRefresh.postDelayed(() -> MokoSupport.getInstance().connDevice(beaconXInfo.mac), 500);
        }
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        back();
    }

    public void onAbout(View view) {
        if (isWindowLocked()) return;
        startActivity(new Intent(this, AboutActivity.class));
    }

    public void onFilter(View view) {
        if (isWindowLocked()) return;

        if (animation != null) {
            mHandler.removeCallbacksAndMessages(null);
            mokoBleScanner.stopScanDevice();
            onStopScan();
        }

        ScanFilterDialog scanFilterDialog = new ScanFilterDialog(this);
        scanFilterDialog.setFilterName(filterName);
        scanFilterDialog.setFilterMac(filterMac);
        scanFilterDialog.setFilterRssi(filterRssi);

        scanFilterDialog.setOnScanFilterListener((filterName, filterMac, filterRssi) -> {
            NordicMainActivity.this.filterName = filterName;
            NordicMainActivity.this.filterMac = filterMac;

            String showFilterMac;
            if (filterMac.length() == 12) {
                StringBuilder sb = new StringBuilder(filterMac);
                sb.insert(2, ":");
                sb.insert(5, ":");
                sb.insert(8, ":");
                sb.insert(11, ":");
                sb.insert(14, ":");
                showFilterMac = sb.toString();
            } else {
                showFilterMac = filterMac;
            }

            NordicMainActivity.this.filterRssi = filterRssi;

            if (!TextUtils.isEmpty(filterName) || !TextUtils.isEmpty(showFilterMac) || filterRssi != -100) {
                rl_filter.setVisibility(View.VISIBLE);
                rl_edit_filter.setVisibility(View.GONE);

                StringBuilder stringBuilder = new StringBuilder();
                if (!TextUtils.isEmpty(filterName)) stringBuilder.append(filterName).append(";");
                if (!TextUtils.isEmpty(showFilterMac)) stringBuilder.append(showFilterMac).append(";");
                if (filterRssi != -100) stringBuilder.append(String.format(Locale.getDefault(), "%sdBm;", String.valueOf(filterRssi)));
                tv_filter.setText(stringBuilder.toString());
            } else {
                rl_filter.setVisibility(View.GONE);
                rl_edit_filter.setVisibility(View.VISIBLE);
            }

            if (animation == null) startScan();
        });

        scanFilterDialog.setOnDismissListener(dialog -> {
            if (isWindowLocked()) return;
            if (animation == null) startScan();
        });

        scanFilterDialog.show();
    }

    public void onRefresh(View view) {
        if (isWindowLocked()) return;

        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            MokoSupport.getInstance().enableBluetooth();
            return;
        }

        if (animation == null) {
            startScan();
        } else {
            mHandler.removeCallbacksAndMessages(null);
            mokoBleScanner.stopScanDevice();
            onStopScan();
        }
    }

    public void onFilterDelete(View view) {
        if (animation != null) {
            mHandler.removeCallbacksAndMessages(null);
            mokoBleScanner.stopScanDevice();
            onStopScan();
        }
        rl_filter.setVisibility(View.GONE);
        rl_edit_filter.setVisibility(View.VISIBLE);
        filterName = "";
        filterMac = "";
        filterRssi = -100;

        if (isWindowLocked()) return;
        if (animation == null) startScan();
    }

    private void back() {
        if (animation != null) {
            mHandler.removeCallbacksAndMessages(null);
            mokoBleScanner.stopScanDevice();
            onStopScan();
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

    // =========================================================
    // Save MAC list
    // =========================================================
    private void saveMacListToFile() {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            File file = new File(dir, today + "_MAC_LIST.txt");

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            StringBuilder newBlock = new StringBuilder();
            newBlock.append("---- Scansione del ").append(timestamp).append(" ----\n");

            List<String> macList = new ArrayList<>(macArrivalOrder);
            Collections.reverse(macList);
            for (String mac : macList) newBlock.append(mac).append("\n");
            newBlock.append("\n");

            StringBuilder oldContent = new StringBuilder();
            if (file.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file));
                String line;
                while ((line = br.readLine()) != null) oldContent.append(line).append("\n");
                br.close();
            }

            FileWriter writer = new FileWriter(file, false);
            writer.write(newBlock.toString());
            writer.write(oldContent.toString());
            writer.close();

            Toast.makeText(this, "Mac salvati in: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Errore durante il salvataggio", Toast.LENGTH_SHORT).show();
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        saveMacListToFile();
    }
}
