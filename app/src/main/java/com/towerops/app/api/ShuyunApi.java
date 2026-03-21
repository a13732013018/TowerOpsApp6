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

    // =====================================================================
    // 7. 智联工单接口（PC端API）
    // =====================================================================
    /**
     * 获取智联待签收工单列表
     * @param pcToken PC端登录Token
     * @param userId 用户ID
     * @param cityArea 区县代码（如330300）
     * @return 工单列表JSON
     */
    public static String getZhilianTaskList(String pcToken, String userId, String cityArea) {
        String url = PC_BASE + "/api/flowable/flowable/task/listToSign"
                + "?page=1"
                + "&limit=10"
                + "&flowId=1024,1124,1160,1220"
                + "&orderType="
                + "&userId=" + userId
                + "&area=330300"
                + "&cityArea=" + cityArea;

        String headers = buildPcApiHeader(pcToken);
        try {
            String result = HttpUtil.get(url, headers, null);
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

        // JSON格式请求体
        String post = "{\"workInstId\":\"" + workInstId + "\","
                + "\"orderNum\":\"" + orderNum + "\","
                + "\"flowId\":\"" + flowId + "\","
                + "\"jobId\":\"" + jobId + "\","
                + "\"userId\":\"" + userId + "\"}";

        String headers = buildPcJsonHeader(post.length());
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
     * @param userId 区县经理代号（36745市区/31950其他）
     * @return 工单列表JSON
     */
    public static String getCountyTaskList(String pcToken, String userId) {
        String url = PC_BASE + "/api/flowable/flowable/task/listTodo"
                + "?page=1"
                + "&limit=10"
                + "&userId=" + userId
                + "&flowId=1025,1054,1055,1056,1131,1027,1028,1033,1038,1040,1048,1072,1118,1122,1127,1137,1143,1063"
                + "&orderType="
                + "&xmlx="
                + "&area="
                + "&cityArea=";

        String headers = buildPcApiHeader(pcToken);
        try {
            String result = HttpUtil.get(url, headers, null);
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

        // 计算nextJobAndUser：根据jobId前缀
        String jobPrefix = jobId.contains("_") ? jobId.substring(0, jobId.indexOf("_")) : jobId;
        String nextJobAndUser = jobPrefix + "_003@12101,12102,12104,12108,12376,22979,30264,37493,12107,37614,37881,12103,12106,12120,12101,12103";

        // JSON格式请求体
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

        String headers = buildPcJsonHeader(post.length());
        // 需要添加Authorization头
        headers = headers.replace("Content-Type: application/json;charset=UTF-8\n", 
                "Content-Type: application/json;charset=UTF-8\n"
                + "Authorization: " + pcToken + "\n");

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
