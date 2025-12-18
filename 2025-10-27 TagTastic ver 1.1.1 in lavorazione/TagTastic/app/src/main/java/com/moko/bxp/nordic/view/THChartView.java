package com.moko.bxp.nordic.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.moko.ble.lib.utils.MokoUtils;
import com.moko.bxp.nordic.R;

import java.util.ArrayList;
import java.util.List;

public class THChartView extends View {
    //xyеќђж ‡иЅґйўњи‰І
    private int xylinecolor = Color.GRAY;
    //xyеќђж ‡иЅґе®Ѕеє¦
    private int xylinewidth = dpToPx(2);
    //xyеќђж ‡иЅґж–‡е­—йўњи‰І
    private int xytextcolor = Color.GRAY;
    //xyеќђж ‡иЅґж–‡е­—е¤§е°Џ
    private int xytextsize = spToPx(12);
    //жЉзєїе›ѕдё­жЉзєїзљ„йўњи‰І
    private int linecolor = Color.BLUE;
    //xиЅґеђ„дёЄеќђж ‡з‚№ж°ґе№ій—ґи·ќ
    private float interval = dpToPx(1);
    //жЇеђ¦ењЁACTION_UPж—¶пјЊж №жЌ®йЂџеє¦иї›иЎЊи‡Єж»‘еЉЁпјЊжІЎжњ‰и¦Ѓж±‚пјЊе»єи®®е…ій—­пјЊиї‡дєЋеЌ з”ЁGPU
//    private boolean isScroll = false;
    //з»е€¶XYиЅґеќђж ‡еЇ№еє”зљ„з”»з¬”
    private Paint xyPaint;
    //з»е€¶XYиЅґзљ„ж–‡жњ¬еЇ№еє”зљ„з”»з¬”
    private Paint xyTextPaint;
    //з”»жЉзєїеЇ№еє”зљ„з”»з¬”
    private Paint linePaint;
    private int bgColor = Color.TRANSPARENT;
    //з”»иѓЊж™ЇеЇ№еє”зљ„з”»з¬”
    private Paint bgPaint;
    private int width;
    private int height;
    //xиЅґзљ„еЋџз‚№еќђж ‡
    private int xOri;
    //yиЅґзљ„еЋџз‚№еќђж ‡
    private int yOri;
    //жЉзєїз»е€¶еЋџз‚№
    private float ylineOri;
    //жЉзєїз»е€¶еЊєеџџй«еє¦
    private float lineDrawHeight;
    //з¬¬дёЂдёЄз‚№Xзљ„еќђж ‡
    private float xInit;
    //з¬¬дёЂдёЄз‚№еЇ№еє”зљ„жњЂе¤§Xеќђж ‡
    private float maxXInit;
    //з¬¬дёЂдёЄз‚№еЇ№еє”зљ„жњЂе°ЏXеќђж ‡
    private float minXInit;
    //yиЅґж–‡е­—жЏЏиї°
    private String ylineDesc = "Temperature(в„ѓ)";
    //yиЅґзљ„ж–‡е­—жЏЏиї°еЇ№еє”зљ„з”»з¬”
    private Paint ylineDescPaint;
    //yиЅґзљ„ж–‡е­—жЏЏиї°е¤§е°Џ
    private int ylineDescsize = spToPx(14);
    //yиЅґзљ„ж–‡е­—жЏЏиї°е®Ѕй«
    private float ylineDescWidth;
    private float ylineDescHeight;
    //xиЅґеќђж ‡еЇ№еє”зљ„ж•°жЌ®
    private List<Float> xValue = new ArrayList<>();
    //yиЅґеќђж ‡еЇ№еє”зљ„ж•°жЌ®
    private List<String> yValue = new ArrayList<>();
    //жЉзєїеЇ№еє”зљ„ж•°жЌ®
//    private Map<String, Integer> value = new HashMap<>();
    //з‚№е‡»зљ„з‚№еЇ№еє”зљ„XиЅґзљ„з¬¬е‡ дёЄз‚№пјЊй»и®¤1
//    private int selectIndex = 1;
    //XиЅґе€»еє¦ж–‡жњ¬еЇ№еє”зљ„жњЂе¤§зџ©еЅўпјЊдёєдє†йЂ‰дё­ж—¶пјЊењЁxиЅґж–‡жњ¬з”»зљ„жЎ†жЎ†е¤§е°ЏдёЂи‡ґ
//    private Rect xValueRect;
    //йЂџеє¦жЈЂжµ‹е™Ё
//    private VelocityTracker velocityTracker;
    private float minValue;
    private float maxValue;
    private float diffValue;
    private boolean canScroll;

