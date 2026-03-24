package com.towerops.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.towerops.app.R;
import com.towerops.app.api.ShuyunApi;
import com.towerops.app.model.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 指标查询 Tab
 * 对应易语言的6个子程序 + PUE有效率，共7个指标
 */
public class MetricsFragment extends Fragment {

    // 7个子Tab
    private static final String[] TAB_NAMES = {
        "电子化覆盖率", "资产一致性", "PUE有效率",
        "FSU离线率", "故障工单合格率", "疑似退服", "超频告警整治"
    };

    private EditText etDate;
    private TextView tvStatus;
    private TabLayout tabMetrics;
    private LinearLayout llHeader;
    private LinearLayout llRows;

    private int currentTab = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 表格列宽（dp），各Tab不同
    // 0:电子化覆盖率  1:资产一致性  2:PUE有效率  3:FSU  4:故障工单  5:疑似退服  6:超频告警
    private static final String[][] HEADERS = {
        // 电子化覆盖率
        {"序号", "区县", "站址总数", "覆盖数", "覆盖率(%)", "日期"},
        // 资产一致性
        {"地市", "开关-分子", "开关-分母", "开关-一致性",
         "空调-分子", "空调-分母", "空调-一致性",
         "铅酸-分子", "铅酸-分母", "铅酸-一致性",
         "普通锂-分子", "普通锂-分母", "普通锂-一致性",
         "智能锂-分子", "智能锂-分母", "智能锂-一致性",
         "蓄电池一致性", "总资产一致性", "日期"},
        // PUE有效率
        {"序号", "区县", "纳管站址", "有效站址", "有效率(%)", "达标站址", "达标率(%)",
         "I级", "II级", "III级", "IIII级", "日期"},
        // FSU离线率
        {"序号", "区县", "FSU离线率", "交维数", "派单总数", "当天超10次", "统计时间"},
        // 故障工单合格率
        {"序号", "区县", "统计时间", "总数", "本月新派发工单数", "遗留工单", "处理及时率", "无效工单数量", "超时工单数量", "工单处理率"},
        // 疑似退服
        {"序号", "区县", "统计时间", "疑似退服数", "一脱告警数", "疑似退服占比"},
        // 超频告警整治有效性
        {"序号", "区县", "总工单数", "故障工单数", "接单超时数", "回单超时数", "接单及时率", "回单及时率", "质检合格率", "处理合格率", "统计时间"},
    };

    private static final int[] COL_WIDTH_DP = {70, 80, 70, 80, 80, 70, 90};
    // 每列统一宽度（资产一致性列较多，单独设窄些）

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_metrics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etDate    = view.findViewById(R.id.etMetricsDate);
        tvStatus  = view.findViewById(R.id.tvMetricsStatus);
        tabMetrics = view.findViewById(R.id.tabMetrics);
        llHeader  = view.findViewById(R.id.llMetricsHeader);
        llRows    = view.findViewById(R.id.llMetricsRows);

        Button btnQuery    = view.findViewById(R.id.btnMetricsQuery);
        Button btnQueryAll = view.findViewById(R.id.btnMetricsQueryAll);
        Button btnDateMinus = view.findViewById(R.id.btnDateMinus);
        Button btnDatePlus  = view.findViewById(R.id.btnDatePlus);

        // 默认填入今天 yyyy-MM-dd
        String defaultDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        etDate.setText(defaultDate);

        // ◀▶ 日期调整（±1天）
        btnDateMinus.setOnClickListener(v -> shiftDate(-1));
        btnDatePlus.setOnClickListener(v  -> shiftDate(+1));

