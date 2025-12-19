package com.moko.bxp.nordic.activity;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.AppConstants;
import com.moko.bxp.nordic.R;
import com.moko.bxp.nordic.R2;
import com.moko.bxp.nordic.able.ISlotDataAction;
import com.moko.bxp.nordic.dialog.BottomDialog;
import com.moko.bxp.nordic.dialog.LoadingMessageDialog;
import com.moko.bxp.nordic.fragment.AxisFragment;
import com.moko.bxp.nordic.fragment.DeviceInfoFragment;
import com.moko.bxp.nordic.fragment.IBeaconFragment;
import com.moko.bxp.nordic.fragment.THFragment;
import com.moko.bxp.nordic.fragment.TlmFragment;
import com.moko.bxp.nordic.fragment.TriggerHumidityFragment;
import com.moko.bxp.nordic.fragment.TriggerLightDetectedFragment;
import com.moko.bxp.nordic.fragment.TriggerMovesFragment;
import com.moko.bxp.nordic.fragment.TriggerTappedFragment;
import com.moko.bxp.nordic.fragment.TriggerTempFragment;
import com.moko.bxp.nordic.fragment.UidFragment;
import com.moko.bxp.nordic.fragment.UrlFragment;
import com.moko.bxp.nordic.utils.ToastUtils;
import com.moko.support.nordic.MokoSupport;
import com.moko.support.nordic.OrderTaskAssembler;
import com.moko.support.nordic.entity.OrderCHAR;
import com.moko.support.nordic.entity.SlotData;
import com.moko.support.nordic.entity.SlotFrameTypeEnum;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.carbswang.android.numberpickerview.library.NumberPickerView;

public class SlotDataActivity extends BaseActivity implements NumberPickerView.OnValueChangeListener {

    // ---- Trigger types (as in original project) ----
    private static final int TRIGGER_TYPE_NULL = 0;
    private static final int TRIGGER_TYPE_TEMPERATURE = 1;
    private static final int TRIGGER_TYPE_HUMIDITY = 2;
    private static final int TRIGGER_TYPE_TRAP_DOUBLE = 3;
    private static final int TRIGGER_TYPE_TRAP_TRIPLE = 4;
    private static final int TRIGGER_TYPE_MOVE = 5;
    private static final int TRIGGER_TYPE_LIGHT = 6;
    private static final int TRIGGER_TYPE_TRAP_SINGLE = 7;

    // ---- Device types ----
    private static final int DEVICE_TYPE_SENSOR_NULL = 0;
    private static final int DEVICE_TYPE_SENSOR_AXIS = 1;
    private static final int DEVICE_TYPE_SENSOR_TH = 2;
    private static final int DEVICE_TYPE_SENSOR_AXIS_TH = 3;
    private static final int DEVICE_TYPE_SENSOR_LIGHT = 4;
    private static final int DEVICE_TYPE_SENSOR_AXIS_LIGHT = 5;
    private static final int DEVICE_TYPE_SENSOR_TH_LIGHT = 6;
    private static final int DEVICE_TYPE_SENSOR_AXIS_TH_LIGHT = 7;

    // ---- UI ----
    @BindView(R2.id.tv_slot_title)
    TextView tvSlotTitle;
    @BindView(R2.id.iv_save)
    ImageView ivSave;
    @BindView(R2.id.frame_slot_container)
    FrameLayout frameSlotContainer;
    @BindView(R2.id.npv_slot_type)
    NumberPickerView npvSlotType;
    @BindView(R2.id.iv_trigger)
    ImageView ivTrigger;
    @BindView(R2.id.tv_trigger_type)
    TextView tvTriggerType;
    @BindView(R2.id.frame_trigger_container)
    FrameLayout frameTriggerContainer;
    @BindView(R2.id.rl_trigger)
    RelativeLayout rlTrigger;
    @BindView(R2.id.rl_trigger_switch)
    RelativeLayout rlTriggerSwitch;

