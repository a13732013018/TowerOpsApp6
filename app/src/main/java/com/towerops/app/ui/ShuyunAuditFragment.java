package com.towerops.app.ui;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.model.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 数运审核子Fragment - 对应"数运审核"Tab
 */
public class ShuyunAuditFragment extends Fragment {

    private static final String TAG = "ShuyunAuditFragment";

    // UI控件
    private TextView tvAuditLog, tvCityFinishedList, tvCityTodoList, tvAuditTitle;
    private Button btnCountyAudit, btnStopCountyAudit;
    private Button btnCityAudit, btnStopCityAudit;
    private Button btnProvinceAudit, btnStopProvinceAudit;
    private Button btnProvinceMonitorAudit;
    private Spinner spinnerCounty;
    private ScrollView svAuditLog;
    private android.widget.ListView lvCityFinishedList, lvCityTodoList;

    // 省级审核开发者权限IMEI
    private static final String PROVINCE_AUTH_IMEI = "ba9f03beaacd4c05";

    // 选中的市级已办工单（用于省监控回单）
    private ShuyunApi.CountyTaskInfo selectedCityFinishedTask = null;

    // 选中的市级待办工单（用于省监控回单）
    private ShuyunApi.CountyTaskInfo selectedCityTodoTask = null;

    // 市级已办工单列表（用于ListView显示）
    private List<ShuyunApi.CountyTaskInfo> cityFinishedTaskList = new ArrayList<>();

    // 市级待办工单列表（用于ListView显示）
    private List<ShuyunApi.CountyTaskInfo> cityTodoTaskList = new ArrayList<>();

    // 区县经理代号（县级审核用）
    private static final String[] COUNTY_CODES = {"36745", "31950"};
    private static final String[] COUNTY_NAMES = {"平阳县(36745)", "泰顺县(31950)"};

    // 行政区划代码（市级审核用）
    private static final String[] CITY_AREA_CODES = {"330326", "330329", "330302", "330327", "330328", "330381", "330382", "330303", "330305", "330324", "330383"};
    private static final String[] CITY_AREA_NAMES = {"平阳县(330326)", "泰顺县(330329)", "鹿城区(330302)", "苍南县(330327)", "文成县(330328)", "瑞安市(330381)", "乐清市(330382)", "龙湾区(330303)", "洞头区(330305)", "永嘉县(330324)", "龙港市(330383)"};

    // 主线程Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 审核状态
    private volatile boolean isCountyRunning = false;
    private volatile boolean isCityRunning = false;
    private volatile boolean isProvinceRunning = false;
    private Thread countyThread;
    private Thread cityThread;
    private Thread provinceThread;
    private int selectedCountyIndex = 0;
    private int selectedCityAreaIndex = 0;

    // 登录信息（从Activity获取）
    private String pcToken = "";
    private String userId = "";

    // 接口回调
    private ShuyunAuditCallback callback;

    public interface ShuyunAuditCallback {
        String getPcToken();
        String getUserId();
        boolean isPcLoggedIn();
    }

    public void setCallback(ShuyunAuditCallback callback) {
        this.callback = callback;
    }

    public boolean isRunning() {
        return isCountyRunning;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shuyun_audit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 恢复登录状态
        Session s = Session.get();
        s.loadConfig(requireContext());

        // 如果Session中已有登录信息，直接使用
        if (!s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
        }

        initViews(view);
        setupListeners();
    }

