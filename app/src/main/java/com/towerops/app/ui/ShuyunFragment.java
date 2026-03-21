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
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
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
 * 数运工单Fragment —— 对应数运系统的工单监测界面
 */
public class ShuyunFragment extends Fragment {

    private static final String TAG = "ShuyunFragment";

    // UI控件
    private TextView tvShuyunStatus, tvPendingCount, tvProcessingCount, tvLog, tvCurrentTime, tvLoginStatus;
    private TextView tvPcLoginStatus, tvAppLoginStatus;
    private CheckBox cbAutoAccept, cbAutoRevert;
    private Button btnStartShuyun, btnStopShuyun, btnLogin;
    private ImageView ivAutoAcceptInfo, ivAutoRevertInfo;
    private TabLayout tabLayoutShuyun;
    private RecyclerView rvPending, rvProcessing;
    private ScrollView svLog;
    private View tvEmpty;
    private Spinner spinnerAccount;

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
    private String appToken = "";
    private String appUserId = "";
    private int selectedAccountIndex = 0; // 选中的账号索引
    private boolean isPcLoggedIn = false;
    private boolean isAppLoggedIn = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shuyun, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupAccountSpinner();
        setupRecyclerViews();
        setupListeners();
        loadConfig();
        startTimeUpdate();
    }

    private void startTimeUpdate() {
        timeUpdateHandler = new Handler(Looper.getMainLooper());
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTime();
                timeUpdateHandler.postDelayed(this, 1000);
            }
        };
        timeUpdateHandler.post(timeUpdateRunnable);
    }

    private void stopTimeUpdate() {
        if (timeUpdateHandler != null && timeUpdateRunnable != null) {
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        }
    }

    private void updateCurrentTime() {
        if (tvCurrentTime != null) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            tvCurrentTime.setText(time);
        }
    }

    private void bindViews(View view) {
        tvShuyunStatus = view.findViewById(R.id.tvShuyunStatus);
        tvPendingCount = view.findViewById(R.id.tvPendingCount);
        tvProcessingCount = view.findViewById(R.id.tvProcessingCount);
        tvLog = view.findViewById(R.id.tvLog);
        cbAutoAccept = view.findViewById(R.id.cbAutoAccept);
        cbAutoRevert = view.findViewById(R.id.cbAutoRevert);
        btnStartShuyun = view.findViewById(R.id.btnStartShuyun);
        btnStopShuyun = view.findViewById(R.id.btnStopShuyun);
        btnLogin = view.findViewById(R.id.btnLogin);
        tabLayoutShuyun = view.findViewById(R.id.tabLayoutShuyun);
        rvPending = view.findViewById(R.id.rvPending);
        rvProcessing = view.findViewById(R.id.rvProcessing);
        svLog = view.findViewById(R.id.svLog);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        ivAutoAcceptInfo = view.findViewById(R.id.ivAutoAcceptInfo);
        ivAutoRevertInfo = view.findViewById(R.id.ivAutoRevertInfo);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvLoginStatus = view.findViewById(R.id.tvLoginStatus);
        tvPcLoginStatus = view.findViewById(R.id.tvPcLoginStatus);
        tvAppLoginStatus = view.findViewById(R.id.tvAppLoginStatus);
        spinnerAccount = view.findViewById(R.id.spinnerAccount);
    }

    private void setupAccountSpinner() {
        // 账号数组
        String[] accounts = new String[]{
            "账号1: 13732013018",
            "账号2: 15858734252"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            accounts
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAccount.setAdapter(adapter);

        spinnerAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAccountIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedAccountIndex = 0;
            }
        });
    }

    private void setupRecyclerViews() {
        // 待处理列表
        rvPending.setLayoutManager(new LinearLayoutManager(getContext()));
        pendingAdapter = new ShuyunAdapter();
        pendingAdapter.setPendingList(true);
        pendingAdapter.setOnItemClickListener(new ShuyunAdapter.OnItemClickListener() {
            @Override
            public void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                manualAccept(item);
            }

            @Override
            public void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                // 待处理列表不显示回单按钮
            }
        });
        rvPending.setAdapter(pendingAdapter);

        // 处理中列表
        rvProcessing.setLayoutManager(new LinearLayoutManager(getContext()));
        processingAdapter = new ShuyunAdapter();
        processingAdapter.setPendingList(false);
        processingAdapter.setOnItemClickListener(new ShuyunAdapter.OnItemClickListener() {
            @Override
            public void onAcceptClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                // 处理中列表不显示接单按钮
            }

            @Override
            public void onRevertClick(int position, ShuyunApi.ShuyunTaskInfo item) {
                manualRevert(item);
            }
        });
        rvProcessing.setAdapter(processingAdapter);
    }

    private void setupListeners() {
        // 登录按钮
        btnLogin.setOnClickListener(v -> doLogin());

        // Tab切换
        tabLayoutShuyun.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                if (currentTab == 0) {
                    rvPending.setVisibility(View.VISIBLE);
                    rvProcessing.setVisibility(View.GONE);
                } else {
                    rvPending.setVisibility(View.GONE);
                    rvProcessing.setVisibility(View.VISIBLE);
                }
                updateEmptyState();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 启动监控
        btnStartShuyun.setOnClickListener(v -> startMonitor());

        // 停止监控
        btnStopShuyun.setOnClickListener(v -> stopMonitor());

        // 配置变更监听
        cbAutoAccept.setOnCheckedChangeListener((buttonView, isChecked) -> saveConfig());
        cbAutoRevert.setOnCheckedChangeListener((buttonView, isChecked) -> saveConfig());

        // 提示图标点击事件
        ivAutoAcceptInfo.setOnClickListener(v -> showAutoAcceptInfo());
        ivAutoRevertInfo.setOnClickListener(v -> showAutoRevertInfo());
    }

    private void showAutoAcceptInfo() {
        new AlertDialog.Builder(requireContext())
                .setTitle("自动接单说明")
                .setMessage("开启后，系统会自动监测待处理工单列表，当有待处理工单时，自动执行接单操作。\n\n" +
                        "• 接单延迟：2-6秒随机延迟，模拟人工操作\n" +
                        "• 防并发：使用网络锁确保操作安全\n" +
                        "• 仅接取当前账号可处理的工单")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showAutoRevertInfo() {
        new AlertDialog.Builder(requireContext())
                .setTitle("自动回单说明")
                .setMessage("开启后，系统会自动监测处理中工单列表，根据工单创建时间自动执行回单操作。\n\n" +
                        "• 回单条件：创建时间超过一定时长\n" +
                        "• 回单延迟：5-12秒随机延迟\n" +
                        "• 防并发：使用操作时序锁确保安全")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void loadConfig() {
        Session s = Session.get();
        if (!s.shuyunConfig.isEmpty()) {
            String[] cfg = s.shuyunConfig.split("\u0001", -1);
            if (cfg.length >= 2) {
                cbAutoAccept.setChecked("true".equalsIgnoreCase(cfg[0]));
                cbAutoRevert.setChecked("true".equalsIgnoreCase(cfg[1]));
            }
        }

        // 恢复APP登录状态
        if (!s.shuyunAppToken.isEmpty()) {
            appToken = s.shuyunAppToken;
            appUserId = s.shuyunAppUserId;
            isAppLoggedIn = true;
            tvAppLoginStatus.setText("已登录");
            tvAppLoginStatus.setTextColor(0xFF10B981); // 绿色
        }

        // 恢复PC登录状态
        if (!s.shuyunPcToken.isEmpty()) {
            pcToken = s.shuyunPcToken;
            isPcLoggedIn = true;
            tvPcLoginStatus.setText("已登录");
            tvPcLoginStatus.setTextColor(0xFF10B981); // 绿色
        }

        // 更新整体状态
        if (isAppLoggedIn) {
            btnLogin.setEnabled(false);
            btnLogin.setText("已登录");
            tvLoginStatus.setText("已登录");
            tvLoginStatus.setTextColor(0xFF10B981); // 绿色
            appendLog("已恢复登录状态");
        }
    }

    private void saveConfig() {
        Session s = Session.get();
        String accept = cbAutoAccept.isChecked() ? "true" : "false";
        String revert = cbAutoRevert.isChecked() ? "true" : "false";
        s.shuyunConfig = accept + "\u0001" + revert;
    }

    /**
     * 执行登录 - 同时登录PC端和APP端
     */
    private void doLogin() {
        if (isAppLoggedIn) {
            Toast.makeText(getContext(), "已登录，请勿重复登录", Toast.LENGTH_SHORT).show();
            return;
        }

        appendLog("正在同时登录PC端和APP端，使用账号" + (selectedAccountIndex + 1) + "...");

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        new Thread(() -> {
            // 同时登录PC端和APP端
            ShuyunApi.ShuyunDualLoginResult result = ShuyunApi.loginDual(selectedAccountIndex);

            // 保存到Session
            Session s = Session.get();
            s.shuyunAppToken = result.appToken;
            s.shuyunAppUserId = result.appUserId;
            s.shuyunPcToken = result.pcToken;
            s.shuyunPcIp = result.pcIp;

            mainHandler.post(() -> {
                // 更新APP端状态
                if (result.appLoginSuccess) {
                    appToken = result.appToken;
                    appUserId = result.appUserId;
                    isAppLoggedIn = true;
                    tvAppLoginStatus.setText("已登录");
                    tvAppLoginStatus.setTextColor(0xFF10B981); // 绿色
                    appendLog("APP端登录成功！Token: " + result.appToken.substring(0, Math.min(15, result.appToken.length())) + "...");
                } else {
                    tvAppLoginStatus.setText("登录失败");
                    tvAppLoginStatus.setTextColor(0xFFEF4444); // 红色
                    appendLog("APP端登录失败: " + result.errorMsg);
                }

                // 更新PC端状态
                if (result.pcLoginSuccess) {
                    pcToken = result.pcToken;
                    isPcLoggedIn = true;
                    tvPcLoginStatus.setText("已登录");
                    tvPcLoginStatus.setTextColor(0xFF10B981); // 绿色
                    appendLog("PC端登录成功！Token: " + result.pcToken.substring(0, Math.min(15, result.pcToken.length())) + "...");
                } else {
                    tvPcLoginStatus.setText("未登录(需验证码)");
                    tvPcLoginStatus.setTextColor(0xFFF59E0B); // 橙色
                    appendLog("PC端登录: " + (result.pcToken.isEmpty() ? "需要图形验证码" : result.pcToken));
                }

                // 更新整体状态
                if (isAppLoggedIn) {
                    btnLogin.setEnabled(false);
                    btnLogin.setText("已登录");
                    tvLoginStatus.setText("已登录");
                    tvLoginStatus.setTextColor(0xFF10B981); // 绿色
                    Toast.makeText(getContext(), "APP端登录成功", Toast.LENGTH_SHORT).show();
                } else {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("重新登录");
                    tvLoginStatus.setText("登录失败");
                    tvLoginStatus.setTextColor(0xFFEF4444); // 红色
                    Toast.makeText(getContext(), "APP端登录失败: " + result.errorMsg, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void startMonitor() {
        // 检查登录状态 - 需要APP端登录
        if (!isAppLoggedIn) {
            Toast.makeText(getContext(), "请先登录数运账号", Toast.LENGTH_SHORT).show();
            appendLog("请先登录数运账号");
            return;
        }

        if (isRunning) {
            Toast.makeText(getContext(), "监控已在运行中", Toast.LENGTH_SHORT).show();
            return;
        }

        saveConfig();
        startMonitorTask();
    }

    private void startMonitorTask() {
        isRunning = true;

        btnStartShuyun.setEnabled(false);
        btnStartShuyun.setText("监控中");
        btnStopShuyun.setEnabled(true);

        tvShuyunStatus.setText("数运监控运行中");
        appendLog("数运监控已启动");

        // 启动监控线程
        monitorThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // 检查是否需要自动接单
                    if (cbAutoAccept.isChecked()) {
                        checkAndAutoAccept();
                    }

                    // 检查是否需要自动回单
                    if (cbAutoRevert.isChecked()) {
                        checkAndAutoRevert();
                    }

                    // 刷新工单列表
                    refreshTaskList();

                    // 等待下次检查（60秒）
                    Thread.sleep(60000);
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
        });
        monitorThread.start();
    }

    private void checkAndAutoAccept() {
        // TODO: 根据实际接口实现自动接单逻辑
        appendLog("检查待处理工单...");
    }

    private void checkAndAutoRevert() {
        // TODO: 根据实际接口实现自动回单逻辑
        appendLog("检查处理中工单...");
    }

    private void refreshTaskList() {
        if (appToken.isEmpty() || appUserId.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                String jsonStr = ShuyunApi.getTaskList(appToken, appUserId);
                List<ShuyunApi.ShuyunTaskInfo> pendingList = ShuyunApi.parseTaskList(jsonStr);

                // 分离待处理和处理中
                // TODO: 根据实际状态字段分类

                mainHandler.post(() -> {
                    pendingAdapter.setData(pendingList);
                    tvPendingCount.setText("待处理: " + pendingList.size());
                    updateEmptyState();
                });
            } catch (Exception e) {
                appendLog("刷新工单列表失败: " + e.getMessage());
            }
        }).start();
    }

    private void stopMonitor() {
        isRunning = false;

        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }

        btnStartShuyun.setEnabled(true);
        btnStartShuyun.setText("启动监控");
        btnStopShuyun.setEnabled(false);

        tvShuyunStatus.setText("数运监控已停止");
        appendLog("数运监控已停止");
    }

    private void manualAccept(ShuyunApi.ShuyunTaskInfo task) {
        if (appToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            appendLog("手动接单: " + task.siteName);
            String result = ShuyunApi.acceptTask(appToken, appUserId, task.id);
            boolean success = ShuyunApi.isSuccess(result);

            mainHandler.post(() -> {
                String msg = success ? "接单成功" : "接单失败";
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                appendLog(msg + ": " + task.siteName);
            });
        }).start();
    }

    private void manualRevert(ShuyunApi.ShuyunTaskInfo task) {
        if (appToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            appendLog("手动回单: " + task.siteName);
            String result = ShuyunApi.revertTask(appToken, appUserId, task.id, "处理完成");
            boolean success = ShuyunApi.isSuccess(result);

            mainHandler.post(() -> {
                String msg = success ? "回单成功" : "回单失败";
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                appendLog(msg + ": " + task.siteName);
            });
        }).start();
    }

    private void updateEmptyState() {
        boolean isEmpty;
        if (currentTab == 0) {
            isEmpty = pendingAdapter.getItemCount() == 0;
        } else {
            isEmpty = processingAdapter.getItemCount() == 0;
        }
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logLine = "[" + time + "] " + message + "\n";

        mainHandler.post(() -> {
            tvLog.append(logLine);
            // 自动滚动到底部
            svLog.post(() -> svLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopMonitor();
        stopTimeUpdate();
    }
}
