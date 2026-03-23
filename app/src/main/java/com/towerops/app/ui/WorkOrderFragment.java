package com.towerops.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.model.WorkOrder;

import java.util.List;

/**
 * 工单监控Fragment
 * 包含：配置控制面板（开关+按钮+阈值输入）、排序工具栏、工单列表
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

        // 绑定列表
        recyclerView = view.findViewById(R.id.recyclerWorkOrders);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new WorkOrderAdapter();
            recyclerView.setAdapter(adapter);
        }
        setupSortButtons(view);
    }
    
    private void setupSortButtons(View view) {
        TextView btnSortBillTime = view.findViewById(R.id.btnSortBillTime);
        TextView btnSortFeedbackTime = view.findViewById(R.id.btnSortFeedbackTime);
        TextView btnSortAlertTime = view.findViewById(R.id.btnSortAlertTime);
        TextView btnSortAlertStatus = view.findViewById(R.id.btnSortAlertStatus);

        // 按钮背景资源
        int bgPrimary = R.drawable.bg_tag_primary;
        int bgSecondary = R.drawable.bg_tag_secondary;

        if (btnSortBillTime != null) {
            btnSortBillTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.BILL_TIME_DESC
                        ? WorkOrderAdapter.SortMode.BILL_TIME_ASC
                        : WorkOrderAdapter.SortMode.BILL_TIME_DESC);
                // 更新按钮样式
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
                // 更新按钮样式
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
                // 更新按钮样式
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
                // 更新按钮样式
                updateSortButtonStyles(btnSortBillTime, btnSortFeedbackTime, btnSortAlertTime, btnSortAlertStatus, bgPrimary, bgSecondary);
            });
        }
    }

    /** 更新排序按钮样式，高亮当前选中的排序按钮 */
    private void updateSortButtonStyles(TextView btnBill, TextView btnFeedback, TextView btnAlert, TextView btnStatus,
                                          int bgPrimary, int bgSecondary) {
        if (adapter == null) return;
        WorkOrderAdapter.SortMode cur = adapter.getSortMode();

        // 先全部设为次要样式
        btnBill.setBackgroundResource(bgSecondary);
        btnBill.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnFeedback.setBackgroundResource(bgSecondary);
        btnFeedback.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnAlert.setBackgroundResource(bgSecondary);
        btnAlert.setTextColor(requireContext().getColor(R.color.text_secondary));
        btnStatus.setBackgroundResource(bgSecondary);
        btnStatus.setTextColor(requireContext().getColor(R.color.text_secondary));

        // 当前选中的按钮设为主要样式
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

    /**
     * 设置工单数据
     */
    public void setData(List<WorkOrder> orders) {
        if (adapter != null) {
            adapter.setData(orders);
        }
    }

    /**
     * 更新单条工单状态
     */
    public void updateStatus(int rowIndex, String billsn, String content) {
        if (adapter != null) {
            adapter.updateStatus(rowIndex, billsn, content);
        }
    }

    /**
     * 获取Adapter
     */
    public WorkOrderAdapter getAdapter() {
        return adapter;
    }

    /**
     * 设置Adapter(由Activity注入)
     */
    public void setAdapter(WorkOrderAdapter adapter) {
        this.adapter = adapter;
        // 如果RecyclerView已经初始化,立即设置adapter
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }
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