    private void initViews(View view) {
        tvAuditTitle = view.findViewById(R.id.tvAuditTitle);
        tvAuditLog = view.findViewById(R.id.tvAuditLog);

        btnCountyAudit = view.findViewById(R.id.btnCountyAudit);
        btnStopCountyAudit = view.findViewById(R.id.btnStopCountyAudit);
        btnCityAudit = view.findViewById(R.id.btnCityAudit);
        btnStopCityAudit = view.findViewById(R.id.btnStopCityAudit);
        btnProvinceAudit = view.findViewById(R.id.btnProvinceAudit);
        btnStopProvinceAudit = view.findViewById(R.id.btnStopProvinceAudit);
        btnProvinceMonitorAudit = view.findViewById(R.id.btnProvinceMonitorAudit);

        spinnerCounty = view.findViewById(R.id.spinnerCounty);
        svAuditLog = view.findViewById(R.id.svAuditLog);
        tvCityFinishedList = view.findViewById(R.id.tvCityFinishedList);
        tvCityTodoList = view.findViewById(R.id.tvCityTodoList);
        lvCityFinishedList = view.findViewById(R.id.lvCityFinishedList);
        lvCityTodoList = view.findViewById(R.id.lvCityTodoList);

        // 初始化区县选择器（县级审核用）
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, COUNTY_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCounty.setAdapter(adapter);

        // 读取保存的区县选择
        Session s = Session.get();
        if (s.countyManagerCode != null) {
            for (int i = 0; i < COUNTY_CODES.length; i++) {
                if (COUNTY_CODES[i].equals(s.countyManagerCode)) {
                    spinnerCounty.setSelection(i);
                    break;
                }
            }
        }

        // 市级已办列表点击事件（用于省监控回单选择）
        lvCityFinishedList.setOnItemClickListener((parent, itemView, position, id) -> {
            if (cityFinishedTaskList != null && position < cityFinishedTaskList.size()) {
                selectedCityFinishedTask = cityFinishedTaskList.get(position);
                selectedCityTodoTask = null; // 清除待办选择
                String jobName = selectedCityFinishedTask.jobName != null ? selectedCityFinishedTask.jobName : "";
                Toast.makeText(getContext(), "已选择: " + selectedCityFinishedTask.station_name + " [" + jobName + "]", Toast.LENGTH_SHORT).show();
            }
        });

        // 市级待办列表点击事件（用于省监控回单选择）
        lvCityTodoList.setOnItemClickListener((parent, itemView, position, id) -> {
            if (cityTodoTaskList != null && position < cityTodoTaskList.size()) {
                selectedCityTodoTask = cityTodoTaskList.get(position);
                selectedCityFinishedTask = null; // 清除已办选择
                String jobName = selectedCityTodoTask.jobName != null ? selectedCityTodoTask.jobName : "";
                Toast.makeText(getContext(), "已选择: " + selectedCityTodoTask.station_name + " (" + selectedCityTodoTask.orderNum + ") [" + jobName + "]", Toast.LENGTH_SHORT).show();
            }
        });

        updateStatus("未启动");
    }

    private void setupListeners() {
        // 区县选择
        spinnerCounty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCountyIndex = position;
                // 保存选择
                Session s = Session.get();
                s.countyManagerCode = COUNTY_CODES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 开始县级审核
        btnCountyAudit.setOnClickListener(v -> startCountyAudit());

        // 停止县级审核
        btnStopCountyAudit.setOnClickListener(v -> stopCountyAudit());

        // 开始市级审核
        btnCityAudit.setOnClickListener(v -> startCityAudit());

        // 停止市级审核
        btnStopCityAudit.setOnClickListener(v -> stopCityAudit());

        // 开始省级审核
        btnProvinceAudit.setOnClickListener(v -> startProvinceAudit());

        // 停止省级审核
        btnStopProvinceAudit.setOnClickListener(v -> stopProvinceAudit());

        // 省监控审核回单
        btnProvinceMonitorAudit.setOnClickListener(v -> doProvinceMonitorAudit());
    }

    private void updateStatus(String status) {
        // 状态栏已删除，此方法保留但不更新界面
    }

    private void startCountyAudit() {
        // 优先从 Session 获取登录信息，兼容 callback 为 null 的情况
        Session s = Session.get();
        if (s.shuyunPcToken != null && !s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
        } else if (callback != null) {
            pcToken = callback.getPcToken();
        }

        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录PC端", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isCountyRunning) {
            Toast.makeText(getContext(), "审核已在运行中", Toast.LENGTH_SHORT).show();
            return;
        }

        userId = COUNTY_CODES[selectedCountyIndex];
        appendLog("县级审核已启动，区县: " + COUNTY_NAMES[selectedCountyIndex] + "(" + userId + ")");

        isCountyRunning = true;
        btnCountyAudit.setEnabled(false);
        btnCountyAudit.setText("区县审核中");
        btnStopCountyAudit.setEnabled(true);
        updateStatus("审核中...");
        tvAuditTitle.setText("区县审核中");

