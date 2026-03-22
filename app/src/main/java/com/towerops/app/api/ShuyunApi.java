package com.towerops.app.api;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.towerops.app.model.Session;
import com.towerops.app.util.HttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 数运工单 API —— 对应数运系统APP版登录和工单接口
 *
 * 接口列表：
 * 1. getImgcode      - 获取验证码图片（PC版）
 * 2. loginByPc       - PC版登录（需要验证码）
 * 3. loginByApp      - APP版登录（用户名+密码+IMEI）
 * 4. getTaskList    - 获取工单列表
 * 5. acceptTask     - 接单
 * 6. revertTask     - 回单
 */
public class ShuyunApi {

    // PC版服务器
    private static final String PC_BASE = "http://zjtowercom.cn:8998";
    // APP版服务器
    private static final String APP_BASE = "http://223.95.77.175:19021";

    // ===================== PC端账号 =====================
    // PC端账号（用于PC版网站登录）
    public static final String PC_USER = "13566295657";
    public static final String PC_PASS = "MLcQylxc4733Iav/cNx+oQ==";

    // ===================== APP端账号 =====================
    // APP端默认账号
    public static final String DEFAULT_USER = "13732013018";
    public static final String DEFAULT_PASS = "F8TVasxWplZNK7AJq4T1cA==";
    public static final String DEFAULT_IMEI = "ba9f03beaacd4c05";

    // APP端备用账号
    public static final String BACKUP_USER = "15858734252";
    public static final String BACKUP_PASS = "0/YBW5U6t/yAZggx3MfHCQ==";
    public static final String BACKUP_IMEI = "a873a215e542edab";

