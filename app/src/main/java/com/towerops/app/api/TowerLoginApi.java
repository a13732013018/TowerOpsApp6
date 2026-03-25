package com.towerops.app.api;

import android.util.Base64;

import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 铁塔4A系统登录API封装
 * 地址: http://4a.chinatowercom.cn:20000/uac/
 *
 * 登录流程：
 * 1. getSalt     → 获取32位盐值
 * 2. getPubKey   → 获取RSA 1024bit公钥
 * 3. checkMixedLogin → 检查是否需要短信验证
 * 4. doPrevLogin → 第一步登录（账号密码加密提交），触发短信
 * 5. refreshMsg  → 发送短信验证码，返回msgId
 * 6. doNextLogin → 第二步登录（提交短信验证码），成功后得到SESSION Cookie
 */
public class TowerLoginApi {

    private static final String BASE_URL = "http://4a.chinatowercom.cn:20000/uac";
    private static final String FP = "6370ceda5e44488e79ff9404a0552ef1";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    private final OkHttpClient client;

    // 登录过程中的临时状态
    private String salt;
    private String publicKey;
    private String loginR;      // 本次登录会话的随机标识
    private String sessionCookie; // 登录成功后的SESSION

    public TowerLoginApi() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /** 结果包装类 */
    public static class Result {
        public final boolean success;
        public final String  message;
        public final String  data;    // 成功时的额外数据（如msgId）

        public Result(boolean success, String message, String data) {
            this.success = success;
            this.message = message;
            this.data    = data;
        }
    }

    /**
     * Step 1+2+3: 获取Salt、公钥，并检查登录类型
     */
    public Result initLogin() {
        try {
            // 1. getSalt
            String r1 = String.valueOf(Math.random());
            Request req1 = new Request.Builder()
                    .url(BASE_URL + "/getSalt?r=" + r1)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            try (Response resp = client.newCall(req1).execute()) {
                if (!resp.isSuccessful()) return new Result(false, "获取Salt失败: " + resp.code(), null);
                salt = resp.body().string().replace("\"", "").trim();
            }

            // 2. getPubKey
            String r2 = String.valueOf(Math.random());
            Request req2 = new Request.Builder()
                    .url(BASE_URL + "/getPubKey?r=" + r2)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            try (Response resp = client.newCall(req2).execute()) {
                if (!resp.isSuccessful()) return new Result(false, "获取公钥失败: " + resp.code(), null);
                publicKey = resp.body().string().replace("\"", "").trim();
                // 去掉公钥中可能存在的换行和空格，确保Base64解析正常
                publicKey = publicKey.replaceAll("[\\r\\n\\s]", "");
            }

            // 生成本次登录的随机r（32位hex）
            loginR = UUID.randomUUID().toString().replace("-", "");

            // 3. checkMixedLogin
            Request req3 = new Request.Builder()
                    .url(BASE_URL + "/checkMixedLogin?r=" + loginR)
                    .addHeader("User-Agent", USER_AGENT)
                    .build();
            try (Response resp = client.newCall(req3).execute()) {
                String body = resp.body().string();
                // no_checkmixed = 正常，可继续登录
                // 其他情况也继续，服务端会在doPrevLogin时处理
            }

            return new Result(true, "初始化成功", null);

        } catch (Exception e) {
            return new Result(false, "网络异常: " + e.getMessage(), null);
        }
    }