        // 初始化子Tab
        for (String name : TAB_NAMES) {
            tabMetrics.addTab(tabMetrics.newTab().setText(name));
        }
        tabMetrics.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                clearTable();
                tvStatus.setText("已切换到【" + TAB_NAMES[currentTab] + "】，点击查询");
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 查询按钮
        btnQuery.setOnClickListener(v -> queryCurrentTab());
        // 查全部按钮
        btnQueryAll.setOnClickListener(v -> queryAllTabs());
    }

    // ─── 查询当前Tab ───────────────────────────────────────────────
    private void queryCurrentTab() {
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(requireContext(), "请输入查询日期", Toast.LENGTH_SHORT).show();
            return;
        }
        Session s = Session.get();
        String pcToken = s.shuyunPcToken;
        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录数运账号", Toast.LENGTH_SHORT).show();
            return;
        }
        // cookieToken：优先用 shuyunPcTokenCookie，为空时 fallback 到 pcToken
        String cookieToken = (s.shuyunPcTokenCookie != null && !s.shuyunPcTokenCookie.isEmpty())
                ? s.shuyunPcTokenCookie : pcToken;
        setStatus("查询中...");
        executor.execute(() -> {
            String json = fetchByTab(currentTab, pcToken, cookieToken, date);
            mainHandler.post(() -> renderTable(currentTab, json));
        });
    }

    // ─── 查询全部Tab（并行） ──────────────────────────────────────
    private void queryAllTabs() {
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            Toast.makeText(requireContext(), "请输入查询日期", Toast.LENGTH_SHORT).show();
            return;
        }
        Session s = Session.get();
        String pcToken = s.shuyunPcToken;
        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录数运账号", Toast.LENGTH_SHORT).show();
            return;
        }
        String cookieToken = (s.shuyunPcTokenCookie != null && !s.shuyunPcTokenCookie.isEmpty())
                ? s.shuyunPcTokenCookie : pcToken;
        setStatus("正在查询全部指标...");
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tabIdx = i;
            executor.execute(() -> {
                String json = fetchByTab(tabIdx, pcToken, cookieToken, date);
                mainHandler.post(() -> {
                    // 只渲染当前展示的Tab
                    if (tabIdx == currentTab) renderTable(tabIdx, json);
                    setStatus("全部查询完成");
                });
            });
        }
    }

    // ─── 根据Tab索引调用对应API ────────────────────────────────────
    private String fetchByTab(int tabIdx, String pcToken, String cookieToken, String date) {
        try {
            switch (tabIdx) {
                case 0: return ShuyunApi.queryEleCoverRate(pcToken, cookieToken, date);
                case 1: return ShuyunApi.queryAssetConsistency(pcToken, cookieToken, date);
                case 2: return ShuyunApi.queryPueRate(pcToken, cookieToken, date);
                case 3: return ShuyunApi.queryFsuOfflineRate(pcToken, cookieToken, date);
                case 4: return ShuyunApi.queryFaultOrderQuality(pcToken, cookieToken, date);
                case 5: return ShuyunApi.querySuspectedOutOfService(pcToken, cookieToken, date);
                case 6: return ShuyunApi.queryAlarmEffectiveness(pcToken, cookieToken, date);
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    // ─── 渲染表格 ──────────────────────────────────────────────────
    private void renderTable(int tabIdx, String jsonStr) {
        clearTable();
        if (jsonStr == null || jsonStr.isEmpty()) {
            setStatus("查询失败：网络异常或无数据");
            return;
        }
        try {
            JSONObject root = new JSONObject(jsonStr);

            // 兼容两种结构：data 直接是 Array，或 data.rows 是 Array
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) data = dataObj.optJSONArray("rows");
            }
            // 再尝试顶层 rows
            if (data == null) data = root.optJSONArray("rows");

            if (data == null || data.length() == 0) {
                // 打印原始返回供排查
                String raw = jsonStr.length() > 300 ? jsonStr.substring(0, 300) + "..." : jsonStr;
                android.util.Log.w("MetricsFragment", "[" + TAB_NAMES[tabIdx] + "] raw: " + raw);
                setStatus("无数据（" + TAB_NAMES[tabIdx] + "）\n返回：" + raw);
                return;
            }

            // 打印第一条原始字段到Logcat，方便确认字段名
            JSONObject firstRow = data.getJSONObject(0);
            android.util.Log.d("MetricsFragment", "[" + TAB_NAMES[tabIdx] + "] keys: " + firstRow.toString());

            // 先尝试固定字段渲染
            String[] cells0 = buildCells(tabIdx, firstRow, 1);
            boolean allEmpty = true;
            for (int c = 1; c < cells0.length; c++) {  // 跳过序号列
                if (!cells0[c].isEmpty()) { allEmpty = false; break; }
            }

            if (allEmpty) {
                // 固定字段全空 → 动态渲染，同时显示原始key提示
                android.util.Log.w("MetricsFragment", "[" + TAB_NAMES[tabIdx] + "] fixed fields all empty, fallback to dynamic");
                renderDynamic(tabIdx, data);
                setStatus(TAB_NAMES[tabIdx] + " · " + data.length() + " 条（动态渲染，请确认字段）");
            } else {
                // 固定字段有数据 → 正常渲染
                String[] hdrs = HEADERS[tabIdx];
                addRow(llHeader, hdrs, true, tabIdx);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject row = data.getJSONObject(i);
                    String[] cells = buildCells(tabIdx, row, i + 1);
                    LinearLayout rowView = new LinearLayout(requireContext());
                    rowView.setOrientation(LinearLayout.HORIZONTAL);
                    rowView.setBackgroundColor(i % 2 == 0 ? Color.parseColor("#FFFFFF") : Color.parseColor("#F0F4FF"));
                    addRow(rowView, cells, false, tabIdx);
                    llRows.addView(rowView);
                }
                setStatus(TAB_NAMES[tabIdx] + " · 共 " + data.length() + " 条");
            }

        } catch (Exception e) {
            setStatus("解析失败：" + e.getMessage());
            android.util.Log.e("MetricsFragment", "parse error", e);
        }
    }

    /** 常见英文字段名 → 中文映射 */
    private static java.util.Map<String, String> buildKeyMap() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        // 通用
        m.put("AREA_NAME",        "区县");
        m.put("LAT_NAME",         "区县");
        m.put("DISTRICT_NAME",    "区县");
        m.put("REGION_NAME",      "区县");
        m.put("DATA_DATE",        "日期");
        m.put("MONTH",            "月份");
        // FSU离线率
        m.put("FSU_TOTAL",        "FSU总数");
        m.put("FSU_OFFLINE",      "离线数");
        m.put("FSU_OFFLINE_RATE", "离线率(%)");
        m.put("ONLINE_RATE",      "在线率(%)");
        m.put("OFFLINE_COUNT",    "离线数量");
        m.put("TOTAL_COUNT",      "总数量");
        m.put("RATE",             "比率(%)");
        m.put("ONLINE_COUNT",     "在线数量");
        // 故障工单合格率
        m.put("ORDER_TOTAL",      "工单总数");
        m.put("PASS_COUNT",       "合格数");
        m.put("PASS_RATE",        "合格率(%)");
        m.put("FAIL_COUNT",       "不合格数");
        m.put("FAIL_RATE",        "不合格率(%)");
        m.put("ORDER_COUNT",      "工单数");
        m.put("QUALIFIED_COUNT",  "合格数");
        m.put("QUALIFIED_RATE",   "合格率(%)");
        m.put("UNQUALIFIED_COUNT","不合格数");
        m.put("FINISH_COUNT",     "完成数");
        m.put("FINISH_RATE",      "完成率(%)");
        // 疑似退服
        m.put("SITE_COUNT",       "站址总数");
        m.put("TUIFU_COUNT",      "疑似退服数");
        m.put("TUIFU_RATE",       "疑似退服率(%)");
        m.put("SITE_NAME",        "站点名称");
        m.put("ALARM_COUNT",      "告警次数");
        m.put("PROCESS_COUNT",    "处理数");
        // 超频告警整治
        m.put("ALARM_TOTAL",      "告警总数");
        m.put("EFFECTIVE_COUNT",  "有效整治数");
        m.put("EFFECTIVE_RATE",   "整治有效率(%)");
        m.put("INVALID_COUNT",    "无效数");
        m.put("HANDLE_COUNT",     "处理数");
        m.put("HANDLE_RATE",      "处理率(%)");
        // 兜底缩写
        m.put("CNT",   "数量");
        m.put("NUM",   "数量");
        m.put("TOTAL", "总数");
        m.put("COUNT", "数量");
        return m;
    }
    private static final java.util.Map<String, String> KEY_ZH = buildKeyMap();

    /** 英文key转中文，找不到则保留原key */
    private static String keyZh(String key) {
        String zh = KEY_ZH.get(key.toUpperCase(Locale.US));
        return zh != null ? zh : key;
    }

    /** 动态列渲染：用第一行的 JSON key 作为表头，按 key 顺序展示每行数据 */
    private void renderDynamic(int tabIdx, JSONArray data) throws Exception {
        JSONObject firstRow = data.getJSONObject(0);
        // 收集所有 key（先区县/地市，再其他）
        java.util.List<String> keys = new java.util.ArrayList<>();
        // 优先展示区域列
        for (String areaKey : new String[]{"AREA_NAME", "LAT_NAME", "DISTRICT_NAME", "REGION_NAME"}) {
            if (firstRow.has(areaKey) && !keys.contains(areaKey)) {
                keys.add(areaKey);
                break;
            }
        }
        java.util.Iterator<String> it = firstRow.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!keys.contains(k)) keys.add(k);
        }

        // 表头：序号 + 所有key（转中文）
        String[] headerArr = new String[keys.size() + 1];
        headerArr[0] = "序号";
        for (int i = 0; i < keys.size(); i++) headerArr[i + 1] = keyZh(keys.get(i));
        addRow(llHeader, headerArr, true, tabIdx);

        // 数据行
        for (int r = 0; r < data.length(); r++) {
            JSONObject row = data.getJSONObject(r);
            String[] cells = new String[keys.size() + 1];
            cells[0] = String.valueOf(r + 1);
            for (int c = 0; c < keys.size(); c++) {
                cells[c + 1] = row.optString(keys.get(c), "");
            }
            LinearLayout rowView = new LinearLayout(requireContext());
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setBackgroundColor(r % 2 == 0 ? Color.parseColor("#FFFFFF") : Color.parseColor("#F0F4FF"));
            addRow(rowView, cells, false, tabIdx);
            llRows.addView(rowView);
        }
    }


    // ─── 按Tab取JSON字段组成cell数组 ──────────────────────────────
    private String[] buildCells(int tabIdx, JSONObject row, int seq) {
        switch (tabIdx) {
            case 0: // 电子化覆盖率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", ""),
                    row.optString("SITE_ALL_COUNT", ""),
                    row.optString("SITE_COVER_COUNT", ""),
                    row.optString("ELE_COVER_RATE", ""),
                    row.optString("DATA_DATE", "")
                };
            case 1: // 资产一致性
                return new String[]{
                    row.optString("AREA_NAME", ""),
                    row.optString("SWITCH_SON", ""), row.optString("SWITCH_FATHER", ""), row.optString("SWITCH_RATE", ""),
                    row.optString("AIR_SON", ""), row.optString("AIR_FATHER", ""), row.optString("AIR_RATE", ""),
                    row.optString("QS_BATTERY_SON", ""), row.optString("QS_BATTERY_FATHER", ""), row.optString("QS_BATTERY_RATE", ""),
                    row.optString("PT_BATTERY_SON", ""), row.optString("PT_BATTERY_FATHER", ""), row.optString("PT_BATTERY_RATE", ""),
                    row.optString("ZN_BATTERY_SON", ""), row.optString("ZN_BATTERY_FATHER", ""), row.optString("ZN_BATTERY_RATE", ""),
                    row.optString("BATTERY_RATE", ""), row.optString("PROPERTY_RATE", ""), row.optString("DATA_DATE", "")
                };
            case 2: // PUE有效率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("LAT_NAME", ""),
                    row.optString("SITE_ALL", ""),
                    row.optString("YX_SITE", ""),
                    row.optString("YX_RATE", ""),
                    row.optString("DB_SITE", ""),
                    row.optString("DB_RATE", ""),
                    row.optString("NH_RATE_1", ""),
                    row.optString("NH_RATE_2", ""),
                    row.optString("NH_RATE_3", ""),
                    row.optString("NH_RATE_4", ""),
                    row.optString("DATA_DATE", "")
                };
            case 3: // FSU离线率
                // 序号 区县 FSU离线率 交维数 派单总数 当天超10次 统计时间
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", row.optString("LAT_NAME", "")),
                    row.optString("FSU_OFFLINE_RATE", row.optString("RATE", "")),
                    row.optString("JW_COUNT",  row.optString("JIAOWEI_COUNT", "")),
                    row.optString("ORDER_TOTAL", row.optString("TOTAL_COUNT", "")),
                    row.optString("DAY10_COUNT", row.optString("OVER10_COUNT", "")),
                    row.optString("DATA_DATE",  row.optString("MONTH", ""))
                };
            case 4: // 故障工单合格率
                // 序号 区县 统计时间 总数 本月新派发工单数 遗留工单 处理及时率 无效工单数量 超时工单数量 工单处理率
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", row.optString("LAT_NAME", "")),
                    row.optString("DATA_DATE", row.optString("MONTH", "")),
                    row.optString("TOTAL",     row.optString("TOTAL_COUNT", "")),
                    row.optString("NEW_ORDER_COUNT",     row.optString("派发工单数", "")),
                    row.optString("LEAVE_ORDER_COUNT",   row.optString("REMAIN_COUNT", "")),
                    row.optString("PROCESS_RATE",        row.optString("HANDLE_RATE", "")),
                    row.optString("INVALID_ORDER_COUNT", row.optString("INVALID_COUNT", "")),
                    row.optString("TIMEOUT_ORDER_COUNT", row.optString("OVER_TIME_COUNT", "")),
                    row.optString("ORDER_PROCESS_RATE",  row.optString("FINISH_RATE", ""))
                };
            case 5: // 疑似退服
                // 序号 区县 统计时间 疑似退服数 一脱告警数 疑似退服占比
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", row.optString("LAT_NAME", "")),
                    row.optString("DATA_DATE", row.optString("MONTH", "")),
                    row.optString("TUIFU_COUNT",   row.optString("QUIT_COUNT", "")),
                    row.optString("ALARM_COUNT",   row.optString("ONE_DROP_COUNT", "")),
                    row.optString("TUIFU_RATE",    row.optString("QUIT_RATE", ""))
                };
            case 6: // 超频告警整治有效性
                // 序号 区县 总工单数 故障工单数 接单超时数 回单超时数 接单及时率 回单及时率 质检合格率 处理合格率 统计时间
                return new String[]{
                    String.valueOf(seq),
                    row.optString("AREA_NAME", row.optString("LAT_NAME", "")),
                    row.optString("TOTAL_ORDER",       row.optString("ORDER_TOTAL", "")),
                    row.optString("FAULT_ORDER",       row.optString("FAULT_COUNT", "")),
                    row.optString("RECEIVE_OVER_TIME", row.optString("JIEDAN_TIMEOUT", "")),
                    row.optString("REPLY_OVER_TIME",   row.optString("HUIDAN_TIMEOUT", "")),
                    row.optString("RECEIVE_RATE",      row.optString("JIEDAN_RATE", "")),
                    row.optString("REPLY_RATE",        row.optString("HUIDAN_RATE", "")),
                    row.optString("QUALITY_RATE",      row.optString("ZJ_RATE", "")),
                    row.optString("HANDLE_RATE",       row.optString("PASS_RATE", "")),
                    row.optString("DATA_DATE",         row.optString("MONTH", ""))
                };
            default:
                try {
                    String area = row.optString("AREA_NAME", row.optString("LAT_NAME", String.valueOf(seq)));
                    // 把所有值拼在一起
                    StringBuilder sb = new StringBuilder();
                    java.util.Iterator<String> keys = row.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (!k.equals("AREA_NAME") && !k.equals("LAT_NAME")) {
                            sb.append(k).append(":").append(row.optString(k)).append("  ");
                        }
                    }
                    return new String[]{String.valueOf(seq), area, sb.toString().trim()};
                } catch (Exception e) {
                    return new String[]{String.valueOf(seq), "", ""};
                }
        }
    }

    // ─── 添加一行（表头或数据行）────────────────────────────────────
    private void addRow(LinearLayout parent, String[] cells, boolean isHeader, int tabIdx) {
        // 资产一致性列宽窄一些
        int colWidthDp = (tabIdx == 1) ? 68 : 76;
        float density = getResources().getDisplayMetrics().density;
        int colWidthPx = (int)(colWidthDp * density);

        for (int c = 0; c < cells.length; c++) {
            // 第一列宽一点（区县/地市名）
            int w = (c == 0 && cells.length > 2) ? (int)(50 * density) : colWidthPx;
            // 最后一列（日期）更宽
            if (c == cells.length - 1 && cells.length > 4) w = (int)(90 * density);

            TextView tv = new TextView(requireContext());
            tv.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            tv.setText(cells[c]);
            tv.setTextSize(isHeader ? 9.5f : 9f);
            tv.setTextColor(isHeader
                ? Color.parseColor("#2563EB")
                : Color.parseColor("#1a1a2e"));
            tv.setTypeface(null, isHeader ? Typeface.BOLD : Typeface.NORMAL);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(2, 4, 2, 4);
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            tv.setIncludeFontPadding(false);

            // 表头行背景+分割线
            if (isHeader) {
                tv.setBackgroundColor(Color.parseColor("#EEF2FF"));
            }

            // 竖向分割线（除最后一列）
            if (c < cells.length - 1) {
                LinearLayout cell = new LinearLayout(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT);
                cell.setLayoutParams(lp);
                cell.setOrientation(LinearLayout.HORIZONTAL);
                cell.addView(tv);
                View divider = new View(requireContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(Color.parseColor("#DDEAFF"));
                cell.addView(divider);
                parent.addView(cell);
            } else {
                parent.addView(tv);
            }
        }
    }

    // ─── 清空表格 ──────────────────────────────────────────────────
    private void clearTable() {
        if (llHeader != null) llHeader.removeAllViews();
        if (llRows != null) llRows.removeAllViews();
    }

    // ─── 日期 ±1 天 ────────────────────────────────────────────────
    private void shiftDate(int days) {
        String cur = etDate.getText().toString().trim();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date d = sdf.parse(cur);
            if (d == null) d = new Date();
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.DAY_OF_MONTH, days);
            etDate.setText(sdf.format(cal.getTime()));
        } catch (ParseException e) {
            // 解析失败就用今天
            etDate.setText(sdf.format(new Date()));
        }
    }

    private void setStatus(String msg) {
        mainHandler.post(() -> {
            if (tvStatus != null) tvStatus.setText(msg);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }
}