    public THChartView(Context context) {
        this(context, null);
    }

    public THChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public THChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
        initPaint();
    }

    /**
     * е€ќе§‹еЊ–з•«з­†
     */
    private void initPaint() {
        xyPaint = new Paint();
        xyPaint.setAntiAlias(true);
        xyPaint.setStrokeWidth(xylinewidth);
        xyPaint.setStrokeCap(Paint.Cap.ROUND);
        xyPaint.setColor(xylinecolor);

        xyTextPaint = new Paint();
        xyTextPaint.setAntiAlias(true);
        xyTextPaint.setTextSize(xytextsize);
        xyTextPaint.setStrokeCap(Paint.Cap.ROUND);
        xyTextPaint.setColor(xytextcolor);
        xyTextPaint.setStyle(Paint.Style.FILL);

        ylineDescPaint = new Paint();
        ylineDescPaint.setAntiAlias(true);
        ylineDescPaint.setTextSize(ylineDescsize);
        ylineDescPaint.setStrokeCap(Paint.Cap.ROUND);
        ylineDescPaint.setColor(xytextcolor);
        ylineDescPaint.setStyle(Paint.Style.FILL);
        ylineDescPaint.setFakeBoldText(true);

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStrokeWidth(xylinewidth);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(linecolor);
        linePaint.setStyle(Paint.Style.STROKE);

        bgPaint = new Paint();
        bgPaint.setAntiAlias(true);
        bgPaint.setStrokeWidth(xylinewidth);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);
        bgPaint.setColor(bgColor);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * е€ќе§‹еЊ–
     *
     * @param context
     * @param attrs
     * @param defStyleAttr
     */
    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.chartView, defStyleAttr, 0);
        int count = array.getIndexCount();
        for (int i = 0; i < count; i++) {
            int attr = array.getIndex(i);
            if (attr == R.styleable.chartView_xylinecolor) {//xyеќђж ‡иЅґйўњи‰І
                xylinecolor = array.getColor(attr, xylinecolor);
            }
            if (attr == R.styleable.chartView_xylinewidth) {//xyеќђж ‡иЅґе®Ѕеє¦
                xylinewidth = (int) array.getDimension(attr, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, xylinewidth, getResources().getDisplayMetrics()));
            } else if (attr == R.styleable.chartView_xytextcolor) {//xyеќђж ‡иЅґж–‡е­—йўњи‰І
                xytextcolor = array.getColor(attr, xytextcolor);
            } else if (attr == R.styleable.chartView_xytextsize) {//xyеќђж ‡иЅґж–‡е­—е¤§е°Џ
                xytextsize = (int) array.getDimension(attr, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, xytextsize, getResources().getDisplayMetrics()));
            } else if (attr == R.styleable.chartView_linecolor) {//жЉзєїе›ѕдё­жЉзєїзљ„йўњи‰І
                linecolor = array.getColor(attr, linecolor);
            } else if (attr == R.styleable.chartView_ylineDesc) {//жЉзєїе›ѕдё­жЉзєїзљ„йўњи‰І
                ylineDesc = array.getString(attr);
            }
        }
        array.recycle();

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
//        if (changed) {
        //иї™й‡ЊйњЂи¦ЃзЎ®е®ље‡ дёЄеџєжњ¬з‚№пјЊеЏЄжњ‰зЎ®е®љдє†xyиЅґеЋџз‚№еќђж ‡пјЊз¬¬дёЂдёЄз‚№зљ„Xеќђж ‡еЂјеЏЉе…¶жњЂе¤§жњЂе°ЏеЂј
        width = getWidth();
        height = getHeight();
        //YиЅґжЏЏиї°ж–‡жњ¬е®Ѕй«
        Rect ylineRect = getTextBounds(ylineDesc, ylineDescPaint);
        ylineDescWidth = ylineRect.width();
        ylineDescHeight = ylineRect.height();
        yValue.clear();
        //YиЅґж–‡жњ¬жњЂе¤§е®Ѕеє¦
        float textYWdith = getTextBounds("-000.0", xyTextPaint).width();
        for (int i = 0; i < yValue.size(); i++) {//ж±‚еЏ–yиЅґж–‡жњ¬жњЂе¤§зљ„е®Ѕеє¦
            float temp = getTextBounds(String.valueOf(yValue.get(i)), xyTextPaint).width();
            if (temp > textYWdith)
                textYWdith = temp;
        }
        int dp5 = dpToPx(5);
        xOri = (int) (dp5 * 2 + textYWdith + xylinewidth + ylineDescHeight);//dp2жЇyиЅґж–‡жњ¬и·ќз¦»е·¦иѕ№пјЊд»ҐеЏЉи·ќз¦»yиЅґзљ„и·ќз¦»
        // и®Ўз®—yиЅґе€»еє¦
        if (xValue.size() > 0) {
            minValue = xValue.get(0);
            maxValue = xValue.get(0);
            for (int i = 0; i < xValue.size(); i++) {
                float value = xValue.get(i);
                if (value < minValue) {
                    minValue = value;
                } else if (value > maxValue) {
                    maxValue = value;
                }
            }
            if (minValue == maxValue) {
                for (int i = 0; i < 5; i++) {
                    yValue.add(String.valueOf(minValue));
                }
            } else {
                diffValue = (maxValue - minValue) / 2;
                yValue.add(MokoUtils.getDecimalFormat("#.0").format(minValue - diffValue));
                yValue.add(String.valueOf(minValue));
                yValue.add(MokoUtils.getDecimalFormat("#.0").format(minValue + diffValue));
                yValue.add(String.valueOf(maxValue));
                yValue.add(MokoUtils.getDecimalFormat("#.0").format(maxValue + diffValue));
            }
        }

        //и®Ўз®—дё¤з‚№д№‹й—ґзљ„й—ґйљ”
        int size = xValue.size();
        if (size <= 1000) {
            interval = (width - xOri) * 1.0f / size;
        } else {
            canScroll = true;
            interval = (width - xOri) * 1.0f / 1000;
        }

        yOri = height - xylinewidth;//dp3жЇxиЅґж–‡жњ¬и·ќз¦»еє•иѕ№пјЊdp2жЇxиЅґж–‡жњ¬и·ќз¦»xиЅґзљ„и·ќз¦»
        lineDrawHeight = yOri * 1.0f / 2;
        ylineOri = yOri * 3.0f / 4;
        xInit = xOri;
        minXInit = width - interval * (size - 1);
        maxXInit = xInit;