        countyThread = new Thread(() -> {
            while (isCountyRunning) {
                try {
                    // 获取待审核工单列表
                    mainHandler.post(() -> updateStatus("获取工单中..."));
                    String jsonStr = ShuyunApi.getCountyTaskList(pcToken, userId);

                    List<ShuyunApi.CountyTaskInfo> taskList = ShuyunApi.parseCountyTaskList(jsonStr);

                    if (taskList.isEmpty()) {
                        appendLog("待审核工单为空");
                        mainHandler.post(() -> updateStatus("待审: 0"));
                        // 等待下次检查（45-105秒随机），显示倒计时
                        int sleepTime = (int) (Math.random() * 60000) + 45000;
                        showCountdown("等待下次: ", sleepTime);
                        continue;
                    }

                    appendLog("发现待审核工单: " + taskList.size() + " 个");
                    mainHandler.post(() -> updateStatus("待审: " + taskList.size()));

                    // 遍历并审核
                    for (int i = 0; i < taskList.size() && isCountyRunning; i++) {
                        ShuyunApi.CountyTaskInfo task = taskList.get(i);
                        final ShuyunApi.CountyTaskInfo currentTask = task;

                        // 仿生延迟：基础2-6秒
                        int delayMs = (int) (Math.random() * 4000) + 2000;
                        // 每3条额外4-8秒
                        if (i > 0 && i % 3 == 0) {
                            delayMs += (int) (Math.random() * 4000) + 4000;
                        }
                        final int delay = delayMs;

                        final int currentIndex = i;
                        mainHandler.post(() -> {
                            updateStatus("审核中 " + (currentIndex + 1) + "/" + taskList.size());
                        });

                        // 添加等待日志
                        appendLog("等待 " + delay / 1000 + " 秒后审核: " + task.station_name);

                        Thread.sleep(delay);

                        if (!isCountyRunning) break;

                        // 提交审核
                        String result = ShuyunApi.submitCountyAudit(
                            pcToken,
                            task.orderNum,
                            task.jobInstId,
                            task.flowInstId,
                            task.jobId,
                            task.workInstId,
                            task.flowId,
                            userId
                        );

                        // 精简日志
                        if (ShuyunApi.isSuccess(result)) {
                            appendLog("✓ [" + (currentIndex + 1) + "/" + taskList.size() + "] 通过: " + task.station_name);
                        } else {
                            appendLog("✗ [" + (currentIndex + 1) + "/" + taskList.size() + "] 失败: " + task.station_name);
                        }
                    }

                    // 本轮审核完成，等待下一轮
                    if (isCountyRunning) {
                        int sleepTime = (int) (Math.random() * 60000) + 45000;
                        showCountdown("下次: ", sleepTime);
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    appendLog("审核异常: " + e.getMessage());
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

            // 审核结束
            mainHandler.post(() -> {
                isCountyRunning = false;
                btnCountyAudit.setEnabled(true);
                btnCountyAudit.setText("区县审核");
                btnStopCountyAudit.setEnabled(false);
                updateStatus("已停止");
                tvAuditTitle.setText("数运审核");
                appendLog("县级审核已停止");
            });
        });
        countyThread.start();
    }

    private void stopCountyAudit() {
        isCountyRunning = false;
        if (countyThread != null) {
            countyThread.interrupt();
        }
    }

    // ==================== 市级审核 ====================
    private void startCityAudit() {
        // 优先从 Session 获取登录信息
        Session s = Session.get();
        if (s.shuyunPcToken != null && !s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
        } else if (callback != null) {
            pcToken = callback.getPcToken();
        }

        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录PC端", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isCityRunning) {
            Toast.makeText(getContext(), "市级审核已在运行中", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用当前选择的区县代码
        selectedCityAreaIndex = selectedCountyIndex;
        String cityAreaCode = CITY_AREA_CODES[selectedCityAreaIndex];

        appendLog("市级审核已启动，区县: " + CITY_AREA_NAMES[selectedCityAreaIndex] + "(" + cityAreaCode + ")");

        // 初始获取已办列表（原待办区域现在显示已办数据）
        try {
            // 获取已办列表
            String finishedJson = ShuyunApi.getCityFinishedList(pcToken, cityAreaCode);
            List<ShuyunApi.CountyTaskInfo> finishedList = ShuyunApi.parseCountyTaskList(finishedJson);
            // 原来显示待办的区域现在显示已办数据
            updateCityTodoList(finishedList);

            // 同时也更新已办列表区域（保留）
            updateCityFinishedList(finishedList);
        } catch (Exception e) {
            // 忽略初始获取错误
        }

        isCityRunning = true;
        btnCityAudit.setEnabled(false);
        btnCityAudit.setText("市级审核中");
        btnStopCityAudit.setEnabled(true);
        updateStatus("市级审核中...");
        tvAuditTitle.setText("市级审核中");

        cityThread = new Thread(() -> {
            while (isCityRunning) {
                try {
                    // 获取已办工单列表（原来显示待办的区域现在显示已办数据）
                    mainHandler.post(() -> updateStatus("获取市级工单中..."));

                    // 获取已办列表
                    String finishedJson = ShuyunApi.getCityFinishedList(pcToken, cityAreaCode);
                    List<ShuyunApi.CountyTaskInfo> finishedList = ShuyunApi.parseCountyTaskList(finishedJson);

                    // 更新已办列表显示（原来显示待办的区域现在显示已办数据）
                    updateCityTodoList(finishedList);

                    // 也获取待办列表用于审核逻辑
                    String jsonStr = ShuyunApi.getCityTaskList(pcToken, cityAreaCode);
                    List<ShuyunApi.CountyTaskInfo> taskList = ShuyunApi.parseCountyTaskList(jsonStr);

                    if (taskList.isEmpty()) {
                        appendLog("市级待审核工单为空");
                        mainHandler.post(() -> updateStatus("待审: 0"));

                        // 刷新已办列表显示（原来显示待办的区域现在显示已办数据）
                        // 复用已获取的 finishedList
                        updateCityTodoList(finishedList);
                        updateCityFinishedList(finishedList);

                        // 等待下次检查（50-100秒随机）
                        int sleepTime = (int) (Math.random() * 50000) + 50000;
                        showCountdown("等待下次: ", sleepTime);
                        continue;
                    }

                    appendLog("发现市级待审核工单: " + taskList.size() + " 个");
                    mainHandler.post(() -> updateStatus("待审: " + taskList.size()));

                    // 遍历并审核
                    for (int i = 0; i < taskList.size() && isCityRunning; i++) {
                        ShuyunApi.CountyTaskInfo task = taskList.get(i);

                        // 仿生延迟：基础1-3秒
                        int delayMs = (int) (Math.random() * 2000) + 1000;
                        final int delay = delayMs;

                        final int currentIndex = i;
                        mainHandler.post(() -> {
                            updateStatus("审核中 " + (currentIndex + 1) + "/" + taskList.size());
                        });

                        // 添加等待日志
                        appendLog("等待 " + delay / 1000 + " 秒后审核: " + task.station_name);

                        Thread.sleep(delay);

                        if (!isCityRunning) break;

                        // 延期判断（静默执行，不输出详细信息）
                        String delayResult = ShuyunApi.checkDelay(pcToken,
                            task.orderNum, task.jobInstId, task.relaType,
                            task.flowInstId, task.jobId, task.workInstId, task.flowId);

                        String delayType = ShuyunApi.parseDelayResult(delayResult);
                        String jobIdFromDelay = ShuyunApi.extractJobIdFromDelayResult(delayResult);

                        // 提交审核：两种都尝试，一种成功就OK
                        String result = "";
                        boolean auditSuccess = false;

                        if ("省监控审核".equals(delayType)) {
                            // 延期审核优先
                            result = ShuyunApi.submitCityDelayAudit(pcToken,
                                task.orderNum, task.jobInstId, task.flowInstId,
                                task.jobId, task.workInstId, task.flowId, jobIdFromDelay);

                            if (ShuyunApi.isSuccess(result)) {
                                auditSuccess = true;
                            } else {
                                // 延期审核失败，尝试普通审核
                                result = ShuyunApi.submitCityAudit(pcToken,
                                    task.orderNum, task.jobInstId, task.flowInstId,
                                    task.jobId, task.workInstId, task.flowId, jobIdFromDelay);
                                auditSuccess = ShuyunApi.isSuccess(result);
                            }
                        } else {
                            // 普通审核优先
                            result = ShuyunApi.submitCityAudit(pcToken,
                                task.orderNum, task.jobInstId, task.flowInstId,
                                task.jobId, task.workInstId, task.flowId, jobIdFromDelay);

                            if (ShuyunApi.isSuccess(result)) {
                                auditSuccess = true;
                            } else {
                                // 普通审核失败，尝试延期审核
                                result = ShuyunApi.submitCityDelayAudit(pcToken,
                                    task.orderNum, task.jobInstId, task.flowInstId,
                                    task.jobId, task.workInstId, task.flowId, jobIdFromDelay);
                                auditSuccess = ShuyunApi.isSuccess(result);
                            }
                        }

                        // 最终结果（精简日志）
                        if (auditSuccess) {
                            appendLog("✓ [" + (currentIndex + 1) + "/" + taskList.size() + "] 通过: " + task.station_name);
                        } else {
                            appendLog("✗ [" + (currentIndex + 1) + "/" + taskList.size() + "] 失败: " + task.station_name);
                        }
                    }

                    // 本轮审核完成，获取已办列表后等待下一轮
                    if (isCityRunning) {
                        // 重新获取已办列表
                        String finishedJson2 = ShuyunApi.getCityFinishedList(pcToken, cityAreaCode);
                        List<ShuyunApi.CountyTaskInfo> finishedList2 = ShuyunApi.parseCountyTaskList(finishedJson2);
                        appendLog("已办: " + finishedList2.size() + " 条");

                        // 显示前3条到已办列表区域
                        updateCityTodoList(finishedList2);
                        updateCityFinishedList(finishedList2);

                        int sleepTime = (int) (Math.random() * 50000) + 50000;
                        showCountdown("下次: ", sleepTime);
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    appendLog("市级审核异常: " + e.getMessage());
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

            // 审核结束
            mainHandler.post(() -> {
                isCityRunning = false;
                btnCityAudit.setEnabled(true);
                btnCityAudit.setText("市级审核");
                btnStopCityAudit.setEnabled(false);
                updateStatus("已停止");
                tvAuditTitle.setText("数运审核");
                appendLog("市级审核已停止");
            });
        });
        cityThread.start();
    }

    private void stopCityAudit() {
        isCityRunning = false;
        if (cityThread != null) {
            cityThread.interrupt();
        }
    }

    // ==================== 省级审核 ====================
    /**
     * 获取设备IMEI/序列号
     */
    private String getDeviceId() {
        try {
            Context context = getContext();
            if (context == null) return "";
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String imei = tm.getDeviceId();
                if (imei != null && !imei.isEmpty()) {
                    return imei;
                }
            }
            // 备用：获取Android ID
            String androidId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            return androidId != null ? androidId : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 启动省级审核
     */
    private void startProvinceAudit() {
        // 优先从 Session 获取登录信息
        Session s = Session.get();
        if (s.shuyunPcToken != null && !s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
        } else if (callback != null) {
            pcToken = callback.getPcToken();
        }

        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录PC端", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isProvinceRunning) {
            Toast.makeText(getContext(), "省级审核已在运行中", Toast.LENGTH_SHORT).show();
            return;
        }

        // IMEI权限验证：从Session中获取APP登录时保存的imei
        String loginImei = s.shuyunAppImei;
        if (loginImei == null || loginImei.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录APP端获取权限", Toast.LENGTH_SHORT).show();
            appendLog("省级审核：请先登录APP端");
            return;
        }
        if (!PROVINCE_AUTH_IMEI.equalsIgnoreCase(loginImei)) {
            Toast.makeText(getContext(), "无省级审核权限，需联系开发者", Toast.LENGTH_LONG).show();
            appendLog("省级审核：无权限 (IMEI不匹配)");
            return;
        }

        // 使用当前选择的区县代码
        selectedCityAreaIndex = selectedCountyIndex;
        String cityAreaCode = CITY_AREA_CODES[selectedCityAreaIndex];

        // 先获取待审核工单数量
        appendLog("正在获取省级待审核工单...");
        String jsonStr = ShuyunApi.getProvinceTaskList(pcToken, cityAreaCode);
        List<ShuyunApi.CountyTaskInfo> taskList = ShuyunApi.parseCountyTaskList(jsonStr);

        if (taskList.isEmpty()) {
            Toast.makeText(getContext(), "省级待审核工单为空", Toast.LENGTH_SHORT).show();
            appendLog("省级待审核工单为空");
            return;
        }

        // 构建工单信息用于确认对话框
        StringBuilder taskInfo = new StringBuilder();
        int count = Math.min(taskList.size(), 10);
        for (int i = 0; i < count; i++) {
            ShuyunApi.CountyTaskInfo task = taskList.get(i);
            taskInfo.append(i + 1).append(". ").append(task.station_name);
            if (task.orderNum != null && !task.orderNum.isEmpty()) {
                taskInfo.append(" (").append(task.orderNum).append(")");
            }
            taskInfo.append("\n");
        }
        if (taskList.size() > 10) {
            taskInfo.append("... 共").append(taskList.size()).append("条");
        }

        // 弹出确认对话框
        new AlertDialog.Builder(requireContext())
                .setTitle("省级审核确认")
                .setMessage("发现 " + taskList.size() + " 条待审核工单：\n\n" + taskInfo.toString() + "\n是否确认审核？")
                .setPositiveButton("确认审核", (dialog, which) -> {
                    // 用户确认后开始审核
                    performProvinceAudit(taskList, cityAreaCode);
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    appendLog("省级审核已取消");
                })
                .show();
    }

    /**
     * 执行省级审核
     */
    private void performProvinceAudit(List<ShuyunApi.CountyTaskInfo> taskList, String cityAreaCode) {
        appendLog("省级审核已启动，区县: " + CITY_AREA_NAMES[selectedCityAreaIndex] + "(" + cityAreaCode + ")");
        appendLog("待审核工单: " + taskList.size() + " 个");

        isProvinceRunning = true;
        btnProvinceAudit.setEnabled(false);
        btnProvinceAudit.setText("省级审核中");
        btnStopProvinceAudit.setEnabled(true);
        updateStatus("省级审核中...");
        tvAuditTitle.setText("省级审核中");

        provinceThread = new Thread(() -> {
            try {
                for (int i = 0; i < taskList.size() && isProvinceRunning; i++) {
                    ShuyunApi.CountyTaskInfo task = taskList.get(i);

                    // 跳过"超频告警整治工单"（与易语言一致）
                    if ("超频告警整治工单".equals(task.flowName)) {
                        appendLog("跳过: " + task.station_name + " (超频工单)");
                        continue;
                    }

                    // 仿生延迟：基础1-3秒
                    int delayMs = (int) (Math.random() * 2000) + 1000;
                    final int delay = delayMs;

                    final int currentIndex = i;
                    mainHandler.post(() -> {
                        updateStatus("审核中 " + (currentIndex + 1) + "/" + taskList.size());
                    });

                    // 添加等待日志
                    appendLog("等待 " + delay / 1000 + " 秒后审核: " + task.station_name);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        break;
                    }

                    if (!isProvinceRunning) break;

                    // 延期判断（与市级审核相同）
                    String delayResult = ShuyunApi.checkDelay(pcToken,
                        task.orderNum, task.jobInstId, task.relaType,
                        task.flowInstId, task.jobId, task.workInstId, task.flowId);

                    String delayType = ShuyunApi.parseDelayResult(delayResult);
                    String jobIdFromDelay = ShuyunApi.extractJobIdFromDelayResult(delayResult);

                    // 提交审核：两种都尝试
                    String result = "";
                    boolean auditSuccess = false;

                    if ("省监控审核".equals(delayType)) {
                        // 延期审核优先
                        result = ShuyunApi.submitProvinceDelayAudit(pcToken,
                            task.orderNum, task.jobInstId, task.flowInstId,
                            task.jobId, task.workInstId, task.flowId, jobIdFromDelay);

                        if (ShuyunApi.isSuccess(result)) {
                            auditSuccess = true;
                        } else {
                            // 延期审核失败，尝试普通审核
                            result = ShuyunApi.submitProvinceAudit(pcToken,
                                task.orderNum, task.jobInstId, task.flowInstId,
                                task.jobId, task.workInstId, task.flowId, jobIdFromDelay);
                            auditSuccess = ShuyunApi.isSuccess(result);
                        }
                    } else {
                        // 普通审核优先
                        result = ShuyunApi.submitProvinceAudit(pcToken,
                            task.orderNum, task.jobInstId, task.flowInstId,
                            task.jobId, task.workInstId, task.flowId, jobIdFromDelay);

                        if (ShuyunApi.isSuccess(result)) {
                            auditSuccess = true;
                        } else {
                            // 普通审核失败，尝试延期审核
                            result = ShuyunApi.submitProvinceDelayAudit(pcToken,
                                task.orderNum, task.jobInstId, task.flowInstId,
                                task.jobId, task.workInstId, task.flowId, jobIdFromDelay);
                            auditSuccess = ShuyunApi.isSuccess(result);
                        }
                    }

                    // 最终结果
                    if (auditSuccess) {
                        appendLog("✓ [" + (currentIndex + 1) + "/" + taskList.size() + "] 通过: " + task.station_name);
                    } else {
                        appendLog("✗ [" + (currentIndex + 1) + "/" + taskList.size() + "] 失败: " + task.station_name);
                    }
                }
            } catch (Exception e) {
                appendLog("省级审核异常: " + e.getMessage());
            }

            // 审核结束
            mainHandler.post(() -> {
                isProvinceRunning = false;
                btnProvinceAudit.setEnabled(true);
                btnProvinceAudit.setText("省级审核");
                btnStopProvinceAudit.setEnabled(false);
                updateStatus("已完成");
                tvAuditTitle.setText("数运审核");
                appendLog("省级审核已完成");
            });
        });
        provinceThread.start();
    }

    private void stopProvinceAudit() {
        isProvinceRunning = false;
        if (provinceThread != null) {
            provinceThread.interrupt();
        }
    }

    /**
     * 省监控审核回单（对已选择的市级工单进行归档）
     */
    private void doProvinceMonitorAudit() {
        // 优先使用省监控回单列表（lvCityFinishedList）选中的工单
        ShuyunApi.CountyTaskInfo selectedTask = selectedCityFinishedTask != null ? selectedCityFinishedTask : selectedCityTodoTask;

        // 检查是否选择了工单
        if (selectedTask == null) {
            Toast.makeText(getContext(), "请先点击选择列表中的工单", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查当前环节是否是省监控审核
        String jobName = selectedTask.jobName;
        if (jobName == null || !jobName.contains("省监控")) {
            Toast.makeText(getContext(), "该工单当前环节不是省监控审核，无法回单", Toast.LENGTH_SHORT).show();
            appendLog("省监控回单失败：当前环节 [" + jobName + "] 不是省监控审核");
            return;
        }

        // 检查权限
        Session s = Session.get();
        String loginImei = s.shuyunAppImei;
        if (loginImei == null || loginImei.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录APP端获取权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PROVINCE_AUTH_IMEI.equalsIgnoreCase(loginImei)) {
            Toast.makeText(getContext(), "无回单权限，需联系开发者", Toast.LENGTH_LONG).show();
            return;
        }

        // 检查PC端登录
        if (s.shuyunPcToken != null && !s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
        } else if (callback != null) {
            pcToken = callback.getPcToken();
        }

        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录PC端", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确认对话框
        new AlertDialog.Builder(requireContext())
                .setTitle("省监控审核回单确认")
                .setMessage("确认对以下工单进行省监控审核归档？\n\n站点：" + selectedTask.station_name + "\n环节：" + jobName + "\n工单号：" + selectedTask.orderNum)
                .setPositiveButton("确认回单", (dialog, which) -> {
                    performProvinceMonitorAudit(selectedTask);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 执行省监控审核回单
     */
    private void performProvinceMonitorAudit(ShuyunApi.CountyTaskInfo task) {
        appendLog("开始省监控回单: " + task.station_name);

        new Thread(() -> {
            try {
                // 延期判断
                String delayResult = ShuyunApi.checkDelay(pcToken,
                    task.orderNum, task.jobInstId, task.relaType,
                    task.flowInstId, task.jobId, task.workInstId, task.flowId);

                String delayType = ShuyunApi.parseDelayResult(delayResult);
                String jobIdFromDelay = ShuyunApi.extractJobIdFromDelayResult(delayResult);

                // 尝试两种审核方式
                String result = "";
                boolean auditSuccess = false;

                if ("省监控审核".equals(delayType)) {
                    // 延期审核优先
                    result = ShuyunApi.submitProvinceDelayAudit(pcToken,
                        task.orderNum, task.jobInstId, task.flowInstId,
                        task.jobId, task.workInstId, task.flowId, jobIdFromDelay);

                    if (ShuyunApi.isSuccess(result)) {
                        auditSuccess = true;
                    } else {
                        // 延期审核失败，尝试普通审核
                        result = ShuyunApi.submitProvinceAudit(pcToken,
                            task.orderNum, task.jobInstId, task.flowInstId,
                            task.jobId, task.workInstId, task.flowId, jobIdFromDelay);
                        auditSuccess = ShuyunApi.isSuccess(result);
                    }
                } else {
                    // 普通审核优先
                    result = ShuyunApi.submitProvinceAudit(pcToken,
                        task.orderNum, task.jobInstId, task.flowInstId,
                        task.jobId, task.workInstId, task.flowId, jobIdFromDelay);

                    if (ShuyunApi.isSuccess(result)) {
                        auditSuccess = true;
                    } else {
                        // 普通审核失败，尝试延期审核
                        result = ShuyunApi.submitProvinceDelayAudit(pcToken,
                            task.orderNum, task.jobInstId, task.flowInstId,
                            task.jobId, task.workInstId, task.flowId, jobIdFromDelay);
                        auditSuccess = ShuyunApi.isSuccess(result);
                    }
                }

                if (auditSuccess) {
                    appendLog("✓ 省监控回单成功: " + task.station_name);
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "回单成功: " + task.station_name, Toast.LENGTH_SHORT).show();
                        selectedCityFinishedTask = null;
                        selectedCityTodoTask = null;
                        // 刷新列表
                        refreshCityFinishedList();
                    });
                } else {
                    appendLog("✗ 省监控回单失败: " + task.station_name);
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "回单失败: " + task.station_name, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                appendLog("省监控回单异常: " + e.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "回单异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 刷新市级已办列表
     */
    private void refreshCityFinishedList() {
        Session s = Session.get();
        if (s.shuyunPcToken != null && !s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
        }

        if (pcToken == null || pcToken.isEmpty()) {
            return;
        }

        String cityAreaCode = CITY_AREA_CODES[selectedCountyIndex];
        new Thread(() -> {
            try {
                String finishedJson = ShuyunApi.getCityFinishedList(pcToken, cityAreaCode);
                List<ShuyunApi.CountyTaskInfo> finishedList = ShuyunApi.parseCountyTaskList(finishedJson);
                updateCityFinishedList(finishedList);
            } catch (Exception e) {
                // 忽略
            }
        }).start();
    }

    /**
     * 更新市级已办列表显示（只显示省监控审核工单）
     * 原"市级待办"区域现在显示已办数据
     */
    private void updateCityTodoList(List<ShuyunApi.CountyTaskInfo> todoList) {
        // 保存列表数据（实际上是已办数据）
        cityTodoTaskList = todoList != null ? todoList : new ArrayList<>();

        mainHandler.post(() -> {
            if (cityTodoTaskList.isEmpty()) {
                tvCityTodoList.setText("暂无省监控工单");
                lvCityTodoList.setAdapter(null);
                return;
            }

            // 只显示省监控审核工单
            List<String> displayList = new ArrayList<>();
            List<ShuyunApi.CountyTaskInfo> provinceMonitorTasks = new ArrayList<>();

            for (ShuyunApi.CountyTaskInfo task : cityTodoTaskList) {
                if (task.jobName != null && task.jobName.contains("省监控")) {
                    provinceMonitorTasks.add(task);
                }
            }

            if (provinceMonitorTasks.isEmpty()) {
                tvCityTodoList.setText("暂无省监控工单");
                lvCityTodoList.setAdapter(null);
                return;
            }

            // 显示省监控工单前3条：站名 + 工单号 + 当前环节
            int count = Math.min(provinceMonitorTasks.size(), 3);
            for (int i = 0; i < count; i++) {
                ShuyunApi.CountyTaskInfo task = provinceMonitorTasks.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append(i + 1).append(". ").append(task.station_name);
                if (task.orderNum != null && !task.orderNum.isEmpty()) {
                    sb.append(" (").append(task.orderNum).append(")");
                }
                if (task.jobName != null && !task.jobName.isEmpty()) {
                    sb.append(" [").append(task.jobName).append("]");
                }
                displayList.add(sb.toString());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, displayList);
            lvCityTodoList.setAdapter(adapter);

            // 同时更新TextView：站名 + 工单号 + 当前环节（前3条）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                ShuyunApi.CountyTaskInfo task = provinceMonitorTasks.get(i);
                sb.append(i + 1).append(". ").append(task.station_name);
                if (task.orderNum != null && !task.orderNum.isEmpty()) {
                    sb.append(" (").append(task.orderNum).append(")");
                }
                if (task.jobName != null && !task.jobName.isEmpty()) {
                    sb.append(" [").append(task.jobName).append("]");
                }
                if (i < count - 1) {
                    sb.append("\n");
                }
            }
            tvCityTodoList.setText(sb.toString());
        });
    }

    /**
     * 更新市级已办列表显示（只显示省监控审核工单）
     */
    private void updateCityFinishedList(List<ShuyunApi.CountyTaskInfo> finishedList) {
        // 保存列表数据
        cityFinishedTaskList = finishedList != null ? finishedList : new ArrayList<>();

        mainHandler.post(() -> {
            if (cityFinishedTaskList.isEmpty()) {
                tvCityFinishedList.setText("暂无省监控工单");
                lvCityFinishedList.setAdapter(null);
                return;
            }

            // 只显示省监控审核工单
            List<String> displayList = new ArrayList<>();
            List<ShuyunApi.CountyTaskInfo> provinceMonitorTasks = new ArrayList<>();

            for (ShuyunApi.CountyTaskInfo task : cityFinishedTaskList) {
                if (task.jobName != null && task.jobName.contains("省监控")) {
                    provinceMonitorTasks.add(task);
                }
            }

            if (provinceMonitorTasks.isEmpty()) {
                tvCityFinishedList.setText("暂无省监控工单");
                lvCityFinishedList.setAdapter(null);
                return;
            }

            // 显示省监控工单
            for (int i = 0; i < provinceMonitorTasks.size(); i++) {
                ShuyunApi.CountyTaskInfo task = provinceMonitorTasks.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append(i + 1).append(". ").append(task.station_name);
                if (task.orderNum != null && !task.orderNum.isEmpty()) {
                    sb.append(" (").append(task.orderNum).append(")");
                }
                displayList.add(sb.toString());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, displayList);
            lvCityFinishedList.setAdapter(adapter);

            // 同时更新TextView（保留兼容性）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < provinceMonitorTasks.size(); i++) {
                ShuyunApi.CountyTaskInfo task = provinceMonitorTasks.get(i);
                sb.append(i + 1).append(". ").append(task.station_name);
                if (task.orderNum != null && !task.orderNum.isEmpty()) {
                    sb.append(" (").append(task.orderNum).append(")");
                }
                if (i < provinceMonitorTasks.size() - 1) {
                    sb.append("\n");
                }
            }
            tvCityFinishedList.setText(sb.toString());
        });
    }

    private void appendLog(String msg) {
        mainHandler.post(() -> {
            // 添加时间前缀
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String timeStr = sdf.format(new Date());
            String log = tvAuditLog.getText().toString();
            tvAuditLog.setText(log + "\n" + timeStr + " " + msg);
            svAuditLog.post(() -> svAuditLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    /**
     * 显示倒计时（等待期间实时显示剩余秒数，仅在日志中显示）
     * @param prefix 日志前缀（已被忽略）
     * @param totalMs 总等待毫秒数
     */
    private void showCountdown(String prefix, int totalMs) {
        // 静默等待，不输出倒计时日志
        try {
            Thread.sleep(totalMs);
        } catch (InterruptedException e) {
            // 被中断时清理
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopCountyAudit();
        stopCityAudit();
        stopProvinceAudit();
    }
}
