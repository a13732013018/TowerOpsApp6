package com.towerops.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.model.PowerOutage;

import java.util.List;

/**
 * 停电监控Fragment
 */
public class PowerOutageFragment extends Fragment {

    private PowerOutageAdapter adapter;
    private RecyclerView recyclerView;

    public static PowerOutageFragment newInstance() {
        return new PowerOutageFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_power_outage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recyclerPowerOutages);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new PowerOutageAdapter();
            recyclerView.setAdapter(adapter);
        }
        setupSortButtons(view);
    }

    /**
     * 设置排序按钮点击事件
     */
    private void setupSortButtons(View view) {
        if (adapter == null) return;

        // 电压排序按钮
        view.findViewById(R.id.btnSortVoltage).setOnClickListener(v -> {
            if (adapter == null) return;
            PowerOutageAdapter.SortMode cur = adapter.getSortMode();
            if (cur == PowerOutageAdapter.SortMode.VOLTAGE_DESC) {
                adapter.setSortMode(PowerOutageAdapter.SortMode.VOLTAGE_ASC);
            } else {
                adapter.setSortMode(PowerOutageAdapter.SortMode.VOLTAGE_DESC);
            }
        });

        // 负载电流排序按钮
        view.findViewById(R.id.btnSortLoadCurrent).setOnClickListener(v -> {
            if (adapter == null) return;
            PowerOutageAdapter.SortMode cur = adapter.getSortMode();
            if (cur == PowerOutageAdapter.SortMode.LOAD_CURRENT_DESC) {
                adapter.setSortMode(PowerOutageAdapter.SortMode.LOAD_CURRENT_ASC);
            } else {
                adapter.setSortMode(PowerOutageAdapter.SortMode.LOAD_CURRENT_DESC);
            }
        });

        // 告警时间排序按钮
        view.findViewById(R.id.btnSortAlarmTime).setOnClickListener(v -> {
            if (adapter == null) return;
            PowerOutageAdapter.SortMode cur = adapter.getSortMode();
            if (cur == PowerOutageAdapter.SortMode.ALARM_TIME_DESC) {
                adapter.setSortMode(PowerOutageAdapter.SortMode.ALARM_TIME_ASC);
            } else {
                adapter.setSortMode(PowerOutageAdapter.SortMode.ALARM_TIME_DESC);
            }
        });
    }

    /**
     * 设置停电数据（同时重置排序为电压升序）
     */
    public void setData(List<PowerOutage> powerOutages) {
        if (adapter != null) {
            adapter.setData(powerOutages);
        }
    }

    /**
     * 清空列表数据（停止监控时调用）
     */
    public void clearData() {
        if (adapter != null) {
            adapter.clearData();
        }
    }



    /**
     * 更新单条状态
     */
    public void updateStatus(int rowIndex, String billsn, String content) {
        if (adapter != null) {
            adapter.updateStatus(rowIndex, billsn, content);
        }
    }

    /**
     * 获取Adapter
     */
    public PowerOutageAdapter getAdapter() {
        return adapter;
    }

    /**
     * 设置Adapter(由Activity注入)
     */
    public void setAdapter(PowerOutageAdapter adapter) {
        this.adapter = adapter;
        // 如果RecyclerView已经初始化,立即设置adapter
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }
    }
}