//        }
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF rectF = new RectF(0, 0, width, height);
        canvas.drawRect(rectF, bgPaint);
        drawYLineDesc(canvas);

        drawXY(canvas);
        drawBrokenLineAndPoint(canvas);
    }

    private void drawYLineDesc(Canvas canvas) {
        canvas.save();
        canvas.rotate(-90, 0, 0);
        canvas.translate(-height, 0);
        canvas.drawText(ylineDesc, (height - ylineDescWidth) / 2, ylineDescHeight, ylineDescPaint);
        canvas.restore();
    }

    /**
     * з»е€¶жЉзєїе’ЊжЉзєїдє¤з‚№е¤„еЇ№еє”зљ„з‚№
     *
     * @param canvas
     */
    private void drawBrokenLineAndPoint(Canvas canvas) {
        if (xValue.size() <= 0)
            return;
        //й‡Ќж–°ејЂдёЂдёЄе›ѕе±‚
        int layerId = canvas.saveLayer(0, 0, width, height, null, Canvas.ALL_SAVE_FLAG);
        drawBrokenLine(canvas);
//        drawBrokenPoint(canvas);

        bgPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        RectF rectF = new RectF(0, 0, xOri, height);
        // е°†жЉзєїи¶…е‡єxиЅґеќђж ‡зљ„йѓЁе€†ж€ЄеЏ–жЋ‰
//        linePaint.setColor(Color.WHITE);
        canvas.drawRect(rectF, bgPaint);
        bgPaint.setXfermode(null);
        //дїќе­е›ѕе±‚
        canvas.restoreToCount(layerId);
    }

    /**
     * з»е€¶жЉзєїеЇ№еє”зљ„з‚№
     *
     * @param canvas
     */
