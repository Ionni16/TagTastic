package com.moko.bxp.nordic.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.R;
import com.moko.bxp.nordic.R2;
import com.moko.bxp.nordic.able.ISlotDataAction;
import com.moko.bxp.nordic.activity.SlotDataActivity;
import com.moko.bxp.nordic.dialog.UrlSchemeDialog;
import com.moko.bxp.nordic.utils.ToastUtils;
import com.moko.support.nordic.MokoSupport;
import com.moko.support.nordic.OrderTaskAssembler;
import com.moko.support.nordic.entity.SlotFrameTypeEnum;
import com.moko.support.nordic.entity.TxPowerEnum;
import com.moko.support.nordic.entity.UrlExpansionEnum;
import com.moko.support.nordic.entity.UrlSchemeEnum;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class UrlFragment extends Fragment implements SeekBar.OnSeekBarChangeListener, ISlotDataAction {

    private static final String TAG = "UrlFragment";
    private static final String FILTER_ASCII = "[!-~]*";

    @BindView(R2.id.et_url)
    EditText etUrl;
    @BindView(R2.id.sb_adv_tx_power)
    SeekBar sbRssi; // RSSI@0m
    @BindView(R2.id.sb_tx_power)
    SeekBar sbTxPower; // Radio TX Power enum
    @BindView(R2.id.tv_url_scheme)
    TextView tvUrlScheme;
    @BindView(R2.id.tv_adv_tx_power)
    TextView tvRssi;
    @BindView(R2.id.tv_tx_power)
    TextView tvTxPower;
    @BindView(R2.id.et_adv_interval)
    EditText etAdvInterval;

    private SlotDataActivity activity;

    // Prepared by isValid()
    private byte[] advIntervalBytes;
    private byte[] advTxPowerBytes;
    private byte[] txPowerBytes;
    private byte[] urlParamsBytes;

    private String mUrlSchemeHex;

    public UrlFragment() {
    }

    public static UrlFragment newInstance() {
        return new UrlFragment();
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
        View view = inflater.inflate(R.layout.fragment_url, container, false);
        ButterKnife.bind(this, view);

        activity = (SlotDataActivity) getActivity();

        sbRssi.setOnSeekBarChangeListener(this);
        sbTxPower.setOnSeekBarChangeListener(this);

        InputFilter asciiFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if (!(source + "").matches(FILTER_ASCII)) return "";
                return null;
            }
        };
        etUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), asciiFilter});

        setDefaultSafe();
        return view;
    }

    private void setDefaultSafe() {
        if (activity == null || activity.slotData == null) return;

        // Default scheme (most common)
        mUrlSchemeHex = MokoUtils.int2HexString(UrlSchemeEnum.HTTP_WWW.getUrlType());
        tvUrlScheme.setText(UrlSchemeEnum.HTTP_WWW.getUrlDesc());

        // Defaults for new/empty slot
        if (activity.slotData.frameTypeEnum == SlotFrameTypeEnum.NO_DATA) {
            // UI works in 100ms steps, range 1..100
            etAdvInterval.setText("10"); // 1000ms
            etAdvInterval.setSelection(etAdvInterval.getText().toString().length());

            // SAFE defaults to avoid "radio params not accepted"
            applyRssiDbm(-59); // classic Eddystone recommended reference
            applyTxPowerDbm(0); // safer than 4 on some firmwares

            return;
        }

        // Existing slot values
        int advIntervalProgress = activity.slotData.advInterval / 100;
        etAdvInterval.setText(String.valueOf(advIntervalProgress));
        etAdvInterval.setSelection(etAdvInterval.getText().toString().length());
        advIntervalBytes = MokoUtils.toByteArray(activity.slotData.advInterval, 2);

        if (activity.slotData.frameTypeEnum == SlotFrameTypeEnum.TLM) {
            // Some firmwares ignore RSSI for TLM; keep safe and consistent
            applyRssiDbm(-59);
        } else {
            applyRssiDbm(activity.slotData.rssi_0m);
        }

        applyTxPowerDbm(activity.slotData.txPower);

        // If current slot is URL, load scheme + content
        if (activity.slotData.frameTypeEnum == SlotFrameTypeEnum.URL) {
            if (activity.slotData.urlSchemeEnum != null) {
                mUrlSchemeHex = MokoUtils.int2HexString(activity.slotData.urlSchemeEnum.getUrlType());
                tvUrlScheme.setText(activity.slotData.urlSchemeEnum.getUrlDesc());
            }

            String urlHex = activity.slotData.urlContent;
            if (!TextUtils.isEmpty(urlHex) && urlHex.length() >= 2) {
                // Try URL expansion decode (best effort)
                try {
                    String lastByte = urlHex.substring(urlHex.length() - 2);
                    int expType = Integer.parseInt(lastByte, 16);
                    UrlExpansionEnum expEnum = UrlExpansionEnum.fromUrlExpanType(expType);
                    if (expEnum == null) {
                        etUrl.setText(MokoUtils.hex2String(urlHex));
                    } else {
                        etUrl.setText(MokoUtils.hex2String(urlHex.substring(0, urlHex.length() - 2)) + expEnum.getUrlExpanDesc());
                    }
                } catch (Exception e) {
                    try {
                        etUrl.setText(MokoUtils.hex2String(urlHex));
                    } catch (Exception ignored) {
                    }
                }
                etUrl.setSelection(etUrl.getText().toString().length());
            }
        }
    }

    // ---- Seekbars ----

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser) return;
        updateData(seekBar.getId(), progress);
    }

    // keep old misspelt name (compat)
    public void upgdateData(int viewId, int progress) {
        updateData(viewId, progress);
    }

    private void updateData(int viewId, int progress) {
        if (viewId == R.id.sb_adv_tx_power) {
            int rssi = progress - 100;

            // Clamp to safe realistic range
            rssi = clamp(rssi, -100, 0);

            tvRssi.setText(String.format("%ddBm", rssi));
            advTxPowerBytes = MokoUtils.toByteArray(rssi, 1);

        } else if (viewId == R.id.sb_tx_power) {
            TxPowerEnum txPowerEnum = TxPowerEnum.fromOrdinal(progress);
            int tx = (txPowerEnum != null) ? txPowerEnum.getTxPower() : 0;

            tvTxPower.setText(String.format("%ddBm", tx));
            txPowerBytes = MokoUtils.toByteArray(tx, 1);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private void applyRssiDbm(int rssiDbm) {
        int safe = clamp(rssiDbm, -100, 0);
        tvRssi.setText(String.format("%ddBm", safe));
        advTxPowerBytes = MokoUtils.toByteArray(safe, 1);
        sbRssi.setProgress(safe + 100);
    }

    private void applyTxPowerDbm(int txDbm) {
        // If device value not exactly in enum, fall back to 0dBm
        TxPowerEnum e = TxPowerEnum.fromTxPower(txDbm);
        if (e == null) e = TxPowerEnum.fromTxPower(0);

        int safeTx = (e != null) ? e.getTxPower() : 0;
        tvTxPower.setText(String.format("%ddBm", safeTx));
        txPowerBytes = MokoUtils.toByteArray(safeTx, 1);

        if (e != null) sbTxPower.setProgress(e.ordinal());
    }

    // ---- ISlotDataAction ----

    @Override
    public boolean isValid() {
        if (activity == null || activity.slotData == null) return false;

        String urlContent = etUrl.getText().toString().trim();
        String advInterval = etAdvInterval.getText().toString().trim();

        if (TextUtils.isEmpty(urlContent) || TextUtils.isEmpty(mUrlSchemeHex)) {
            ToastUtils.showToast(activity, "Data format incorrect!");
            return false;
        }

        if (TextUtils.isEmpty(advInterval)) {
            ToastUtils.showToast(activity, "The Adv interval can not be empty.");
            return false;
        }

        int advIntervalInt;
        try {
            advIntervalInt = Integer.parseInt(advInterval);
        } catch (Exception e) {
            ToastUtils.showToast(activity, "The Adv interval range is 1~100");
            return false;
        }

        if (advIntervalInt < 1 || advIntervalInt > 100) {
            ToastUtils.showToast(activity, "The Adv interval range is 1~100");
            return false;
        }

        // Ensure bytes exist even if user never touched seekbars
        if (advTxPowerBytes == null) applyRssiDbm(-59);
        if (txPowerBytes == null) applyTxPowerDbm(0);

        String urlContentHex;

        // Keep original encoding rules
        if (urlContent.contains(".")) {
            String urlExpansion = urlContent.substring(urlContent.lastIndexOf("."));
            UrlExpansionEnum urlExpansionEnum = UrlExpansionEnum.fromUrlExpanDesc(urlExpansion);

            if (urlExpansionEnum == null) {
                // Contains dot but not eddystone suffix => total len 2..17
                if (urlContent.length() < 2 || urlContent.length() > 17) {
                    ToastUtils.showToast(activity, "Data format incorrect!");
                    return false;
                }
                urlContentHex = MokoUtils.string2Hex(urlContent);
            } else {
                String content = urlContent.substring(0, urlContent.lastIndexOf("."));
                if (content.length() < 1 || content.length() > 16) {
                    ToastUtils.showToast(activity, "Data format incorrect!");
                    return false;
                }
                urlContentHex = MokoUtils.string2Hex(content)
                        + MokoUtils.byte2HexString((byte) urlExpansionEnum.getUrlExpanType());
            }
        } else {
            // No dot => total len 2..17
            if (urlContent.length() < 2 || urlContent.length() > 17) {
                ToastUtils.showToast(activity, "Data format incorrect!");
                return false;
            }
            urlContentHex = MokoUtils.string2Hex(urlContent);
        }

        // IMPORTANT: force URL frame type here (prevents mismatch / race)
        String frameTypeHex = SlotFrameTypeEnum.URL.getFrameType();
        String urlParamsHex = frameTypeHex + mUrlSchemeHex + urlContentHex;

        try {
            urlParamsBytes = MokoUtils.hex2bytes(urlParamsHex);
        } catch (Exception e) {
            ToastUtils.showToast(activity, "Data format incorrect!");
            return false;
        }

        // UI value is in 100ms steps
        advIntervalBytes = MokoUtils.toByteArray(advIntervalInt * 100, 2);
        return true;
    }

    @Override
    public void sendData() {
        if (activity == null || activity.slotData == null) return;

        ArrayList<OrderTask> tasks = new ArrayList<>();
        tasks.add(OrderTaskAssembler.setSlot(activity.slotData.slotEnum));
        tasks.add(OrderTaskAssembler.setSlotData(urlParamsBytes));

        // Keep radio params conservative and consistent
        tasks.add(OrderTaskAssembler.setRadioTxPower(txPowerBytes));
        tasks.add(OrderTaskAssembler.setRssi(advTxPowerBytes));
        tasks.add(OrderTaskAssembler.setAdvInterval(advIntervalBytes));

        MokoSupport.getInstance().sendOrder(tasks.toArray(new OrderTask[0]));
    }

    @Override
    public void resetParams() {
        if (activity == null || activity.slotData == null) return;

        if (activity.slotData.frameTypeEnum == activity.currentFrameTypeEnum) {
            int advIntervalProgress = activity.slotData.advInterval / 100;
            etAdvInterval.setText(String.valueOf(advIntervalProgress));
            etAdvInterval.setSelection(etAdvInterval.getText().toString().length());
            advIntervalBytes = MokoUtils.toByteArray(activity.slotData.advInterval, 2);

            applyRssiDbm(activity.slotData.rssi_0m);
            applyTxPowerDbm(activity.slotData.txPower);

            if (activity.slotData.urlSchemeEnum != null) {
                mUrlSchemeHex = MokoUtils.int2HexString(activity.slotData.urlSchemeEnum.getUrlType());
                tvUrlScheme.setText(activity.slotData.urlSchemeEnum.getUrlDesc());
            }

            String urlHex = activity.slotData.urlContent;
            if (!TextUtils.isEmpty(urlHex) && urlHex.length() >= 2) {
                try {
                    String lastByte = urlHex.substring(urlHex.length() - 2);
                    int expType = Integer.parseInt(lastByte, 16);
                    UrlExpansionEnum expEnum = UrlExpansionEnum.fromUrlExpanType(expType);
                    if (expEnum == null) {
                        etUrl.setText(MokoUtils.hex2String(urlHex));
                    } else {
                        etUrl.setText(MokoUtils.hex2String(urlHex.substring(0, urlHex.length() - 2)) + expEnum.getUrlExpanDesc());
                    }
                } catch (Exception e) {
                    try {
                        etUrl.setText(MokoUtils.hex2String(urlHex));
                    } catch (Exception ignored) {
                    }
                }
                etUrl.setSelection(etUrl.getText().toString().length());
            }
        } else {
            etAdvInterval.setText("10");
            etAdvInterval.setSelection(etAdvInterval.getText().toString().length());

            applyRssiDbm(-59);
            applyTxPowerDbm(0);

            etUrl.setText("");
        }
    }

    // ---- Scheme dialog ----

    public void selectUrlScheme() {
        UrlSchemeDialog dialog = new UrlSchemeDialog(getActivity());
        dialog.setData(tvUrlScheme.getText().toString());
        dialog.setUrlSchemeClickListener(new UrlSchemeDialog.UrlSchemeClickListener() {
            @Override
            public void onEnsureClicked(String urlType) {
                UrlSchemeEnum urlSchemeEnum = UrlSchemeEnum.fromUrlType(Integer.valueOf(urlType));
                if (urlSchemeEnum != null) {
                    tvUrlScheme.setText(urlSchemeEnum.getUrlDesc());
                }
                mUrlSchemeHex = MokoUtils.int2HexString(Integer.valueOf(urlType));
            }
        });
        dialog.show();
    }

    // ---- Mitech defaults (called by SlotDataActivity autowrite or controller flow) ----
    public void SetMitechValoriTag() {
        if (activity == null || activity.slotData == null) return;

        // 10 => 1000ms (valid range 1..100)
        etAdvInterval.setText("10");
        etAdvInterval.setSelection(etAdvInterval.getText().toString().length());

        // SAFE values to avoid "radio params not accepted"
        applyRssiDbm(-59);
        applyTxPowerDbm(0);

        // Scheme: https://www.
        UrlSchemeEnum scheme = UrlSchemeEnum.HTTPS_WWW;
        tvUrlScheme.setText(scheme.getUrlDesc());
        mUrlSchemeHex = MokoUtils.int2HexString(scheme.getUrlType());

        // Content only (no scheme)
        etUrl.setText("mtsystem.it/");
        etUrl.setSelection(etUrl.getText().toString().length());
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
