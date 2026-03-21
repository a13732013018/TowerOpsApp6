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
    private Spinner spinnerCounty;
    private ScrollView svAuditLog;

    // 区县经理代号
    private static final String[] COUNTY_CODES = {"36745", "31950"};
    private static final String[] COUNTY_NAMES = {"平阳(36745)", "泰顺(31950)"};

    // 主线程Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 审核状态
    private volatile boolean isCountyRunning = false;
    private Thread countyThread;
    private int selectedCountyIndex = 0;

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

        spinnerCounty = view.findViewById(R.id.spinnerCounty);
        svAuditLog = view.findViewById(R.id.svAuditLog);

        // 初始化区县选择器
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

        // 开始审核
        btnCountyAudit.setOnClickListener(v -> startCountyAudit());

        // 停止审核
        btnStopCountyAudit.setOnClickListener(v -> stopCountyAudit());
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

                        if (ShuyunApi.isSuccess(result)) {
                            appendLog("✓ 审核通过: " + task.station_name + " (" + task.orderNum + ")");
                        } else {
                            appendLog("✗ 审核失败: " + task.station_name + ", 返回: " + result);
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
    }
}
