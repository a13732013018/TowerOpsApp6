package com.towerops.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 */
public class WorkOrderFragment extends Fragment {

    private WorkOrderAdapter adapter;
    private RecyclerView recyclerView;

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
        
        if (btnSortBillTime != null) {
            btnSortBillTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.BILL_TIME_DESC
                        ? WorkOrderAdapter.SortMode.BILL_TIME_ASC
                        : WorkOrderAdapter.SortMode.BILL_TIME_DESC);
            });
        }
        if (btnSortFeedbackTime != null) {
            btnSortFeedbackTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.FEEDBACK_TIME_DESC
                        ? WorkOrderAdapter.SortMode.FEEDBACK_TIME_ASC
                        : WorkOrderAdapter.SortMode.FEEDBACK_TIME_DESC);
            });
        }
        if (btnSortAlertTime != null) {
            btnSortAlertTime.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ALERT_TIME_DESC
                        ? WorkOrderAdapter.SortMode.ALERT_TIME_ASC
                        : WorkOrderAdapter.SortMode.ALERT_TIME_DESC);
            });
        }
        if (btnSortAlertStatus != null) {
            btnSortAlertStatus.setOnClickListener(v -> {
                if (adapter == null) return;
                WorkOrderAdapter.SortMode cur = adapter.getSortMode();
                adapter.setSortMode(cur == WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM
                        ? WorkOrderAdapter.SortMode.ALERT_STATUS_RECOVER
                        : WorkOrderAdapter.SortMode.ALERT_STATUS_ALARM);
            });
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
}
