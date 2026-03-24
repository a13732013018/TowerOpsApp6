package com.towerops.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.towerops.app.R;
import com.towerops.app.api.XunjianApi;
import com.towerops.app.model.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 巡检工单 Fragment
 *
 * 三个子 Tab：
 *   0 - 未巡检工单（列表 + 分组统计）
 *   1 - 智联巡检（未开始）
 *   2 - APP 巡检质检（任务列表 / 质检详情 / 统计报表）
 */
public class XunjianFragment extends Fragment {

    // ===================== UI 控件 =====================
    // 顶层导航 Tab
    private TextView tabUnstart, tabZhilian, tabQuality;
    private ViewFlipper viewFlipper;

    // Panel 0: 未巡检
    private TextView tvUnstartCount;
    private Button btnQueryUnstart;
    private LinearLayout llUnstartList, llUnstartStat;

    // Panel 1: 智联巡检
    private TextView tvZhilianXunjianCount;
    private Button btnQueryZhilianXunjian;
    private LinearLayout llZhilianXunjianList;

    // Panel 2: APP 质检
    private Spinner spinnerPlanYear, spinnerArea, spinnerQualityYear, spinnerQualityMonth;
    private CheckBox cbQualityByMonth, cbQualityToday;
    private Button btnQueryQuality;
    private ProgressBar progressQuality;
    private TextView tvQualityProgress;
    private TextView tabQualityTask, tabQualityDetail, tabQualityReport;
    private ViewFlipper viewFlipperQuality;
    private LinearLayout llQualityTaskList, llQualityDetailList, llQualityReport;

    // ===================== 状态 =====================
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int currentMainTab = 0;
    private int currentQualityTab = 0;

    // APP 质检并发控制
    private final Object qualityLock = new Object();
    private volatile int qualityRunning = 0;
    private volatile int qualityFinished = 0;
    private volatile int qualityTotal = 0;
    private volatile boolean isQualityRunning = false;
    private volatile int qualityYear = 2026;
    private volatile int qualityMonth = 3;
    private volatile boolean qualityByMonth = false;
    private volatile boolean qualityTodayOnly = false;
    private volatile int currentAreaIndex = 0;
    private volatile String currentPlanName = "2026";

    // 统计数组（索引0=汇总 1-5=各组）
    private final int[] statRoomTotal = new int[7];
    private final int[] statRoomDone  = new int[7];
    private final int[] statCabTotal  = new int[7];
    private final int[] statCabDone   = new int[7];
    private final int[] statRruTotal  = new int[7];
    private final int[] statRruDone   = new int[7];
    private final int[] statTowerTotal= new int[7];
    private final int[] statTowerDone = new int[7];
    private final int[] statTodayRoom = new int[7];
    private final int[] statTodayTower= new int[7];

    // 任务池
    private final List<XunjianApi.TaskPackage> taskPool = new ArrayList<>();
    private ExecutorService qualityExecutor;

    // ===================== 分组常量 =====================
    // 区域0（平阳）：综合1-5组
    private static final String[][] TASKUSER_AREA0 = {
        {"陶大取", "卢智伟"},
        {"陈龙", "林元龙"},
        {"高树调", "倪传井"},
        {"苏忠前", "许方喜"},
        {"黄经兴", "蔡亮"},
    };
    private static final String[] GROUPNAMES_AREA0 = {
        "综合1组", "综合2组", "综合3组", "综合4组", "综合5组"
    };
    // 区域1（泰顺）：4个片区
    private static final String[][] TASKUSER_AREA1 = {
        {"朱兴达"},
        {"王成"},
        {"夏念悦"},
        {"胡叙渐"},
    };
    private static final String[] GROUPNAMES_AREA1 = {
        "罗阳片区", "雅阳片区", "泗溪片区", "仕阳片区"
    };

    // ===================== 生命周期 =====================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_xunjian, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupTabListeners();
        setupQualitySpinners();
        selectMainTab(0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isQualityRunning = false;
        if (qualityExecutor != null && !qualityExecutor.isShutdown()) {
            qualityExecutor.shutdownNow();
        }
    }

    // ===================== 绑定控件 =====================

    private void bindViews(View v) {
        tabUnstart  = v.findViewById(R.id.tabXunjianUnstart);
        tabZhilian  = v.findViewById(R.id.tabXunjianZhilian);
        tabQuality  = v.findViewById(R.id.tabXunjianQuality);
        viewFlipper = v.findViewById(R.id.viewFlipperXunjian);

        // Panel 0
        tvUnstartCount  = v.findViewById(R.id.tvUnstartCount);
        btnQueryUnstart = v.findViewById(R.id.btnQueryUnstart);
        llUnstartList   = v.findViewById(R.id.llUnstartList);
        llUnstartStat   = v.findViewById(R.id.llUnstartStat);

        // Panel 1
        tvZhilianXunjianCount  = v.findViewById(R.id.tvZhilianXunjianCount);
        btnQueryZhilianXunjian = v.findViewById(R.id.btnQueryZhilianXunjian);
        llZhilianXunjianList   = v.findViewById(R.id.llZhilianXunjianList);

        // Panel 2
        spinnerPlanYear    = v.findViewById(R.id.spinnerPlanYear);
        spinnerArea        = v.findViewById(R.id.spinnerArea);
        spinnerQualityYear = v.findViewById(R.id.spinnerQualityYear);
        spinnerQualityMonth= v.findViewById(R.id.spinnerQualityMonth);
        cbQualityByMonth   = v.findViewById(R.id.cbQualityByMonth);
        cbQualityToday     = v.findViewById(R.id.cbQualityToday);
        btnQueryQuality    = v.findViewById(R.id.btnQueryQuality);
        progressQuality    = v.findViewById(R.id.progressQuality);
        tvQualityProgress  = v.findViewById(R.id.tvQualityProgress);

        tabQualityTask   = v.findViewById(R.id.tabQualityTask);
        tabQualityDetail = v.findViewById(R.id.tabQualityDetail);
        tabQualityReport = v.findViewById(R.id.tabQualityReport);
        viewFlipperQuality = v.findViewById(R.id.viewFlipperQuality);

        llQualityTaskList   = v.findViewById(R.id.llQualityTaskList);
        llQualityDetailList = v.findViewById(R.id.llQualityDetailList);
        llQualityReport     = v.findViewById(R.id.llQualityReport);
    }