//    private void drawBrokenPoint(Canvas canvas) {
//        float dp2 = dpToPx(2);
//        float dp4 = dpToPx(4);
//        float dp7 = dpToPx(7);
//        //з»е€¶иЉ‚з‚№еЇ№еє”зљ„еЋџз‚№
//        for (int i = 0; i < xValue.size(); i++) {
//            float x = xInit + interval * i;
//            float y = yOri - yOri  * value.get(xValue.get(i)) / yValue.get(yValue.size() - 1);
//            //з»е€¶йЂ‰дё­зљ„з‚№
//            if (i == selectIndex - 1) {
//                linePaint.setStyle(Paint.Style.FILL);
//                linePaint.setColor(0xffd0f3f2);
//                canvas.drawCircle(x, y, dp7, linePaint);
//                linePaint.setColor(0xff81dddb);
//                canvas.drawCircle(x, y, dp4, linePaint);
//                drawFloatTextBox(canvas, x, y - dp7, value.get(xValue.get(i)));
//            }
//            //з»е€¶ж™®йЂљзљ„иЉ‚з‚№
//            linePaint.setStyle(Paint.Style.FILL);
//            linePaint.setColor(Color.WHITE);
//            canvas.drawCircle(x, y, dp2, linePaint);
//            linePaint.setStyle(Paint.Style.STROKE);
//            linePaint.setColor(linecolor);
//            canvas.drawCircle(x, y, dp2, linePaint);
//
//        }
//    }

    /**
     * з»е€¶жѕз¤єYеЂјзљ„жµ®еЉЁжЎ†
     *
     * @param canvas
     * @param x
     * @param y
     * @param text
     */