    // =====================================================================
    // 1. 获取验证码图片（PC版）
    // =====================================================================
    /**
     * 获取验证码图片和IP
     * @return 包含image和ip的JSON字符串
     */
    public static String getImgcode() {
        String url = PC_BASE + "/api/auth/jwt/getImgcode";
        try {
            String result = HttpUtil.get(url, null, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析验证码返回的IP
     */
    public static String parseIp(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            return root.optString("ip", "");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析验证码返回的Base64图片
     */
    public static android.graphics.Bitmap parseCaptchaImage(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            String base64Image = root.optString("image", "");
            if (!base64Image.isEmpty()) {
                byte[] imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解析验证码返回的数学运算题目（如果有）
     * 返回格式如：{"num1": 5, "num2": 3, "symbol": "+"}
     */
    public static CaptchaMath parseMathCode(String jsonStr) {
        CaptchaMath math = new CaptchaMath();
        try {
            JSONObject root = new JSONObject(jsonStr);
            
            // 先检查这些字段是否真的存在于JSON中，而不是用默认值
            boolean hasNum1 = root.has("num1");
            boolean hasNum2 = root.has("num2");
            boolean hasSymbol = root.has("symbol");
            
            // 只有当所有字段都存在时，才解析数学题
            if (hasNum1 && hasNum2 && hasSymbol) {
                math.num1 = root.getInt("num1");
                math.num2 = root.getInt("num2");
                math.symbol = root.getString("symbol");
                // 计算结果
                switch (math.symbol) {
                    case "+": math.result = math.num1 + math.num2; break;
                    case "-": math.result = math.num1 - math.num2; break;
                    case "×": math.result = math.num1 * math.num2; break;
                    case "÷": math.result = math.num2 != 0 ? math.num1 / math.num2 : 0; break;
                    default: math.result = math.num1 + math.num2;
                }
                math.hasMath = true;
            } else {
                // 服务器没有返回数学题字段，说明是普通图形验证码
                math.hasMath = false;
            }
        } catch (Exception e) {
            math.hasMath = false;
        }
        return math;
    }

    /**
     * 验证码数学运算封装
     */
    public static class CaptchaMath {
        public int num1 = 0;
        public int num2 = 0;
        public String symbol = "+";
        public int result = 0;
        public boolean hasMath = false;
    }

    /**
     * 验证码结果封装（包含图片和IP）
     */
    public static class CaptchaResult {
        public android.graphics.Bitmap image;
        public String ip = "";
        public CaptchaMath math = new CaptchaMath();
    }

    /**
     * 获取验证码图片和IP（从JSON中解析Base64图片）
     * @return CaptchaResult包含图片和IP
     */
    public static CaptchaResult getCaptcha() {
        CaptchaResult result = new CaptchaResult();
        try {
            // 获取验证码JSON
            String jsonStr = getImgcode();
            if (jsonStr.isEmpty()) {
                return result;
            }

            // 解析IP
            result.ip = parseIp(jsonStr);

            // 解析数学题
            result.math = parseMathCode(jsonStr);

            // 从JSON中解析Base64图片
            result.image = parseCaptchaImage(jsonStr);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 从URL获取图片Bitmap
     */
    private static android.graphics.Bitmap getImageBitmap(String imgUrl) {
        try {
            java.net.URL url = new java.net.URL(imgUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            java.io.InputStream is = conn.getInputStream();
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // =====================================================================
    // 2. PC版登录
    // =====================================================================
    /**
     * PC版登录（需要验证码）
     * @param username 用户名
     * @param password 密码（加密后的）
     * @param imgcode 验证码
     * @param ip IP地址（从getImgcode获取）
     * @return 登录结果JSON
     */
    public static String loginByPc(String username, String password, String imgcode, String ip) {
        String url = PC_BASE + "/api/auth/jwt/token";

        String post = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"imgcode\":\"" + imgcode + "\","
                + "\"ip\":\"" + ip + "\"}";

        String headers = buildPcLoginHeader(post.length());
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析PC版登录返回的token
     */
    public static String parsePcToken(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            return root.optString("data", "");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // =====================================================================
    // 3. 同时登录PC端和APP端
    // =====================================================================
    /**
     * 同时登录PC端和APP端（使用指定账号）
     * @param userIndex 账号索引（0或1）
     * @return 包含PC和APP登录结果的封装对象
     */
    public static ShuyunDualLoginResult loginDual(int userIndex) {
        ShuyunDualLoginResult result = new ShuyunDualLoginResult();

        // 获取账号信息
        String user, pass, imei;
        if (userIndex == 1) {
            user = BACKUP_USER;
            pass = BACKUP_PASS;
            imei = BACKUP_IMEI;
        } else {
            user = DEFAULT_USER;
            pass = DEFAULT_PASS;
            imei = DEFAULT_IMEI;
        }

        // 1. 先获取验证码图片和IP（PC端）
        String imgcodeResult = getImgcode();
        String pcIp = parseIp(imgcodeResult);

        if (pcIp.isEmpty()) {
            result.errorMsg = "获取PC端IP失败";
            return result;
        }
        result.pcIp = pcIp;

        // 2. PC端登录（使用固定验证码 "1234"，实际使用时需要用户输入）
        // 注意：PC端需要图形验证码，这里用默认值，实际应该让用户输入
        String pcLoginResult = loginByPc(user, pass, "1234", pcIp);
        String pcToken = parsePcToken(pcLoginResult);

        if (pcToken.isEmpty()) {
            // PC端登录可能需要验证码，暂时跳过或用备用方式
            result.pcToken = "";
            result.pcLoginSuccess = false;
        } else {
            result.pcToken = pcToken;
            result.pcLoginSuccess = true;
        }

        // 3. APP端登录
        String appLoginResult = loginByApp(user, pass, imei);
        ShuyunLoginResult appLogin = parseAppLogin(appLoginResult);

        if (appLogin.success) {
            result.appToken = appLogin.token;
            result.appUserId = appLogin.userId;
            result.appLoginSuccess = true;
        } else {
            result.appLoginSuccess = false;
            result.errorMsg = "APP登录失败";
        }

        result.success = result.appLoginSuccess; // APP登录成功即认为整体成功
        return result;
    }

    /**
     * 同时登录结果封装
     */
    public static class ShuyunDualLoginResult {
        public boolean success = false;
        public boolean pcLoginSuccess = false;
        public boolean appLoginSuccess = false;
        public String pcToken = "";
        public String pcIp = "";
        public String appToken = "";
        public String appUserId = "";
        public String errorMsg = "";
    }

    // =====================================================================
    // APP版登录（原有方法保留）
    // =====================================================================
    /**
     * APP版登录（用户名+密码+IMEI）
     * @param userIndex 账号索引（0或1）
     * @return 登录结果JSON
     */
    public static String loginByApp(int userIndex) {
        String user, pass, imei;

        if (userIndex == 1) {
            user = BACKUP_USER;
            pass = BACKUP_PASS;
            imei = BACKUP_IMEI;
        } else {
            user = DEFAULT_USER;
            pass = DEFAULT_PASS;
            imei = DEFAULT_IMEI;
        }

        return loginByApp(user, pass, imei);
    }

    /**
     * APP版登录（指定账号）
     */
    public static String loginByApp(String user, String pass, String imei) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"deviceType\":\"ANDROID\","
                + "\"password\":\"" + pass + "\","
                + "\"imei\":\"" + imei + "\","
                + "\"userId\":\"" + user + "\""
                + "},"
                + "\"interfaceName\":\"/api/auth/jwt/tokenAppByAiPu\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppLoginHeader();
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析APP版登录返回的token和userId
     */
    public static ShuyunLoginResult parseAppLogin(String jsonStr) {
        ShuyunLoginResult result = new ShuyunLoginResult();
        try {
            JSONObject root = new JSONObject(jsonStr);
            // 解析错误信息
            result.message = root.optString("message", "");
            // 解析data
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                result.token = data.optString("data", "");
                result.userId = data.optString("userId", "");
                result.success = !result.token.isEmpty();
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.message = e.getMessage();
        }
        return result;
    }

    public static class ShuyunLoginResult {
        public String token = "";
        public String userId = "";
        public boolean success = false;
        public String message = "";
    }

    // =====================================================================
    // 4. 获取工单列表（待实现，根据实际接口补充）
    // =====================================================================
    /**
     * 获取工单列表
     * @param token 登录token
     * @param userId 用户ID
     * @return 工单列表JSON
     */
    public static String getTaskList(String token, String userId) {
        // TODO: 根据实际接口实现
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"userId\":\"" + userId + "\""
                + "},"
                + "\"interfaceName\":\"/api/task/list\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(token);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析工单列表
     */
    public static List<ShuyunTaskInfo> parseTaskList(String jsonStr) {
        List<ShuyunTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray arr = data.optJSONArray("list");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject item = arr.getJSONObject(i);
                        ShuyunTaskInfo info = new ShuyunTaskInfo();
                        info.id = item.optString("id", "");
                        info.taskName = item.optString("taskName", "");
                        info.siteName = item.optString("siteName", "");
                        info.status = item.optString("status", "");
                        info.createTime = item.optString("createTime", "");
                        list.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static class ShuyunTaskInfo {
        public String id = "";
        public String taskName = "";
        public String siteName = "";
        public String status = "";
        public String createTime = "";
    }

    // =====================================================================
    // 5. 接单
    // =====================================================================
    /**
     * 接单
     */
    public static String acceptTask(String token, String userId, String taskId) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"userId\":\"" + userId + "\","
                + "\"taskId\":\"" + taskId + "\""
                + "},"
                + "\"interfaceName\":\"/api/task/accept\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(token);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // =====================================================================
    // 6. 回单
    // =====================================================================
    /**
     * 回单
     */
    public static String revertTask(String token, String userId, String taskId, String remark) {
        String url = APP_BASE + "/zjtt-app-server/mobiledata";

        String post = "data={"
                + "\"head\":{},"
                + "\"requestType\":\"post\","
                + "\"data\":{"
                + "\"userId\":\"" + userId + "\","
                + "\"taskId\":\"" + taskId + "\","
                + "\"remark\":\"" + remark + "\""
                + "},"
                + "\"interfaceName\":\"/api/task/revert\","
                + "\"apiType\":\"REST\""
                + "}&action=CoreInterfaceBean.commonInterfaceHandler";

        String headers = buildAppHeader(token);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // =====================================================================
    // 工具方法
    // =====================================================================

    /**
     * 判断API调用是否成功
     */
    public static boolean isSuccess(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(jsonStr);
            // 根据不同接口判断success字段
            if (root.has("success")) {
                return root.getBoolean("success");
            }
            if (root.has("code")) {
                return "200".equals(root.getString("code")) || "0".equals(root.getString("code"));
            }
            // data字段存在即认为成功
            return root.has("data");
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildPcHeader() {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/login\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    private static String buildPcLoginHeader(int contentLength) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Length: " + contentLength + "\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/login\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    /**
     * 县级/市级审核 form-urlencoded POST请求头（用于获取列表，对应易语言的数运协议头）
     */
    private static String buildCountyApiHeader(String pcToken) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + pcToken + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/x-www-form-urlencoded\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    /**
     * 县级/市级审核 JSON POST请求头（用于提交审核）
     */
    private static String buildCountyJsonHeader(String pcToken) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + pcToken + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    private static String buildAppLoginHeader() {
        return "Content-Type: application/x-www-form-urlencoded\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 14; M2011K2C Build/UKQ1.240624.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36\n"
                + "Host: 223.95.77.175:19021\n"
                + "Connection: Keep-Alive\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept: application/json, text/plain, */*\n"
                + "Origin: http://223.95.77.175:19021\n"
                + "Referer: http://223.95.77.175:19021/\n"
                + "X-Requested-With: com.zjtt.mobile";
    }

    private static String buildAppHeader(String token) {
        return "Content-Type: application/x-www-form-urlencoded\n"
                + "Authorization: " + token + "\n"
                + "User-Agent: Mozilla/5.0 (Linux; Android 14; M2011K2C Build/UKQ1.240624.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36\n"
                + "Host: 223.95.77.175:19021\n"
                + "Connection: Keep-Alive\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept: application/json, text/plain, */*\n"
                + "Origin: http://223.95.77.175:19021\n"
                + "Referer: http://223.95.77.175:19021/\n"
                + "X-Requested-With: com.zjtt.mobile";
    }

    // =====================================================================
    // 7. 智联工单接口（PC端API）
    // =====================================================================
    /**
     * 获取智联待签收工单列表
     * @param pcToken PC端登录Token
     * @param userId 用户ID
     * @param cityArea 区县代码（如330326）
     * @return 工单列表JSON
     */
    public static String getZhilianTaskList(String pcToken, String userId, String cityArea) {
        String url = PC_BASE + "/api/flowable/flowable/task/listToSign";

        // POST body参数（与易语言一致）
        String post = "page=1"
                + "&limit=10"
                + "&flowId=1024,1124,1160,1220"
                + "&orderType="
                + "&userId=" + userId
                + "&area=330300"
                + "&cityArea=" + cityArea;

        String headers = buildCountyApiHeader(pcToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析智联工单列表
     * @param jsonStr API返回的JSON
     * @return 工单列表
     */
    public static List<ZhilianTaskInfo> parseZhilianTaskList(String jsonStr) {
        List<ZhilianTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray rows = data.optJSONArray("rows");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.getJSONObject(i);
                        ZhilianTaskInfo info = new ZhilianTaskInfo();
                        info.flowId = item.optString("flowId", "");
                        info.jobId = item.optString("jobId", "");
                        info.orderNum = item.optString("orderNum", "");
                        info.userId = item.optString("userId", "");
                        info.workInstId = item.optString("workInstId", "");
                        info.siteName = item.optString("title", item.optString("siteName", ""));
                        info.createTime = item.optString("createTime", "");
                        info.flowName = item.optString("flowName", "");
                        list.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 智联工单接单
     * @param pcToken PC端登录Token
     * @param workInstId 工单实例ID
     * @param orderNum 工单编号
     * @param flowId 流程ID
     * @param jobId 任务ID
     * @param userId 用户ID
     * @return 接单结果
     */
    public static String acceptZhilianTask(String pcToken, String workInstId, String orderNum,
            String flowId, String jobId, String userId) {
        String url = PC_BASE + "/api/flowable/flowableFlow/updateWorkStatus";

        // JSON格式请求体（与易语言一致）
        String post = "{\"workInstId\":\"" + workInstId + "\","
                + "\"orderNum\":\"" + orderNum + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"userId\":\"" + userId + "\"}";

        // 使用JSON POST请求头
        String headers = buildCountyJsonHeader(pcToken);

        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 智联工单信息封装
     */
    public static class ZhilianTaskInfo {
        public String flowId = "";
        public String jobId = "";
        public String orderNum = "";
        public String userId = "";
        public String workInstId = "";
        public String siteName = "";
        public String createTime = "";
        public String flowName = "";
    }

    // =====================================================================
    // 8. 县级审核接口（PC端API）
    // =====================================================================
    /**
     * 获取县级待审核工单列表
     * @param pcToken PC端登录Token
     * @param userId 区县经理代号（36745平阳/31950泰顺）
     * @return 工单列表JSON
     */
    public static String getCountyTaskList(String pcToken, String userId) {
        // 与易语言完全一致：URL和body都带参数，使用POST请求（form-urlencoded）
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + userId
                + "&flowId=1025,1054,1055,1056,1131,1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,1137,1143,1063"
                + "&orderType=&xmlx=&area=&cityArea=";

        String post = "page=1&limit=10&userId=" + userId
                + "&flowId=1025,1054,1055,1056,1131,1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,1137,1143,1063"
                + "&orderType=&xmlx=&area=&cityArea=";

        String headers = buildCountyApiHeader(pcToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析县级待审核工单列表
     * @param jsonStr API返回的JSON
     * @return 工单列表
     */
    public static List<CountyTaskInfo> parseCountyTaskList(String jsonStr) {
        List<CountyTaskInfo> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray rows = data.optJSONArray("rows");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject item = rows.getJSONObject(i);
                        CountyTaskInfo info = new CountyTaskInfo();
                        info.orderNum = item.optString("orderNum", "");
                        info.req_deal_limit = item.optString("req_deal_limit", "");
                        info.station_code = item.optString("station_code", "");
                        info.req_comp_time = item.optString("req_comp_time", "");
                        info.flowInstId = item.optString("flowInstId", "");
                        info.city_name = item.optString("city_name", "");
                        info.workInstId = item.optString("workInstId", "");
                        info.data_name = item.optString("data_name", "");
                        info.rwkssj_time = item.optString("rwkssj_time", "");
                        info.flowId = item.optString("flowId", "");
                        info.order_type = item.optString("order_type", "");
                        info.jobName = item.optString("jobName", "");
                        info.station_name = item.optString("station_name", "");
                        info.relaType = item.optString("relaType", "");
                        info.flowInstName = item.optString("flowInstName", "");
                        info.flowName = item.optString("flowName", "");
                        info.jobInstId = item.optString("jobInstId", "");
                        info.jobId = item.optString("jobId", "");
                        list.add(info);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 县级审核通过
     * @param pcToken PC端登录Token
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param userId 区县经理代号
     * @return 审核结果
     */
    public static String submitCountyAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String userId) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // 计算nextJobAndUser：根据jobId前缀（注意：需要用英文逗号）
        String jobPrefix = jobId.contains("_") ? jobId.substring(0, jobId.indexOf("_")) : jobId;
        String nextJobAndUser = jobPrefix + "_003@12101,12102,12104,12108,12376,22979,30264,37493,12107,37614,37881,12103,12106,12120,12101,12103";

        // JSON格式请求体（与易语言一致）
        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + userId + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + nextJobAndUser + "\","
                + "\"copyUsers\":\"" + "" + "\"}";

        // 使用JSON POST请求头
        String headers = buildCountyJsonHeader(pcToken);

        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 县级审核工单信息封装
     */
    public static class CountyTaskInfo {
        public String orderNum = "";
        public String req_deal_limit = "";
        public String station_code = "";
        public String req_comp_time = "";
        public String flowInstId = "";
        public String city_name = "";
        public String workInstId = "";
        public String data_name = "";
        public String rwkssj_time = "";
        public String flowId = "";
        public String order_type = "";
        public String jobName = "";
        public String station_name = "";
        public String relaType = "";
        public String flowInstName = "";
        public String flowName = "";
        public String jobInstId = "";
        public String jobId = "";
    }

    // =====================================================================
    // 9. 市级审核接口（PC端API）
    // =====================================================================
    // 市级审核用户ID
    private static final String CITY_AUDIT_USER_ID = "12376";

    /**
     * 获取市级待审核工单列表
     * @param pcToken PC端登录Token
     * @param cityArea 区县代码（如330326）
     * @return 工单列表JSON
     */
    public static String getCityTaskList(String pcToken, String cityArea) {
        // 与易语言完全一致：URL和body都带参数，使用POST请求（form-urlencoded）
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        String post = "page=1&limit=10&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&xmlx=&area=330300&cityArea=" + cityArea;

        String headers = buildCountyApiHeader(pcToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取市级已办工单列表
     * @param pcToken PC端登录Token
     * @param cityArea 区县代码（如330326）
     * @return 已办工单列表JSON
     */
    public static String getCityFinishedList(String pcToken, String cityArea) {
        // 与易语言一致：URL和body都带参数，使用POST请求（form-urlencoded）
        String url = PC_BASE + "/api/flowable/flowable/task/listToFinish"
                + "?page=1"
                + "&limit=30"
                + "&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&area=330300&cityArea=" + cityArea;

        String post = "page=1&limit=30&userId=" + CITY_AUDIT_USER_ID
                + "&flowId=&orderType=&area=330300&cityArea=" + cityArea;

        String headers = buildCountyApiHeader(pcToken);
        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 延期判断接口
     * @param pcToken PC端登录Token
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param relaType 关联类型
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @return 延期判断结果JSON
     */
    public static String checkDelay(String pcToken, String orderNum, String jobInstId,
            String relaType, String flowInstId, String jobId, String workInstId, String flowId) {
        String url = PC_BASE + "/api/flowable/orderInfo/showWorkInfo";

        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"spec\":\"" + relaType + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"workType\":\"D\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"userId\":\"" + CITY_AUDIT_USER_ID + "\","
                + "\"requireId\":\"" + orderNum + "\","
                + "\"gotoType\":\"taskTodo\"}";

        String headers = buildCountyJsonHeader(pcToken);

        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 解析延期判断结果
     * @param jsonStr API返回的JSON
     * @return 延期判断结果：自动审核/省监控审核/铁塔省监控
     */
    public static String parseDelayResult(String jsonStr) {
        try {
            // 从返回的JSON中提取延期判断结果
            // 根据易语言代码，返回值包含 jobId_ID 和延期判断结果
            if (jsonStr == null || jsonStr.isEmpty()) {
                return "自动审核"; // 默认走普通审核
            }
            JSONObject root = new JSONObject(jsonStr);
            // 检查是否包含延期相关字段
            if (root.has("data")) {
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    String jobId = data.optString("jobId", "");
                    // 根据jobId判断审核类型
                    if (jobId.contains("延期") || jobId.contains("省监控")) {
                        return "省监控审核";
                    }
                }
            }
            // 检查是否包含特定关键字
            if (jsonStr.contains("省监控") || jsonStr.contains("延期")) {
                return "省监控审核";
            }
            return "自动审核";
        } catch (Exception e) {
            e.printStackTrace();
            return "自动审核";
        }
    }

    /**
     * 市级普通审核通过
     * @param pcToken PC端登录Token
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param jobId_ID 延期判断返回的jobId
     * @return 审核结果
     */
    public static String submitCityAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // JSON格式请求体
        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + CITY_AUDIT_USER_ID + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + jobId_ID + "@10023\","
                + "\"copyUsers\":\"" + "" + "\"}";

        String headers = buildCountyJsonHeader(pcToken);

        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 市级延期审核通过
     * @param pcToken PC端登录Token
     * @param orderNum 工单编号
     * @param jobInstId 任务实例ID
     * @param flowInstId 流程实例ID
     * @param jobId 任务ID
     * @param workInstId 工作实例ID
     * @param flowId 流程ID
     * @param jobId_ID 延期判断返回的jobId
     * @return 审核结果
     */
    public static String submitCityDelayAudit(String pcToken, String orderNum, String jobInstId,
            String flowInstId, String jobId, String workInstId, String flowId, String jobId_ID) {
        String url = PC_BASE + "/api/flowable/flowable/task/complete";

        // 延期审核的 nextJobAndUser 更长，包含更多审核人
        String nextJobAndUser = jobId_ID + "@11875,11881,12173,12178,12182,12187,12190,12191,12192,12195,12200,12201,12204,12205,22990,24091,29719,29721,29723,30170,30172,31190,31254,31255,31741,31943,32166,32269,32270,32743,33012,33323,33520,34567,34812,34999,35822,35823,35887,35910,36073,36074,36075,36119,37169,37170,37171,37208,37209,37210,37211,37229,37256,37257,37258,37272,37273,37319,37364,37383,37718,37958,37959,38097,38381,38572,38620,39304,39482,39717,40414,40458,40565,40566,40752,40883,40884,40885,40954,40966,40967,40984,41031,41188,41246,41247,41317,41350,41390,41541";

        String post = "{\"orderNum\":\"" + orderNum + "\","
                + "\"userId\":\"" + CITY_AUDIT_USER_ID + "\","
                + "\"jobInstId\":\"" + jobInstId + "\","
                + "\"flowInstId\":\"" + flowInstId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"workInstId\":\"" + workInstId + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"dealContent\":\"" + "通过" + "\","
                + "\"operType\":\"" + "01" + "\","
                + "\"nextJobAndUser\":\"" + nextJobAndUser + "\","
                + "\"copyUsers\":\"" + "" + "\"}";

        String headers = buildCountyJsonHeader(pcToken);

        try {
            String result = HttpUtil.post(url, post, headers, null);
            return result != null ? result : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 市级审核工单信息封装（复用CountyTaskInfo结构）
     */
    public static class CityTaskInfo extends CountyTaskInfo {
        public String shsj_time = ""; // 审核时间
    }

    // =====================================================================
    // PC端API Header
    // =====================================================================
    private static String buildPcApiHeader(String token) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Authorization: " + token + "\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }

    private static String buildPcJsonHeader(int contentLength) {
        return "Accept: application/json, text/plain, */*\n"
                + "Accept-Encoding: gzip, deflate\n"
                + "Accept-Language: zh-CN,zh;q=0.9\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n"
                + "Content-Length: " + contentLength + "\n"
                + "Content-Type: application/json;charset=UTF-8\n"
                + "Host: zjtowercom.cn:8998\n"
                + "Origin: http://zjtowercom.cn:8998\n"
                + "Pragma: no-cache\n"
                + "Referer: http://zjtowercom.cn:8998/dashboard\n"
                + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";
    }
}