    // ===================== Tab 切换 =====================

    private void setupTabListeners() {
        tabUnstart.setOnClickListener(v -> selectMainTab(0));
        tabZhilian.setOnClickListener(v -> selectMainTab(1));
        tabQuality.setOnClickListener(v -> selectMainTab(2));

        btnQueryUnstart.setOnClickListener(v -> startQueryUnstart());
        btnQueryZhilianXunjian.setOnClickListener(v -> startQueryZhilian());
        btnQueryQuality.setOnClickListener(v -> startQueryQuality());

        tabQualityTask.setOnClickListener(v -> selectQualityTab(0));
        tabQualityDetail.setOnClickListener(v -> selectQualityTab(1));
        tabQualityReport.setOnClickListener(v -> selectQualityTab(2));
    }

    private void selectMainTab(int idx) {
        currentMainTab = idx;
        viewFlipper.setDisplayedChild(idx);

        int normalBg = Color.parseColor("#12122a");
        int activeBg = Color.parseColor("#2563EB");

        tabUnstart.setBackgroundColor(idx == 0 ? activeBg : normalBg);
        tabZhilian.setBackgroundColor(idx == 1 ? activeBg : normalBg);
        tabQuality.setBackgroundColor(idx == 2 ? activeBg : normalBg);
    }

    private void selectQualityTab(int idx) {
        currentQualityTab = idx;
        viewFlipperQuality.setDisplayedChild(idx);

        int white = Color.WHITE;
        int gray  = Color.parseColor("#F3F4F6");
        int textActive  = Color.parseColor("#111827");
        int textInactive= Color.parseColor("#6B7280");

        tabQualityTask.setBackgroundColor(idx == 0 ? white : gray);
        tabQualityDetail.setBackgroundColor(idx == 1 ? white : gray);
        tabQualityReport.setBackgroundColor(idx == 2 ? white : gray);

        tabQualityTask.setTextColor(idx == 0 ? textActive : textInactive);
        tabQualityDetail.setTextColor(idx == 1 ? textActive : textInactive);
        tabQualityReport.setTextColor(idx == 2 ? textActive : textInactive);
    }

    // ===================== 初始化 Spinners =====================

