package com.moko.bxp.nordic.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.AppConstants;
import com.moko.bxp.nordic.BaseApplication;
import com.moko.bxp.nordic.R;
import com.moko.bxp.nordic.R2;
import com.moko.bxp.nordic.activity.DeviceInfoActivity;
import com.moko.bxp.nordic.activity.SlotDataActivity;
import com.moko.bxp.nordic.utils.ToastUtils;
import com.moko.support.nordic.MokoSupport;
import com.moko.support.nordic.OrderTaskAssembler;
import com.moko.support.nordic.entity.SlotData;
import com.moko.support.nordic.entity.SlotEnum;
import com.moko.support.nordic.entity.SlotFrameTypeEnum;
import com.moko.support.nordic.entity.UrlSchemeEnum;

import java.util.ArrayList;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SlotFragment extends Fragment {

    private static final String TAG = "SlotFragment";

    @BindView(R2.id.tv_slot1) TextView tvSlot1;
    @BindView(R2.id.rl_slot1) RelativeLayout rlSlot1;
    @BindView(R2.id.tv_slot2) TextView tvSlot2;
    @BindView(R2.id.rl_slot2) RelativeLayout rlSlot2;
    @BindView(R2.id.tv_slot3) TextView tvSlot3;
    @BindView(R2.id.rl_slot3) RelativeLayout rlSlot3;
    @BindView(R2.id.tv_slot4) TextView tvSlot4;
    @BindView(R2.id.rl_slot4) RelativeLayout rlSlot4;
    @BindView(R2.id.tv_slot5) TextView tvSlot5;
    @BindView(R2.id.rl_slot5) RelativeLayout rlSlot5;
    @BindView(R2.id.tv_slot6) TextView tvSlot6;
    @BindView(R2.id.rl_slot6) RelativeLayout rlSlot6;

    private DeviceInfoActivity activity;
    private SlotData slotData;
    private int deviceType;
    private int triggerType;
    private String triggerData;

    private final Handler handler = new Handler();

    public SlotFragment() {}

    public static SlotFragment newInstance() {
        return new SlotFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_slot, container, false);
        ButterKnife.bind(this, view);
        activity = (DeviceInfoActivity) getActivity();
        return view;
    }

    public void createData(SlotFrameTypeEnum frameType, SlotEnum slot) {
        slotData = new SlotData();
        slotData.frameTypeEnum = frameType;
        slotData.slotEnum = slot;

        switch (frameType) {
            case NO_DATA: {
                Intent intent = new Intent(getActivity(), SlotDataActivity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_SLOT_DATA, slotData);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_TYPE, deviceType);
                startActivityForResult(intent, AppConstants.REQUEST_CODE_SLOT_DATA);
                break;
            }
            case IBEACON:
            case TLM:
            case URL:
            case UID:
            case DEVICE:
            case TH:
            case AXIS:
                getSlotData(slot);
                break;
        }
    }

    private void getSlotData(SlotEnum slotEnum) {
        if (activity == null) return;
        activity.showSyncingProgressDialog();
        ArrayList<OrderTask> orderTasks = new ArrayList<>();
        orderTasks.add(OrderTaskAssembler.setSlot(slotEnum));
        orderTasks.add(OrderTaskAssembler.getSlotData());
        orderTasks.add(OrderTaskAssembler.getTrigger());
        orderTasks.add(OrderTaskAssembler.getRssi());
        orderTasks.add(OrderTaskAssembler.getRadioTxPower());
        orderTasks.add(OrderTaskAssembler.getAdvInterval());
        MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
    }

    // 10 20 50 40 FF FF
    public void updateSlotType(byte[] value) {
        changeView((int) value[0] & 0xff, tvSlot1, rlSlot1);
        changeView((int) value[1] & 0xff, tvSlot2, rlSlot2);
        changeView((int) value[2] & 0xff, tvSlot3, rlSlot3);
        changeView((int) value[3] & 0xff, tvSlot4, rlSlot4);
        changeView((int) value[4] & 0xff, tvSlot5, rlSlot5);
        changeView((int) value[5] & 0xff, tvSlot6, rlSlot6);

        if (getActivity() == null) return;

        BaseApplication app = (BaseApplication) getActivity().getApplication();
        int st = app.GetMTAutoWriteStatus();

        // ✅ STEP 1: entra in autowrite -> apri davvero Slot1 (senza click finti)
        if (st == 1) {
            app.SetMTAutoWriteStatus(11); // guardia anti-loop (in corso)
            handler.postDelayed(() -> {
                if (getActivity() == null) return;

                SlotFrameTypeEnum ft = (SlotFrameTypeEnum) rlSlot1.getTag();
                if (ft == null) ft = SlotFrameTypeEnum.NO_DATA;

                // Apri SLOT1
                createData(ft, SlotEnum.SLOT_1);
            }, 600);
            return;
        }

        // ✅ STEP 2: SlotDataActivity ha salvato -> ora cambia password
        if (st == 2) {
            app.SetMTAutoWriteStatus(0); // evita rientri
            if (getActivity() instanceof DeviceInfoActivity) {
                ((DeviceInfoActivity) getActivity()).onMitechAutoWriteSlotConfigured();
            } else {
                ToastUtils.showToast(getActivity(), "Autowrite: step password non disponibile.");
            }
        }
    }

    private void changeView(int frameType, TextView tvSlot, RelativeLayout rlSlot) {
        SlotFrameTypeEnum slotFrameTypeEnum = SlotFrameTypeEnum.fromFrameType(frameType);
        if (slotFrameTypeEnum == null) return;
        tvSlot.setText(slotFrameTypeEnum.getShowName());
        rlSlot.setTag(slotFrameTypeEnum);
    }

    private SlotFrameTypeEnum mSlotFrameTypeEnum;
    private int txPower;
    private int advInterval;

    public void setTxPower(byte[] value) {
        txPower = value[0];
        slotData.txPower = txPower;
    }

    public void setAdvTxPower(byte[] value) {
        switch (mSlotFrameTypeEnum) {
            case IBEACON:
                slotData.rssi_1m = value[0];
                break;
            default:
                slotData.rssi_0m = value[0];
                break;
        }
    }

    public void setAdvInterval(byte[] value) {
        advInterval = Integer.parseInt(MokoUtils.bytesToHexString(value), 16);
        slotData.advInterval = advInterval;
    }

    public void gotoSlotDataDetail() {
        Intent intent = new Intent(getActivity(), SlotDataActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_SLOT_DATA, slotData);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE_TYPE, deviceType);
        intent.putExtra(AppConstants.EXTRA_KEY_TRIGGER_TYPE, triggerType);
        intent.putExtra(AppConstants.EXTRA_KEY_TRIGGER_DATA, triggerData);
        startActivityForResult(intent, AppConstants.REQUEST_CODE_SLOT_DATA);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (getActivity() == null) return;
        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == AppConstants.REQUEST_CODE_SLOT_DATA) {
                Log.i(TAG, "onActivityResult SLOT_DATA -> refresh slot type");
                activity.getSlotType();
            }
        }
    }

    public void setSlotData(byte[] value) {
        int frameType = value[0];
        SlotFrameTypeEnum slotFrameTypeEnum = SlotFrameTypeEnum.fromFrameType(frameType);
        if (slotFrameTypeEnum == null) return;

        mSlotFrameTypeEnum = slotFrameTypeEnum;

        switch (slotFrameTypeEnum) {
            case URL:
                if (value.length > 3) {
                    int urlType = (int) value[2] & 0xff;
                    slotData.urlSchemeEnum = UrlSchemeEnum.fromUrlType(urlType);
                    slotData.urlContent = MokoUtils.bytesToHexString(value).substring(6);
                }
                break;
            case UID:
                if (value.length >= 18) {
                    slotData.namespace = MokoUtils.bytesToHexString(value).substring(4, 24);
                    slotData.instanceId = MokoUtils.bytesToHexString(value).substring(24);
                }
                break;
            case DEVICE:
                byte[] deviceName = Arrays.copyOfRange(value, 1, value.length);
                slotData.deviceName = new String(deviceName);
                break;
            case IBEACON:
                byte[] uuid = Arrays.copyOfRange(value, 1, 17);
                byte[] major = Arrays.copyOfRange(value, 17, 19);
                byte[] minor = Arrays.copyOfRange(value, 19, 21);
                slotData.iBeaconUUID = MokoUtils.bytesToHexString(uuid);
                slotData.major = MokoUtils.bytesToHexString(major);
                slotData.minor = MokoUtils.bytesToHexString(minor);
                break;
        }
    }

    public void setDeviceType(int deviceType) {
        this.deviceType = deviceType;
    }

    public void setTriggerData(byte[] value) {
        triggerType = value[4] & 0xff;
        if (triggerType != 0) {
            triggerData = MokoUtils.bytesToHexString(Arrays.copyOfRange(value, 5, value.length));
        }
    }
}
