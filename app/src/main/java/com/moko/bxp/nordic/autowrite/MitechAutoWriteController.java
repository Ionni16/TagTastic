package com.moko.bxp.nordic.autowrite;

import android.os.Handler;
import android.os.Looper;

import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.activity.DeviceInfoActivity;
import com.moko.support.nordic.MokoSupport;
import com.moko.support.nordic.OrderTaskAssembler;
import com.moko.support.nordic.entity.OrderCHAR;
import com.moko.support.nordic.entity.SlotEnum;
import com.moko.support.nordic.entity.SlotFrameTypeEnum;
import com.moko.support.nordic.entity.UrlSchemeEnum;

import java.util.ArrayList;

public class MitechAutoWriteController {

    public interface Listener {
        void onCompleted();
        void onFailed(String reason);
    }

    private enum State {
        IDLE,

        SEND_SLOT_URL_AND_RADIO,
        WAIT_SLOT_URL_AND_RADIO_FINISH,

        SEND_PASSWORD_CHANGE,
        WAIT_PASSWORD_CHANGE_DISCONNECT,

        DONE,
        FAIL
    }

    private final DeviceInfoActivity activity;
    private final Listener listener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private State state = State.IDLE;
    private boolean running = false;

    // set true when firmware reports "params not accepted" (0x0D)
    private boolean configError = false;

    // REQUIRED
    private static final String NEW_PASSWORD = "20250430";

    // REQUIRED URL: https://www.mtsystem.it/
    private static final SlotEnum TARGET_SLOT = SlotEnum.SLOT_1;
    private static final String URL_CONTENT = "mtsystem.it/"; // scheme provides https://www.

    // Radio params (safe / compatible)
    private static final int TX_POWER_DBM = 4;      // Radio TX Power
    private static final int RSSI_0M_DBM = -59;     // Adv TX Power (RSSI@0m)
    private static final int ADV_INTERVAL_MS = 1000; // 1000ms (your UI "10" => 1000ms)

    // pacing
    private static final long STEP_DELAY_MS = 200;

    public MitechAutoWriteController(DeviceInfoActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;

        running = true;
        configError = false;
        state = State.SEND_SLOT_URL_AND_RADIO;

        activity.showSyncingProgressDialog();

        // IMPORTANT: stop any delayed tasks in the Activity that could send BLE orders
        activity.cancelPendingUiAndBleRunnablesForAutoWrite();

        advance();
    }

    private void stopFail(String reason) {
        if (!running) return;
        running = false;
        state = State.FAIL;

        activity.dismissSyncProgressDialog();
        if (listener != null) listener.onFailed(reason);
    }

    private void stopOk() {
        if (!running) return;
        running = false;
        state = State.DONE;

        activity.dismissSyncProgressDialog();
        if (listener != null) listener.onCompleted();
    }

    private void advance() {
        if (!running) return;

        switch (state) {

            case SEND_SLOT_URL_AND_RADIO: {
                // IMPORTANT:
                // Radio params are per-slot in this firmware stack (UrlFragment does setSlot before radio params),
                // so we MUST setSlot in the same batch to avoid "parametri radio non accettati".
                ArrayList<OrderTask> tasks = new ArrayList<>();
                tasks.add(OrderTaskAssembler.setSlot(TARGET_SLOT));

                // Build Eddystone-URL params: [frameType][scheme][urlContent]
                String frameTypeHex = SlotFrameTypeEnum.URL.getFrameType();
                String schemeHex = MokoUtils.int2HexString(UrlSchemeEnum.HTTPS_WWW.getUrlType());
                String urlContentHex = MokoUtils.string2Hex(URL_CONTENT);
                byte[] urlParams = MokoUtils.hex2bytes(frameTypeHex + schemeHex + urlContentHex);

                // Send slot data + radio params in the same order used by UrlFragment.sendData()
                tasks.add(OrderTaskAssembler.setSlotData(urlParams));
                tasks.add(OrderTaskAssembler.setRadioTxPower(MokoUtils.toByteArray(TX_POWER_DBM, 1)));
                tasks.add(OrderTaskAssembler.setRssi(MokoUtils.toByteArray(RSSI_0M_DBM, 1)));
                tasks.add(OrderTaskAssembler.setAdvInterval(MokoUtils.toByteArray(ADV_INTERVAL_MS, 2)));

                configError = false;
                state = State.WAIT_SLOT_URL_AND_RADIO_FINISH;

                MokoSupport.getInstance().sendOrder(tasks.toArray(new OrderTask[0]));
                break;
            }

            case SEND_PASSWORD_CHANGE: {
                // Password change triggers disconnect after success.
                activity.markExpectingAutoWritePasswordDisconnect();
                state = State.WAIT_PASSWORD_CHANGE_DISCONNECT;

                // MUST send password twice when supported (DeviceInfoActivity handles it)
                activity.modifyPasswordForAutoWrite(NEW_PASSWORD);
                break;
            }

            default:
                break;
        }
    }

    public void onConnectStatusEvent(ConnectStatusEvent event) {
        if (!running) return;

        final String action = event.getAction();

        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            // During password-change flow, disconnect is expected = SUCCESS
            if (state == State.WAIT_PASSWORD_CHANGE_DISCONNECT) {
                stopOk();
                return;
            }

            // Otherwise it's unexpected
            stopFail("Autowrite fallito: disconnessione inattesa");
        }
    }

    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        if (!running) return;

        final String action = event.getAction();

        if (MokoConstants.ACTION_ORDER_TIMEOUT.equals(action)) {
            stopFail("Autowrite fallito: timeout BLE");
            return;
        }

        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            if (response == null) return;

            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            byte[] value = response.responseValue;

            // Same rule used in SlotDataActivity: CHAR_PARAMS key==0x0D => params not accepted
            if (orderCHAR == OrderCHAR.CHAR_PARAMS && value != null && value.length > 1) {
                int key = value[1] & 0xFF;
                if (key == 0x0D) {
                    configError = true;
                }
            }
        }

        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {

            if (state == State.WAIT_SLOT_URL_AND_RADIO_FINISH) {
                if (configError) {
                    stopFail("Autowrite fallito: parametri non accettati (radio/url)");
                    return;
                }

                state = State.SEND_PASSWORD_CHANGE;
                mainHandler.postDelayed(this::advance, STEP_DELAY_MS);
            }
        }
    }
}
