package com.towerops.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.model.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 考核工单 Fragment
 *
 * 对应易语言 子程序_省内考核工单查询
 * 接口：/api/flowable/flowable/task/listToOrder
 *
 * 8个过滤选项完全对应易语言8个选择框（10/18/13/14/15/16/17/22）
 * 5个分组常量与易语言一致
 */
public class KaoHeOrderFragment extends Fragment {

    // ── 行政区划（与省内待办Tab一致） ───────────────────────────────
    private static final String[] CITY_AREA_NAMES = {
        "全部", "平阳县", "泰顺县", "鹿城区", "苍南县", "文成县",
        "瑞安市", "乐清市", "龙湾区", "洞头区", "永嘉县", "龙港市"
    };
    private static final String[] CITY_AREA_CODES = {
        "", "330326", "330329", "330302", "330327", "330328",
        "330381", "330382", "330303", "330305", "330324", "330383"
    };

    // ── 分组常量（对应易语言 分组常量数组+分组名称数组） ──────────────
    private static final String[][] STATION_GROUP_RULES = {
        {"卢智伟",  "卢智伟、杨桂"},
        {"杨桂",    "卢智伟、杨桂"},
        {"高树调",  "高树调、倪传井"},
        {"倪传井",  "高树调、倪传井"},
        {"苏忠前",  "苏忠前、许方喜"},
        {"许方喜",  "苏忠前、许方喜"},
        {"黄经兴",  "黄经兴、蔡亮"},
        {"蔡亮",    "黄经兴、蔡亮"},
        {"陈德岳",  "陈德岳"},
    };

    // ── UI ──────────────────────────────────────────────────────────
    private Spinner spinnerCounty;
    private CheckBox cbFilter10, cbFilter18, cbFilter17;
    private CheckBox cbFilter13, cbFilter14, cbFilter15, cbFilter16, cbFilter22;
    private LinearLayout llFilterDetail;
    private Button btnQuery;
    private TextView tvStatus;
    private RecyclerView rvOrders;
    private KaoHeOrderAdapter adapter;

    private int selectedCountyIndex = 0;
    private volatile boolean isQuerying = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kaohe_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupSpinner();
        setupListeners();
    }

    private void initViews(View view) {
        spinnerCounty   = view.findViewById(R.id.spinnerKHCounty);
        cbFilter10      = view.findViewById(R.id.cbKHFilter10);
        cbFilter18      = view.findViewById(R.id.cbKHFilter18);
        cbFilter17      = view.findViewById(R.id.cbKHFilter17);
        cbFilter13      = view.findViewById(R.id.cbKHFilter13);
        cbFilter14      = view.findViewById(R.id.cbKHFilter14);
        cbFilter15      = view.findViewById(R.id.cbKHFilter15);
        cbFilter16      = view.findViewById(R.id.cbKHFilter16);
        cbFilter22      = view.findViewById(R.id.cbKHFilter22);
        llFilterDetail  = view.findViewById(R.id.llKHFilterDetail);
        btnQuery        = view.findViewById(R.id.btnKHQuery);
        tvStatus        = view.findViewById(R.id.tvKHStatus);
        rvOrders        = view.findViewById(R.id.rvKaoHeOrders);

        adapter = new KaoHeOrderAdapter();
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(adapter);
    }

    private void setupSpinner() {
        ArrayAdapter<String> countyAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, CITY_AREA_NAMES);
        countyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCounty.setAdapter(countyAdapter);
    }

    private void setupListeners() {
        spinnerCounty.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                selectedCountyIndex = position;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 选择框18联动：选中时隐藏细分选项，取消时展开
        cbFilter18.setOnCheckedChangeListener((btn, checked) -> {
            llFilterDetail.setVisibility(checked ? View.GONE : View.VISIBLE);
        });

        btnQuery.setOnClickListener(v -> startQuery());
    }

    // ── 查询 ─────────────────────────────────────────────────────────
    private void startQuery() {
        if (isQuerying) {
            Toast.makeText(getContext(), "查询中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        Session s = Session.get();
        String pcToken = s.shuyunPcToken;
        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先登录数运PC端", Toast.LENGTH_SHORT).show();
            return;
        }

        String cityArea = CITY_AREA_CODES[selectedCountyIndex];

        // 读取过滤状态
        boolean f10 = cbFilter10.isChecked();
        boolean f18 = cbFilter18.isChecked();
        boolean f13 = cbFilter13.isChecked();
        boolean f14 = cbFilter14.isChecked();
        boolean f15 = cbFilter15.isChecked();
        boolean f16 = cbFilter16.isChecked();
        boolean f17 = cbFilter17.isChecked();
        boolean f22 = cbFilter22.isChecked();

        isQuerying = true;
        btnQuery.setEnabled(true);
        btnQuery.setText("查询中...");
        adapter.setData(new ArrayList<>());
        tvStatus.setText("查询中...");

        executor.execute(() -> {
            try {
                String json = ShuyunApi.getKaoHeOrderList(pcToken, cityArea);
                List<ShuyunApi.KaoHeOrderInfo> list = ShuyunApi.parseKaoHeOrderList(
                        json, f10, f18, f13, f14, f15, f16, f17, f22,
                        STATION_GROUP_RULES);

                mainHandler.post(() -> {
                    adapter.setData(list);
                    tvStatus.setText("查询完成，共 " + list.size() + " 条考核工单"
                            + (selectedCountyIndex > 0 ? " · " + CITY_AREA_NAMES[selectedCountyIndex] : ""));
                    btnQuery.setEnabled(true);
                    btnQuery.setText("查询");
                    isQuerying = false;
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("查询失败：" + e.getMessage());
                    btnQuery.setEnabled(true);
                    btnQuery.setText("查询");
                    isQuerying = false;
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }
}
