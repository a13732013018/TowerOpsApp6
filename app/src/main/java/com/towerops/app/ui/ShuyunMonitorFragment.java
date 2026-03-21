package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.model.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 数运监控子Fragment - 对应"数运监控"Tab
 */
public class ShuyunMonitorFragment extends Fragment {

    private static final String TAG = "ShuyunMonitorFragment";

    // UI控件
    private TextView tvShuyunStatus, tvPendingCount, tvProcessingCount, tvLog, tvCurrentTime, tvLoginStatus;
    private TextView tvPcLoginStatus, tvAppLoginStatus;
    private CheckBox cbAutoAccept, cbAutoRevert;
    private Button btnStartShuyun, btnStopShuyun, btnLogin;
    private TabLayout tabLayoutShuyun;
    private RecyclerView rvPending, rvProcessing;
    private ScrollView svLog;
    private View tvEmpty;

    // 适配器
    private ShuyunAdapter pendingAdapter;
    private ShuyunAdapter processingAdapter;

    // 主线程Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 当前选中的Tab
    private int currentTab = 0;

    // 时间更新Handler
    private Handler timeUpdateHandler;
    private Runnable timeUpdateRunnable;

    // 监控状态
    private volatile boolean isRunning = false;
    private Thread monitorThread;

    // 登录状态
    private String pcToken = "";
    private String pcIp = "";
    private String appToken = "";
    private String appUserId = "";
    private int selectedAccountIndex = 0;
    private boolean isPcLoggedIn = false;
    private boolean isAppLoggedIn = false;

    // 接口回调
    private ShuyunMonitorCallback callback;

    public interface ShuyunMonitorCallback {
        void onLoginStatusChanged(boolean pcLoggedIn, boolean appLoggedIn);
        void onMonitorStatusChanged(boolean isRunning);
    }

