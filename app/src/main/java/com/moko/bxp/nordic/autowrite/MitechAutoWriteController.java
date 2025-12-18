package com.moko.bxp.nordic.autowrite;

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
        SEND_SLOT1_URL_BATCH,
        WAIT_SLOT_BATCH_FINISH,
        SEND_PASSWORD_CHANGE,
        WAIT_PASSWORD_CHANGE_DISCONNECT,
        DONE,
        FAIL
    }

    private final DeviceInfoActivity activity;
    private final Listener listener;

    private State state = State.IDLE;
    private boolean running = false;

    private boolean slotConfigError = false;

    private static final String NEW_PASSWORD = "20250430";

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
        slotConfigError = false;
        state = State.SEND_SLOT1_URL_BATCH;
        activity.showSyncingProgressDialog();
        advance();
    }

    public void stopFail(String reason) {
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
        switch (state) {

            case SEND_SLOT1_URL_BATCH: {
                // SLOT 1 -> URL
                ArrayList<OrderTask> tasks = new ArrayList<>();
                tasks.add(OrderTaskAssembler.setSlot(SlotEnum.SLOT_1));

                // URL: https://www.mitechsystem.it
                // Eddystone URL: frameType + scheme + content
                String frameTypeHex = SlotFrameTypeEnum.URL.getFrameType(); // di solito "10"
                String schemeHex = MokoUtils.int2HexString(UrlSchemeEnum.HTTPS_WWW.getUrlType()); // 0x01
                String urlContentHex = MokoUtils.string2Hex("mitechsystem.it"); // .it non è in tabella espansione
                byte[] urlParams = MokoUtils.hex2bytes(frameTypeHex + schemeHex + urlContentHex);

                tasks.add(OrderTaskAssembler.setSlotData(urlParams));

                // TX Power: 4 dBm
                tasks.add(OrderTaskAssembler.setRadioTxPower(MokoUtils.toByteArray(4, 1)));

                // RSSI@0m: 0 dBm (come nel tuo SetMitechValoriTag)
                tasks.add(OrderTaskAssembler.setRssi(MokoUtils.toByteArray(0, 1)));

                // Adv Interval: 1000ms (nel tuo codice: advIntervalInt*100, quindi 10->1000)
                tasks.add(OrderTaskAssembler.setAdvInterval(MokoUtils.toByteArray(1000, 2)));

                slotConfigError = false;
                state = State.WAIT_SLOT_BATCH_FINISH;
                MokoSupport.getInstance().sendOrder(tasks.toArray(new OrderTask[]{}));
                break;
            }

            case SEND_PASSWORD_CHANGE: {
                // Cambio password: 20250430
                // Importantissimo: prevenire popup disconnect in DeviceInfoActivity
                activity.markExpectingAutoWritePasswordDisconnect();

                state = State.WAIT_PASSWORD_CHANGE_DISCONNECT;
                activity.modifyPassword(NEW_PASSWORD); // usa la tua pipeline già pronta (setLockState)
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
            // Se disconnette in un momento non previsto, è failure.
            if (state != State.WAIT_PASSWORD_CHANGE_DISCONNECT) {
                stopFail("Autowrite fallito: disconnessione inattesa");
            }
        }
    }

    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        if (!running) return;

        final String action = event.getAction();

        if (MokoConstants.ACTION_ORDER_TIMEOUT.equals(action)) {
            stopFail("Autowrite fallito: timeout BLE");
            return;
        }

        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
            if (state == State.WAIT_SLOT_BATCH_FINISH) {
                if (slotConfigError) {
                    stopFail("Autowrite fallito: salvataggio slot non riuscito");
                } else {
                    state = State.SEND_PASSWORD_CHANGE;
                    advance();
                }
            }
            return;
        }

        // Intercettiamo errori “config” come fa SlotDataActivity (key == 0x0D)
        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            if (response == null) return;

            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            byte[] value = response.responseValue;

            if (orderCHAR == OrderCHAR.CHAR_PARAMS && value != null && value.length > 1) {
                int key = value[1] & 0xFF;
                if (key == 0x0D) {
                    slotConfigError = true;
                }
            }
        }

        // La “success” del cambio password nel tuo progetto passa da:
        // CHAR_DISCONNECT con mDisconnectType == 1 + isModifyPassword true
        // e poi la UI fa finish(). Qui non facciamo altro: DeviceInfoActivity chiuderà e noi completiamo lì.
    }
}
