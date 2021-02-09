package com.mt.retail.mtapidemo;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mt.retail.platform.printer.MtPrintResult;
import com.mt.retail.printapi.IMtPrintView;
import com.mt.retail.printapi.MtPrintApi;
import com.mt.retail.weighapi.IMtWeighView;
import com.mt.retail.weighapi.MtWeighApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements IMtWeighView, IMtPrintView, Handler.Callback, View.OnClickListener {
    // 1. 称重返回JSON字符串中key名称
    public static final String RET_JSON_VALUE_STATUS_OK      = "0";
    public static final String RET_JSON_KEY_STATUS           = "result"; // Don't change this value, because client API use it.
    public static final String RET_JSON_KEY_WEIGHT_NET       = "net";
    public static final String RET_JSON_KEY_ZERO             = "zero";
    public static final String RET_JSON_KEY_TARE_TYPE        = "tareType";
    public static final String RET_JSON_KEY_TARE             = "tare";
    public static final String RET_JSON_KEY_UNIT             = "unit";
    public static final String RET_JSON_KEY_TARE_UNIT        = "tareUnit";
    public static final String RET_JSON_KEY_MIN_PRESET_TARE  = "minPresetTare";
    public static final String RET_JSON_KEY_MAX_PRESET_TARE  = "maxPresetTare";
    public static final String RET_JSON_KEY_RANGE_NUMBER     = "rangeNumber";
    public static final String RET_JSON_KEY_MIN_RANGE        = "minRange";
    public static final String RET_JSON_KEY_MAX_RANGE        = "maxRange";
    public static final String RET_JSON_KEY_RESOLUTION       = "resolution";

    // 2. Load Cell Special Return Code
    public static final int RETURN_CODE_OK                    =  0;
    public static final int RETURN_CODE_WEIGHT_UNSTABLE       = -1000;
    public static final int RETURN_CODE_WEIGHT_UNDERLOAD      = -1001;
    public static final int RETURN_CODE_WEIGHT_OVERLOAD       = -1002;

    // 3. Test data
    private static final int FONT_BIG_SIZE    = 40;
    private static final int FONT_NORMAL_SIZE = 35;
    private static final int FONT_SMALL_SIZE  = 28;

    public static final byte ALIGNMENT_LEFT    = 0;
    public static final byte ALIGNMENT_CENTER  = 1;
    public static final byte ALIGNMENT_RIGHT   = 2;

    // 子线程消息ID
    private static final int MSG_PRINT_TICKET = 1;

    private MyPresentation mSecondScreen;

    private MtWeighApi mMtWeightService;
    private MtPrintApi mMtPrintService;

    private Handler mSubThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initSystemControlView();

        // 创建业务子线程
        HandlerThread subThread = new HandlerThread("WorkThread");
        subThread.start();
        mSubThreadHandler = new Handler(subThread.getLooper(), this);

        mMtWeightService = MtWeighApi.getInstance();
        mMtPrintService  = MtPrintApi.getInstance();
        mMtWeightService.connectToService(this, this);
        mMtPrintService.connectToService(this, this);

        Log.e("sty", "model: " + Build.MODEL);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        mMtWeightService.disconnectService(this);
        mMtPrintService.disconnectService(this);
        mSubThreadHandler.getLooper().quit();
    }

    @Override
    protected void onResume(){
        super.onResume();

        // 启动第二屏幕
        setupSecondScreen();
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(null != mSecondScreen){
            mSecondScreen.cancel();
        }
    }

    // 工作子线程用
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what){
            case MSG_PRINT_TICKET:
                printTestText();
                break;
        }
        return false;
    }

    @Override
    public void onMtPrintServiceConnected() {
        initPrintView();
    }

    @Override
    public void onMtPrintServiceDisconnected() {
        Log.e("MtApiDemo", "PrintService is Disconnected!");
    }

    @Override
    public void onPlugEvent(String s, int i) {

    }

    @Override
    public void onPrinterListChanged(ArrayList<String> arrayList) {

    }

    @Override
    public void onFeedPaperFinished(int i) {

    }

    @Override
    public void onPrintFinished(int i) {

    }

    @Override
    public void onMtWeighServiceConnected() {
        initWeightView();
    }

    @Override
    public void onMtWeighServiceDisconnected() {
        runOnUiThread(() -> Toast.makeText(this, "The connection to WeighService is Disconnected!" , Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onWeightChanged(String weightInfo) {
        runOnUiThread(()->updateUIWeight(weightInfo));
    }

    @Override
    public void onBaseInfoChanged(String baseInfo) {
        runOnUiThread(() -> updateUIBaseInfo(baseInfo));
    }

    @Override
    public void onSetTareFinished(String s) {
        runOnUiThread(() -> Toast.makeText(this, "Set Tare finished, return:" + s , Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onZeroFinished(int returnCode) {
        if(RETURN_CODE_OK != returnCode){
            runOnUiThread(() -> Toast.makeText(this, "Zero failed! ", Toast.LENGTH_SHORT).show());
        }
    }

    private void initWeightView(){
        Button setTareButton   = findViewById(R.id.measureTareButton);
        Button clearTareButton = findViewById(R.id.clearTareButton);
        Button doZeroButton    = findViewById(R.id.doZeroButton);
        Button openCashBox = findViewById(R.id.openCashBoxButton);

        setTareButton.setOnClickListener(v -> mMtWeightService.setTareAsync());

        clearTareButton.setOnClickListener(v -> updateUIClearTare(mMtWeightService.clearTare()));

        doZeroButton.setOnClickListener(v -> mMtWeightService.setZeroAsync());

        openCashBox.setOnClickListener(v -> mMtWeightService.openCashDrawer());
    }

    private void initPrintView(){
        Button printTextButton= findViewById(R.id.printTextButton);
        Button cutPaperButton = findViewById(R.id.cutPaperButton);
        Button fitFontButton = findViewById(R.id.fitFontButton);
        Button normalFontButton = findViewById(R.id.normalFontButton);

        printTextButton.setOnClickListener(v -> mSubThreadHandler.sendEmptyMessage(MSG_PRINT_TICKET));

        cutPaperButton.setOnClickListener(v -> mMtPrintService.cutPaper(80, new MtPrintResult()));

        fitFontButton.setOnClickListener(v -> mMtPrintService.setScaleX(0.5f, new MtPrintResult()));
        normalFontButton.setOnClickListener(v -> mMtPrintService.setScaleX(1f, new MtPrintResult()));
    }

    private void initSystemControlView(){
        findViewById(R.id.getDensity).setOnClickListener(this);
        findViewById(R.id.setDensity).setOnClickListener(this);
        findViewById(R.id.disableNotification).setOnClickListener(this);
        findViewById(R.id.enableNotification).setOnClickListener(this);
        findViewById(R.id.hideNavigationBar).setOnClickListener(this);
        findViewById(R.id.showNavigationBar).setOnClickListener(this);
        findViewById(R.id.hideStatusBar).setOnClickListener(this);
        findViewById(R.id.showStatusBar).setOnClickListener(this);
        findViewById(R.id.idBtnShowSN).setOnClickListener(this);
    }

    private void updateUIBaseInfo(String s){
        try {
            JSONObject jsonObject = new JSONObject(s);
            if( !jsonObject.getString(RET_JSON_KEY_STATUS).equals(RET_JSON_VALUE_STATUS_OK)) {
                return;
            }
            // 最小称重
            TextView minRange   = findViewById(R.id.minRange);
            minRange.setText(jsonObject.getString(RET_JSON_KEY_MIN_RANGE));

            // 最大称重
            TextView maxRange   = findViewById(R.id.maxRange);
            String strMaxRange = jsonObject.getString(RET_JSON_KEY_MAX_RANGE + 1);
            // 双量程
            if( jsonObject.getString(RET_JSON_KEY_RANGE_NUMBER).equals("2")) {
                strMaxRange = strMaxRange + "/" + jsonObject.getString(RET_JSON_KEY_MAX_RANGE + 2);
            }
            strMaxRange = strMaxRange + jsonObject.getString(RET_JSON_KEY_UNIT+1);
            maxRange.setText(strMaxRange);

            // 精度
            TextView resolution = findViewById(R.id.resolution);
            String strResolution = jsonObject.getString(RET_JSON_KEY_RESOLUTION + 1);
            if( jsonObject.getString(RET_JSON_KEY_RANGE_NUMBER).equals("2")) {
                strResolution = strResolution + "/" + jsonObject.getString(RET_JSON_KEY_RESOLUTION + 2);
            }
            strResolution = strResolution + jsonObject.getString(RET_JSON_KEY_UNIT+1);
            resolution.setText(strResolution);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void updateUIWeight(String s){
        TextView weightView = findViewById(R.id.netTextView);
        TextView tareView   = findViewById(R.id.tareTextView);

        try {
            JSONObject jsonObject = new JSONObject(s);
            String returnCode = jsonObject.getString(RET_JSON_KEY_STATUS);
            String strValue;
            switch (Integer.parseInt(returnCode)) {
                case RETURN_CODE_OK:
                case RETURN_CODE_WEIGHT_UNSTABLE:
                    strValue = jsonObject.getString(RET_JSON_KEY_WEIGHT_NET);
                    tareView.setText(jsonObject.getString(RET_JSON_KEY_TARE));
                    break;

                case RETURN_CODE_WEIGHT_UNDERLOAD:
                    strValue = "-____-";
                    break;

                case RETURN_CODE_WEIGHT_OVERLOAD:
                    strValue = "+___+";
                    break;
                default:
                    strValue = "-----";
            }
            weightView.setText(strValue);
            if(null != mSecondScreen) {

                TextView secondNewView = (TextView)mSecondScreen.getView(R.id.secondNetTextView);
                secondNewView.setText(strValue);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateUIClearTare(int returnCode){
        if(RETURN_CODE_OK != returnCode){
            runOnUiThread(() -> Toast.makeText(this, "Clear Tare failed! The gross weight need to be Zero.", Toast.LENGTH_SHORT).show());
        }
    }

    private void printTestText(){

        // 推荐在子线程中做如下打印，防止导致UI线程ANR，因为在做文本格式化时可能占用几百ms的时间；
        MtPrintResult result = new MtPrintResult();
        ArrayList<BookItem> transaction = new ArrayList<>();
        transaction.add(new BookItem("苹果", 10.00, 1.010));

        transaction.add(new BookItem("Banana", 8.00, 1.010));
        transaction.add(new BookItem("A", 100.00, 14.010));
        transaction.add(new BookItem("ij", 10.00, 1.010));
        transaction.add(new BookItem("西瓜", 15.0, 1, false));

        transaction.add(new BookItem("山东红富士", 15.0, 1.010));

        // 下面举例代码为了简单没有做出错检查
        // 收据头部分
        mMtPrintService.setDefaultFontSize(FONT_SMALL_SIZE, result);

        mMtPrintService.printTextWithFontAndAlignment("  商品打印测试  ", FONT_BIG_SIZE, ALIGNMENT_CENTER, result);
        mMtPrintService.printBlankLine(1,result);
        Date date = new Date();
        String dataFormat = String.format("%tF %tR", date, date);
        mMtPrintService.printTextWithFont("日期：" + dataFormat, FONT_SMALL_SIZE, result);
        mMtPrintService.printTextWithFont("秤号：0001", FONT_SMALL_SIZE, result);
        mMtPrintService.printSeparationLine("=", result);

        mMtPrintService.printTextWithFont("商品名称", FONT_NORMAL_SIZE, result);

        mMtPrintService.setDefaultFontSize(FONT_NORMAL_SIZE, result);
        String[] titleColumnsText  = new String[]{ "单价", "重量", "金额"};
        int[] titleColumnsWidth    = new int[]{1, 1, 1}; //设置各列相对比例
        int[] titleColumnsAlign    = new int[]{0, 1, 2};
        mMtPrintService.printColumnsText(titleColumnsText, titleColumnsWidth, titleColumnsAlign, result);

        mMtPrintService.setDefaultFontSize(FONT_SMALL_SIZE, result);
        mMtPrintService.printSeparationLine( "-", result);


        // 交易商品清单
        String priceFormat = "%,3.2f";
        String weightFormat= "%, 2.3fkg";
        String countFormat = "%, 3d";
        for(BookItem item: transaction){
            mMtPrintService.printText( item.getName(), result);

            String[] columnsText  = new String[3];
            int[] columnsWidth    = new int[]{1, 1, 1}; //设置各列相对比例
            int[] columnsAlign    = new int[]{0, 1, 2};
            columnsText[0]  = String.format( priceFormat, item.getUnitPrice() );

            if(item.mIsByWeight){
                columnsText[1] = String.format(weightFormat, item.getWeight());
            }else{
                columnsText[1] = String.format(countFormat, item.getCount());
            }
            columnsText[2] = String.format(priceFormat, item.getPrice());
            Log.d("printTestText2", " price:" + columnsText[2]);
            mMtPrintService.printColumnsText(columnsText, columnsWidth, columnsAlign, result);

            mMtPrintService.printSeparationLine("-", result);
        }

        // 合计部分
        mMtPrintService.printTextWithFontAndAlignment("件数："+ transaction.size() + "    合计：xxx元",
                FONT_NORMAL_SIZE, ALIGNMENT_RIGHT, result);
        mMtPrintService.printBlankLine(1,result);

        // 条码
        mMtPrintService.printBarCode( "http://www.mt.com", 8, 300, 100, 1, 0, 2, result);
        mMtPrintService.printQRCode( "梅特勒-托利多(METTLER TOLEDO)", 300, 1, 0, result);

        mMtPrintService.printBlankLine(5,result);
    }

    // 以下是双屏异显Demo代码
    private void setupSecondScreen(){
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display[] presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if(presentationDisplays.length > 0){
            for( Display displayItem : presentationDisplays){
                if(displayItem.getDisplayId() != defaultDisplay.getDisplayId()){
                    MyPresentation mySecondScreen = new MyPresentation(this, displayItem);
                    mySecondScreen.show();
                    mSecondScreen  = mySecondScreen;
                    return;
                }
            }
        }
    }

    private class MyPresentation extends Presentation {
        public MyPresentation(Context outerContext, Display display) {
            super(outerContext, display);
            // 第二屏幕的视图
            setContentView(R.layout.second_screen);
        }

        public View getView(int id){
            return findViewById(id);
        }
    }


    // 以下是系统控制功能, 部分功能需要系统权限声明，参考 AndroidManifest.xml
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.getDensity:
                Toast.makeText(this, "" + getDensity(), Toast.LENGTH_SHORT).show();
                break;
            case R.id.setDensity:
                int density = 160;
                if(160 == getDensity()){
                    density = 320;
                }
                setDensity(density);
                break;
            case R.id.disableNotification:
                disableNotification(true);
                break;
            case R.id.enableNotification:
                disableNotification(false);
                break;
            case R.id.hideNavigationBar:
                hideNavigationBar();
                break;
            case R.id.showNavigationBar:
                showNavigationBar();
                break;
            case R.id.hideStatusBar:
                hideStatusBar();
                break;
            case R.id.showStatusBar:
                showStatusBar();
                break;
            case R.id.idBtnShowSN:
                Toast.makeText(this, getSerialNumber(), Toast.LENGTH_SHORT).show();
        }
    }

    private int getDensity() {
        return getResources().getDisplayMetrics().densityDpi;
    }

    private void setDensity(int density) {
        String cmd = "wm density " + density;
        ShellUtils.execCommand(cmd, false, true);
    }

    private void disableNotification(boolean disable) {
        Settings.Global.putInt(getContentResolver(), "disable_notification", disable ? 1: 0);
    }

    private void hideNavigationBar () {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.HIDE_NAVIGATION_BAR");
        sendBroadcast(intent);
    }

    private void showNavigationBar () {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.SHOW_NAVIGATION_BAR");
        sendBroadcast(intent);
    }

    private void hideStatusBar () {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.HIDE_STATUS_BAR");
        sendBroadcast(intent);
    }

    private void showStatusBar () {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.SHOW_STATUS_BAR");
        sendBroadcast(intent);
    }

    private String getSerialNumber() {
        return android.os.Build.SERIAL;
    }
}
