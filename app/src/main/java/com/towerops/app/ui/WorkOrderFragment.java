package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.TowerLoginApi;
import com.towerops.app.model.Session;
import com.towerops.app.model.WorkOrder;

import java.util.List;

/**
 * 工单监控Fragment
 * 包含：配置控制面板（开关+按钮+阈值输入）、4A登录区、排序工具栏、工单列表
 */
public class WorkOrderFragment extends Fragment {

    private WorkOrderAdapter adapter;
    private RecyclerView recyclerView;

    // ===== 配置区控件 =====
    private CheckBox cbAutoFeedback;
    private CheckBox cbAutoAccept;
    private CheckBox cbAutoRevert;
    private Button   btnStartMonitor;
    private Button   btnStopMonitor;
    private EditText etIntervalMin;
    private EditText etIntervalMax;
    private EditText etFeedbackMin;
    private EditText etFeedbackMax;
    private EditText etAcceptMin;
    private EditText etAcceptMax;

    // ===== 4A登录区控件 =====
    private EditText et4aUsername;
    private EditText et4aPassword;
    private EditText et4aMsgCode;
    private Button   btn4aSendCode;
    private Button   btn4aLogin;
    private TextView tv4aStatus;
    private View     layout4aCodeRow;

    // 4A登录状态
    private TowerLoginApi towerLoginApi;
    private String        msgId;          // refreshMsg 返回的 msgId
    private boolean       codeSent = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static WorkOrderFragment newInstance() {
        return new WorkOrderFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_work_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定配置区控件
        cbAutoFeedback  = view.findViewById(R.id.cbAutoFeedback);
        cbAutoAccept    = view.findViewById(R.id.cbAutoAccept);
        cbAutoRevert    = view.findViewById(R.id.cbAutoRevert);
        btnStartMonitor = view.findViewById(R.id.btnStartMonitor);
        btnStopMonitor  = view.findViewById(R.id.btnStopMonitor);
        etIntervalMin   = view.findViewById(R.id.etIntervalMin);
        etIntervalMax   = view.findViewById(R.id.etIntervalMax);
        etFeedbackMin   = view.findViewById(R.id.etFeedbackMin);
        etFeedbackMax   = view.findViewById(R.id.etFeedbackMax);
        etAcceptMin     = view.findViewById(R.id.etAcceptMin);
        etAcceptMax     = view.findViewById(R.id.etAcceptMax);

        // 绑定4A登录控件
        et4aUsername    = view.findViewById(R.id.et4aUsername);
        et4aPassword    = view.findViewById(R.id.et4aPassword);
        et4aMsgCode     = view.findViewById(R.id.et4aMsgCode);
        btn4aSendCode   = view.findViewById(R.id.btn4aSendCode);
        btn4aLogin      = view.findViewById(R.id.btn4aLogin);
        tv4aStatus      = view.findViewById(R.id.tv4aStatus);
        layout4aCodeRow = view.findViewById(R.id.layout4aCodeRow);

        // 初始化登录API
        towerLoginApi = new TowerLoginApi();

        // 恢复已保存的4A登录状态
        Session s = Session.get();
        if (s.tower4aSessionCookie != null && !s.tower4aSessionCookie.isEmpty()) {
            update4aStatus(true);
        }

        // 设置按钮监听
        btn4aSendCode.setOnClickListener(v -> doSendCode());
        btn4aLogin.setOnClickListener(v    -> doConfirmLogin());

        // 绑定列表
        recyclerView = view.findViewById(R.id.recyclerWorkOrders);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new WorkOrderAdapter();
            recyclerView.setAdapter(adapter);
        }
        setupSortButtons(view);
    }

    // ─── 4A登录流程 ──────────────────────────────────────────────────────────

    /**
     * 点击"获取验证码"：先 initLogin → doPrevLogin → refreshMsg
     */
    private void doSendCode() {
        String username = et4aUsername.getText().toString().trim();
        String password = et4aPassword.getText().toString().trim();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "请输入4A账号和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        btn4aSendCode.setEnabled(false);
        btn4aSendCode.setText("请稍候...");
        tv4aStatus.setText("登录中");
        tv4aStatus.setTextColor(0xFF3B82F6);

        new Thread(() -> {
            // Step 1~3: 初始化（获取salt/公钥/checkMixedLogin）
            TowerLoginApi.Result initResult = towerLoginApi.initLogin();
            if (!initResult.success) {
                showToast("初始化失败: " + initResult.message);
                mainHandler.post(() -> {
                    btn4aSendCode.setEnabled(true);
                    btn4aSendCode.setText("获取验证码");
                    tv4aStatus.setText("未登录");
                    tv4aStatus.setTextColor(0xFFEF4444);
                });
                return;
            }

            // Step 4: doPrevLogin
            TowerLoginApi.Result prevResult = towerLoginApi.doPrevLogin(username, password);
            if (!prevResult.success) {
                showToast(prevResult.message);
                mainHandler.post(() -> {
                    btn4aSendCode.setEnabled(true);
                    btn4aSendCode.setText("获取验证码");
                    tv4aStatus.setText("密码错误");
                    tv4aStatus.setTextColor(0xFFEF4444);
                });
                return;
            }

            // 若直接登录成功（极少数）
            if ("direct".equals(prevResult.data)) {
                String cookie = towerLoginApi.getSessionCookie();
                saveCookieAndNotify(cookie);
                return;
            }

            // Step 5: refreshMsg（发送短信）
            TowerLoginApi.Result smsResult = towerLoginApi.refreshMsg(username, password);
            if (!smsResult.success) {
                showToast("短信发送失败: " + smsResult.message);
                mainHandler.post(() -> {
                    btn4aSendCode.setEnabled(true);
                    btn4aSendCode.setText("获取验证码");
                    tv4aStatus.setText("未登录");
                    tv4aStatus.setTextColor(0xFFEF4444);
                });
                return;
            }

            msgId = smsResult.data;
            codeSent = true;
            showToast(smsResult.message.isEmpty() ? "验证码已发送" : smsResult.message);

            mainHandler.post(() -> {
                btn4aSendCode.setEnabled(true);
                btn4aSendCode.setText("重发验证码");
                tv4aStatus.setText("待验证");
                tv4aStatus.setTextColor(0xFFEAB308);
                // 显示验证码行
                if (layout4aCodeRow != null) layout4aCodeRow.setVisibility(View.VISIBLE);
            });

        }).start();
    }

    /**
     * 点击"确认登录"：doNextLogin 提交短信验证码
     */
    private void doConfirmLogin() {
        if (!codeSent || msgId == null) {
            Toast.makeText(getContext(), "请先点击"获取验证码"", Toast.LENGTH_SHORT).show();
            return;
        }
        String msgCode = et4aMsgCode.getText().toString().trim();
        if (msgCode.isEmpty()) {
            Toast.makeText(getContext(), "请输入短信验证码", Toast.LENGTH_SHORT).show();
            return;
        }
        String username = et4aUsername.getText().toString().trim();
        String password = et4aPassword.getText().toString().trim();

        btn4aLogin.setEnabled(false);
        btn4aLogin.setText("验证中...");

        new Thread(() -> {
            TowerLoginApi.Result result = towerLoginApi.doNextLogin(username, password, msgId, msgCode);
            if (result.success) {
                String cookie = towerLoginApi.getSessionCookie();
                saveCookieAndNotify(cookie);
            } else {
                showToast(result.message);
                mainHandler.post(() -> {
                    btn4aLogin.setEnabled(true);
                    btn4aLogin.setText("确认登录");
                });
            }
        }).start();
    }

    /** 保存Cookie到Session并更新UI */
    private void saveCookieAndNotify(String cookie) {
        Session s = Session.get();
        s.tower4aSessionCookie = cookie != null ? cookie : "";
        if (getContext() != null) {
            s.saveTower4aCookie(getContext());
        }
        mainHandler.post(() -> {
            update4aStatus(true);
            btn4aSendCode.setEnabled(true);
            btn4aSendCode.setText("已登录");
            btn4aSendCode.setEnabled(false);
            if (btn4aLogin != null) {
                btn4aLogin.setEnabled(false);
                btn4aLogin.setText("确认登录");
            }
            if (layout4aCodeRow != null) layout4aCodeRow.setVisibility(View.GONE);
            Toast.makeText(getContext(), "4A登录成功 ✓", Toast.LENGTH_SHORT).show();
        });
    }

    /** 更新4A状态文字颜色 */
    private void update4aStatus(boolean loggedIn) {
        if (tv4aStatus == null) return;
        if (loggedIn) {
            tv4aStatus.setText("已登录");
            tv4aStatus.setTextColor(0xFF10B981);
            if (btn4aSendCode != null) {
                btn4aSendCode.setText("已登录");
                btn4aSendCode.setEnabled(false);
            }
        } else {
            tv4aStatus.setText("未登录");
            tv4aStatus.setTextColor(0xFFEF4444);
            if (btn4aSendCode != null) {
                btn4aSendCode.setText("获取验证码");
                btn4aSendCode.setEnabled(true);
            }
        }
    }

    private void showToast(String msg) {
        mainHandler.post(() -> {
            if (getContext() != null)
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    // ─── 排序按钮 ─────────────────────────────────────────────────────────────

    private void setupSortButtons(View view) {
        TextView btnSortBillTime = view.findViewById(R.id.btnSortBillTime);
        TextView btnSortFeedbackTime = view.findViewById(R.id.btnSortFeedbackTime);
        TextView btnSortAlertTime = view.findViewById(R.id.btnSortAlertTime);
        TextView btnSortAlertStatus = view.findViewById(R.id.btnSortAlertStatus);

        int bgPrimary   = R.drawable.bg_tag_primary;
        int bgSecondary = R.drawable.bg_tag_secondary;

        if (btnSortBillTime != null) {
            btnSortBillTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.BILL_TIME_DESC
                        ? WorkOrderAdapter.SortMode.BILL_TIME_ASC
                        : WorkOrderAdapter.SortMode.BILL_TIME_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortFeedbackTime != null) {
            btnSortFeedbackTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.FEEDBACK_TIME_DESC
                        ? WorkOrderAdapter.SortMode.FEEDBACK_TIME_ASC
                        : WorkOrderAdapter.SortMode.FEEDBACK_TIME_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortAlertTime != null) {
            btnSortAlertTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ALERT_TIME_DESC
                        ? WorkOrderAdapter.SortMode.ALERT_TIME_ASC
                        : WorkOrderAdapter.SortMode.ALERT_TIME_DESC);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
        if (btnSortAlertStatus != null) {
            btnSortAlertStatus.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM
                        ? WorkOrderAdapter.SortMode.ALERT_STATUS_RECOVER
                        : WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM);
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
    }

    private void updateSortButtonStyles(TextView btnBill, TextView btnFeedback, TextView btnAlert, TextView btnStatus,
                                        int bgPrimary, int bgSecondary) {
        if (adapter == null) return;
        WorkOrderAdapter.SortMode cur = adapter.getSortMode();

        btnBill.setBackgroundResource(bgSecondary);
        btnBill.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnFeedback.setBackgroundResource(bgSecondary);
        btnFeedback.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnAlert.setBackgroundResource(bgSecondary);
        btnAlert.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnStatus.setBackgroundResource(bgSecondary);
        btnStatus.setTextColor(requireContext().getColor(R.color.text_secondary));

        switch (cur) {
            case BILL_TIME_DESC:
            case BILL_TIME_ASC:
                btnBill.setBackgroundResource(bgPrimary);
                btnBill.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnBill.setText(cur == WorkOrderAdapter.SortMode.BILL_TIME_ASC ? "工单历时 ↑" : "工单历时 ↓");
                break;
            case FEEDBACK_TIME_DESC:
            case FEEDBACK_TIME_ASC:
                btnFeedback.setBackgroundResource(bgPrimary);
                btnFeedback.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnFeedback.setText(cur == WorkOrderAdapter.SortMode.FEEDBACK_TIME_ASC ? "反馈历时 ↑" : "反馈历时 ↓");
                break;
            case ALERT_TIME_DESC:
            case ALERT_TIME_ASC:
                btnAlert.setBackgroundResource(bgPrimary);
                btnAlert.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnAlert.setText(cur == WorkOrderAdapter.SortMode.ALERT_TIME_ASC ? "告警时间 ↑" : "告警时间 ↓");
                break;
            case ALERT_STATUS_ALARM:
            case ALERT_STATUS_RECOVER:
                btnStatus.setBackgroundResource(bgPrimary);
                btnStatus.setTextColor(requireContext().getColor(R.color.text_inverse));
                btnStatus.setText(cur == WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM ? "告警状态 ⚡" : "告警状态 ✓");
                break;
        }
    }

    // ─── 数据方法 ─────────────────────────────────────────────────────────────

    public void setData(List<WorkOrder> orders) {
        if (adapter != null) adapter.setData(orders);
    }

    public void updateStatus(int rowIndex, String billsn, String content) {
        if (adapter != null) adapter.updateStatus(rowIndex, billsn, content);
    }

    public WorkOrderAdapter getAdapter() {
        return adapter;
    }

    public void setAdapter(WorkOrderAdapter adapter) {
        this.adapter = adapter;
        if (recyclerView != null) recyclerView.setAdapter(adapter);
    }

    // ===== 配置区控件公共访问方法 =====

    public CheckBox getCbAutoFeedback()  { return cbAutoFeedback; }
    public CheckBox getCbAutoAccept()    { return cbAutoAccept; }
    public CheckBox getCbAutoRevert()    { return cbAutoRevert; }
    public Button   getBtnStartMonitor() { return btnStartMonitor; }
    public Button   getBtnStopMonitor()  { return btnStopMonitor; }
    public EditText getEtIntervalMin()   { return etIntervalMin; }
    public EditText getEtIntervalMax()   { return etIntervalMax; }
    public EditText getEtFeedbackMin()   { return etFeedbackMin; }
    public EditText getEtFeedbackMax()   { return etFeedbackMax; }
    public EditText getEtAcceptMin()     { return etAcceptMin; }
    public EditText getEtAcceptMax()     { return etAcceptMax; }
}