    /**
     * Step 4: doPrevLogin - 提交账号密码，触发短信验证码
     * @return success=true 时表示短信已发送，需要输入验证码
     */
    public Result doPrevLogin(String username, String password) {
        try {
            String encUsername  = rsaEncrypt(username);
            String encPassword  = rsaEncrypt(password);
            String encLoginCode = rsaEncrypt(salt);   // loginCode = 加密salt

            FormBody body = new FormBody.Builder()
                    .add("loginCode",  encLoginCode)
                    .add("csrftoken",  "")
                    .add("username",   encUsername)
                    .add("password",   encPassword)
                    .add("loginFrom",  "oth")
                    .add("fp",         FP)
                    .add("useragent",  USER_AGENT)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/doPrevLogin?r=" + loginR)
                    .post(body)
                    .addHeader("User-Agent",   USER_AGENT)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer",      "http://4a.chinatowercom.cn:20000/uac/")
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                String responseBody = resp.body().string();
                JSONObject json = new JSONObject(responseBody);
                String message = json.optString("message", "");

                if ("NEXT".equals(message)) {
                    return new Result(true, "密码验证通过，正在发送短信...", null);
                } else if ("OK".equals(message)) {
                    // 极少数情况下直接登录成功
                    extractAndSaveCookie(resp);
                    return new Result(true, "登录成功", "direct");
                } else {
                    return new Result(false, "账号或密码错误", null);
                }
            }
        } catch (Exception e) {
            return new Result(false, "登录异常: " + e.getMessage(), null);
        }
    }

    /**
     * Step 5: refreshMsg - 发送短信验证码
     * @return data 中包含 msgId，用于 doNextLogin
     */
    public Result refreshMsg(String username, String password) {
        try {
            String encUsername = rsaEncrypt(username);
            String encPassword = rsaEncrypt(password);

            FormBody body = new FormBody.Builder()
                    .add("csrftoken", "")
                    .add("username",  encUsername)
                    .add("password",  encPassword)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/refreshMsg?r=" + loginR)
                    .post(body)
                    .addHeader("User-Agent",   USER_AGENT)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer",      "http://4a.chinatowercom.cn:20000/uac/")
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                String responseBody = resp.body().string();
                JSONObject json = new JSONObject(responseBody);
                String message  = json.optString("message", "");
                String msgId    = json.optString("msgId", "");
                String phoneMsg = json.optString("phoneMsg", "");

                if ("NEXT".equals(message) && !msgId.isEmpty()) {
                    return new Result(true, phoneMsg.isEmpty() ? "短信已发送" : phoneMsg, msgId);
                } else {
                    return new Result(false, "短信发送失败: " + responseBody, null);
                }
            }
        } catch (Exception e) {
            return new Result(false, "发送短信异常: " + e.getMessage(), null);
        }
    }

    /**
     * Step 6: doNextLogin - 提交短信验证码，完成登录
     * 成功后可通过 getSessionCookie() 获取Cookie
     */
    public Result doNextLogin(String username, String password, String msgId, String msgCode) {
        try {
            String encUsername = rsaEncrypt(username);
            String encPassword = rsaEncrypt(password);
            String r = String.valueOf(Math.random());

            FormBody body = new FormBody.Builder()
                    .add("csrftoken",  "")
                    .add("msgCode",    msgCode)
                    .add("msgId",      msgId)
                    .add("loginFrom",  "oth")
                    .add("username",   encUsername)
                    .add("password",   encPassword)
                    .add("fp",         FP)
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL + "/doNextLogin?r=" + r)
                    .post(body)
                    .addHeader("User-Agent",   USER_AGENT)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Referer",      "http://4a.chinatowercom.cn:20000/uac/")
                    .build();

            try (Response resp = client.newCall(request).execute()) {
                String responseBody = resp.body().string();
                JSONObject json = new JSONObject(responseBody);
                String message = json.optString("message", "");

                if ("OK".equals(message)) {
                    extractAndSaveCookie(resp);
                    return new Result(true, "登录成功", sessionCookie);
                } else {
                    return new Result(false, "验证码错误或已过期", null);
                }
            }
        } catch (Exception e) {
            return new Result(false, "提交验证码异常: " + e.getMessage(), null);
        }
    }

    /** 从响应头提取 SESSION Cookie */
    private void extractAndSaveCookie(Response response) {
        // 遍历所有 Set-Cookie 头
        for (String header : response.headers("Set-Cookie")) {
            if (header.startsWith("SESSION=")) {
                int end = header.indexOf(";");
                sessionCookie = end > 0 ? header.substring(0, end) : header;
                break;
            }
        }
    }

    /** 获取登录成功后的SESSION Cookie */
    public String getSessionCookie() {
        return sessionCookie;
    }

    /**
     * RSA/ECB/PKCS1Padding 加密，返回 Base64 字符串
     */
    private String rsaEncrypt(String plaintext) throws Exception {
        byte[] keyBytes = Base64.decode(publicKey, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey pubKey = keyFactory.generatePublic(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
}