//    private void drawFloatTextBox(Canvas canvas, float x, float y, int text) {
//        int dp6 = dpToPx(6);
//        int dp18 = dpToPx(18);
//        //p1
//        Path path = new Path();
//        path.moveTo(x, y);
//        //p2
//        path.lineTo(x - dp6, y - dp6);
//        //p3
//        path.lineTo(x - dp18, y - dp6);
//        //p4
//        path.lineTo(x - dp18, y - dp6 - dp18);
//        //p5
//        path.lineTo(x + dp18, y - dp6 - dp18);
//        //p6
//        path.lineTo(x + dp18, y - dp6);
//        //p7
//        path.lineTo(x + dp6, y - dp6);
//        //p1
//        path.lineTo(x, y);
//        canvas.drawPath(path, linePaint);
//        linePaint.setColor(Color.WHITE);
//        linePaint.setTextSize(spToPx(14));
//        Rect rect = getTextBounds(text + "", linePaint);
//        canvas.drawText(text + "", x - rect.width() / 2, y - dp6 - (dp18 - rect.height()) / 2, linePaint);
//    }

    /**
     * з»е€¶жЉзєї
     *
     * @param canvas
     */
    private void drawBrokenLine(Canvas canvas) {
//        linePaint.setColor(linecolor);
        //з»е€¶жЉзєї
        Path path = new Path();
        float x = xInit;
        float y;
        if (maxValue == minValue) {
            y = ylineOri;
        } else {
            y = ylineOri - lineDrawHeight * ((xValue.get(0) - minValue) / (maxValue - minValue));
        }
        path.moveTo(x, y);
        for (int i = 1; i < xValue.size(); i++) {
            x = xInit + interval * i;
            if (maxValue == minValue) {
                y = ylineOri;
            } else {
                y = ylineOri - lineDrawHeight * ((xValue.get(i) - minValue) / (maxValue - minValue));
            }
            path.lineTo(x, y);
//            canvas.drawLine(x, y, x, yOri, linePaint);
        }
        canvas.drawPath(path, linePaint);
    }

    /**
     * з»е€¶XYеќђж ‡
     *
     * @param canvas
     */
    private void drawXY(Canvas canvas) {
        int length = dpToPx(1);//е€»еє¦зљ„й•їеє¦
        //з»е€¶Yеќђж ‡
        canvas.drawLine(xOri - xylinewidth / 2, 0, xOri - xylinewidth / 2, yOri, xyPaint);
//        //з»е€¶yиЅґз®­е¤ґ
//        xyPaint.setStyle(Paint.Style.STROKE);
//        Path path = new Path();
//        path.moveTo(xOri - xylinewidth / 2 - dpToPx(5), dpToPx(12));
//        path.lineTo(xOri - xylinewidth / 2, xylinewidth / 2);
//        path.lineTo(xOri - xylinewidth / 2 + dpToPx(5), dpToPx(12));
//        canvas.drawPath(path, xyPaint);
        //з»е€¶yиЅґе€»еє¦
        int yScale = yOri / (yValue.size() - 1);
        for (int i = 0; i < yValue.size(); i++) {
            //з»е€¶YиЅґе€»еє¦
            canvas.drawLine(xOri, yOri - yScale * i, xOri + length, yOri - yScale * i, xyPaint);
//            xyTextPaint.setColor(xytextcolor);
            //з»е€¶YиЅґж–‡жњ¬
            String text = yValue.get(i);
            Rect rect = getTextBounds(text, xyTextPaint);
            if (i == 0) {
                canvas.drawText(text, 0, text.length(), xOri - xylinewidth - dpToPx(5) - rect.width(), yOri - xylinewidth, xyTextPaint);
                continue;
            } else if (i == 4) {
                canvas.drawText(text, 0, text.length(), xOri - xylinewidth - dpToPx(5) - rect.width(), rect.height() + xylinewidth, xyTextPaint);
                continue;
            } else {
                canvas.drawText(text, 0, text.length(), xOri - xylinewidth - dpToPx(5) - rect.width(), yOri - xylinewidth - yScale * i + rect.height() / 2, xyTextPaint);
                continue;
            }
        }
        //з»е€¶XиЅґеќђж ‡
//        canvas.drawLine(xOri, yOri + xylinewidth / 2, width, yOri + xylinewidth / 2, xyPaint);
//        //з»е€¶xиЅґз®­е¤ґ
//        xyPaint.setStyle(Paint.Style.STROKE);
//        path = new Path();
//        //ж•ґдёЄXиЅґзљ„й•їеє¦
//        float xLength = xInit + interval * (xValue.size() - 1) + (width - xOri) * 0.1f;
//        if (xLength < width)
//            xLength = width;
//        path.moveTo(xLength - dpToPx(12), yOri + xylinewidth / 2 - dpToPx(5));
//        path.lineTo(xLength - xylinewidth / 2, yOri + xylinewidth / 2);
//        path.lineTo(xLength - dpToPx(12), yOri + xylinewidth / 2 + dpToPx(5));
//        canvas.drawPath(path, xyPaint);
        //з»е€¶xиЅґе€»еє¦
//        for (int i = 0; i < xValue.size(); i++) {
//            float x = xInit + interval * i;
//            if (x >= xOri) {//еЏЄз»е€¶д»ЋеЋџз‚№ејЂе§‹зљ„еЊєеџџ
//                xyTextPaint.setColor(xytextcolor);
//                canvas.drawLine(x, yOri, x, yOri - length, xyPaint);
//                //з»е€¶XиЅґяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя