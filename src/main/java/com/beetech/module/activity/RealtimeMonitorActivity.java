package com.beetech.module.activity;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import com.beetech.module.R;
import com.beetech.module.adapter.ReadDataRealtimeRvAdapter;
import com.beetech.module.application.MyApplication;
import com.beetech.module.bean.QueryConfigRealtime;
import com.beetech.module.bean.ReadDataRealtime;
import com.beetech.module.constant.Constant;
import com.beetech.module.control.InitConfig;
import com.beetech.module.control.MySyntherizer;
import com.beetech.module.control.NonBlockSyntherizer;
import com.beetech.module.dao.AppLogSDDao;
import com.beetech.module.dao.BaseSDDaoUtils;
import com.beetech.module.dao.ReadDataRealtimeSDDao;
import com.beetech.module.fragment.GridSpacingItemDecoration;
import com.beetech.module.listener.BatteryListener;
import com.beetech.module.listener.MyBDLocationListener;
import com.beetech.module.listener.PhoneStatListener;
import com.beetech.module.listener.UiMessageListener;
import com.beetech.module.service.JobProtectService;
import com.beetech.module.service.ModuleService;
import com.beetech.module.service.RemoteService;
import com.beetech.module.utils.DevStateUtils;
import com.beetech.module.utils.NetUtils;
import com.beetech.module.utils.OfflineResource;
import com.beetech.module.utils.ServiceAliveUtils;
import com.beetech.module.utils.ShutdownRequestUtils;
import com.beetech.module.utils.SysRequestUtils;
import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.OnClick;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class RealtimeMonitorActivity extends AppCompatActivity {
    private final static String TAG = RealtimeMonitorActivity.class.getSimpleName();
    private int refreshInterval = 1000*30*1; //刷新数据间隔

    private AppLogSDDao appLogSDDao;
    private BatteryListener listener;

    public TelephonyManager mTelephonyManager;
    public PhoneStatListener mListener;

    @ViewInject(R.id.rvReadDataRealtimeData)
    private RecyclerView rvReadDataRealtime;

    private ReadDataRealtimeSDDao readDataRealtimeSDDao;
    private List<ReadDataRealtime> readDataRealtimeList;
    private List<ReadDataRealtime> readDataRealtimeListAlarm;
    private Integer sensorCount;

    private ReadDataRealtimeRvAdapter readDataRealtimeRvAdapter;
    int spanCount = 1;
    int spacing = 5;
    public ProgressDialog progressDialog;

    //定位
    @ViewInject(R.id.bmapView)
    private MapView mMapView = null;
    private BaiduMap mBaiduMap;
    private MyApplication myApp;

    // ================== 初始化参数设置开始 ==========================
    /**
     * 发布时请替换成自己申请的appId appKey 和 secretKey。注意如果需要离线合成功能,请在您申请的应用中填写包名。
     * 本demo的包名是com.baidu.tts.sample，定义在build.gradle中。
     */
    protected String appId = "15781335";

    protected String appKey = "qlXSWMVjuugGpUnaGMNxUoAu";

    protected String secretKey = "KTpYAsgPTNbbf0N8dKmnUUpkVYVNhbz4";

    // TtsMode.MIX; 离在线融合，在线优先； TtsMode.ONLINE 纯在线； 没有纯离线
    protected TtsMode ttsMode = TtsMode.MIX;

    // 离线发音选择，VOICE_FEMALE即为离线女声发音。
    // assets目录下bd_etts_common_speech_m15_mand_eng_high_am-mix_v3.0.0_20170505.dat为离线男声模型；
    // assets目录下bd_etts_common_speech_f7_mand_eng_high_am-mix_v3.0.0_20170512.dat为离线女声模型
    protected String offlineVoice = OfflineResource.VOICE_MALE;

    // ===============初始化参数设置完毕，更多合成参数请至getParams()方法中设置 =================

    // 主控制类，所有合成控制方法从这个类开始
    protected MySyntherizer synthesizer;
    protected Handler mainHandler;


    @ViewInject(R.id.tvImei)
    TextView tvImei;

    @ViewInject(R.id.tvDevNum)
    TextView tvDevNum;
    @ViewInject(R.id.tvNetState)
    TextView tvNetState;
    @ViewInject(R.id.tvMonitorState)
    TextView tvMonitorState;
    @ViewInject(R.id.tvBeginMonitorTime)
    TextView tvBeginMonitorTime;
    @ViewInject(R.id.tvEndMonitorTime)
    TextView tvEndMonitorTime;

    //按钮
    @ViewInject(R.id.btn_beginMonitor)
    private Button btnBeginMonitor;

    @ViewInject(R.id.btn_endMonitor)
    private Button btnEndMonitor;

    @ViewInject(R.id.btn_print)
    private Button btnPrint;
    @ViewInject(R.id.btn_refreshNode)
    private Button btnRefreshNode;


    private BaseSDDaoUtils baseSDDaoUtils;
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_realtime_monitor);
        ViewUtils.inject(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD, WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);//开机不锁屏 设置
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //屏幕唤醒
        PowerManager pm = (PowerManager) getBaseContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "StartupReceiver:");//最后的参数是LogCat里用的Tag
        wl.acquire();

        //屏幕解锁
        KeyguardManager km= (KeyguardManager) getBaseContext().getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock kl = km.newKeyguardLock("StartupReceiver");//参数是LogCat里用的Tag
        kl.disableKeyguard();

        if(appLogSDDao == null){
            appLogSDDao = new AppLogSDDao(this);
        }

        appLogSDDao.save(TAG + " onCreate");
        startModuleService();

        myApp = (MyApplication) getApplication();
        baseSDDaoUtils = new BaseSDDaoUtils(myApp);
        timer = new Timer();
        //定位
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMapType(BaiduMap.MAP_TYPE_NORMAL);
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.zoomTo(15));

        //电量和插拔电源状态广播监听
        if(listener == null){
            listener = new BatteryListener(this);
            listener.register();
        }

        //获取telephonyManager, 监听信号强度
        if(mListener == null){
            mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            mListener = new PhoneStatListener(this);
            mTelephonyManager.listen(mListener, PhoneStatListener.LISTEN_SIGNAL_STRENGTHS);
        }

        rvReadDataRealtime = (RecyclerView)findViewById(R.id.rvReadDataRealtimeData);
        if(readDataRealtimeSDDao == null){
            readDataRealtimeSDDao = new ReadDataRealtimeSDDao(this);
        }

        rvReadDataRealtime.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacing, true));

        //定时刷新
        handlerRefresh.removeCallbacks(runnableRefresh);
        handlerRefresh.postDelayed(runnableRefresh, 0);

        mainHandler = new Handler() {
            /*
             * @param msg
             */
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handle(msg);
            }

        };

        new InitialTtsAsyncTask().execute();

        initBaiduGps();
    }

    class InitialTtsAsyncTask extends AsyncTask<String, Integer, Integer> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Integer doInBackground(String... params) {
            try {
                initialTts(); // 初始化TTS引擎
                SystemClock.sleep(3000);
                speak("测试");
            } catch (Exception e){
                e.printStackTrace();
                Log.e(TAG, "初始化TTS引擎异常");
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
        }
    }

    public  void initBaiduGps(){
        //开启前台定位服务：
        Notification.Builder builder = new Notification.Builder (getApplicationContext());
        Intent nfIntent = new Intent(getBaseContext(), MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(getBaseContext(), 0, nfIntent, 0)) // 设置PendingIntent
                .setContentTitle("正在进行后台定位")
                .setSmallIcon(R.mipmap.temp)
                .setContentText("后台定位通知")
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());
        Notification notification = builder.build();
        notification.defaults = Notification.DEFAULT_SOUND;
        myApp.locationService.getClient().enableLocInForeground(1001, notification);

        myApp.locationListener = new MyBDLocationListener(getBaseContext());
        myApp.locationService.registerListener(myApp.locationListener);
