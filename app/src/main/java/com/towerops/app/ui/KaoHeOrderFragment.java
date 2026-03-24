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
import java.util.Collections;
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

    // ── 行政区划 ──────────────────────────────────────────────────────
    private static final String[] CITY_AREA_NAMES = {
        "全部", "平阳县", "泰顺县", "瓯海区", "苍南县", "文成县",
        "瑞安市", "乐清市", "鹿城区", "龙湾区", "洞头区", "永嘉县", "龙港市"
    };
    private static final String[] CITY_AREA_CODES = {
        "", "330326", "330329", "330304", "330327", "330328",
        "330381", "330382", "330302", "330303", "330305", "330324", "330383"
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
    private Spinner spinnerSortField;
    private Button btnSortDir;
    private CheckBox cbFilter10, cbFilter18, cbFilter17;
    private CheckBox cbFilter13, cbFilter14, cbFilter15, cbFilter16, cbFilter22;
    private Button btnQuery;
    private TextView tvStatus;
    private RecyclerView rvOrders;
    private KaoHeOrderAdapter adapter;

    // ── 排序状态 ─────────────────────────────────────────────────────
    // 0=站名 1=工单类型 2=要求完成 3=接单人
    private boolean sortAscending = true;  // true=正序 false=反序
    private int sortFieldIndex = 0;        // 0=站名 1=工单类型 2=要求完成 3=接单人

    // 当前已加载的完整列表（用于重新排序，无需重复请求）
    private List<ShuyunApi.KaoHeOrderInfo> currentFullList = new ArrayList<>();

    private int selectedCountyIndex = 0;
    private volatile boolean isQuerying = false;
    private boolean updatingFilter18 = false; // 防止联动循环

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
        spinnerSortField = view.findViewById(R.id.spinnerKHSortField);
        btnSortDir      = view.findViewById(R.id.btnKHSortDir);
        cbFilter10      = view.findViewById(R.id.cbKHFilter10);
        cbFilter18      = view.findViewById(R.id.cbKHFilter18);
        cbFilter17      = view.findViewById(R.id.cbKHFilter17);
        cbFilter13      = view.findViewById(R.id.cbKHFilter13);
        cbFilter14      = view.findViewById(R.id.cbKHFilter14);
        cbFilter15      = view.findViewById(R.id.cbKHFilter15);
        cbFilter16      = view.findViewById(R.id.cbKHFilter16);
        cbFilter22      = view.findViewById(R.id.cbKHFilter22);
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

        // 排序字段：站名/工单类型/要求完成/接单人
        String[] sortFields = {"站名", "工单类型", "要求完成", "接单人"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, sortFields);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSortField.setAdapter(sortAdapter);
    }

    private void setupListeners() {
        spinnerCounty.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                selectedCountyIndex = position;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 排序字段切换 → 立即对当前列表重排
        spinnerSortField.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                sortFieldIndex = position;
                applySortAndRefresh();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 正反序切换按钮
        btnSortDir.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            btnSortDir.setText(sortAscending ? "↑" : "↓");
            applySortAndRefresh();
        });

        // cb18：全不显示特殊类型（快捷键）
        // 勾选 → 13/14/15/16/22 全取消；取消 → 13/14/15/16/22 全勾选
        cbFilter18.setOnCheckedChangeListener((btn, checked) -> {
            if (updatingFilter18) return;
            updatingFilter18 = true;
            cbFilter13.setChecked(!checked);
            cbFilter14.setChecked(!checked);
            cbFilter15.setChecked(!checked);
            cbFilter16.setChecked(!checked);
            cbFilter22.setChecked(!checked);
            updatingFilter18 = false;
        });

        // 当13/14/15/16/22任一改变时，同步cb18状态（全不显示时=true）
        android.widget.CompoundButton.OnCheckedChangeListener subListener = (btn, checked) -> {
            if (updatingFilter18) return;
            boolean allHidden = !cbFilter13.isChecked() && !cbFilter14.isChecked()
                    && !cbFilter15.isChecked() && !cbFilter16.isChecked() && !cbFilter22.isChecked();
            updatingFilter18 = true;
            cbFilter18.setChecked(allHidden);
            updatingFilter18 = false;
        };
        cbFilter13.setOnCheckedChangeListener(subListener);
        cbFilter14.setOnCheckedChangeListener(subListener);
        cbFilter15.setOnCheckedChangeListener(subListener);
        cbFilter16.setOnCheckedChangeListener(subListener);
        cbFilter22.setOnCheckedChangeListener(subListener);

        btnQuery.setOnClickListener(v -> startQuery());
    }

    // ── 对 currentFullList 排序后刷新列表 ───────────────────────────
    private void applySortAndRefresh() {
        if (currentFullList == null || currentFullList.isEmpty()) return;
        List<ShuyunApi.KaoHeOrderInfo> sorted = new ArrayList<>(currentFullList);
        Collections.sort(sorted, (a, b) -> {
            String va, vb;
            switch (sortFieldIndex) {
                case 0: // 站名
                    va = a.stationName != null ? a.stationName : "";
                    vb = b.stationName != null ? b.stationName : "";
                    break;
                case 1: // 工单类型
                    va = a.flowName != null ? a.flowName : "";
                    vb = b.flowName != null ? b.flowName : "";
                    break;
                case 2: // 要求完成时间
                    va = a.reqCompTime != null ? a.reqCompTime : "";
                    vb = b.reqCompTime != null ? b.reqCompTime : "";
                    break;
                case 3: // 接单人
                    va = a.accestaff != null ? a.accestaff : "";
                    vb = b.accestaff != null ? b.accestaff : "";
                    break;
                default:
                    va = ""; vb = "";
            }
            int cmp = va.compareTo(vb);
            return sortAscending ? cmp : -cmp;
        });
        // 重新设置序号
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).index = String.valueOf(i + 1);
        }
        adapter.setData(sorted);
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
        // cookieToken：优先用 shuyunPcTokenCookie，为空时 fallback 到 pcToken
        String cookieToken = (s.shuyunPcTokenCookie != null && !s.shuyunPcTokenCookie.isEmpty())
                ? s.shuyunPcTokenCookie : pcToken;

        String cityArea = CITY_AREA_CODES[selectedCountyIndex];

        // 读取过滤状态
        boolean f10 = cbFilter10.isChecked();
        boolean f18 = cbFilter18.isChecked();  // 修复：读取实际状态，原来写死为false
        boolean f17 = cbFilter17.isChecked();
        boolean f13 = cbFilter13.isChecked();
        boolean f14 = cbFilter14.isChecked();
        boolean f15 = cbFilter15.isChecked();
        boolean f16 = cbFilter16.isChecked();
        boolean f22 = cbFilter22.isChecked();

        isQuerying = true;
        btnQuery.setEnabled(false);  // 查询中禁用按钮
        btnQuery.setText("查询中...");
        currentFullList = new ArrayList<>();
        adapter.setData(new ArrayList<>());
        tvStatus.setText("查询中...");

        executor.execute(() -> {
            try {
                String json = ShuyunApi.getKaoHeOrderList(pcToken, cookieToken, cityArea);
                // 打印原始返回前300字符供排查
                android.util.Log.d("KaoHeOrder", "raw: " + (json.length() > 300 ? json.substring(0, 300) : json));
                List<ShuyunApi.KaoHeOrderInfo> list = ShuyunApi.parseKaoHeOrderList(
                        json, f10, f18, f13, f14, f15, f16, f17, f22,
                        STATION_GROUP_RULES);

                mainHandler.post(() -> {
                    currentFullList = new ArrayList<>(list); // 保存原始列表备排序
                    applySortAndRefresh(); // 按当前排序规则展示
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
