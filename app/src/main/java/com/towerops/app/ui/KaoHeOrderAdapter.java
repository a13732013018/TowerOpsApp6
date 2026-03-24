package com.towerops.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 考核工单列表适配器
 */
public class KaoHeOrderAdapter extends RecyclerView.Adapter<KaoHeOrderAdapter.VH> {

    private List<ShuyunApi.KaoHeOrderInfo> data = new ArrayList<>();

    public void setData(List<ShuyunApi.KaoHeOrderInfo> list) {
        this.data = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public ShuyunApi.KaoHeOrderInfo getItem(int position) {
        if (position >= 0 && position < data.size()) return data.get(position);
        return null;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_kaohe_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ShuyunApi.KaoHeOrderInfo item = data.get(position);
        // 交替背景
        h.itemView.setBackgroundColor(position % 2 == 0
                ? 0xFFFFFFFF : 0xFFF0F4FF);

        h.tvIndex.setText(item.index);
        h.tvCityName.setText(item.orderCityName);
        h.tvOrderNum.setText(item.orderNum);
        h.tvJobName.setText(item.jobName);
        h.tvStationName.setText(item.stationName);
        h.tvGroupName.setText(item.groupName);
        h.tvGroupName.setVisibility(item.groupName != null && !item.groupName.isEmpty()
                ? View.VISIBLE : View.GONE);
        h.tvFlowName.setText(item.flowName);
        h.tvDataName.setText(item.dataName);
        // 创建时间 → 要求完成时间
        String timeStr = formatShort(item.createTime) + " → " + formatShort(item.reqCompTime);
        h.tvCreateTime.setText(timeStr);
        h.tvAccestaff.setText(item.accestaff);
    }

    /** 简化时间格式：取 yyyy-MM-dd HH:mm 部分 */
    private String formatShort(String t) {
        if (t == null || t.isEmpty()) return "";
        if (t.length() > 16) return t.substring(0, 16);
        return t;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIndex, tvCityName, tvOrderNum, tvJobName;
        TextView tvStationName, tvGroupName;
        TextView tvFlowName, tvDataName;
        TextView tvCreateTime, tvAccestaff;

        VH(@NonNull View v) {
            super(v);
            tvIndex       = v.findViewById(R.id.tvKHIndex);
            tvCityName    = v.findViewById(R.id.tvKHCityName);
            tvOrderNum    = v.findViewById(R.id.tvKHOrderNum);
            tvJobName     = v.findViewById(R.id.tvKHJobName);
            tvStationName = v.findViewById(R.id.tvKHStationName);
            tvGroupName   = v.findViewById(R.id.tvKHGroupName);
            tvFlowName    = v.findViewById(R.id.tvKHFlowName);
            tvDataName    = v.findViewById(R.id.tvKHDataName);
            tvCreateTime  = v.findViewById(R.id.tvKHCreateTime);
            tvAccestaff   = v.findViewById(R.id.tvKHAccestaff);
        }
    }
}