    private void setupQualitySpinners() {
        // 计划年份
        List<String> years = new ArrayList<>();
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = curYear; y >= curYear - 2; y--) years.add(String.valueOf(y));
        years.add("全部");
        spinnerPlanYear.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, years));

        // 区域
        List<String> areas = new ArrayList<>();
        areas.add("平阳");
        areas.add("泰顺");
        spinnerArea.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, areas));

        // 质检年份
        List<String> qYears = new ArrayList<>();
        for (int y = curYear; y >= curYear - 1; y--) qYears.add(String.valueOf(y));
        spinnerQualityYear.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, qYears));
        spinnerQualityYear.setSelection(0);

        // 质检月份
        List<String> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) months.add(String.valueOf(m));
        spinnerQualityMonth.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, months));
        int curMonth = Calendar.getInstance().get(Calendar.MONTH); // 0-based
        spinnerQualityMonth.setSelection(curMonth);
    }

    // =========================================================
    // === Panel 0: 未巡检工单 ===
    // =========================================================

    private void startQueryUnstart() {
        if (Session.get().userid.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        btnQueryUnstart.setEnabled(false);
        btnQueryUnstart.setText("查询中...");
        llUnstartList.removeAllViews();
        llUnstartStat.removeAllViews();

        new Thread(() -> {
            String json = XunjianApi.getUnstartList();
            mainHandler.post(() -> {
                btnQueryUnstart.setEnabled(true);
                btnQueryUnstart.setText("查询");
                parseAndShowUnstart(json);
            });
        }).start();
    }

    private void parseAndShowUnstart(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("taskInfoList");
            if (arr == null) {
                tvUnstartCount.setText("未巡检: 0 条（解析失败）");
                return;
            }

            // 计数矩阵 [6组][5专业]，索引0-based
            int[][] matrix = new int[6][5];
            String[] groupNames = {"综合1组","综合2组","综合3组","综合4组","综合5组","室分1组"};

            int total = arr.length();
            tvUnstartCount.setText("未巡检: " + total + " 条");

            for (int i = 0; i < total; i++) {
                JSONObject item = arr.getJSONObject(i);
                String stationname  = XunjianApi.cleanNull(item.optString("stationname"));
                String applymajor   = XunjianApi.cleanNull(item.optString("applymajor"));
                String tasksn       = XunjianApi.cleanNull(item.optString("tasksn"));
                String deviceid     = XunjianApi.cleanNull(item.optString("deviceid"));
                String stationcode  = XunjianApi.cleanNull(item.optString("stationcode"));
                String date         = XunjianApi.cleanNull(item.optString("date"));
                String mainplanname = XunjianApi.cleanNull(item.optString("mainplanname"));

                // 分组匹配
                String groupName = matchGroup(stationname);
                int groupIdx = groupIndexOf(groupName, groupNames);

                // 专业序号（0-based）：机房0 机柜1 拉远2 铁塔3 室内分布4
                int majorIdx = -1;
                if ("机房及配套".equals(applymajor))    majorIdx = 0;
                else if ("机柜及配套".equals(applymajor)) majorIdx = 1;
                else if ("RRU拉远".equals(applymajor))  majorIdx = 2;
                else if ("铁塔".equals(applymajor))     majorIdx = 3;
                else if ("室内分布".equals(applymajor)) majorIdx = 4;

                if (groupIdx >= 0 && majorIdx >= 0) {
                    matrix[groupIdx][majorIdx]++;
                }

                // 添加列表行
                addUnstartRow(i + 1, groupName, tasksn, deviceid, stationname, applymajor, date);
            }

            // 渲染统计
            renderUnstartStat(matrix, groupNames);

        } catch (Exception e) {
            e.printStackTrace();
            tvUnstartCount.setText("未巡检: 解析异常");
        }
    }

    private void addUnstartRow(int seq, String groupName, String tasksn,
                                String deviceid, String stationname, String applymajor, String date) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 3, 4, 3);
        row.setBackgroundColor(seq % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE);

        addCell(row, 30,  String.valueOf(seq), 8, Gravity.CENTER);
        addCell(row, 50,  groupName, 8, Gravity.CENTER);
        addCell(row, 80,  tasksn, 8, Gravity.CENTER);
        addCellWeight(row, 1, stationname, 8, Gravity.START);
        addCell(row, 50,  applymajor, 8, Gravity.CENTER);
        addCell(row, 60,  date != null && date.length() > 10 ? date.substring(0, 10) : date, 8, Gravity.CENTER);

        llUnstartList.addView(row);
    }

    private void renderUnstartStat(int[][] matrix, String[] groupNames) {
        String[] majorHeaders = {"机房", "机柜", "拉远", "铁塔", "室分"};
        int[] colTotal = new int[5];
        int grandTotal = 0;

        for (int g = 0; g < 6; g++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(4, 2, 4, 2);
            row.setBackgroundColor(g % 2 == 0 ? Color.parseColor("#1F2937") : Color.parseColor("#111827"));

            addCell(row, 50, groupNames[g], 9, Gravity.CENTER).setTextColor(Color.parseColor("#D1D5DB"));

            int rowTotal = 0;
            for (int m = 0; m < 5; m++) {
                int val = matrix[g][m];
                rowTotal += val;
                colTotal[m] += val;
                addCellWeight(row, 1, String.valueOf(val), 9, Gravity.CENTER)
                        .setTextColor(val > 0 ? Color.parseColor("#FCD34D") : Color.parseColor("#6B7280"));
            }
            grandTotal += rowTotal;
            addCell(row, 40, String.valueOf(rowTotal), 9, Gravity.CENTER)
                    .setTextColor(Color.parseColor("#60A5FA"));

            llUnstartStat.addView(row);
        }

        // 合计行
        LinearLayout totalRow = new LinearLayout(getContext());
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        totalRow.setPadding(4, 2, 4, 2);
        totalRow.setBackgroundColor(Color.parseColor("#0D1117"));

        addCell(totalRow, 50, "总计", 9, Gravity.CENTER).setTextColor(Color.parseColor("#F9FAFB"));
        for (int m = 0; m < 5; m++) {
            addCellWeight(totalRow, 1, String.valueOf(colTotal[m]), 9, Gravity.CENTER)
                    .setTextColor(Color.parseColor("#34D399"));
        }
        addCell(totalRow, 40, String.valueOf(grandTotal), 9, Gravity.CENTER)
                .setTextColor(Color.parseColor("#F59E0B"));
        llUnstartStat.addView(totalRow);
    }

    // =========================================================
    // === Panel 1: 智联巡检 ===
    // =========================================================

    private void startQueryZhilian() {
        if (Session.get().userid.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        btnQueryZhilianXunjian.setEnabled(false);
        btnQueryZhilianXunjian.setText("查询中...");
        llZhilianXunjianList.removeAllViews();

        new Thread(() -> {
            String json = XunjianApi.getZhilianXunjianList();
            mainHandler.post(() -> {
                btnQueryZhilianXunjian.setEnabled(true);
                btnQueryZhilianXunjian.setText("查询");
                parseAndShowZhilianXunjian(json);
            });
        }).start();
    }

    private void parseAndShowZhilianXunjian(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                tvZhilianXunjianCount.setText("智联巡检: 0 条（解析失败）");
                return;
            }
            JSONArray records = data.optJSONArray("records");
            if (records == null) records = new JSONArray();

            int total = records.length();
            tvZhilianXunjianCount.setText("智联巡检: " + total + " 条");

            for (int i = 0; i < total; i++) {
                JSONObject item = records.getJSONObject(i);
                String tasksn      = XunjianApi.cleanNull(item.optString("tasksn"));
                String stationName = XunjianApi.cleanNull(item.optString("stationName"));
                String deviceid    = XunjianApi.cleanNull(item.optString("deviceid"));
                String applymajor  = XunjianApi.cleanNull(item.optString("applymajor"));
                String createtime  = XunjianApi.cleanNull(item.optString("createtime"));
                String mainPlanName= XunjianApi.cleanNull(item.optString("mainPlanName"));

                String groupName = matchGroup(stationName);
                addZhilianXunjianRow(i + 1, groupName, tasksn, deviceid, stationName, applymajor, createtime);
            }

            // 滚动到底部
            if (total > 0) {
                llZhilianXunjianList.post(() -> {
                    View parent = (View) llZhilianXunjianList.getParent();
                    if (parent instanceof android.widget.ScrollView) {
                        ((android.widget.ScrollView) parent).fullScroll(View.FOCUS_DOWN);
                    }
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
            tvZhilianXunjianCount.setText("智联巡检: 解析异常");
        }
    }

    private void addZhilianXunjianRow(int seq, String groupName, String tasksn,
                                       String deviceid, String stationName,
                                       String applymajor, String createtime) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 3, 4, 3);
        row.setBackgroundColor(seq % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE);

        addCell(row, 30,  String.valueOf(seq), 8, Gravity.CENTER);
        addCell(row, 50,  groupName, 8, Gravity.CENTER);
        addCell(row, 80,  tasksn, 8, Gravity.CENTER);
        addCellWeight(row, 1, stationName, 8, Gravity.START);
        addCell(row, 50,  applymajor, 8, Gravity.CENTER);
        addCell(row, 100, createtime, 8, Gravity.CENTER);

        llZhilianXunjianList.addView(row);
    }

    // =========================================================
    // === Panel 2: APP 巡检质检 ===
    // =========================================================

    private void startQueryQuality() {
        if (isQualityRunning) {
            Toast.makeText(getContext(), "正在查询中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Session.get().userid.isEmpty()) {
            Toast.makeText(getContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 读取 UI 配置
        currentAreaIndex = spinnerArea.getSelectedItemPosition();
        String planYearStr = (String) spinnerPlanYear.getSelectedItem();
        currentPlanName = "全部".equals(planYearStr) ? "2026" : planYearStr;
        qualityByMonth  = cbQualityByMonth.isChecked();
        qualityTodayOnly= cbQualityToday.isChecked();
        try {
            qualityYear = Integer.parseInt((String) spinnerQualityYear.getSelectedItem());
        } catch (Exception e) { qualityYear = Calendar.getInstance().get(Calendar.YEAR); }
        try {
            qualityMonth = Integer.parseInt((String) spinnerQualityMonth.getSelectedItem());
        } catch (Exception e) { qualityMonth = Calendar.getInstance().get(Calendar.MONTH) + 1; }

        // 清空 UI
        llQualityTaskList.removeAllViews();
        llQualityDetailList.removeAllViews();
        llQualityReport.removeAllViews();
        progressQuality.setProgress(0);
        tvQualityProgress.setText("0%");

        // 重置统计
        resetStats();

        isQualityRunning = true;
        btnQueryQuality.setEnabled(false);

        new Thread(this::runQualityQuery).start();
    }

    private void runQualityQuery() {
        // 步骤1：获取主计划列表
        String planJson = XunjianApi.getPlanList(currentPlanName);
        if (planJson == null || planJson.isEmpty()) {
            mainHandler.post(() -> {
                isQualityRunning = false;
                btnQueryQuality.setEnabled(true);
                Toast.makeText(getContext(), "获取计划列表失败", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // 步骤2：解析计划列表，组建任务池
        synchronized (taskPool) {
            taskPool.clear();
        }
        try {
            buildTaskPool(planJson);
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (qualityLock) {
            qualityTotal    = taskPool.size();
            qualityFinished = 0;
            qualityRunning  = 0;
        }

        if (qualityTotal == 0) {
            mainHandler.post(() -> {
                isQualityRunning = false;
                btnQueryQuality.setEnabled(true);
                tvQualityProgress.setText("无任务");
                Toast.makeText(getContext(), "未找到巡检任务", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        // 步骤3：并发处理（最多50并发，含超时防死等待）
        if (qualityExecutor != null && !qualityExecutor.isShutdown()) {
            qualityExecutor.shutdownNow();
        }
        qualityExecutor = Executors.newFixedThreadPool(50);

        for (int i = 0; i < qualityTotal; i++) {
            if (!isQualityRunning) break;
            final int idx = i;
            // 错峰发包
            try { Thread.sleep((long)(Math.random() * 200 + 50)); } catch (InterruptedException ie) { break; }
            qualityExecutor.submit(() -> processOneTask(idx));
        }

        // 等待所有任务完成（超时 180 秒）
        long deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            synchronized (qualityLock) {
                if (qualityFinished >= qualityTotal) break;
            }
            // 更新进度
            int fin, tot;
            synchronized (qualityLock) { fin = qualityFinished; tot = qualityTotal; }
            if (tot > 0) {
                int pct = (int)((float) fin / tot * 100);
                mainHandler.post(() -> {
                    progressQuality.setProgress(pct);
                    tvQualityProgress.setText(pct + "%");
                });
            }
            try { Thread.sleep(200); } catch (InterruptedException ie) { break; }
        }

        // 完成
        mainHandler.post(() -> {
            progressQuality.setProgress(100);
            tvQualityProgress.setText("100%");
            isQualityRunning = false;
            btnQueryQuality.setEnabled(true);
            renderQualityReport();
        });
    }

    /** 解析计划列表 JSON，为每个任务建立 TaskPackage 并放入 taskPool */
    private void buildTaskPool(String planJson) throws Exception {
        JSONObject root = new JSONObject(planJson);
        JSONArray planList = root.optJSONArray("planList");
        if (planList == null) return;

        for (int i = 0; i < planList.length(); i++) {
            JSONObject plan = planList.getJSONObject(i);
            String mainplanid   = plan.optString("mainplanid");
            String mainplanname = XunjianApi.cleanNull(plan.optString("mainplanname"));
            String applymajor   = XunjianApi.cleanNull(plan.optString("applymajor"));
            String allnum       = XunjianApi.cleanNull(plan.optString("allnum"));
            String finishnum    = XunjianApi.cleanNull(plan.optString("finishnum"));
            String remark       = XunjianApi.cleanNull(plan.optString("remark"));

            // 获取该计划下的具体任务
            String taskJson = XunjianApi.getTaskListByPlanId(mainplanid);
            if (taskJson == null || taskJson.isEmpty()) continue;

            JSONObject taskRoot = new JSONObject(taskJson);
            JSONArray taskArr   = taskRoot.optJSONArray("taskInfoList");
            if (taskArr == null) continue;

            for (int j = 0; j < taskArr.length(); j++) {
                JSONObject t = taskArr.getJSONObject(j);
                XunjianApi.TaskPackage pkg = new XunjianApi.TaskPackage();
                pkg.mainplanname = mainplanname;
                pkg.applymajor   = applymajor;
                pkg.allnum       = allnum;
                pkg.finishnum    = finishnum;
                pkg.remark       = remark;
                pkg.endtime      = XunjianApi.cleanNull(t.optString("endtime"));
                pkg.starttime    = XunjianApi.cleanNull(t.optString("starttime"));
                pkg.stationname  = XunjianApi.cleanNull(t.optString("stationname"));
                pkg.taskuser     = XunjianApi.cleanNull(t.optString("taskuser"));
                pkg.stationcode  = XunjianApi.cleanNull(t.optString("stationcode"));
                pkg.inspecttime  = XunjianApi.cleanNull(t.optString("inspecttime"));
                pkg.taskid       = XunjianApi.cleanNull(t.optString("taskid"));
                pkg.taskuserid   = XunjianApi.cleanNull(t.optString("taskuserid"));
                synchronized (taskPool) { taskPool.add(pkg); }
            }
        }
    }

    /** 处理单个任务（并发工作线程） */
    private void processOneTask(int idx) {
        XunjianApi.TaskPackage pkg;
        synchronized (taskPool) {
            if (idx >= taskPool.size()) { finishOne(); return; }
            pkg = taskPool.get(idx);
        }

        // 获取签到距离/精确专业信息
        String signJson = XunjianApi.getTaskSignInfo(pkg.taskid);
        String rangeStr = "";
        String tasksn   = "";
        String deviceid = "";
        try {
            JSONObject root = new JSONObject(signJson);
            JSONArray list  = root.optJSONArray("list");
            if (list != null && list.length() > 0) {
                JSONObject item = list.getJSONObject(0);
                String bottomMajor = XunjianApi.cleanNull(item.optString("applymajor"));
                if (!bottomMajor.isEmpty()) pkg.applymajor = bottomMajor;
                rangeStr = XunjianApi.cleanNull(item.optString("range_site"));
                tasksn   = XunjianApi.cleanNull(item.optString("tasksn"));
                deviceid = XunjianApi.cleanNull(item.optString("deviceid"));
            }
        } catch (Exception ignore) {}

        // 英中翻译
        pkg.applymajor = XunjianApi.translateMajor(pkg.applymajor);

        // 获取分组
        String[] groupResult = getGroupInfo(pkg.taskuser, pkg.stationname);
        String groupName = groupResult[0];
        int groupIdx     = Integer.parseInt(groupResult[1]);

        // 统计
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        boolean isDone  = !pkg.endtime.isEmpty() && !pkg.endtime.contains("null");
        boolean isToday = isDone && pkg.endtime.startsWith(todayStr);

        int cat = XunjianApi.getMajorCategory(pkg.applymajor);
        updateStats(cat, groupIdx, isDone, isToday);

        // 计算轮检周期
        String pollingperiod = "";
        if (!pkg.endtime.isEmpty() && !pkg.starttime.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date dt1 = sdf.parse(pkg.endtime);
                Date dt2 = sdf.parse(pkg.starttime);
                if (dt1 != null && dt2 != null) {
                    long diffMin = Math.abs(dt1.getTime() - dt2.getTime()) / 60000;
                    pollingperiod = String.valueOf(diffMin);
                }
            } catch (Exception ignore) {}
        }

        // 写入任务列表
        final String fGroupName  = groupName;
        final String fTasksn     = tasksn;
        final String fDeviceid   = deviceid;
        final String fStationname= pkg.stationname;
        final String fRemark     = pkg.remark;
        final String fApply      = pkg.applymajor;
        final String fPlanName   = pkg.mainplanname;
        final String fProgress   = pkg.finishnum + "/" + pkg.allnum;
        final String fStart      = pkg.starttime;
        final String fEnd        = pkg.endtime;
        final String fPoll       = pollingperiod;
        final String fTaskuser   = pkg.taskuser;
        final int    fSeq        = idx + 1;

        mainHandler.post(() -> addQualityTaskRow(fSeq, fGroupName, fTasksn, fDeviceid,
                pkg.stationcode, fStationname, fRemark, fApply, fPlanName, fProgress,
                fStart, fEnd, fPoll, pkg.inspecttime, fTaskuser));

        // 质检逻辑（按月/今日）
        boolean needQuality = false;
        if (!pkg.starttime.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date startDate = sdf.parse(pkg.starttime);
                if (startDate != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(startDate);
                    int taskYear  = cal.get(Calendar.YEAR);
                    int taskMonth = cal.get(Calendar.MONTH) + 1;
                    int taskDay   = cal.get(Calendar.DAY_OF_MONTH);
                    int curDay    = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

                    if (qualityByMonth && taskMonth == qualityMonth && taskYear == qualityYear) {
                        needQuality = true;
                    }
                    if (qualityTodayOnly && taskDay == curDay
                            && taskMonth == qualityMonth && taskYear == qualityYear) {
                        needQuality = true;
                    }
                }
            } catch (Exception ignore) {}
        }

        if (needQuality && !pkg.taskid.isEmpty()) {
            runQualityCheck(pkg.taskid, fGroupName, fPlanName, fStationname,
                    fApply, fTaskuser, fStart, pkg.stationcode, rangeStr, fSeq);
        }

        finishOne();
    }

    /** 质检子流程：获取巡检项目列表 → 逐条获取详情 → 写入质检详情列表 */
    private void runQualityCheck(String taskid, String groupName, String planName,
                                  String stationname, String applymajor, String taskuser,
                                  String starttime, String stationcode,
                                  String rangeStr, int seq) {
        String modListJson = XunjianApi.getModularList(taskid);
        try {
            JSONObject modRoot = new JSONObject(modListJson);
            JSONArray modList  = modRoot.optJSONArray("list");
            if (modList == null) return;

            for (int k = 0; k < modList.length(); k++) {
                JSONObject mod = modList.getJSONObject(k);
                String modularid  = mod.optString("modularid");
                String imagecount = XunjianApi.cleanNull(mod.optString("imagecount"));
                String devname    = XunjianApi.cleanNull(mod.optString("devname"));

                String detailJson = XunjianApi.getModularDetail(taskid, modularid);
                JSONObject detailRoot = new JSONObject(detailJson);
                JSONArray detailList  = detailRoot.optJSONArray("list");
                if (detailList == null) continue;

                for (int p = 0; p < detailList.length(); p++) {
                    JSONObject d = detailList.getJSONObject(p);
                    XunjianApi.AppQualityDetail detail = new XunjianApi.AppQualityDetail();
                    detail.seq          = String.valueOf(p + 1);
                    detail.groupName    = groupName;
                    detail.mainplanname = planName;
                    detail.sitecode     = XunjianApi.cleanNull(d.optString("sitecode"));
                    detail.sitename     = XunjianApi.cleanNull(d.optString("sitename"));
                    detail.applymajor   = applymajor;
                    detail.devname      = devname;
                    detail.projectname  = XunjianApi.cleanNull(d.optString("projectname"));
                    detail.request      = XunjianApi.cleanNull(d.optString("request"))
                            .replace("隐患拍照", "").replace("有隐患拍照", "");
                    detail.actualfill   = XunjianApi.cleanNull(d.optString("actualfill"));
                    detail.ishidden     = XunjianApi.cleanNull(d.optString("ishidden"));
                    detail.remark       = XunjianApi.cleanNull(d.optString("remark"));
                    detail.endtime      = XunjianApi.cleanNull(d.optString("endtime"));
                    detail.imagecount   = imagecount;
                    detail.range_site   = rangeStr;
                    detail.starttime    = starttime;
                    detail.taskuser     = taskuser;

                    // 质检判断
                    checkQualityRules(detail);

                    mainHandler.post(() -> addQualityDetailRow(detail));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 质检规则判断（对应易语言的质检逻辑） */
    private void checkQualityRules(XunjianApi.AppQualityDetail d) {
        // 必拍项目无照片
        if (d.request.contains("拍照") && parseInt0(d.imagecount) == 0) {
            d.qualityPass   = "否";
            d.qualityReason = "必拍项目无照片";
            return;
        }
        // 巡检结果与备注逻辑不符
        if ("N".equals(d.actualfill) && "正常".equals(d.remark)) {
            d.qualityPass   = "否";
            d.qualityReason = "巡检结果与备注逻辑不符";
            return;
        }
        // 无烟感隐患未上报
        if ("N".equals(d.ishidden) && d.remark.contains("无") && "烟感检查".equals(d.projectname)) {
            d.qualityPass   = "否";
            d.qualityReason = "无烟感隐患未上报";
            return;
        }
        // 经纬度偏差大于1000米
        if (!d.range_site.isEmpty() && parseDouble0(d.range_site) > 1000) {
            d.qualityPass   = "否";
            d.qualityReason = "经纬度偏差大于1000米";
            return;
        }
        // 负载总电流为0
        if ("负载总电流".equals(d.projectname) && parseDouble0(d.actualfill) == 0) {
            d.qualityPass   = "否";
            d.qualityReason = "负载总电流为0不规范";
            return;
        }
        // 低压线路铺设方式填写错误
        if ("低压线路铺设方式".equals(d.projectname)) {
            if (!d.actualfill.contains("架空") && !d.actualfill.contains("地埋")
                    && !d.actualfill.contains("管道") && !d.actualfill.contains("明敷")) {
                d.qualityPass   = "否";
                d.qualityReason = "巡检结果填写错误";
                return;
            }
        }
        // 交流引入电缆材质
        if ("交流引入电缆材质".equals(d.projectname)) {
            if (!d.actualfill.contains("铜") && !d.actualfill.contains("铝")) {
                d.qualityPass   = "否";
                d.qualityReason = "巡检结果填写错误，填写铜或铝";
                return;
            }
        }
        // 智联设备检查
        if ("是否存在智联设备-附挂物检查".equals(d.projectname)) {
            if ("不符合规范".equals(d.actualfill)) {
                if (!"现场巡检未发现智联设备".equals(d.remark)) {
                    d.qualityPass   = "否";
                    d.qualityReason = "智联设备巡检备注填写有误";
                    return;
                }
            } else if ("符合规范".equals(d.actualfill)) {
                if (!d.remark.contains("现场巡检发现智联设备")) {
                    d.qualityPass   = "否";
                    d.qualityReason = "智联设备巡检备注填写有误";
                    return;
                }
            } else {
                if (!d.actualfill.contains("符合规范") && !d.actualfill.contains("纠纷")
                        && !d.actualfill.contains("拆")) {
                    d.qualityPass   = "否";
                    d.qualityReason = "智联设备巡检结果填写有误";
                    return;
                }
            }
        }
    }

    // ===================== 统计 =====================

    private void resetStats() {
        for (int i = 0; i < 7; i++) {
            statRoomTotal[i] = statRoomDone[i] = 0;
            statCabTotal[i]  = statCabDone[i]  = 0;
            statRruTotal[i]  = statRruDone[i]  = 0;
            statTowerTotal[i]= statTowerDone[i] = 0;
            statTodayRoom[i] = statTodayTower[i]= 0;
        }
    }

    private void updateStats(int cat, int groupIdx, boolean isDone, boolean isToday) {
        synchronized (qualityLock) {
            // cat: 1=拉远/室分  2=机柜  3=铁塔  4=机房(默认)
            int g6 = 6; // 汇总槽（索引6）
            switch (cat) {
                case 1: // 拉远
                    statRruTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statRruTotal[groupIdx]++;
                    if (isDone) {
                        statRruDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statRruDone[groupIdx]++;
                        if (isToday) {
                            statTodayRoom[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayRoom[groupIdx]++;
                        }
                    }
                    break;
                case 2: // 机柜
                    statCabTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statCabTotal[groupIdx]++;
                    if (isDone) {
                        statCabDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statCabDone[groupIdx]++;
                        if (isToday) {
                            statTodayRoom[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayRoom[groupIdx]++;
                        }
                    }
                    break;
                case 3: // 铁塔
                    statTowerTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statTowerTotal[groupIdx]++;
                    if (isDone) {
                        statTowerDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statTowerDone[groupIdx]++;
                        if (isToday) {
                            statTodayTower[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayTower[groupIdx]++;
                        }
                    }
                    break;
                default: // 机房
                    statRoomTotal[g6]++;
                    if (groupIdx >= 1 && groupIdx <= 5) statRoomTotal[groupIdx]++;
                    if (isDone) {
                        statRoomDone[g6]++;
                        if (groupIdx >= 1 && groupIdx <= 5) statRoomDone[groupIdx]++;
                        if (isToday) {
                            statTodayRoom[g6]++;
                            if (groupIdx >= 1 && groupIdx <= 5) statTodayRoom[groupIdx]++;
                        }
                    }
                    break;
            }
        }
    }

    private void renderQualityReport() {
        llQualityReport.removeAllViews();
        String[] names, members;
        int groupCount;
        if (currentAreaIndex == 0) {
            names = new String[]{"综合1组","综合2组","综合3组","综合4组","综合5组","合计"};
            members = new String[]{"陶大取、卢智伟","陈龙、林元龙","高树调、倪传井","苏忠前、许方喜","黄经兴、蔡亮",""};
            groupCount = 6;
        } else {
            names = new String[]{"罗阳片区","雅阳片区","泗溪片区","仕阳片区","合计",""};
            members = new String[]{"朱兴达","王成","夏念悦","胡叙渐","",""};
            groupCount = 5;
        }

        for (int i = 1; i <= groupCount; i++) {
            int gi = (i == groupCount) ? 6 : i; // 最后一行用汇总槽
            String name   = names[i - 1];
            String member = members[i - 1];

            String roomText  = statRoomDone[gi]  + "/" + statRoomTotal[gi];
            String cabText   = statCabDone[gi]   + "/" + statCabTotal[gi];
            String rruText   = statRruDone[gi]   + "/" + statRruTotal[gi];
            String towerText = statTowerDone[gi] + "/" + statTowerTotal[gi];

            int roomDone  = statRoomDone[gi]  + statCabDone[gi]  + statRruDone[gi];
            int roomTotal2= statRoomTotal[gi] + statCabTotal[gi] + statRruTotal[gi];
            int towerDone2= statTowerDone[gi];
            int towerTotal2=statTowerTotal[gi];

            int todayMachine = statTodayRoom[gi];
            int todayTower   = statTodayTower[gi];
            int grandTotal   = roomDone + towerDone2;

            String roomPct = roomTotal2 > 0
                    ? String.format(Locale.getDefault(), "%.1f%%", (float) roomDone / roomTotal2 * 100) : "0%";
            String towerPct = towerTotal2 > 0
                    ? String.format(Locale.getDefault(), "%.1f%%", (float) towerDone2 / towerTotal2 * 100) : "0%";

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(2, 3, 2, 3);
            row.setBackgroundColor(i % 2 == 0 ? Color.parseColor("#EFF6FF") : Color.WHITE);
            if (i == groupCount) row.setBackgroundColor(Color.parseColor("#DBEAFE"));

            addCell(row, 50, name, 9, Gravity.CENTER).setTypeface(null, android.graphics.Typeface.BOLD);
            addCellWeight(row, 1, member, 8, Gravity.START);
            addCell(row, 44, roomText, 8, Gravity.CENTER);
            addCell(row, 44, cabText, 8, Gravity.CENTER);
            addCell(row, 44, rruText, 8, Gravity.CENTER);
            addCell(row, 44, towerText, 8, Gravity.CENTER);
            addCell(row, 36, String.valueOf(todayMachine), 9, Gravity.CENTER).setTextColor(Color.parseColor("#2563EB"));
            addCell(row, 36, String.valueOf(todayTower), 9, Gravity.CENTER).setTextColor(Color.parseColor("#D97706"));
            addCell(row, 36, String.valueOf(grandTotal), 9, Gravity.CENTER).setTypeface(null, android.graphics.Typeface.BOLD);
            addCell(row, 44, roomPct, 8, Gravity.CENTER);
            addCell(row, 44, towerPct, 8, Gravity.CENTER);

            llQualityReport.addView(row);
        }
    }

    // ===================== 列表行渲染 =====================

    private void addQualityTaskRow(int seq, String groupName, String tasksn, String deviceid,
                                    String stationcode, String stationname, String remark,
                                    String applymajor, String mainplanname, String progress,
                                    String starttime, String endtime, String pollingperiod,
                                    String inspecttime, String taskuser) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 2, 4, 2);
        row.setBackgroundColor(seq % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE);

        addCell(row, 30,  String.valueOf(seq), 8, Gravity.CENTER);
        addCell(row, 44,  groupName, 8, Gravity.CENTER);
        addCellWeight(row, 1, stationname, 8, Gravity.START);
        addCell(row, 40,  applymajor, 8, Gravity.CENTER);
        addCell(row, 56,  progress, 8, Gravity.CENTER);
        addCell(row, 90,  starttime != null && starttime.length() > 16 ? starttime.substring(0, 16) : starttime, 8, Gravity.CENTER);
        addCell(row, 90,  endtime != null && endtime.length() > 16 ? endtime.substring(0, 16) : endtime, 8, Gravity.CENTER);

        llQualityTaskList.addView(row);
    }

    private void addQualityDetailRow(XunjianApi.AppQualityDetail d) {
        int seq;
        try { seq = Integer.parseInt(d.seq); } catch (Exception e) { seq = 1; }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 2, 4, 2);

        boolean fail = "否".equals(d.qualityPass);
        row.setBackgroundColor(fail ? Color.parseColor("#FEF2F2") : (seq % 2 == 0 ? Color.parseColor("#F9FAFB") : Color.WHITE));

        addCell(row, 30,  d.seq, 8, Gravity.CENTER);
        addCell(row, 44,  d.groupName, 8, Gravity.CENTER);
        addCellWeight(row, 1, d.sitename, 8, Gravity.START);
        addCell(row, 44,  d.applymajor, 8, Gravity.CENTER);
        addCellWeight(row, 1, d.projectname, 8, Gravity.START);
        addCell(row, 44,  d.actualfill, 8, Gravity.CENTER);

        TextView tvPass = addCell(row, 32, d.qualityPass, 8, Gravity.CENTER);
        if (fail) tvPass.setTextColor(Color.parseColor("#EF4444"));

        addCellWeight(row, 1, d.qualityReason, 8, Gravity.START)
                .setTextColor(fail ? Color.parseColor("#DC2626") : Color.parseColor("#374151"));

        llQualityDetailList.addView(row);
    }

    // ===================== 原子完成计数 =====================

    private void finishOne() {
        synchronized (qualityLock) {
            qualityRunning--;
            qualityFinished++;
        }
    }

    // ===================== 分组匹配 =====================

    private String matchGroup(String stationname) {
        // 站名匹配（基于 taskuser 匹配在 getGroupInfo 里）
        // 此处仅返回"其他分组"作为 fallback
        return "其他分组";
    }

    /**
     * 综合分组匹配（优先站名，其次 taskuser）
     * @return [groupName, groupIndex(1-based，0=未知)]
     */
    private String[] getGroupInfo(String taskuser, String stationname) {
        // 区域0（平阳）
        if (currentAreaIndex == 0) {
            for (int g = 0; g < TASKUSER_AREA0.length; g++) {
                for (String name : TASKUSER_AREA0[g]) {
                    if (taskuser.contains(name)) {
                        return new String[]{GROUPNAMES_AREA0[g], String.valueOf(g + 1)};
                    }
                }
            }
        } else {
            // 区域1（泰顺）
            for (int g = 0; g < TASKUSER_AREA1.length; g++) {
                for (String name : TASKUSER_AREA1[g]) {
                    if (taskuser.contains(name)) {
                        return new String[]{GROUPNAMES_AREA1[g], String.valueOf(g + 1)};
                    }
                }
            }
        }
        return new String[]{"其他分组", "0"};
    }

    private int groupIndexOf(String groupName, String[] names) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(groupName)) return i;
        }
        return -1;
    }

    // ===================== View 工具方法 =====================

    private TextView addCell(LinearLayout parent, int widthDp, String text, int textSp, int gravity) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setText(text != null ? text : "");
        tv.setTextSize(textSp);
        tv.setGravity(gravity);
        tv.setPadding(2, 1, 2, 1);
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setSingleLine(false);
        parent.addView(tv);
        return tv;
    }

    private TextView addCellWeight(LinearLayout parent, float weight, String text, int textSp, int gravity) {
        TextView tv = new TextView(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        tv.setText(text != null ? text : "");
        tv.setTextSize(textSp);
        tv.setGravity(gravity);
        tv.setPadding(2, 1, 2, 1);
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setSingleLine(false);
        parent.addView(tv);
        return tv;
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int)(dp * density + 0.5f);
    }

    private int parseInt0(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private double parseDouble0(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }
}
