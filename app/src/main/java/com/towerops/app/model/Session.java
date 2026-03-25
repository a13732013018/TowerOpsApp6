package com.towerops.app.model;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全局会话信息 —— 登录成功后持久保存，供所有线程使用（等价于易语言全局变量）
 */
public class Session {

    private static volatile Session instance;

    private Session() {}

    public static Session get() {
        if (instance == null) {
            synchronized (Session.class) {
                if (instance == null) instance = new Session();
            }
        }
        return instance;
    }

    // ---------- 登录后写入 ----------
    public volatile String userid       = "";
    public volatile String token        = "";   // Authorization 值，发请求时动态组头
    public volatile String mobilephone  = "";
    public volatile String username     = "";
    /**
     * 真实姓名（中文），来自 AccountConfig 第三列。
     * 用于与工单 actionlist 中的 acceptOperator（中文接单人姓名）比对，
     * 以决定是否触发自动接单/回单。
     *
     * [BUG-FIX] 原代码用 username（账号工号）比对中文姓名，永远不等，后台接单/回单无法触发。
     */
    public volatile String realname     = "";

    // ---------- 智联工单相关 ----------
    /**
     * c_sign 签名，用于智联工单接口
     * 从登录响应或配置中获取
     */
    public volatile String cSign        = "E9163ADC4E8E9B20293C8FC11A78E652";

    /**
     * 运维账号信息数组（对应易语言的运维账号信息数组）
     * 用于智联工单接单时的用户名参数
     */
    public volatile String[] accountConfig = new String[0];

    // ---------- 运行时配置（主线程写，工作线程读）----------
    // ★ appConfig 同时保存在内存和 SharedPreferences，服务重建后可从 prefs 恢复 ★
    public volatile String appConfig    = ""; // 选1|选2|选5|阈值反馈|阈值接单 用 \u0001 分隔
    public volatile String[] taskArray  = new String[0];

    // ---------- 智联工单配置 ----------
    /**
     * 智联工单配置：enableAccept|enableRevert|minRevertDelay|maxRevertDelay
     * 用 \u0001 分隔
     */
    public volatile String zhilianConfig = "";

    // ---------- 铁塔4A系统登录相关 ----------
    /**
     * 铁塔4A系统 SESSION Cookie（如 SESSION=xxxx）
     * 登录成功后由 TowerLoginActivity 写入
     */
    public volatile String tower4aSessionCookie = "";

    // ---------- 数运工单相关 ----------
    /**
     * 数运APP端登录token
     */
    public volatile String shuyunAppToken = "";

    /**
     * 数运APP用户ID
     */
    public volatile String shuyunAppUserId = "";

    /**
     * 数运APP登录IMEI（用于省级审核权限验证）
     */
    public volatile String shuyunAppImei = "";

    /**
     * 数运PC端登录token（用于 Authorization）
     */
    public volatile String shuyunPcToken = "";

    /**
     * 数运PC端登录token（用于 Cookie 中的 towerNumber-Token）
     * 【核心】可能与 shuyunPcToken 不同，来自登录响应的 Set-Cookie
     */
    public volatile String shuyunPcTokenCookie = "";

    /**
     * 数运PC端登录IP（用于验证）
     */
    public volatile String shuyunPcIp = "";

    /**
     * 数运区县代码（如330300），用于智联工单接口
     */
    public volatile String shuyunCityArea = "330300";

    /**
     * 区县经理代号（用于县级审核）
     * 市区: 36745, 其他: 31950
     */
    public volatile String countyManagerCode = "36745";

    /**
     * 数运账号信息（用于APP登录）
     * 格式：用户名|密码|imei 用 \u0001 分隔
     */
    public volatile String[] shuyunAccountConfig = new String[0];

    /**
     * 数运工单配置：enableAccept|enableRevert|minRevertDelay|maxRevertDelay
     * 用 \u0001 分隔
     */
    public volatile String shuyunConfig = "";

    private static final String PREF_SESSION    = "session_prefs";
    private static final String KEY_APP_CONFIG  = "app_config";
    private static final String KEY_USERID      = "userid";
    private static final String KEY_TOKEN       = "token";
    private static final String KEY_MOBILE      = "mobilephone";
    private static final String KEY_USERNAME    = "username";
    private static final String KEY_REALNAME    = "realname";

    // ---------- 数运登录信息持久化Key ----------
    private static final String KEY_SHUYUN_APP_TOKEN = "shuyun_app_token";
    private static final String KEY_SHUYUN_APP_USERID = "shuyun_app_userid";
    private static final String KEY_SHUYUN_APP_IMEI = "shuyun_app_imei";
    private static final String KEY_SHUYUN_PC_TOKEN = "shuyun_pc_token";
    private static final String KEY_SHUYUN_PC_TOKEN_COOKIE = "shuyun_pc_token_cookie";
    private static final String KEY_SHUYUN_PC_IP = "shuyun_pc_ip";
    private static final String KEY_SHUYUN_CITY_AREA = "shuyun_city_area";
    private static final String KEY_COUNTY_MANAGER_CODE = "county_manager_code";

    // ---------- 铁塔4A持久化Key ----------
    private static final String KEY_TOWER4A_COOKIE = "tower4a_session_cookie";

    /**
     * 保存铁塔4A SESSION Cookie 到 SharedPreferences
     * 在4A登录成功后调用（WorkOrderFragment）
     */
    public void saveTower4aCookie(android.content.Context ctx) {
        ctx.getApplicationContext()
           .getSharedPreferences(PREF_SESSION, android.content.Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_TOWER4A_COOKIE, tower4aSessionCookie)
           .apply();
    }