    public void setCallback(ShuyunMonitorCallback callback) {
        this.callback = callback;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public String getPcToken() {
        return pcToken;
    }

    public String getAppToken() {
        return appToken;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public boolean isPcLoggedIn() {
        return isPcLoggedIn;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shuyun_monitor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupListeners();
        setupTimeUpdate();
    }

    private void initViews(View view) {
        tvShuyunStatus = view.findViewById(R.id.tvShuyunStatus);
        tvPendingCount = view.findViewById(R.id.tvPendingCount);
        tvProcessingCount = view.findViewById(R.id.tvProcessingCount);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvLoginStatus = view.findViewById(R.id.tvLoginStatus);
        tvPcLoginStatus = view.findViewById(R.id.tvPcLoginStatus);
        tvAppLoginStatus = view.findViewById(R.id.tvAppLoginStatus);

        cbAutoAccept = view.findViewById(R.id.cbAutoAccept);
        cbAutoRevert = view.findViewById(R.id.cbAutoRevert);

        btnStartShuyun = view.findViewById(R.id.btnStartShuyun);
        btnStopShuyun = view.findViewById(R.id.btnStopShuyun);
        btnLogin = view.findViewById(R.id.btnLogin);

        tabLayoutShuyun = view.findViewById(R.id.tabLayoutShuyun);

        rvPending = view.findViewById(R.id.rvPending);
        rvProcessing = view.findViewById(R.id.rvProcessing);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        svLog = view.findViewById(R.id.svLog);

        // 初始化RecyclerView
        rvPending.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProcessing.setLayoutManager(new LinearLayoutManager(getContext()));

        // 使用默认构造方法
        pendingAdapter = new ShuyunAdapter();
        processingAdapter = new ShuyunAdapter();

        pendingAdapter.setPendingList(true);
        processingAdapter.setPendingList(false);

        rvPending.setAdapter(pendingAdapter);
        rvProcessing.setAdapter(processingAdapter);

        // 设置点击事件
        pendingAdapter.setOnItemClickListener(new ShuyunAdapter.OnItemClickListener() {
            @Override
            public void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                acceptTask(item.id);
            }

            @Override
            public void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                revertTask(item.id);
            }
        });

        processingAdapter.setOnItemClickListener(new ShuyunAdapter.OnItemClickListener() {
            @Override
            public void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item) {}

            @Override
            public void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                revertTask(item.id);
            }
        });
    }

    private void setupListeners() {
        // Tab切换
        tabLayoutShuyun.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateTabContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 登录按钮
        btnLogin.setOnClickListener(v -> doLogin());

        // 启动/停止监控
        btnStartShuyun.setOnClickListener(v -> startMonitor());
        btnStopShuyun.setOnClickListener(v -> stopMonitor());
    }

    private void setupTimeUpdate() {
        timeUpdateHandler = new Handler(Looper.getMainLooper());
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                tvCurrentTime.setText(sdf.format(new Date()));
                timeUpdateHandler.postDelayed(this, 1000);
            }
        };
        timeUpdateHandler.post(timeUpdateRunnable);
    }

    private void updateTabContent() {
        if (currentTab == 0) {
            rvPending.setVisibility(View.VISIBLE);
            rvProcessing.setVisibility(View.GONE);
        } else {
            rvPending.setVisibility(View.GONE);
            rvProcessing.setVisibility(View.VISIBLE);
        }
    }

    private void doLogin() {
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        new Thread(() -> {
            try {
                // 使用loginDual进行PC+APP双端登录
                ShuyunApi.ShuyunDualLoginResult result = ShuyunApi.loginDual(selectedAccountIndex);

                if (result.success) {
                    pcToken = result.pcToken;
                    pcIp = result.pcIp;
                    appToken = result.appToken;
                    appUserId = result.appUserId;
                    isPcLoggedIn = result.pcLoginSuccess;
                    isAppLoggedIn = result.appLoginSuccess;

                    // 保存到Session
                    Session s = Session.get();
                    s.shuyunPcToken = pcToken;
                    s.shuyunPcIp = pcIp;
                    s.shuyunAppToken = appToken;
                    s.shuyunAppUserId = appUserId;

                    mainHandler.post(() -> {
                        updateLoginStatus();
                        Toast.makeText(getContext(), "登录成功", Toast.LENGTH_SHORT).show();
                        btnLogin.setEnabled(true);
                        btnLogin.setText("同时登录PC+APP");

                        if (callback != null) {
                            callback.onLoginStatusChanged(isPcLoggedIn, isAppLoggedIn);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "登录失败: " + result.errorMsg, Toast.LENGTH_SHORT).show();
                        btnLogin.setEnabled(true);
                        btnLogin.setText("同时登录PC+APP");
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "登录异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnLogin.setEnabled(true);
                    btnLogin.setText("同时登录PC+APP");
                });
            }
        }).start();
    }

    private void updateLoginStatus() {
        tvPcLoginStatus.setText(isPcLoggedIn ? "已登录" : "未登录");
        tvPcLoginStatus.setTextColor(isPcLoggedIn ? 0xFF10B981 : 0xFFEF4444);

        tvAppLoginStatus.setText(isAppLoggedIn ? "已登录" : "未登录");
        tvAppLoginStatus.setTextColor(isAppLoggedIn ? 0xFF10B981 : 0xFFEF4444);

        tvLoginStatus.setText(isPcLoggedIn && isAppLoggedIn ? "已登录" : "未登录");
        tvLoginStatus.setTextColor(isPcLoggedIn && isAppLoggedIn ? 0xFF10B981 : 0xFFEF4444);
    }

    private void startMonitor() {
        if (!isAppLoggedIn || appToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRunning) return;

        isRunning = true;
        btnStartShuyun.setEnabled(false);
        btnStopShuyun.setEnabled(true);
        tvShuyunStatus.setText("监控运行中");
        tvShuyunStatus.setTextColor(0xFF10B981);

        appendLog("监控已启动");

        if (callback != null) {
            callback.onMonitorStatusChanged(true);
        }

        monitorThread = new Thread(() -> {
            while (isRunning) {
                try {
                    mainHandler.post(() -> tvShuyunStatus.setText("监控运行中..."));

                    // 自动接单
                    if (cbAutoAccept.isChecked()) {
                        checkAndAutoAccept();
                    }

                    // 自动回单
                    if (cbAutoRevert.isChecked()) {
                        checkAndAutoRevert();
                    }

                    // 刷新列表
                    refreshTaskList();

                    // 等待下次轮询 (30-60秒)
                    int sleepTime = (int) (Math.random() * 30000) + 30000;
                    Thread.sleep(sleepTime);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    appendLog("监控异常: " + e.getMessage());
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

            mainHandler.post(() -> {
                isRunning = false;
                btnStartShuyun.setEnabled(true);
                btnStopShuyun.setEnabled(false);
                tvShuyunStatus.setText("监控已停止");
                tvShuyunStatus.setTextColor(0xFF6B7280);

                if (callback != null) {
                    callback.onMonitorStatusChanged(false);
                }
            });
        });
        monitorThread.start();
    }

    private void stopMonitor() {
        isRunning = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    private void checkAndAutoAccept() {
        appendLog("检查待接单...");
        try {
            String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
            List<ShuyunApi.ShuyunTaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            if (tasks.isEmpty()) {
                appendLog("待接单为空");
                return;
            }

            // 筛选待处理的工单
            int pendingCount = 0;
            for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                if ("待处理".equals(task.status) || "0".equals(task.status)) {
                    pendingCount++;
                }
            }

            appendLog("待处理工单: " + pendingCount);

            if (pendingCount > 0 && pendingCount < 10) {
                // 仿生延迟
                int delay = (int) (Math.random() * 3500) + 2500;
                Thread.sleep(delay);

                // 接单
                for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                    if ("待处理".equals(task.status) || "0".equals(task.status)) {
                        acceptTask(task.id);
                    }
                }
            }
        } catch (Exception e) {
            appendLog("接单异常: " + e.getMessage());
        }
    }

    private void acceptTask(String taskId) {
        try {
            String result = ShuyunApi.acceptTask(appToken, appUserId, taskId);
            if (ShuyunApi.isSuccess(result)) {
                appendLog("✓ 接单成功: " + taskId);
            } else {
                appendLog("✗ 接单失败: " + taskId);
            }
        } catch (Exception e) {
            appendLog("接单异常: " + e.getMessage());
        }
    }

    private void checkAndAutoRevert() {
        appendLog("检查处理中工单...");
        try {
            String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
            List<ShuyunApi.ShuyunTaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            if (tasks.isEmpty()) {
                appendLog("处理中为空");
                return;
            }

            // 筛选处理中的工单
            for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                if ("处理中".equals(task.status) || "1".equals(task.status)) {
                    // 简单处理：直接回单
                    // 实际应根据创建时间判断是否满足回单条件
                    revertTask(task.id);
                }
            }
        } catch (Exception e) {
            appendLog("回单异常: " + e.getMessage());
        }
    }

    private void revertTask(String taskId) {
        try {
            String result = ShuyunApi.revertTask(appToken, appUserId, taskId, "自动回单");
            if (ShuyunApi.isSuccess(result)) {
                appendLog("✓ 回单成功: " + taskId);
            } else {
                appendLog("✗ 回单失败: " + taskId);
            }
        } catch (Exception e) {
            appendLog("回单异常: " + e.getMessage());
        }
    }

    private void refreshTaskList() {
        try {
            String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
            List<ShuyunApi.ShuyunTaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            // 分离待处理和处理中
            java.util.List<ShuyunApi.ShuyunTaskInfo> pending = new java.util.ArrayList<>();
            java.util.List<ShuyunApi.ShuyunTaskInfo> processing = new java.util.ArrayList<>();

            for (ShuyunApi.ShuyunTaskInfo task : tasks) {
                if ("待处理".equals(task.status) || "0".equals(task.status)) {
                    pending.add(task);
                } else if ("处理中".equals(task.status) || "1".equals(task.status)) {
                    processing.add(task);
                }
            }

            pendingAdapter.setData(pending);
            processingAdapter.setData(processing);

            mainHandler.post(() -> {
                tvPendingCount.setText("待处理: " + pending.size());
                tvProcessingCount.setText("处理中: " + processing.size());

                if (pending.isEmpty() && processing.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            });
        } catch (Exception e) {
            appendLog("刷新列表异常: " + e.getMessage());
        }
    }

    private void appendLog(String msg) {
        mainHandler.post(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String log = tvLog.getText().toString();
            tvLog.setText(log + "\n[" + time + "] " + msg);
            svLog.post(() -> svLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timeUpdateHandler != null && timeUpdateRunnable != null) {
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        }
        stopMonitor();
    }
}