//        myApp.locationService.start();
    }

    private void speak(String text) {
        // 需要合成的文本text的长度不能超过1024个GBK字节。
        if (TextUtils.isEmpty(text)) {
            return;
        }
        // 合成前可以修改参数：
        // Map<String, String> params = getParams();
        // synthesizer.setParams(params);
        if(synthesizer != null){
            int result = synthesizer.speak(text);
            checkResult(result, "speak");
        }
    }

    private void checkResult(int result, String method) {
        if (result != 0) {
            toPrint("error code :" + result + " method:" + method + ", 错误码文档:http://yuyin.baidu.com/docs/tts/122 ");
        }
    }

    protected void handle(Message msg) {
        int what = msg.what;
        switch (what) {
            case MainHandlerConstant.PRINT:
//                print(msg);
                String message = (String) msg.obj;
                Log.d(TAG, message);
                if (message != null && Constant.IS_TTSS_TOAST) {
                    Toast.makeText(RealtimeMonitorActivity.this, message, Toast.LENGTH_SHORT).show();
                }
                break;
            case MainHandlerConstant.UI_CHANGE_INPUT_TEXT_SELECTION:
//                if (msg.arg1 <= mInput.getText().length()) {
//                    mInput.setSelection(0, msg.arg1);
//                }
                break;
            case MainHandlerConstant.UI_CHANGE_SYNTHES_TEXT_SELECTION:
//                SpannableString colorfulText = new SpannableString(mInput.getText().toString());
//                if (msg.arg1 <= colorfulText.toString().length()) {
//                    colorfulText.setSpan(new ForegroundColorSpan(Color.GRAY), 0, msg.arg1, Spannable
//                            .SPAN_EXCLUSIVE_EXCLUSIVE);
//                    mInput.setText(colorfulText);
//                }
                break;
            default:
                break;
        }
    }

    protected OfflineResource createOfflineResource(String voiceType) {
        OfflineResource offlineResource = null;
        try {
            offlineResource = new OfflineResource(this, voiceType);
        } catch (IOException e) {
            // IO 错误自行处理
            e.printStackTrace();
//            toPrint("【error】:copy files from assets failed." + e.getMessage());
            Toast.makeText(RealtimeMonitorActivity.this, "【error】:copy files from assets failed." + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return offlineResource;
    }

    /**
     * 合成的参数，可以初始化时填写，也可以在合成前设置。
     *
     * @return
     */
    protected Map<String, String> getParams() {
        Map<String, String> params = new HashMap<String, String>();
        // 以下参数均为选填
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        params.put(SpeechSynthesizer.PARAM_SPEAKER, "0");
        // 设置合成的音量，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_VOLUME, "9");
        // 设置合成的语速，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_SPEED, "5");
        // 设置合成的语调，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_PITCH, "5");

        params.put(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        // 该参数设置为TtsMode.MIX生效。即纯在线模式不生效。
        // MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线

        // 离线资源文件， 从assets目录中复制到临时目录，需要在initTTs方法前完成
        OfflineResource offlineResource = createOfflineResource(offlineVoice);
        // 声学模型文件路径 (离线引擎使用), 请确认下面两个文件存在
        params.put(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, offlineResource.getTextFilename());
        params.put(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE,
                offlineResource.getModelFilename());
        return params;
    }

    /**
     * 初始化引擎，需要的参数均在InitConfig类里
     * <p>
     * DEMO中提供了3个SpeechSynthesizerListener的实现
     * MessageListener 仅仅用log.i记录日志，在logcat中可以看见
     * UiMessageListener 在MessageListener的基础上，对handler发送消息，实现UI的文字更新
     * FileSaveListener 在UiMessageListener的基础上，使用 onSynthesizeDataArrived回调，获取音频流
     */
    protected void initialTts() {
        LoggerProxy.printable(true); // 日志打印在logcat中
        // 设置初始化参数
        // 此处可以改为 含有您业务逻辑的SpeechSynthesizerListener的实现类
        SpeechSynthesizerListener listener = new UiMessageListener(mainHandler);

        Map<String, String> params = getParams();


        // appId appKey secretKey 网站上您申请的应用获取。注意使用离线合成功能的话，需要应用中填写您app的包名。包名在build.gradle中获取。
        InitConfig initConfig = new InitConfig(appId, appKey, secretKey, ttsMode, params, listener);

        // 如果您集成中出错，请将下面一段代码放在和demo中相同的位置，并复制InitConfig 和 AutoCheck到您的项目中
        // 上线时请删除AutoCheck的调用
//        AutoCheck.getInstance(getApplicationContext()).check(initConfig, new Handler() {
//            @Override
//            public void handleMessage(Message msg) {
//                if (msg.what == 100) {
//                    AutoCheck autoCheck = (AutoCheck) msg.obj;
//                    synchronized (autoCheck) {
//                        String message = autoCheck.obtainDebugMessage();
//                        toPrint(message); // 可以用下面一行替代，在logcat中查看代码
//                        // Log.w("AutoCheckMessage", message);
//                    }
//                }
//            }
//
//        });
        synthesizer = new NonBlockSyntherizer(this, initConfig, mainHandler); // 此处可以改为MySyntherizer 了解调用过程
    }

    protected void toPrint(String str) {
        Message msg = Message.obtain();
        msg.obj = str;
        mainHandler.sendMessage(msg);
    }

    public void startModuleService(){
        /*如果服务正在运行，直接return*/
        if (!ServiceAliveUtils.isServiceRunning(this, Constant.className_moduleService)){
            /* 启动串口通信服务 */
            startService(new Intent(this, ModuleService.class));

            //开启守护线程 aidl
            startService(new Intent(this, RemoteService.class));

            //创建唤醒定时任务
            try {
                //获取JobScheduler 他是一种系统服务
                JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                jobScheduler.cancelAll();
                JobInfo.Builder builder = new JobInfo.Builder(1024, new ComponentName(getPackageName(), JobProtectService.class.getName()));

                if(Build.VERSION.SDK_INT >= 24) {
                    //android N之后时间必须在15分钟以上
                    //            builder.setMinimumLatency(10 * 1000);
                    builder.setPeriodic(1000 * 60 * 15);
                }else{
                    builder.setPeriodic(1000 * 60 * 15);
                }
                builder.setPersisted(true);
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
                int schedule = jobScheduler.schedule(builder.build());
                if (schedule <= 0) {
                    Log.w(TAG, "schedule error！");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void queryData(){
        readDataRealtimeList = readDataRealtimeSDDao.queryAll();
        sensorCount = readDataRealtimeList == null ? 0 : readDataRealtimeList.size();
    }

    public void refresh(){
        readDataRealtimeRvAdapter = new ReadDataRealtimeRvAdapter(readDataRealtimeList);
        readDataRealtimeRvAdapter.setOnItemLongClickListener(new ReadDataRealtimeRvAdapter.OnItemLongClickListener() {
            @Override
            public void onItemLongClick(View view, int position) {
                ReadDataRealtime readDataRealtime = readDataRealtimeList.get(position);
                Intent intent=new Intent(getBaseContext(), QueryDataActivity.class);
                intent.putExtra("sensorId", readDataRealtime.getSensorId());
                startActivity(intent);
            }
        });
        readDataRealtimeRvAdapter.setOnItemClickListener(new ReadDataRealtimeRvAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                ReadDataRealtime readDataRealtime = readDataRealtimeList.get(position);
                Intent intent=new  Intent(RealtimeMonitorActivity.this, TempLineActivity.class);
                intent.putExtra("sensorId",readDataRealtime.getSensorId());
                startActivity(intent);
            }
        });

        GridLayoutManager mGridLayoutManager = new GridLayoutManager(this, spanCount);
        mGridLayoutManager.setOrientation(LinearLayout.VERTICAL);
        rvReadDataRealtime.setLayoutManager(mGridLayoutManager);

        rvReadDataRealtime.setAdapter(readDataRealtimeRvAdapter);
        readDataRealtimeRvAdapter.notifyDataSetChanged();

        refreshState();
    }

    //刷新左侧状态
    public void refreshState(){
        tvImei.setText(Constant.imei);
        tvDevNum.setText(Constant.devNum);


        try {
            int dbm = 0;
            myApp.netWorkType = NetUtils.getNetworkState(myApp);
            if (myApp.netWorkType == NetUtils.NETWORK_WIFI) {
                dbm = DevStateUtils.getWifiRssi(myApp);
            }else if(myApp.netWorkType == NetUtils.NETWORK_2G || myApp.netWorkType == NetUtils.NETWORK_3G || myApp.netWorkType == NetUtils.NETWORK_4G ){
                dbm = DevStateUtils.getMobileDbm(myApp);
            }
            myApp.signalStrength = dbm;
            if(myApp.netWorkType == NetUtils.NETWORK_NONE){
                tvNetState.setText(NetUtils.network_type_name.get(myApp.netWorkType));
                tvNetState.setTextColor(Color.RED);
            } else {
                tvNetState.setText(NetUtils.network_type_name.get(myApp.netWorkType) +"  信号: "+myApp.signalStrength+"dbm");
                tvNetState.setTextColor(Color.BLUE);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

//        tvGtwState.setText(ConnectUtils.HOST + ":" +ConnectUtils.PORT);
//        if(myApp.session != null){
//            if(myApp.session.isConnected()){
//                tvGtwState.setTextColor(Color.BLUE);
//            } else {
//                tvGtwState.setTextColor(Color.RED);
//            }
//        } else {
//            tvGtwState.setTextColor(Color.GRAY);
//        }

        if(myApp.monitorState == 0){
            tvMonitorState.setText("未监控");
            tvMonitorState.setTextColor(Color.RED);
        } else {
            tvMonitorState.setText("监控中");
            tvMonitorState.setTextColor(Color.BLUE);
        }

        if(myApp.beginMonitorTime != null){
            tvBeginMonitorTime.setText(Constant.sdf.format(myApp.beginMonitorTime));
            tvBeginMonitorTime.setTextColor(Color.BLUE);
        }else {
            tvBeginMonitorTime.setText(null);
        }
        if(myApp.endMonitorTime != null){
            tvEndMonitorTime.setText(Constant.sdf.format(myApp.endMonitorTime));
            tvEndMonitorTime.setTextColor(Color.BLUE);
        } else {
            tvEndMonitorTime.setText(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handlerRefresh.removeCallbacks(runnableRefresh);
        handlerRefresh.postDelayed(runnableRefresh, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            listener.unregister();
        }

//        new ModuleUtils(this).free(); //我们的应用要一直运行
        appLogSDDao.save(TAG+" onDestroy");
    }

    //定时刷新
    private Handler handlerRefresh = new Handler(){};
    Runnable runnableRefresh = new Runnable() {
        @Override
        public void run() {

            RefreshAsyncTask refreshAsyncTask = new RefreshAsyncTask();
            refreshAsyncTask.execute();

            handlerRefresh.postDelayed(this, refreshInterval);
        }
    };

    class RefreshAsyncTask extends AsyncTask<String, Integer, Integer> {

        @Override
        protected void onPreExecute() {
            if(progressDialog == null){

            }
            if(progressDialog != null && !progressDialog.isShowing()) {
                progressDialog.show();
            }
        }

        @Override
        protected Integer doInBackground(String... params) {
            try{
                queryData();
            } catch (Exception e){
                e.printStackTrace();
                Log.d(TAG, "刷新数据异常");
            }

            if(Constant.alarmFlag && myApp.monitorState == 1){
                try{
                    checkAlarm();
                } catch (Exception e){
                    e.printStackTrace();
                    Log.d(TAG, "监测报警异常");
                }
            }

            try{
                locRefresh();
            } catch (Exception e){
                e.printStackTrace();
                Log.d(TAG, "位置刷新异常");
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            refresh();
            if(progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }

    public void checkAlarm(){
        readDataRealtimeListAlarm = new ArrayList<>();
        StringBuffer speakTextLong = new StringBuffer();
        if(readDataRealtimeList != null){
            for (ReadDataRealtime readDataRealtime : readDataRealtimeList){
                String sensorId = readDataRealtime.getSensorId();
                String devName = readDataRealtime.getDevName();
                double temp = readDataRealtime.getTemp();
                double tempLower = readDataRealtime.getTempLower();
                double tempHight = readDataRealtime.getTempHight();
                if(tempLower != 0 && tempHight != 0 && (temp > tempHight || temp < tempLower)){
                    readDataRealtimeListAlarm.add(readDataRealtime);
                    if(!TextUtils.isEmpty(devName)){
                        speakTextLong.append(devName);
                    }
                    speakTextLong.append("，").append(sensorId).append("，").append(temp).append("℃");
                }
            }
        }

        int alarmSize = readDataRealtimeListAlarm.size();
        Log.d(TAG, "alarmSize="+alarmSize);
        if(alarmSize > 0){
            StringBuffer speakText = new StringBuffer();
            speakText.append(alarmSize).append("个监测点超温");
            if(alarmSize < 5){
                speakText.append(speakTextLong);
            }
            Log.d(TAG, "speakText = "+speakText.toString());
            if(Constant.alarmVoiceFlag){
                speak(speakText.toString());
            }

            if(Constant.alarmLightFlag){
                try {
                    myApp.powerLED.on();
                    Log.d(TAG, "LED on");
                    //延迟3秒关闭
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                myApp.powerLED.off();
                                Log.d(TAG, "LED off");
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e(TAG, "LED 关灯异常", e);
                            }
                        }
                    }, 3*1000L);
                }catch (Exception e){
                    e.printStackTrace();
                    Log.e(TAG, "LED 开灯异常",e);
                }
            }

        } else {
            if(Constant.alarmLightFlag) {
                try {
                    myApp.powerLED.off();
                    Log.d(TAG, "LED off");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "LED 关灯异常", e);
                }
            }
        }
    }

    private void locRefresh(){
        Log.d(TAG, "locRefresh");

        BDLocation location = myApp.location;
        if (location == null) {
            return;
        }
        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        // 构建Marker图标
        BitmapDescriptor bitmap = BitmapDescriptorFactory.fromResource(R.drawable.loc);
        // 构建MarkerOption，用于在地图上添加Marker
        OverlayOptions option = new MarkerOptions().position(point).icon(bitmap);
        // 在地图上添加Marker，并显示
        mBaiduMap.clear();
        mBaiduMap.addOverlay(option);
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(point));

        //画圆
//                    OverlayOptions ooCircle = new CircleOptions().fillColor(0x384d73b3)
//                            .center(point).stroke(new Stroke(3, 0x784d73b3))
//                            .radius(Float.valueOf(location.getRadius()).intValue());
//                    mBaiduMap.addOverlay(ooCircle);
    }

    @OnClick(R.id.btn_beginMonitor)
    public void btn_beginMonitor_onClick(View v) {
        if(myApp.monitorState == 1){
            AlertDialog.Builder builder = new  AlertDialog.Builder(RealtimeMonitorActivity.this);
            builder.setMessage("已 开始监控，要先 结束监控 吗？");
            builder.setTitle("提示");
            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    endMonitor();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();

        } else {
            AlertDialog.Builder builder = new  AlertDialog.Builder(this);
            builder.setMessage("确定要 开始监控 吗？");
            builder.setTitle("提示");
            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    beginMonitor();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
        }

    }

    public void beginMonitor(){
        try{
            //设置数据开始时间
            Message msg = new Message();
            msg.what = 9;
            myApp.moduleHandler.sendMessageAtFrontOfQueue(msg);

            //删除历史数据
//            baseSDDaoUtils.deleteLog();

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, 1); // 延迟一分钟启动时间
            cal.set(Calendar.SECOND, 0); //秒和毫秒取0
            cal.set(Calendar.MILLISECOND, 0);

//            timer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//
//                }
//            }, cal.getTime());

//            Log.d(TAG, "SetDataBeginTimeUtils.setDataBeginTime");
//            try {
//                SetDataBeginTimeUtils.setDataBeginTime(myApp);
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Toast.makeText(RealtimeMonitorActivity.this, "设置数据开始时间完成", Toast.LENGTH_SHORT).show();
//                        SystemClock.sleep(1000);
//                        refreshState();
//                    }
//                });
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                Log.e(TAG, "SetDataBeginTimeUtils.setDataBeginTime 异常", e);
//            };

            myApp.beginMonitorTime = cal.getTime();
            myApp.endMonitorTime = null;
            myApp.monitorState = 1;
            QueryConfigRealtime queryConfigRealtime = myApp.queryConfigRealtimeSDDao.queryLast();
            if(queryConfigRealtime != null){
                queryConfigRealtime.setMonitorState(myApp.monitorState);
                queryConfigRealtime.setBeginMonitorTime(myApp.beginMonitorTime);
                queryConfigRealtime.setEndMonitorTime(myApp.endMonitorTime);
                myApp.queryConfigRealtimeSDDao.update(queryConfigRealtime);
            }
            if(!myApp.locationService.isStart()){
                myApp.locationService.start();
            }
            myApp.appLogSDDao.save("开始监控"+Constant.sdf.format(myApp.beginMonitorTime));
            Toast.makeText(RealtimeMonitorActivity.this, "开始监控, "+Constant.sdf.format(myApp.beginMonitorTime), Toast.LENGTH_SHORT).show();
            btnBeginMonitor.setTextColor(Color.BLUE);
            btnEndMonitor.setTextColor(Color.BLACK);
            refreshState();

            //发送SYS报文
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "SysRequestUtils.requestSys");
                    try {
                        SysRequestUtils.requestSys(myApp);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RealtimeMonitorActivity.this, "发送SYS报文完成", Toast.LENGTH_SHORT).show();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "SysRequestUtils.requestSys 异常", e);
                    }
                }
            }).start();
        }catch (Exception e){
            e.printStackTrace();
            Log.e(TAG, "开始监控异常", e);
        }
    }

    @OnClick(R.id.btn_endMonitor)
    public void btn_endMonitor_onClick(View v) {
        if(myApp.monitorState == 0){
            AlertDialog.Builder builder = new  AlertDialog.Builder(RealtimeMonitorActivity.this);
            builder.setMessage("未 开始监控，要 开始监控 吗？");
            builder.setTitle("提示");
            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    beginMonitor();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();

        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("确定要 结束监控 吗？");
            builder.setTitle("提示");
            builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {
                    endMonitor();
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
        }
    }

    public void endMonitor(){
        try{
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -2); // 提前2分钟结束监控时间
            cal.set(Calendar.SECOND, 0); //秒和毫秒取0
            cal.set(Calendar.MILLISECOND, 0);
            if(myApp.beginMonitorTime != null && (cal.getTime().getTime() == myApp.beginMonitorTime.getTime() || cal.getTime().before(myApp.beginMonitorTime))){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(RealtimeMonitorActivity.this, "监控时间过短，请稍后再试", Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
            myApp.endMonitorTime = cal.getTime();
            myApp.monitorState = 0;
            QueryConfigRealtime queryConfigRealtime = myApp.queryConfigRealtimeSDDao.queryLast();
            if(queryConfigRealtime != null){
                queryConfigRealtime.setMonitorState(myApp.monitorState);
                queryConfigRealtime.setEndMonitorTime(myApp.endMonitorTime);
                myApp.queryConfigRealtimeSDDao.update(queryConfigRealtime);
            }

            myApp.locationService.stop();
            myApp.appLogSDDao.save("结束监控, "+ Constant.sdf.format(myApp.endMonitorTime));
            btnBeginMonitor.setTextColor(Color.BLACK);
            btnEndMonitor.setTextColor(Color.RED);
            Toast.makeText(RealtimeMonitorActivity.this,"结束监控"+ Constant.sdf.format(myApp.endMonitorTime), Toast.LENGTH_SHORT).show();

            refreshState();

            //发送SHUTDOWN报文
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "ShutdownRequestUtils.requestShutdown");
                    try {
                        ShutdownRequestUtils.requestShutdown(myApp);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RealtimeMonitorActivity.this, "发送SHUTDOWN报文完成", Toast.LENGTH_SHORT).show();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "ShutdownRequestUtils.requestShutdown 异常", e);
                    }
                }
            }).start();

        } catch (Exception e){
            e.printStackTrace();
            Log.e(TAG, "结束监控异常", e);
        }
    }

    @OnClick(R.id.btn_print)
    public void btn_print_onClick(View v) {

        try{
            Intent intent = new Intent(RealtimeMonitorActivity.this, QueryDataAllActivity.class);
            startActivity(intent);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @OnClick(R.id.btn_refreshNode)
    public void btn_refreshNode_onClick(View v) {
        AlertDialog.Builder builder = new  AlertDialog.Builder(this);
        builder.setMessage("确定要清空标签节点吗？");
        builder.setTitle("提示");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                try {
                    myApp.readDataRealtimeSDDao.truncate();

                    Message msg = new Message();
                    msg.obj = "清空标签监测节点";
                    handlerToast.sendMessage(msg);

                    handlerRefresh.removeCallbacks(runnableRefresh);
                    handlerRefresh.postDelayed(runnableRefresh, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "刷新标签异常", e);
                } finally {
                    dialog.dismiss();
                }
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if(keyCode == KeyEvent.KEYCODE_BACK){
            handlerRefresh.removeCallbacks(runnableRefresh);//切换到背景不更新

//            moveTaskToBack(true);
            Intent intent = new Intent();
            intent.setClass(RealtimeMonitorActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private Handler handlerToast = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Object toastMsg = msg.obj;
            if(toastMsg != null){
                Toast.makeText(RealtimeMonitorActivity.this, toastMsg.toString(), Toast.LENGTH_SHORT).show();
            }
            super.handleMessage(msg);
        }
    };
}