    // ---- Fragments ----
    private FragmentManager fragmentManager;
    private UidFragment uidFragment;
    private UrlFragment urlFragment;
    private TlmFragment tlmFragment;
    private IBeaconFragment iBeaconFragment;
    private DeviceInfoFragment deviceInfoFragment;
    private AxisFragment axisFragment;
    private THFragment thFragment;

    private TriggerTempFragment tempFragment;
    private TriggerHumidityFragment humidityFragment;
    private TriggerTappedFragment tappedFragment;
    private TriggerMovesFragment movesFragment;
    private TriggerLightDetectedFragment lightDetectedFragment;

    // ---- State ----
    public SlotData slotData;
    public SlotFrameTypeEnum currentFrameTypeEnum;
    public int deviceType;

    private ISlotDataAction slotDataActionImpl;

    private boolean mReceiverTag = false;

    private int triggerType;
    private byte[] triggerData;
    private String[] slotTypeArray;
    private ArrayList<String> triggerTypes;
    private int triggerTypeSelected = 1;

    // BLE write outcome flag from device (key 0x0D)
    public boolean isConfigError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_slot_data);
        ButterKnife.bind(this);

        readIntent();

        fragmentManager = getFragmentManager();
        createFragments();
        setupPickerAndTriggerUi();

        EventBus.getDefault().register(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
        mReceiverTag = true;

        if (!MokoSupport.getInstance().isBluetoothOpen()) {
            MokoSupport.getInstance().enableBluetooth();
        }
    }

    private void readIntent() {
        if (getIntent() == null || getIntent().getExtras() == null) return;

        slotData = (SlotData) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_SLOT_DATA);
        if (slotData == null) return;

        currentFrameTypeEnum = slotData.frameTypeEnum;
        deviceType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_DEVICE_TYPE, 0);

        triggerType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_TRIGGER_TYPE, 0);
        String triggerDataStr = getIntent().getStringExtra(AppConstants.EXTRA_KEY_TRIGGER_DATA);
        if (!TextUtils.isEmpty(triggerDataStr)) {
            triggerData = MokoUtils.hex2bytes(triggerDataStr);
        }

        XLog.i(slotData.toString());
    }

    private void setupPickerAndTriggerUi() {
        triggerTypes = new ArrayList<>();

        if (deviceType == DEVICE_TYPE_SENSOR_NULL) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_no_sensor);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");

        } else if (deviceType == DEVICE_TYPE_SENSOR_AXIS) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_axis);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Device moves");

        } else if (deviceType == DEVICE_TYPE_SENSOR_TH) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_th);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Temperature above");
            triggerTypes.add("Temperature below");
            triggerTypes.add("Humidity above");
            triggerTypes.add("Humidity below");

        } else if (deviceType == DEVICE_TYPE_SENSOR_AXIS_TH) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_all);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Temperature above");
            triggerTypes.add("Temperature below");
            triggerTypes.add("Humidity above");
            triggerTypes.add("Humidity below");
            triggerTypes.add("Device moves");

        } else if (deviceType == DEVICE_TYPE_SENSOR_LIGHT) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_no_sensor);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Ambient light detected");

        } else if (deviceType == DEVICE_TYPE_SENSOR_AXIS_LIGHT) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_axis);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Device moves");
            triggerTypes.add("Ambient light detected");

        } else if (deviceType == DEVICE_TYPE_SENSOR_TH_LIGHT) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_th);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Temperature above");
            triggerTypes.add("Temperature below");
            triggerTypes.add("Humidity above");
            triggerTypes.add("Humidity below");
            triggerTypes.add("Ambient light detected");

        } else if (deviceType == DEVICE_TYPE_SENSOR_AXIS_TH_LIGHT) {
            slotTypeArray = getResources().getStringArray(R.array.slot_type_all);
            npvSlotType.setDisplayedValues(slotTypeArray);
            triggerTypes.add("Single click button");
            triggerTypes.add("Press button twice");
            triggerTypes.add("Press button three times");
            triggerTypes.add("Temperature above");
            triggerTypes.add("Temperature below");
            triggerTypes.add("Humidity above");
            triggerTypes.add("Humidity below");
            triggerTypes.add("Device moves");
            triggerTypes.add("Ambient light detected");
        }

        final int length = slotTypeArray.length;
        npvSlotType.setMinValue(0);
        npvSlotType.setMaxValue(length - 1);
        npvSlotType.setOnValueChangedListener(this);

        // Initial picker value from current frame
        for (int i = 0; i < length; i++) {
            if (slotData != null && slotData.frameTypeEnum != null
                    && slotData.frameTypeEnum.getShowName().equals(slotTypeArray[i])) {
                npvSlotType.setValue(i);
                showFragment(i);
                break;
            }
        }

        if (slotData != null) {
            tvSlotTitle.setText(slotData.slotEnum.getTitle());
            rlTriggerSwitch.setVisibility(slotData.frameTypeEnum != SlotFrameTypeEnum.NO_DATA ? View.VISIBLE : View.GONE);
        }

        if (triggerType > 0) {
            ivTrigger.setImageResource(R.drawable.ic_checked);
            rlTrigger.setVisibility(View.VISIBLE);
        } else {
            ivTrigger.setImageResource(R.drawable.ic_unchecked);
            rlTrigger.setVisibility(View.GONE);
        }

        createTriggerFragments();
        showTriggerFragment();
        setTriggerDataSafe();
    }

    // =========================================================
    // Fragments
    // =========================================================

    private void createTriggerFragments() {
        FragmentTransaction ft = fragmentManager.beginTransaction();
        tempFragment = TriggerTempFragment.newInstance();
        humidityFragment = TriggerHumidityFragment.newInstance();
        tappedFragment = TriggerTappedFragment.newInstance();
        movesFragment = TriggerMovesFragment.newInstance();
        lightDetectedFragment = TriggerLightDetectedFragment.newInstance();

        ft.add(R.id.frame_trigger_container, tempFragment);
        ft.add(R.id.frame_trigger_container, humidityFragment);
        ft.add(R.id.frame_trigger_container, tappedFragment);
        ft.add(R.id.frame_trigger_container, movesFragment);
        ft.add(R.id.frame_trigger_container, lightDetectedFragment);
        ft.commit();
    }

    private void createFragments() {
        FragmentTransaction ft = fragmentManager.beginTransaction();

        if ((deviceType & 1) == 0 && (deviceType & 2) == 0) { // 0,4
            uidFragment = UidFragment.newInstance();
            urlFragment = UrlFragment.newInstance();
            tlmFragment = TlmFragment.newInstance();
            iBeaconFragment = IBeaconFragment.newInstance();
            deviceInfoFragment = DeviceInfoFragment.newInstance();

            ft.add(R.id.frame_slot_container, uidFragment);
            ft.add(R.id.frame_slot_container, urlFragment);
            ft.add(R.id.frame_slot_container, tlmFragment);
            ft.add(R.id.frame_slot_container, iBeaconFragment);
            ft.add(R.id.frame_slot_container, deviceInfoFragment);

        } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) { // 1,5
            uidFragment = UidFragment.newInstance();
            urlFragment = UrlFragment.newInstance();
            tlmFragment = TlmFragment.newInstance();
            iBeaconFragment = IBeaconFragment.newInstance();
            deviceInfoFragment = DeviceInfoFragment.newInstance();
            axisFragment = AxisFragment.newInstance();

            ft.add(R.id.frame_slot_container, uidFragment);
            ft.add(R.id.frame_slot_container, urlFragment);
            ft.add(R.id.frame_slot_container, tlmFragment);
            ft.add(R.id.frame_slot_container, iBeaconFragment);
            ft.add(R.id.frame_slot_container, deviceInfoFragment);
            ft.add(R.id.frame_slot_container, axisFragment);

        } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) { // 2,6
            uidFragment = UidFragment.newInstance();
            urlFragment = UrlFragment.newInstance();
            tlmFragment = TlmFragment.newInstance();
            iBeaconFragment = IBeaconFragment.newInstance();
            deviceInfoFragment = DeviceInfoFragment.newInstance();
            thFragment = THFragment.newInstance();

            ft.add(R.id.frame_slot_container, uidFragment);
            ft.add(R.id.frame_slot_container, urlFragment);
            ft.add(R.id.frame_slot_container, tlmFragment);
            ft.add(R.id.frame_slot_container, iBeaconFragment);
            ft.add(R.id.frame_slot_container, deviceInfoFragment);
            ft.add(R.id.frame_slot_container, thFragment);

        } else if ((deviceType & 1) == 1 && (deviceType & 2) == 2) { // 3,7
            uidFragment = UidFragment.newInstance();
            urlFragment = UrlFragment.newInstance();
            tlmFragment = TlmFragment.newInstance();
            iBeaconFragment = IBeaconFragment.newInstance();
            deviceInfoFragment = DeviceInfoFragment.newInstance();
            axisFragment = AxisFragment.newInstance();
            thFragment = THFragment.newInstance();

            ft.add(R.id.frame_slot_container, uidFragment);
            ft.add(R.id.frame_slot_container, urlFragment);
            ft.add(R.id.frame_slot_container, tlmFragment);
            ft.add(R.id.frame_slot_container, iBeaconFragment);
            ft.add(R.id.frame_slot_container, deviceInfoFragment);
            ft.add(R.id.frame_slot_container, axisFragment);
            ft.add(R.id.frame_slot_container, thFragment);
        }

        ft.commit();
    }

    // =========================================================
    // BLE EventBus
    // =========================================================

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 200)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        final String action = event.getAction();
        runOnUiThread(() -> {
            if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
                finish();
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 200)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        EventBus.getDefault().cancelEventDelivery(event);
        final String action = event.getAction();

        runOnUiThread(() -> {

            if (MokoConstants.ACTION_ORDER_TIMEOUT.equals(action)) {
                dismissSyncProgressDialog();
                ToastUtils.showToast(SlotDataActivity.this, "Timeout");
                return;
            }

            if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
                OrderTaskResponse response = event.getResponse();
                if (response == null) return;

                OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
                byte[] value = response.responseValue;

                if (orderCHAR == OrderCHAR.CHAR_PARAMS && value != null && value.length > 1) {
                    int key = value[1] & 0xFF;
                    if (key == 0x0D) {
                        isConfigError = true;
                    }
                }
                return;
            }

            if (MokoConstants.ACTION_CURRENT_DATA.equals(action)) {
                OrderTaskResponse response = event.getResponse();
                if (response == null) return;

                OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
                byte[] value = response.responseValue;

                if (orderCHAR == OrderCHAR.CHAR_LOCKED_NOTIFY && value != null) {
                    String valueHexStr = MokoUtils.bytesToHexString(value);
                    if ("eb63000100".equalsIgnoreCase(valueHexStr)) {
                        ToastUtils.showToast(SlotDataActivity.this, "Locked");
                        finish();
                    }
                }
                return;
            }

            if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
                ToastUtils.showToast(SlotDataActivity.this, isConfigError ? "Error" : "Successfully configure");
                dismissSyncProgressDialog();
                isConfigError = false;
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    // =========================================================
    // Bluetooth state receiver
    // =========================================================

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;

            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (blueState == BluetoothAdapter.STATE_TURNING_OFF) {
                finish();
            }
        }
    };

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
    // Loading
    // =========================================================

    private LoadingMessageDialog mLoadingMessageDialog;

    public void showSyncingProgressDialog() {
        mLoadingMessageDialog = new LoadingMessageDialog();
        mLoadingMessageDialog.setMessage("Syncing..");
        mLoadingMessageDialog.show(getSupportFragmentManager());
    }

    public void dismissSyncProgressDialog() {
        if (mLoadingMessageDialog != null) {
            mLoadingMessageDialog.dismissAllowingStateLoss();
        }
    }

    // =========================================================
    // Picker
    // =========================================================

    @Override
    public void onValueChange(NumberPickerView picker, int oldVal, int newVal) {
        XLog.i(String.valueOf(newVal));
        XLog.i(picker.getContentByCurrValue());

        showFragment(newVal);

        if (slotDataActionImpl != null) {
            slotDataActionImpl.resetParams();
        }

        SlotFrameTypeEnum ft = SlotFrameTypeEnum.fromShowName(slotTypeArray[newVal]);
        rlTriggerSwitch.setVisibility(ft != SlotFrameTypeEnum.NO_DATA ? View.VISIBLE : View.GONE);
    }

    private void showFragment(int index) {
        SlotFrameTypeEnum ft = SlotFrameTypeEnum.fromShowName(slotTypeArray[index]);
        FragmentTransaction tr = fragmentManager.beginTransaction();

        switch (ft) {
            case TLM:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(iBeaconFragment).hide(deviceInfoFragment).show(tlmFragment).commit();
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(iBeaconFragment).hide(deviceInfoFragment).hide(axisFragment).show(tlmFragment).commit();
                } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(urlFragment).hide(iBeaconFragment).hide(deviceInfoFragment).hide(thFragment).show(tlmFragment).commit();
                } else {
                    tr.hide(uidFragment).hide(urlFragment).hide(iBeaconFragment).hide(deviceInfoFragment).hide(axisFragment).hide(thFragment).show(tlmFragment).commit();
                }
                slotDataActionImpl = tlmFragment;
                break;

            case UID:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 0) {
                    tr.hide(urlFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).show(uidFragment).commit();
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(urlFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).hide(axisFragment).show(uidFragment).commit();
                } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(urlFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).hide(thFragment).show(uidFragment).commit();
                } else {
                    tr.hide(urlFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).hide(axisFragment).hide(thFragment).show(uidFragment).commit();
                }
                slotDataActionImpl = uidFragment;
                break;

            case URL:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).show(urlFragment).commit();
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).hide(axisFragment).show(urlFragment).commit();
                } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).hide(thFragment).show(urlFragment).commit();
                } else {
                    tr.hide(uidFragment).hide(iBeaconFragment).hide(tlmFragment).hide(deviceInfoFragment).hide(axisFragment).hide(thFragment).show(urlFragment).commit();
                }
                slotDataActionImpl = urlFragment;
                break;

            case IBEACON:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(deviceInfoFragment).show(iBeaconFragment).commit();
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(deviceInfoFragment).show(iBeaconFragment).hide(axisFragment).commit();
                } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(deviceInfoFragment).show(iBeaconFragment).hide(thFragment).commit();
                } else {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(deviceInfoFragment).show(iBeaconFragment).hide(axisFragment).hide(thFragment).commit();
                }
                slotDataActionImpl = iBeaconFragment;
                break;

            case DEVICE:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).show(deviceInfoFragment).commit();
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).show(deviceInfoFragment).hide(axisFragment).commit();
                } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).show(deviceInfoFragment).hide(thFragment).commit();
                } else {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).show(deviceInfoFragment).hide(axisFragment).hide(thFragment).commit();
                }
                slotDataActionImpl = deviceInfoFragment;
                break;

            case AXIS:
                if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).show(axisFragment).commit();
                    slotDataActionImpl = axisFragment;
                } else if (deviceType == 3) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).show(axisFragment).hide(thFragment).commit();
                    slotDataActionImpl = axisFragment;
                } else {
                    slotDataActionImpl = null;
                }
                break;

            case TH:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).show(thFragment).commit();
                    slotDataActionImpl = thFragment;
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).show(thFragment).hide(axisFragment).commit();
                    slotDataActionImpl = thFragment;
                } else {
                    slotDataActionImpl = null;
                }
                break;

            case NO_DATA:
            default:
                if ((deviceType & 1) == 0 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).commit();
                } else if ((deviceType & 1) == 1 && (deviceType & 2) == 0) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).hide(axisFragment).commit();
                } else if ((deviceType & 1) == 0 && (deviceType & 2) == 2) {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).hide(thFragment).commit();
                } else {
                    tr.hide(uidFragment).hide(urlFragment).hide(tlmFragment).hide(iBeaconFragment).hide(deviceInfoFragment).hide(axisFragment).hide(thFragment).commit();
                }
                slotDataActionImpl = null;
                break;
        }

        if (slotData != null) slotData.frameTypeEnum = ft;
    }

    // =========================================================
    // Trigger UI
    // =========================================================

    private void setTriggerDataSafe() {
        if (triggerType <= 0) {
            if (triggerTypes != null && !triggerTypes.isEmpty()) {
                tvTriggerType.setText(triggerTypes.get(Math.min(triggerTypeSelected, triggerTypes.size() - 1)));
            }
            return;
        }

        if (triggerData == null || triggerData.length == 0) {
            triggerType = TRIGGER_TYPE_NULL;
            ivTrigger.setImageResource(R.drawable.ic_unchecked);
            rlTrigger.setVisibility(View.GONE);
            if (triggerTypes != null && !triggerTypes.isEmpty()) {
                tvTriggerType.setText(triggerTypes.get(Math.min(triggerTypeSelected, triggerTypes.size() - 1)));
            }
            return;
        }

        switch (triggerType) {
            case TRIGGER_TYPE_TEMPERATURE: {
                boolean isTempAbove = (triggerData[0] & 0xff) == 1;
                triggerTypeSelected = isTempAbove ? 3 : 4;
                tempFragment.setTempType(isTempAbove);
                tempFragment.setData(MokoUtils.byte2short(Arrays.copyOfRange(triggerData, 1, 3)));
                tempFragment.setStart((triggerData[3] & 0xff) == 1);
                break;
            }
            case TRIGGER_TYPE_HUMIDITY: {
                boolean isHumidityAbove = (triggerData[0] & 0xff) == 1;
                triggerTypeSelected = isHumidityAbove ? 5 : 6;
                humidityFragment.setHumidityType(isHumidityAbove);
                byte[] humidityBytes = Arrays.copyOfRange(triggerData, 1, 3);
                humidityFragment.setData(MokoUtils.toInt(humidityBytes));
                humidityFragment.setStart((triggerData[3] & 0xff) == 1);
                break;
            }
            case TRIGGER_TYPE_TRAP_SINGLE: {
                triggerTypeSelected = 0;
                tappedFragment.setTrapType(0);
                byte[] tappedSingleBytes = Arrays.copyOfRange(triggerData, 0, 2);
                tappedFragment.setData(MokoUtils.toInt(tappedSingleBytes));
                tappedFragment.setStart((triggerData[2] & 0xff) == 1);
                break;
            }
            case TRIGGER_TYPE_TRAP_DOUBLE: {
                triggerTypeSelected = 1;
                tappedFragment.setTrapType(1);
                byte[] tappedDoubleBytes = Arrays.copyOfRange(triggerData, 0, 2);
                tappedFragment.setData(MokoUtils.toInt(tappedDoubleBytes));
                tappedFragment.setStart((triggerData[2] & 0xff) == 1);
                break;
            }
            case TRIGGER_TYPE_TRAP_TRIPLE: {
                triggerTypeSelected = 2;
                tappedFragment.setTrapType(2);
                byte[] tappedTripleBytes = Arrays.copyOfRange(triggerData, 0, 2);
                tappedFragment.setData(MokoUtils.toInt(tappedTripleBytes));
                tappedFragment.setStart((triggerData[2] & 0xff) == 1);
                break;
            }
            case TRIGGER_TYPE_MOVE: {
                if ((deviceType & 1) == 1 && (deviceType & 2) == 0) triggerTypeSelected = 3;
                if ((deviceType & 1) == 1 && (deviceType & 2) == 2) triggerTypeSelected = 7;
                byte[] movesBytes = Arrays.copyOfRange(triggerData, 0, 2);
                movesFragment.setData(MokoUtils.toInt(movesBytes));
                movesFragment.setStart((triggerData[2] & 0xff) == 2);
                break;
            }
            case TRIGGER_TYPE_LIGHT: {
                if (deviceType == DEVICE_TYPE_SENSOR_LIGHT) triggerTypeSelected = 3;
                if (deviceType == DEVICE_TYPE_SENSOR_AXIS_LIGHT) triggerTypeSelected = 4;
                if (deviceType == DEVICE_TYPE_SENSOR_TH_LIGHT) triggerTypeSelected = 7;
                if (deviceType == DEVICE_TYPE_SENSOR_AXIS_TH_LIGHT) triggerTypeSelected = 8;

                byte[] lightBytes = Arrays.copyOfRange(triggerData, 0, 2);
                lightDetectedFragment.setData(MokoUtils.toInt(lightBytes));
                lightDetectedFragment.setAlwaysAdv((triggerData[2] & 0xff) == 0);
                lightDetectedFragment.setStart((triggerData[3] & 0xff) == 1);
                break;
            }
        }

        if (triggerTypes != null && !triggerTypes.isEmpty()) {
            tvTriggerType.setText(triggerTypes.get(Math.min(triggerTypeSelected, triggerTypes.size() - 1)));
        }
    }

    private void showTriggerFragment() {
        FragmentTransaction tr = fragmentManager.beginTransaction();
        switch (triggerType) {
            case TRIGGER_TYPE_TEMPERATURE:
                tr.show(tempFragment).hide(humidityFragment).hide(tappedFragment).hide(movesFragment).hide(lightDetectedFragment).commit();
                break;
            case TRIGGER_TYPE_HUMIDITY:
                tr.hide(tempFragment).show(humidityFragment).hide(tappedFragment).hide(movesFragment).hide(lightDetectedFragment).commit();
                break;
            case TRIGGER_TYPE_TRAP_SINGLE:
            case TRIGGER_TYPE_TRAP_DOUBLE:
            case TRIGGER_TYPE_TRAP_TRIPLE:
                tr.hide(tempFragment).hide(humidityFragment).show(tappedFragment).hide(movesFragment).hide(lightDetectedFragment).commit();
                break;
            case TRIGGER_TYPE_MOVE:
                tr.hide(tempFragment).hide(humidityFragment).hide(tappedFragment).show(movesFragment).hide(lightDetectedFragment).commit();
                break;
            case TRIGGER_TYPE_LIGHT:
                tr.hide(tempFragment).hide(humidityFragment).hide(tappedFragment).hide(movesFragment).show(lightDetectedFragment).commit();
                break;
            default:
                tr.hide(tempFragment).hide(humidityFragment).hide(tappedFragment).hide(movesFragment).hide(lightDetectedFragment).commit();
                break;
        }
    }

    // =========================================================
    // UI actions
    // =========================================================

    public void onTrigger(View view) {
        if (isWindowLocked()) return;

        if (triggerType > 0) {
            triggerType = TRIGGER_TYPE_NULL;
            ivTrigger.setImageResource(R.drawable.ic_unchecked);
            rlTrigger.setVisibility(View.GONE);
        } else {
            ivTrigger.setImageResource(R.drawable.ic_checked);
            rlTrigger.setVisibility(View.VISIBLE);
            triggerType = TRIGGER_TYPE_TRAP_DOUBLE;
            showTriggerFragment();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (slotData == null) return;

        // reset error before a new save attempt
        isConfigError = false;

        if (slotDataActionImpl == null) {
            byte[] noData = new byte[]{(byte) 0xFF};
            ArrayList<OrderTask> tasks = new ArrayList<>();
            tasks.add(OrderTaskAssembler.setSlot(slotData.slotEnum));
            tasks.add(OrderTaskAssembler.setSlotData(noData));
            showSyncingProgressDialog();
            MokoSupport.getInstance().sendOrder(tasks.toArray(new OrderTask[0]));
            return;
        }

        OrderTask triggerOrder = null;
        switch (triggerType) {
            case TRIGGER_TYPE_NULL:
                triggerOrder = OrderTaskAssembler.setTriggerClose();
                break;
            case TRIGGER_TYPE_TEMPERATURE:
                triggerOrder = OrderTaskAssembler.setTHTrigger(triggerType, tempFragment.getTempType(), tempFragment.getData(), tempFragment.isStart());
                break;
            case TRIGGER_TYPE_HUMIDITY:
                triggerOrder = OrderTaskAssembler.setTHTrigger(triggerType, humidityFragment.getHumidityType(), humidityFragment.getData(), humidityFragment.isStart());
                break;
            case TRIGGER_TYPE_TRAP_SINGLE:
            case TRIGGER_TYPE_TRAP_DOUBLE:
            case TRIGGER_TYPE_TRAP_TRIPLE:
                if (tappedFragment.getData() < 0) return;
                triggerOrder = OrderTaskAssembler.setTappedMovesTrigger(triggerType, tappedFragment.getData(), tappedFragment.isStart());
                break;
            case TRIGGER_TYPE_MOVE:
                if (movesFragment.getData() < 0) return;
                triggerOrder = OrderTaskAssembler.setTappedMovesTrigger(triggerType, movesFragment.getData(), movesFragment.isStart());
                break;
            case TRIGGER_TYPE_LIGHT:
                // ✅ FIX: check light fragment data (not movesFragment)
                if (lightDetectedFragment.getData() < 0) return;
                triggerOrder = OrderTaskAssembler.setLightTrigger(triggerType,
                        lightDetectedFragment.getData(),
                        lightDetectedFragment.isAlways(),
                        lightDetectedFragment.isStart());
                break;
        }

        if (!slotDataActionImpl.isValid()) {
            return;
        }

        showSyncingProgressDialog();
        slotDataActionImpl.sendData();

        if (triggerOrder != null) {
            MokoSupport.getInstance().sendOrder(triggerOrder);
        }
    }

    public void onTriggerType(View view) {
        if (isWindowLocked()) return;

        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(triggerTypes, triggerTypeSelected);
        dialog.setListener(value -> {
            triggerTypeSelected = value;

            switch (triggerTypeSelected) {
                case 0: triggerType = TRIGGER_TYPE_TRAP_SINGLE; break;
                case 1: triggerType = TRIGGER_TYPE_TRAP_DOUBLE; break;
                case 2: triggerType = TRIGGER_TYPE_TRAP_TRIPLE; break;
                case 3:
                    if (deviceType == DEVICE_TYPE_SENSOR_AXIS || deviceType == DEVICE_TYPE_SENSOR_AXIS_LIGHT) {
                        triggerType = TRIGGER_TYPE_MOVE;
                    } else if (deviceType == DEVICE_TYPE_SENSOR_LIGHT) {
                        triggerType = TRIGGER_TYPE_LIGHT;
                    } else {
                        triggerType = TRIGGER_TYPE_TEMPERATURE;
                    }
                    break;
                case 4:
                    if (deviceType == DEVICE_TYPE_SENSOR_AXIS_LIGHT) {
                        triggerType = TRIGGER_TYPE_LIGHT;
                    } else {
                        triggerType = TRIGGER_TYPE_TEMPERATURE;
                    }
                    break;
                case 5:
                case 6:
                    triggerType = TRIGGER_TYPE_HUMIDITY;
                    break;
                case 7:
                    if (deviceType == DEVICE_TYPE_SENSOR_TH_LIGHT) {
                        triggerType = TRIGGER_TYPE_LIGHT;
                    } else {
                        triggerType = TRIGGER_TYPE_MOVE;
                    }
                    break;
                case 8:
                    triggerType = TRIGGER_TYPE_LIGHT;
                    break;
            }

            showTriggerFragment();

            switch (triggerTypeSelected) {
                case 0:
                    tappedFragment.setTrapType(0);
                    tappedFragment.updateTips();
                    break;
                case 1:
                    tappedFragment.setTrapType(1);
                    tappedFragment.updateTips();
                    break;
                case 2:
                    tappedFragment.setTrapType(2);
                    tappedFragment.updateTips();
                    break;
                case 3:
                    if ((deviceType & 2) == 2) tempFragment.setTempTypeAndRefresh(true);
                    break;
                case 4:
                    if ((deviceType & 2) == 2) tempFragment.setTempTypeAndRefresh(false);
                    break;
                case 5:
                    humidityFragment.setHumidityTypeAndRefresh(true);
                    break;
                case 6:
                    humidityFragment.setHumidityTypeAndRefresh(false);
                    break;
            }

            tvTriggerType.setText(triggerTypes.get(value));
        });

        dialog.show(getSupportFragmentManager());
    }

    public void onSelectUrlScheme(View view) {
        if (isWindowLocked()) return;
        if (urlFragment != null) urlFragment.selectUrlScheme();
    }
}
