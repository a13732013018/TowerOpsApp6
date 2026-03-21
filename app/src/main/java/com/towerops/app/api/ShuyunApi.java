package com.towerops.app.api;

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

    // 默认账号（从用户提供的信息）
    public static final String DEFAULT_USER = "13732013018";
    public static final String DEFAULT_PASS = "F8TVasxWplZNK7AJq4T1cA==";
    public static final String DEFAULT_IMEI = "ba9f03beaacd4c05";

    // 备用账号
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
        String headers = buildPcHeader();
        try {
            String result = HttpUtil.get(url, headers, null);
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
    // 3. APP版登录
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
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                result.token = data.optString("data", "");
                result.userId = data.optString("userId", "");
                result.success = !result.token.isEmpty();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static class ShuyunLoginResult {
        public String token = "";
        public String userId = "";
        public boolean success = false;
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

    public static boolean isSuccess(String result) {
        if (result == null || result.isEmpty()) return false;
        return result.contains("\"status\":\"ok\"")
                || result.contains("\"status\": \"ok\"")
                || result.contains("\"success\":true")
                || result.contains("成功")
                || result.contains("操作成功")
                || result.contains("处理成功");
    }
}
