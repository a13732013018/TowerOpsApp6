package com.towerops.app.ui;

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
import androidx.fragment.app.Fragment;

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
    private TextView tvCountyStatus, tvAuditLog;
    private Button btnCountyAudit, btnStopCountyAudit;
    private Button btnCityAudit, btnStopCityAudit;
    private Spinner spinnerCounty;
    private ScrollView svAuditLog;

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
    private Thread countyThread;
    private Thread cityThread;
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
        initViews(view);
        setupListeners();
    }

    private void initViews(View view) {
        tvCountyStatus = view.findViewById(R.id.tvCountyStatus);
        tvAuditLog = view.findViewById(R.id.tvAuditLog);

        btnCountyAudit = view.findViewById(R.id.btnCountyAudit);
        btnStopCountyAudit = view.findViewById(R.id.btnStopCountyAudit);
        btnCityAudit = view.findViewById(R.id.btnCityAudit);
        btnStopCityAudit = view.findViewById(R.id.btnStopCityAudit);

        spinnerCounty = view.findViewById(R.id.spinnerCounty);
        svAuditLog = view.findViewById(R.id.svAuditLog);

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
    }

    private void updateStatus(String status) {
        tvCountyStatus.setText(status);
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
                        // 等待下次检查（45-105秒随机）
                        int sleepTime = (int) (Math.random() * 60000) + 45000;
                        Thread.sleep(sleepTime);
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
                            appendLog("等待 " + delay / 1000 + " 秒后审核: " + currentTask.station_name);
                        });

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

                        // 输出完整审核结果用于调试
                        appendLog("审核返回: " + (result != null ? result : "null"));

                        if (ShuyunApi.isSuccess(result)) {
                            appendLog("✓ 审核通过: " + task.station_name + " (" + task.orderNum + ")");
                        } else {
                            appendLog("✗ 审核失败: " + task.station_name);
                        }
                    }

                    // 本轮审核完成，等待下一轮
                    if (isCountyRunning) {
                        int sleepTime = (int) (Math.random() * 60000) + 45000;
                        appendLog("本轮审核完成，等待 " + sleepTime / 1000 + " 秒后下一轮...");
                        Thread.sleep(sleepTime);
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
                btnCountyAudit.setText("开始区县审核");
                btnStopCountyAudit.setEnabled(false);
                updateStatus("已停止");
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

        isCityRunning = true;
        btnCityAudit.setEnabled(false);
        btnCityAudit.setText("市级审核中");
        btnStopCityAudit.setEnabled(true);
        updateStatus("市级审核中...");

        cityThread = new Thread(() -> {
            while (isCityRunning) {
                try {
                    // 获取待审核工单列表
                    mainHandler.post(() -> updateStatus("获取市级工单中..."));
                    String jsonStr = ShuyunApi.getCityTaskList(pcToken, cityAreaCode);

                    List<ShuyunApi.CountyTaskInfo> taskList = ShuyunApi.parseCountyTaskList(jsonStr);

                    if (taskList.isEmpty()) {
                        appendLog("市级待审核工单为空");
                        mainHandler.post(() -> updateStatus("待审: 0"));
                        // 等待下次检查（50-100秒随机）
                        int sleepTime = (int) (Math.random() * 50000) + 50000;
                        Thread.sleep(sleepTime);
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
                            appendLog("等待 " + delay / 1000 + " 秒后审核: " + task.station_name);
                        });

                        Thread.sleep(delay);

                        if (!isCityRunning) break;

                        // 延期判断
                        String delayResult = ShuyunApi.checkDelay(pcToken,
                            task.orderNum, task.jobInstId, task.relaType,
                            task.flowInstId, task.jobId, task.workInstId, task.flowId);

                        String delayType = ShuyunApi.parseDelayResult(delayResult);

                        // 提交审核：两种都尝试，一种成功就OK
                        String result = "";
                        boolean auditSuccess = false;

                        if ("省监控审核".equals(delayType)) {
                            // 延期审核优先
                            appendLog("延期判断: 省监控审核，执行延期审核");
                            result = ShuyunApi.submitCityDelayAudit(pcToken,
                                task.orderNum, task.jobInstId, task.flowInstId,
                                task.jobId, task.workInstId, task.flowId, task.jobId);
                            appendLog("延期审核返回: " + (result != null ? result : "null"));

                            if (ShuyunApi.isSuccess(result)) {
                                auditSuccess = true;
                            } else {
                                // 延期审核失败，尝试普通审核
                                appendLog("延期审核失败，尝试普通审核");
                                result = ShuyunApi.submitCityAudit(pcToken,
                                    task.orderNum, task.jobInstId, task.flowInstId,
                                    task.jobId, task.workInstId, task.flowId, task.jobId);
                                appendLog("普通审核返回: " + (result != null ? result : "null"));
                                auditSuccess = ShuyunApi.isSuccess(result);
                            }
                        } else {
                            // 普通审核优先
                            appendLog("延期判断: 普通审核，执行普通审核");
                            result = ShuyunApi.submitCityAudit(pcToken,
                                task.orderNum, task.jobInstId, task.flowInstId,
                                task.jobId, task.workInstId, task.flowId, task.jobId);
                            appendLog("普通审核返回: " + (result != null ? result : "null"));

                            if (ShuyunApi.isSuccess(result)) {
                                auditSuccess = true;
                            } else {
                                // 普通审核失败，尝试延期审核
                                appendLog("普通审核失败，尝试延期审核");
                                result = ShuyunApi.submitCityDelayAudit(pcToken,
                                    task.orderNum, task.jobInstId, task.flowInstId,
                                    task.jobId, task.workInstId, task.flowId, task.jobId);
                                appendLog("延期审核返回: " + (result != null ? result : "null"));
                                auditSuccess = ShuyunApi.isSuccess(result);
                            }
                        }

                        // 最终结果
                        if (auditSuccess) {
                            appendLog("✓ 审核通过: " + task.station_name);
                        } else {
                            appendLog("✗ 审核失败: " + task.station_name);
                        }
                    }

                    // 本轮审核完成，获取已办列表后等待下一轮
                    if (isCityRunning) {
                        // 获取已办列表
                        String finishedJson = ShuyunApi.getCityFinishedList(pcToken, cityAreaCode);
                        List<ShuyunApi.CountyTaskInfo> finishedList = ShuyunApi.parseCountyTaskList(finishedJson);
                        appendLog("市级已办: " + finishedList.size() + " 条");

                        int sleepTime = (int) (Math.random() * 50000) + 50000;
                        appendLog("等待 " + sleepTime / 1000 + " 秒后下一轮...");
                        Thread.sleep(sleepTime);
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
                btnCityAudit.setText("开始市级审核");
                btnStopCityAudit.setEnabled(false);
                updateStatus("已停止");
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

    private void appendLog(String msg) {
        mainHandler.post(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String log = tvAuditLog.getText().toString();
            tvAuditLog.setText(log + "\n[" + time + "] " + msg);
            svAuditLog.post(() -> svAuditLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopCountyAudit();
        stopCityAudit();
    }
}
