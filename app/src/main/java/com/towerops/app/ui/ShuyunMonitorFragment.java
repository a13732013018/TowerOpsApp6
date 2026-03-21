package com.towerops.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private Button btnStartShuyun, btnStopShuyun, btnLogin, btnRefreshCaptcha;
    private ImageView ivAutoAcceptInfo, ivAutoRevertInfo, ivCaptcha;
    private EditText etImgcode;
    private TabLayout tabLayoutShuyun;
    private RecyclerView rvPending, rvProcessing;
    private ScrollView svLog;
    private View tvEmpty;
    private Spinner spinnerAccount;

    // 验证码相关
    private String currentPcIp = "";
    private byte[] captchaImageBytes = null;

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
        btnRefreshCaptcha = view.findViewById(R.id.btnRefreshCaptcha);

        ivAutoAcceptInfo = view.findViewById(R.id.ivAutoAcceptInfo);
        ivAutoRevertInfo = view.findViewById(R.id.ivAutoRevertInfo);
        ivCaptcha = view.findViewById(R.id.ivCaptcha);

        etImgcode = view.findViewById(R.id.etImgcode);

        tabLayoutShuyun = view.findViewById(R.id.tabLayoutShuyun);

        rvPending = view.findViewById(R.id.rvPending);
        rvProcessing = view.findViewById(R.id.rvProcessing);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        svLog = view.findViewById(R.id.svLog);

        spinnerAccount = view.findViewById(R.id.spinnerAccount);

        // 初始化RecyclerView
        rvPending.setLayoutManager(new LinearLayoutManager(getContext()));
        rvProcessing.setLayoutManager(new LinearLayoutManager(getContext()));

        pendingAdapter = new ShuyunAdapter(getContext());
        processingAdapter = new ShuyunAdapter(getContext());

        rvPending.setAdapter(pendingAdapter);
        rvProcessing.setAdapter(processingAdapter);

        // 加载保存的账号
        loadAccounts();
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

        // 刷新验证码
        btnRefreshCaptcha.setOnClickListener(v -> refreshCaptcha());

        // 验证码输入
        etImgcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    // 自动尝试登录
                    doLogin();
                }
            }
        });

        // 启动/停止监控
        btnStartShuyun.setOnClickListener(v -> startMonitor());
        btnStopShuyun.setOnClickListener(v -> stopMonitor());

        // 信息提示
        ivAutoAcceptInfo.setOnClickListener(v -> showInfo("自动接单", "当待接单数量少于10时自动接单，延迟2.5-6秒"));
        ivAutoRevertInfo.setOnClickListener(v -> showInfo("自动回单", "工单创建300-720分钟后自动回单，延迟5-12秒"));
    }

    private void loadAccounts() {
        Session s = Session.get();
        String accountsStr = s.shuyunAccounts;

        String[] accounts;
        if (accountsStr != null && !accountsStr.isEmpty()) {
            accounts = accountsStr.split(",");
        } else {
            accounts = new String[]{"默认账号"};
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, accounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAccount.setAdapter(adapter);

        spinnerAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAccountIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
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

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void doLogin() {
        String code = etImgcode.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(getContext(), "请输入验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        new Thread(() -> {
            try {
                // PC端登录
                String result = ShuyunApi.pcLogin(code);
                if (ShuyunApi.isSuccess(result)) {
                    pcToken = ShuyunApi.parsePcToken(result);
                    currentPcIp = ShuyunApi.parsePcIp(result);
                    isPcLoggedIn = true;
                    mainHandler.post(this::refreshCaptcha);
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(getContext(), "PC登录失败: " + result, Toast.LENGTH_SHORT).show();
                        btnLogin.setEnabled(true);
                        btnLogin.setText("同时登录PC+APP");
                    });
                    return;
                }

                // APP端登录
                String appLoginResult = ShuyunApi.appLogin(pcToken);
                if (ShuyunApi.isSuccess(appLoginResult)) {
                    appToken = ShuyunApi.parseAppToken(appLoginResult);
                    appUserId = ShuyunApi.parseAppUserId(appLoginResult);
                    isAppLoggedIn = true;
                }

                mainHandler.post(() -> {
                    updateLoginStatus();
                    Toast.makeText(getContext(), "登录成功", Toast.LENGTH_SHORT).show();
                    btnLogin.setEnabled(true);
                    btnLogin.setText("同时登录PC+APP");

                    if (callback != null) {
                        callback.onLoginStatusChanged(isPcLoggedIn, isAppLoggedIn);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "登录异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnLogin.setEnabled(true);
                    btnLogin.setText("同时登录PC+APP");
                });
            }
        }).start();
    }

    private void refreshCaptcha() {
        new Thread(() -> {
            try {
                captchaImageBytes = ShuyunApi.getCaptchaImage(currentPcIp);
                if (captchaImageBytes != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(captchaImageBytes, 0, captchaImageBytes.length);
                    mainHandler.post(() -> ivCaptcha.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                appendLog("刷新验证码失败: " + e.getMessage());
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
        if (!isPcLoggedIn || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录PC端", Toast.LENGTH_SHORT).show();
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
            String jsonStr = ShuyunApi.getPendingTaskList(pcToken);
            List<ShuyunApi.TaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            if (tasks.isEmpty()) {
                appendLog("待接单为空");
                return;
            }

            appendLog("待接单: " + tasks.size());

            if (tasks.size() < 10) {
                // 仿生延迟
                int delay = (int) (Math.random() * 3500) + 2500;
                Thread.sleep(delay);

                // 接单
                for (ShuyunApi.TaskInfo task : tasks) {
                    String result = ShuyunApi.acceptTask(pcToken, task.orderId);
                    if (ShuyunApi.isSuccess(result)) {
                        appendLog("✓ 接单成功: " + task.stationName);
                    } else {
                        appendLog("✗ 接单失败: " + task.stationName);
                    }

                    // 每单间隔
                    int interval = (int) (Math.random() * 2000) + 500;
                    Thread.sleep(interval);
                }
            }
        } catch (Exception e) {
            appendLog("接单异常: " + e.getMessage());
        }
    }

    private void checkAndAutoRevert() {
        appendLog("检查处理中工单...");
        try {
            String jsonStr = ShuyunApi.getProcessingTaskList(pcToken);
            List<ShuyunApi.TaskInfo> tasks = ShuyunApi.parseTaskList(jsonStr);

            if (tasks.isEmpty()) {
                appendLog("处理中为空");
                return;
            }

            long now = System.currentTimeMillis();
            for (ShuyunApi.TaskInfo task : tasks) {
                // 判断是否需要回单 (300-720分钟)
                if (task.createTime > 0) {
                    long minutes = (now - task.createTime) / 60000;
                    if (minutes >= 300 && minutes <= 720) {
                        // 仿生延迟
                        int delay = (int) (Math.random() * 7000) + 5000;
                        Thread.sleep(delay);

                        // 回单
                        String result = ShuyunApi.revertTask(pcToken, task.orderId);
                        if (ShuyunApi.isSuccess(result)) {
                            appendLog("✓ 回单成功: " + task.stationName);
                        } else {
                            appendLog("✗ 回单失败: " + task.stationName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            appendLog("回单异常: " + e.getMessage());
        }
    }

    private void refreshTaskList() {
        try {
            // 待处理
            String jsonStr1 = ShuyunApi.getPendingTaskList(pcToken);
            List<ShuyunApi.TaskInfo> pending = ShuyunApi.parseTaskList(jsonStr1);
            pendingAdapter.setData(pending);

            // 处理中
            String jsonStr2 = ShuyunApi.getProcessingTaskList(pcToken);
            List<ShuyunApi.TaskInfo> processing = ShuyunApi.parseTaskList(jsonStr2);
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