    /**
     * 将 appConfig 持久化到 SharedPreferences。
     * 在 MainActivity.buildConfig() 写入 appConfig 后立刻调用。
     */
    public void saveConfig(Context ctx) {
        ctx.getApplicationContext()
           .getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_APP_CONFIG, appConfig)
           .apply();
    }

    /**
     * 登录成功后调用：把登录凭据（token/userid 等）写入 SharedPreferences。
     * 服务被系统重建（START_STICKY）时进程可能重启，内存变量丢失，
     * 必须持久化才能让后台接单的 Authorization 头带上正确的 token。
     */
    public void saveLogin(Context ctx) {
        ctx.getApplicationContext()
           .getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_USERID,      userid)
           .putString(KEY_TOKEN,       token)
           .putString(KEY_MOBILE,      mobilephone)
           .putString(KEY_USERNAME,    username)
           .putString(KEY_REALNAME,    realname)
           .apply();
    }

    /**
     * 保存数运登录信息（PC端和APP端token）
     * 在数运登录成功后调用
     */
    public void saveShuyunLogin(Context ctx) {
        ctx.getApplicationContext()
           .getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_SHUYUN_APP_TOKEN, shuyunAppToken)
           .putString(KEY_SHUYUN_APP_USERID, shuyunAppUserId)
           .putString(KEY_SHUYUN_APP_IMEI, shuyunAppImei)
           .putString(KEY_SHUYUN_PC_TOKEN, shuyunPcToken)
           .putString(KEY_SHUYUN_PC_TOKEN_COOKIE, shuyunPcTokenCookie)
           .putString(KEY_SHUYUN_PC_IP, shuyunPcIp)
           .putString(KEY_SHUYUN_CITY_AREA, shuyunCityArea)
           .putString(KEY_COUNTY_MANAGER_CODE, countyManagerCode)
           .apply();
    }

    /**
     * 从 SharedPreferences 恢复 appConfig 和登录凭据（服务重建/进程恢复时调用）。
     * 若 prefs 里没有，对应字段保持原值不变。
     */
    public void loadConfig(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE);

        String savedConfig = sp.getString(KEY_APP_CONFIG, "");
        if (!savedConfig.isEmpty()) appConfig = savedConfig;

        // ★ 恢复登录凭据：服务重建后 token/userid 等内存变量会清空，
        //   acceptBill() 需要用 s.token 构建 Authorization 头，
        //   若 token 为空则服务器鉴权失败，接单被拒 ★
        String savedToken = sp.getString(KEY_TOKEN, "");
        if (!savedToken.isEmpty()) {
            token       = savedToken;
            userid      = sp.getString(KEY_USERID,    userid);
            mobilephone = sp.getString(KEY_MOBILE,    mobilephone);
            username    = sp.getString(KEY_USERNAME,  username);
            realname    = sp.getString(KEY_REALNAME,  realname);
        }

        // 恢复数运登录信息
        String savedShuyunAppToken = sp.getString(KEY_SHUYUN_APP_TOKEN, "");
        if (!savedShuyunAppToken.isEmpty()) {
            shuyunAppToken = savedShuyunAppToken;
            shuyunAppUserId = sp.getString(KEY_SHUYUN_APP_USERID, shuyunAppUserId);
            shuyunAppImei = sp.getString(KEY_SHUYUN_APP_IMEI, shuyunAppImei);
        }

        String savedShuyunPcToken = sp.getString(KEY_SHUYUN_PC_TOKEN, "");
        if (!savedShuyunPcToken.isEmpty()) {
            shuyunPcToken = savedShuyunPcToken;
            shuyunPcTokenCookie = sp.getString(KEY_SHUYUN_PC_TOKEN_COOKIE, shuyunPcToken);
            shuyunPcIp = sp.getString(KEY_SHUYUN_PC_IP, shuyunPcIp);
        }

        // 恢复配置信息
        String savedCityArea = sp.getString(KEY_SHUYUN_CITY_AREA, "");
        if (!savedCityArea.isEmpty()) {
            shuyunCityArea = savedCityArea;
        }

        String savedCountyCode = sp.getString(KEY_COUNTY_MANAGER_CODE, "");
        if (!savedCountyCode.isEmpty()) {
            countyManagerCode = savedCountyCode;
        }

        // 恢复铁塔4A Cookie
        String saved4aCookie = sp.getString(KEY_TOWER4A_COOKIE, "");
        if (!saved4aCookie.isEmpty()) {
            tower4aSessionCookie = saved4aCookie;
        }
    }

    // ---------- 并发计数（用 synchronized 保护）----------
    private int runningThreads = 0;
    private int finishedCount  = 0;
    private int totalCount     = 0;

    public synchronized void resetProgress(int total) {
        this.totalCount     = total;
        this.runningThreads = 0;
        this.finishedCount  = 0;
    }

    public synchronized boolean tryAcquireSlot(int maxSlots) {
        if (runningThreads < maxSlots) { runningThreads++; return true; }
        return false;
    }

    public synchronized void releaseSlot() {
        runningThreads--;
        finishedCount++;
    }

    public synchronized boolean allDone() {
        return finishedCount >= totalCount;
    }

    public synchronized int getFinished() { return finishedCount; }
    public synchronized int getTotal()    { return totalCount; }
}
