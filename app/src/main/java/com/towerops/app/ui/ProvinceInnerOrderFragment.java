package com.towerops.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 省内待办 Fragment
 *
 * 对应易语言"待办简易工单"查询功能：
 *  - 按小组（第一小组 6人 / 第二小组 5人）分类
 *  - 按工单类型（全部/应急/投诉/综合/其他）过滤
 *  - 按处理人过滤（单人 or 全部）
 *  - 5线程并发查询，加互斥锁写入列表
 *  - 仿生延迟（站点分组标注）
 */
public class ProvinceInnerOrderFragment extends Fragment {

    private static final String TAG = "ProvinceInnerFrag";

    // ── 分组常量（用于 station_name 匹配） ──────────────────────────
    // 每项：{常量关键词, 分组名称}
    private static final String[][] STATION_GROUP_RULES = {
        {"卢智伟",   "卢智伟、杨桂"},
        {"杨桂",     "卢智伟、杨桂"},
        {"高树调",   "高树调、倪传井"},
        {"倪传井",   "高树调、倪传井"},
        {"苏忠前",   "苏忠前、许方喜"},
        {"许方喜",   "苏忠前、许方喜"},
        {"黄经兴",   "黄经兴、蔡亮"},
        {"蔡亮",     "黄经兴、蔡亮"},
        {"陈德岳",   "陈德岳"},
    };

    // ── 计划上站小组配置（与易语言一致）────────────────────────────────
    // 分组常量数组（用于匹配站点名称）
    private static final String[] PLAN_GROUP_CONSTANTS = {
        "抢修1组", "抢修2组", "综合1组", "综合2组", "室分1组"
    };
    // 小组ID数组
    private static final String[] PLAN_GROUP_IDS = {"361", "363", "365", "367", "369"};
    // 小组名称数组（平阳区域）
    private static final String[] PLAN_GROUP_NAMES_PY = {
        "抢修1组-昆阳片区", "抢修2组-鳌江片区", "综合1组-水头片区", 
        "综合2组-顺溪片区", "全区域-机动组"
    };
    // 小组名称数组（其他区域/泰顺）
    private static final String[] PLAN_GROUP_NAMES_OT = {
        "综合小组", "专业小组（仕阳片巡检）", "专业巡检（雅阳片巡检小组）",
        "专业巡检（罗阳片巡检小组）", "专业小组（泗溪片巡检小组）"
    };
    // 其他区域小组ID
    private static final String[] PLAN_GROUP_IDS_OT = {"261", "263", "265", "267", "269"};

    // ── 小组成员 ────────────────────────────────────────────────────
    private static final String[][] GROUP1_MEMBERS = {
        {"12001", "林甲雨"},
        {"22961", "卢智伟"},
        {"12005", "高树调"},
        {"12004", "苏忠前"},
        {"12003", "黄经兴"},
        {"12007", "陶大取"}
    };
    private static final String[][] GROUP2_MEMBERS = {
        {"11961", "刘娟娟"},
        {"11956", "朱兴达"},
        {"11954", "王成"},
        {"11953", "夏念悦"},
        {"11950", "梅传威"}
    };

    // ── 行政区划代码（与数运审核保持一致）────────────────────────────────
    private static final String[] CITY_AREA_CODES = {
        "330326", "330329", "330302", "330327", "330328",
        "330381", "330382", "330303", "330305", "330324", "330383"
    };
    private static final String[] CITY_AREA_NAMES = {
        "平阳县", "泰顺县", "鹿城区", "苍南县", "文成县",
        "瑞安市", "乐清市", "龙湾区", "洞头区", "永嘉县", "龙港市"
    };

    // ── 工单类型 ────────────────────────────────────────────────────
    private static final String[] ORDER_TYPE_NAMES  = {"全部", "应急", "投诉", "综合", "其他"};
    private static final String[] ORDER_TYPE_CODES  = {
        "1124,1220,1028,1063",
        "1028",
        "1063",
        "1124,1220",
        "1118"
    };

    // ── UI ──────────────────────────────────────────────────────────
    private RecyclerView rvOrders;
    private ProvinceInnerOrderAdapter adapter;
    private Spinner spinnerGroup;
    private Spinner spinnerPerson;
    private Spinner spinnerOrderType;
    private Spinner spinnerCounty;      // 区县选择器
    private Button btnQuery;
    private Button btnToSignQuery;      // 待签查询按钮
    private TextView tvStatus;
    
    // 搜索和排序
    private EditText etSearchStation;
    private Button btnSearch;
    private Button btnSortStation;
    private Button btnSortTime;
    private Button btnSortType;

    // ── 状态 ────────────────────────────────────────────────────────
    private int selectedGroupIndex      = 0;   // 0=第一小组, 1=第二小组
    private int selectedPersonIndex     = 0;   // 0=全部, 1..n=具体人
    private int selectedOrderTypeIndex  = 0;   // 工单类型下标
    private int selectedCountyIndex     = 0;   // 区县下标
    private volatile boolean isQuerying = false;
    
    // ── 排序状态 ─────────────────────────────────────────────────────
    private static final int SORT_NONE = 0;
    private static final int SORT_ASC = 1;   // 正序
    private static final int SORT_DESC = 2;  // 倒序
    private int sortStationState = SORT_NONE;
    private int sortTimeState = SORT_NONE;
    private int sortTypeState = SORT_NONE;
    
    // ── 原始数据缓存 ─────────────────────────────────────────────────
    private List<ShuyunApi.ProvinceInnerTaskInfo> originalData = new ArrayList<>();
    private Map<String, Integer> stationOrderCount = new HashMap<>(); // 站点工单数量统计

    // ── 主线程Handler ────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── 并发相关 ─────────────────────────────────────────────────────
    private final ReentrantLock listLock = new ReentrantLock();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_province_inner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupSpinners();
        setupListeners();
    }

    // ── 初始化 ───────────────────────────────────────────────────────

    private void initViews(View view) {
        rvOrders       = view.findViewById(R.id.rvProvinceInnerOrders);
        spinnerGroup   = view.findViewById(R.id.spinnerPIGroup);
        spinnerPerson  = view.findViewById(R.id.spinnerPIPerson);
        spinnerOrderType = view.findViewById(R.id.spinnerPIOrderType);
        spinnerCounty  = view.findViewById(R.id.spinnerPICounty);
        btnQuery       = view.findViewById(R.id.btnPIQuery);
        tvStatus       = view.findViewById(R.id.tvPIStatus);
        
        // 搜索和排序
        etSearchStation = view.findViewById(R.id.etSearchStation);
        btnSearch = view.findViewById(R.id.btnSearch);
        btnSortStation = view.findViewById(R.id.btnSortStation);
        btnSortTime = view.findViewById(R.id.btnSortTime);
        btnSortType = view.findViewById(R.id.btnSortType);
        
        // 待签查询按钮
        btnToSignQuery = view.findViewById(R.id.btnToSignQuery);

        adapter = new ProvinceInnerOrderAdapter();
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvOrders.setAdapter(adapter);
        
        // 设置长按监听（双击效果）
        adapter.setOnItemLongClickListener((item, position) -> {
            showActionDialog(item);
        });
    }
    
    /**
     * 显示操作选择对话框（计划上站 / 综合上站回单 / 签到）
     */
    private void showActionDialog(ShuyunApi.ProvinceInnerTaskInfo item) {
        // 判断是否为待签工单（有jobId但没有flowInstId或为空）
        boolean isToSign = item.jobId != null && !item.jobId.isEmpty()
                && (item.flowInstId == null || item.flowInstId.isEmpty());

        String[] options;
        if (isToSign) {
            options = new String[]{"签到接单"};
        } else {
            options = new String[]{"计划上站", "综合上站回单"};
        }

        new AlertDialog.Builder(requireContext())
            .setTitle("选择操作 - " + item.station_name)
            .setItems(options, (dialog, which) -> {
                if (isToSign) {
                    // 待签工单 - 签到
                    showSignConfirmDialog(item);
                } else if (which == 0) {
                    // 计划上站
                    showPlanSiteDialog(item);
                } else {
                    // 综合上站回单
                    showReceiptConfirmDialog(item);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 显示签到确认对话框
     */
    private void showSignConfirmDialog(ShuyunApi.ProvinceInnerTaskInfo item) {
        new AlertDialog.Builder(requireContext())
            .setTitle("确认签到")
            .setMessage("站点: " + item.station_name + "\n"
                    + "工单: " + item.orderNum + "\n"
                    + "任务: " + item.jobName + "\n\n"
                    + "确认执行签到操作？")
            .setPositiveButton("确认签到", (dialog, which) -> {
                executeSign(item);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 执行签到
     */
    private void executeSign(ShuyunApi.ProvinceInnerTaskInfo item) {
        Session s = Session.get();
        String appToken = s.shuyunAppToken;
        String userId = s.shuyunAppUserId;

        if (appToken == null || appToken.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录数运APP", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("正在签到: " + item.station_name + "...");

        new Thread(() -> {
            try {
                // 模拟人类点击延迟（2-4秒随机）
                Random random = new Random();
                Thread.sleep(random.nextInt(2000) + 2000);

                String result = ShuyunApi.signTask(appToken, userId,
                        item.jobId, item.workInstId, item.orderNum, item.flowId);

                ShuyunApi.ReceiptResult receiptResult = ShuyunApi.parseSignResult(result);

                mainHandler.post(() -> {
                    if (receiptResult.success) {
                        Toast.makeText(requireContext(), "签到成功: " + item.station_name, Toast.LENGTH_SHORT).show();
                        tvStatus.setText("签到成功: " + item.station_name);
                        // 从列表中移除
                        removeItemFromList(item);
                    } else {
                        Toast.makeText(requireContext(), "签到失败: " + receiptResult.message, Toast.LENGTH_LONG).show();
                        tvStatus.setText("签到失败: " + receiptResult.message);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "签到异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    tvStatus.setText("签到异常");
                });
            }
        }).start();
    }
    
    /**
     * 显示计划上站对话框
     */
    private void showPlanSiteDialog(ShuyunApi.ProvinceInnerTaskInfo item) {
        // 根据站点名称匹配小组
        String[] groupInfo = matchPlanGroup(item.station_name);
        String groupId = groupInfo[0];
        String groupName = groupInfo[1];
        
        // 创建对话框布局
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        
        TextView tvInfo = new TextView(requireContext());
        tvInfo.setText("站点: " + item.station_name + "\n匹配小组: " + groupName);
        tvInfo.setTextSize(14);
        tvInfo.setPadding(0, 0, 0, 20);
        layout.addView(tvInfo);
        
        EditText etDate = new EditText(requireContext());
        etDate.setHint("上站日期 (yyyy-MM-dd)");
        // 默认明天
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date tomorrow = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
        etDate.setText(sdf.format(tomorrow));
        layout.addView(etDate);
        
        new AlertDialog.Builder(requireContext())
            .setTitle("计划上站")
            .setView(layout)
            .setPositiveButton("确定", (dialog, which) -> {
                String date = etDate.getText().toString().trim();
                if (date.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入上站日期", Toast.LENGTH_SHORT).show();
                    return;
                }
                executePlanSite(item, groupId, groupName, date);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 显示回单确认对话框
     */
    private void showReceiptConfirmDialog(ShuyunApi.ProvinceInnerTaskInfo item) {
        // 根据处理人姓名匹配receiptId
        String receiptId = matchHandlerId(item.handler, selectedGroupIndex);
        if (receiptId == null || receiptId.isEmpty()) {
            Toast.makeText(requireContext(), "未匹配到处理人ID: " + item.handler, Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle("确认综合上站回单")
            .setMessage("站点: " + item.station_name + "\n处理人: " + item.handler + "(" + receiptId + ")\n\n确定执行回单操作？")
            .setPositiveButton("确定", (dialog, which) -> {
                executeReceipt(item, receiptId);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 匹配计划上站小组
     * 对应易语言的分组常量匹配逻辑
     */
    private String[] matchPlanGroup(String stationName) {
        if (stationName == null || stationName.isEmpty()) {
            return getRandomGroup();
        }
        
        // 在分组常量中查找匹配的站点
        for (int i = 0; i < PLAN_GROUP_CONSTANTS.length; i++) {
            if (stationName.contains(PLAN_GROUP_CONSTANTS[i])) {
                String groupId = PLAN_GROUP_IDS[i];
                String groupName = selectedGroupIndex == 0 ? PLAN_GROUP_NAMES_PY[i] : PLAN_GROUP_NAMES_OT[i];
                return new String[]{groupId, groupName};
            }
        }
        
        // 未匹配到，使用随机保底
        return getRandomGroup();
    }
    
    /**
     * 获取随机小组（保底逻辑）
     */
    private String[] getRandomGroup() {
        Random random = new Random();
        if (selectedGroupIndex == 0) {
            // 平阳区域
            int idx = random.nextInt(PLAN_GROUP_IDS.length);
            return new String[]{PLAN_GROUP_IDS[idx], PLAN_GROUP_NAMES_PY[idx]};
        } else {
            // 其他区域
            int idx = random.nextInt(PLAN_GROUP_IDS_OT.length);
            return new String[]{PLAN_GROUP_IDS_OT[idx], PLAN_GROUP_NAMES_OT[idx]};
        }
    }
    
    /**
     * 根据处理人姓名匹配ID
     */
    private String matchHandlerId(String handlerName, int groupIndex) {
        String[][] members = groupIndex == 0 ? GROUP1_MEMBERS : GROUP2_MEMBERS;
        for (String[] member : members) {
            if (member[1].equals(handlerName)) {
                return member[0];
            }
        }
        return null;
    }
    
    /**
     * 执行计划上站
     */
    private void executePlanSite(ShuyunApi.ProvinceInnerTaskInfo item, 
            String groupId, String groupName, String upSiteTime) {
        Session s = Session.get();
        final String pcToken = s.shuyunPcToken;
        String cookieToken = s.shuyunPcTokenCookie;
        if (cookieToken == null || cookieToken.isEmpty()) cookieToken = pcToken;
        final String finalCookieToken = cookieToken;
        
        final String cityArea = CITY_AREA_CODES[selectedCountyIndex];
        final String stationCode = item.station_code;
        final String stationName = item.station_name;
        
        tvStatus.setText("正在执行计划上站: " + stationName + "...");
        
        new Thread(() -> {
            try {
                String result = ShuyunApi.saveSitePlan(pcToken, finalCookieToken, 
                    cityArea, groupId, groupName, 
                    stationCode, stationName, upSiteTime);
                
                mainHandler.post(() -> {
                    // 判断成功：status=200 或 code=200 或 code=1
                    boolean isSuccess = result.contains("\"status\":200") 
                            || result.contains("\"code\":200")
                            || result.contains("\"code\":1");
                    if (isSuccess) {
                        Toast.makeText(requireContext(), "计划上站成功: " + stationName, Toast.LENGTH_SHORT).show();
                        tvStatus.setText("计划上站成功: " + stationName);
                    } else {
                        Toast.makeText(requireContext(), "计划上站失败: " + result, Toast.LENGTH_LONG).show();
                        tvStatus.setText("计划上站失败");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "计划上站异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    tvStatus.setText("计划上站异常");
                });
            }
        }).start();
    }
    
    /**
     * 执行综合上站回单
     */
    private void executeReceipt(ShuyunApi.ProvinceInnerTaskInfo item, String receiptId) {
        Session s = Session.get();
        final String pcToken = s.shuyunPcToken;
        String cookieToken = s.shuyunPcTokenCookie;
        if (cookieToken == null || cookieToken.isEmpty()) cookieToken = pcToken;
        final String finalCookieToken = cookieToken;
        
        final String flowInstId = item.flowInstId;
        final String jobId = item.jobId;
        final String workInstId = item.workInstId;
        final String orderNum = item.orderNum;
        final String flowId = item.flowId;
        final String jobInstId = item.jobInstId;
        final String stationCode = item.station_code;
        final String orderType = item.order_type;
        final String workType = item.workType;
        final String stationName = item.station_name;
        final String flowName = item.flowName;
        
        tvStatus.setText("正在执行回单: " + stationName + "...");
        
        new Thread(() -> {
            try {
                // 步骤一：真正的工单流转
                String step1Result = ShuyunApi.receiptStepOne(pcToken, finalCookieToken,
                    receiptId, flowInstId, jobId, workInstId,
                    orderNum, flowId, jobInstId);
                
                // 仿生延迟
                Thread.sleep(4000 + new Random().nextInt(4000));
                
                // 步骤二：记录操作日志
                String step2Result = ShuyunApi.receiptStepTwo(pcToken, finalCookieToken,
                    receiptId, stationCode, orderType, orderNum,
                    jobId, workInstId, workType, stationName,
                    flowId, flowName);
                
                // 解析结果
                ShuyunApi.ReceiptResult result = ShuyunApi.parseReceiptResult(step1Result);
                
                mainHandler.post(() -> {
                    if (result.success) {
                        Toast.makeText(requireContext(), "回单成功: " + stationName, Toast.LENGTH_SHORT).show();
                        tvStatus.setText("回单成功: " + stationName);
                        // 从列表中移除已回单的工单
                        removeItemFromList(item);
                    } else {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
                        tvStatus.setText(result.message);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(), "回单异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    tvStatus.setText("回单异常");
                });
            }
        }).start();
    }
    
    /**
     * 从列表中移除已处理的工单
     */
    private void removeItemFromList(ShuyunApi.ProvinceInnerTaskInfo item) {
        List<ShuyunApi.ProvinceInnerTaskInfo> currentList = new ArrayList<>();
        for (int i = 0; i < adapter.getItemCount(); i++) {
            ShuyunApi.ProvinceInnerTaskInfo currentItem = adapter.getItem(i);
            if (currentItem != null && !currentItem.orderNum.equals(item.orderNum)) {
                currentList.add(currentItem);
            }
        }
        // 重新编号
        for (int i = 0; i < currentList.size(); i++) {
            currentList.get(i).index = String.valueOf(i + 1);
        }
        adapter.setData(currentList);
    }

    private void setupSpinners() {
        // 小组
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new String[]{"第一小组", "第二小组"});
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGroup.setAdapter(groupAdapter);

        // 区县（行政区划代码）
        ArrayAdapter<String> countyAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, CITY_AREA_NAMES);
        countyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCounty.setAdapter(countyAdapter);

        // 处理人（随小组动态生成）
        refreshPersonSpinner();

        // 工单类型
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, ORDER_TYPE_NAMES);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOrderType.setAdapter(typeAdapter);
    }

    /** 根据当前选中小组刷新处理人下拉 */
    private void refreshPersonSpinner() {
        String[][] members = selectedGroupIndex == 0 ? GROUP1_MEMBERS : GROUP2_MEMBERS;
        String[] names = new String[members.length + 1];
        names[0] = "全部";
        for (int i = 0; i < members.length; i++) {
            names[i + 1] = members[i][1];
        }
        ArrayAdapter<String> personAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        personAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPerson.setAdapter(personAdapter);
        selectedPersonIndex = 0;
    }

    private void setupListeners() {
        spinnerGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedGroupIndex = position;
                refreshPersonSpinner();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerPerson.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPersonIndex = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerCounty.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCountyIndex = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerOrderType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedOrderTypeIndex = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnQuery.setOnClickListener(v -> startQuery());
        
        // 待签查询按钮
        btnToSignQuery.setOnClickListener(v -> startToSignQuery());
        
        // 搜索按钮
        btnSearch.setOnClickListener(v -> searchAndLocate());
        
        // 排序按钮 - 支持正反序切换（点击循环：无->正序->倒序->无）
        btnSortStation.setOnClickListener(v -> {
            sortStationState = (sortStationState + 1) % 3;
            if (sortStationState != SORT_NONE) {
                sortTimeState = SORT_NONE;
                sortTypeState = SORT_NONE;
            }
            updateSortButton(btnSortStation, sortStationState, "站名");
            updateSortButton(btnSortTime, sortTimeState, "完成时间");
            updateSortButton(btnSortType, sortTypeState, "工单类型");
            applySort();
        });
        
        btnSortTime.setOnClickListener(v -> {
            sortTimeState = (sortTimeState + 1) % 3;
            if (sortTimeState != SORT_NONE) {
                sortStationState = SORT_NONE;
                sortTypeState = SORT_NONE;
            }
            updateSortButton(btnSortStation, sortStationState, "站名");
            updateSortButton(btnSortTime, sortTimeState, "完成时间");
            updateSortButton(btnSortType, sortTypeState, "工单类型");
            applySort();
        });
        
        btnSortType.setOnClickListener(v -> {
            sortTypeState = (sortTypeState + 1) % 3;
            if (sortTypeState != SORT_NONE) {
                sortStationState = SORT_NONE;
                sortTimeState = SORT_NONE;
            }
            updateSortButton(btnSortStation, sortStationState, "站名");
            updateSortButton(btnSortTime, sortTimeState, "完成时间");
            updateSortButton(btnSortType, sortTypeState, "工单类型");
            applySort();
        });
    }
    
    /** 重置所有排序状态 */
    private void resetSortStates() {
        sortStationState = SORT_NONE;
        sortTimeState = SORT_NONE;
        sortTypeState = SORT_NONE;
        updateSortButton(btnSortStation, SORT_NONE, "站名");
        updateSortButton(btnSortTime, SORT_NONE, "完成时间");
        updateSortButton(btnSortType, SORT_NONE, "工单类型");
    }
    
    /** 更新排序按钮显示 */
    private void updateSortButton(Button btn, int state, String baseText) {
        String text = baseText;
        int color = 0xFFE5E7EB; // 默认灰色
        int textColor = 0xFF333333;
        
        switch (state) {
            case SORT_ASC:
                text = baseText + " ↑";
                color = 0xFFDBEAFE; // 浅蓝色
                textColor = 0xFF2563EB;
                break;
            case SORT_DESC:
                text = baseText + " ↓";
                color = 0xFFDBEAFE;
                textColor = 0xFF2563EB;
                break;
        }
        
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(textColor);
    }
    
    /** 应用排序 */
    private void applySort() {
        if (originalData.isEmpty()) return;
        
        List<ShuyunApi.ProvinceInnerTaskInfo> sortedList = new ArrayList<>(originalData);
        
        Comparator<ShuyunApi.ProvinceInnerTaskInfo> comparator = null;
        
        if (sortStationState != SORT_NONE) {
            comparator = (a, b) -> {
                String nameA = a.station_name != null ? a.station_name : "";
                String nameB = b.station_name != null ? b.station_name : "";
                int result = nameA.compareTo(nameB);
                return sortStationState == SORT_ASC ? result : -result;
            };
        } else if (sortTimeState != SORT_NONE) {
            comparator = (a, b) -> {
                String timeA = a.req_comp_time != null ? a.req_comp_time : "";
                String timeB = b.req_comp_time != null ? b.req_comp_time : "";
                int result = timeA.compareTo(timeB);
                return sortTimeState == SORT_ASC ? result : -result;
            };
        } else if (sortTypeState != SORT_NONE) {
            comparator = (a, b) -> {
                String typeA = a.order_type != null ? a.order_type : "";
                String typeB = b.order_type != null ? b.order_type : "";
                int result = typeA.compareTo(typeB);
                return sortTypeState == SORT_ASC ? result : -result;
            };
        }
        
        if (comparator != null) {
            Collections.sort(sortedList, comparator);
        }
        
        adapter.setData(sortedList);
    }
    
    /** 搜索并定位到指定站点（跳转到该站点的第一个工单） */
    private void searchAndLocate() {
        String keyword = etSearchStation.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(requireContext(), "请输入站名", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取当前显示的数据
        List<ShuyunApi.ProvinceInnerTaskInfo> currentData = adapter.getData();
        if (currentData == null || currentData.isEmpty()) {
            Toast.makeText(requireContext(), "请先查询数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 查找匹配的站点（在当前显示的数据中）
        int targetPosition = -1;
        String foundStationName = null;
        for (int i = 0; i < currentData.size(); i++) {
            ShuyunApi.ProvinceInnerTaskInfo item = currentData.get(i);
            if (item.station_name != null &&
                    item.station_name.toLowerCase().contains(keyword.toLowerCase())) {
                targetPosition = i;
                foundStationName = item.station_name;
                break;
            }
        }

        if (targetPosition >= 0) {
            // 使用LayoutManager平滑滚动到指定位置
            LinearLayoutManager layoutManager = (LinearLayoutManager) rvOrders.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(targetPosition, 0);
            }
            // 高亮显示
            adapter.setHighlightPosition(targetPosition);
            tvStatus.setText("已跳转到: " + foundStationName);
        } else {
            Toast.makeText(requireContext(), "未找到匹配的站点", Toast.LENGTH_SHORT).show();
        }
    }

    // ── 查询主逻辑 ───────────────────────────────────────────────────

    private void startQuery() {
        if (isQuerying) {
            Toast.makeText(getContext(), "查询中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取PC登录Token
        Session s = Session.get();
        String pcToken     = s.shuyunPcToken;
        String cookieToken = s.shuyunPcTokenCookie;
        if (cookieToken == null || cookieToken.isEmpty()) cookieToken = pcToken;

        if (pcToken == null || pcToken.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录PC端", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确定查询哪些人
        String[][] members  = selectedGroupIndex == 0 ? GROUP1_MEMBERS : GROUP2_MEMBERS;
        String  orderType   = ORDER_TYPE_CODES[selectedOrderTypeIndex];

        // 区号：使用用户选择的行政区划代码
        String cityArea = CITY_AREA_CODES[selectedCountyIndex];

        final String finalPcToken     = pcToken;
        final String finalCookieToken = cookieToken;
        final String finalCityArea    = cityArea;

        // 确定要查询的成员列表（全部 or 指定人）
        final List<String[]> targets = new ArrayList<>();
        if (selectedPersonIndex == 0) {
            // 全部
            for (String[] member : members) {
                targets.add(member);
            }
        } else {
            // 选中人（下标从1开始，减1得members索引）
            int idx = selectedPersonIndex - 1;
            if (idx < members.length) {
                targets.add(members[idx]);
            }
        }

        if (targets.isEmpty()) {
            Toast.makeText(getContext(), "无可查询人员", Toast.LENGTH_SHORT).show();
            return;
        }

        // ── 启动查询 ──
        isQuerying = true;
        btnQuery.setEnabled(false);
        btnQuery.setText("查询中...");
        adapter.setData(new ArrayList<>());
        tvStatus.setText("查询中 0/" + targets.size() + "...");

        final AtomicInteger doneCount = new AtomicInteger(0);
        final int totalCount          = targets.size();
        // 结果集（线程安全）
        final List<ShuyunApi.ProvinceInnerTaskInfo> resultList = new ArrayList<>();

        // 5线程池（与易语言一致）
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(5, totalCount));

        for (String[] member : targets) {
            final String userId   = member[0];
            final String handler  = member[1];

            executor.submit(() -> {
                try {
                    String json = ShuyunApi.getProvinceInnerTaskList(
                            finalPcToken, finalCookieToken, userId, orderType, finalCityArea);

                    List<ShuyunApi.ProvinceInnerTaskInfo> taskList =
                            ShuyunApi.parseProvinceInnerTaskList(json, handler, "");

                    // 为每条工单标注分组
                    for (ShuyunApi.ProvinceInnerTaskInfo task : taskList) {
                        task.groupName = matchGroup(task.station_name);
                    }

                    // 加锁写入结果集
                    listLock.lock();
                    try {
                        resultList.addAll(taskList);
                    } finally {
                        listLock.unlock();
                    }

                    int done = doneCount.incrementAndGet();
                    mainHandler.post(() -> {
                        tvStatus.setText("查询中 " + done + "/" + totalCount
                                + "... " + handler + " (" + taskList.size() + "条)");
                    });

                } catch (Exception e) {
                    int done = doneCount.incrementAndGet();
                    mainHandler.post(() -> {
                        tvStatus.setText("查询中 " + done + "/" + totalCount + "...");
                    });
                }

                // 最后一个完成时，更新UI
                if (doneCount.get() >= totalCount) {
                    // 按序号重新排（先按处理人顺序，保持原有顺序稳定）
                    for (int i = 0; i < resultList.size(); i++) {
                        resultList.get(i).index = String.valueOf(i + 1);
                    }
                    
                    // 统计每个站点的工单数量
                    stationOrderCount.clear();
                    for (ShuyunApi.ProvinceInnerTaskInfo item : resultList) {
                        String stationName = item.station_name != null ? item.station_name : "";
                        if (!stationName.isEmpty()) {
                            stationOrderCount.put(stationName, 
                                stationOrderCount.getOrDefault(stationName, 0) + 1);
                        }
                    }
                    
                    // 保存原始数据
                    originalData.clear();
                    originalData.addAll(resultList);
                    
                    mainHandler.post(() -> {
                        adapter.setDataWithCount(resultList, stationOrderCount);
                        tvStatus.setText("查询完成，共 " + resultList.size() + " 条工单，" 
                            + stationOrderCount.size() + " 个站点");
                        btnQuery.setEnabled(true);
                        btnQuery.setText("我的待办");
                        isQuerying = false;
                    });
                }
            });
        }

        executor.shutdown();
    }

    // ── 待签工单查询 ───────────────────────────────────────────────────

    private void startToSignQuery() {
        if (isQuerying) {
            Toast.makeText(getContext(), "查询中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取数运APP登录Token
        Session s = Session.get();
        String appToken = s.shuyunAppToken;

        if (appToken == null || appToken.isEmpty()) {
            Toast.makeText(getContext(), "请先在监控页面登录数运APP", Toast.LENGTH_SHORT).show();
            return;
        }

        isQuerying = true;
        btnToSignQuery.setEnabled(false);
        btnToSignQuery.setText("查询中...");
        adapter.setData(new ArrayList<>());
        tvStatus.setText("查询待签工单中...");

        new Thread(() -> {
            try {
                String json = ShuyunApi.getToSignTaskList(appToken, s.shuyunAppUserId);
                List<ShuyunApi.ProvinceInnerTaskInfo> taskList =
                        ShuyunApi.parseToSignTaskList(json);

                // 为每条工单标注分组
                for (ShuyunApi.ProvinceInnerTaskInfo task : taskList) {
                    task.groupName = matchGroup(task.station_name);
                    task.index = String.valueOf(taskList.indexOf(task) + 1);
                }

                // 统计每个站点的工单数量
                stationOrderCount.clear();
                for (ShuyunApi.ProvinceInnerTaskInfo item : taskList) {
                    String stationName = item.station_name != null ? item.station_name : "";
                    if (!stationName.isEmpty()) {
                        stationOrderCount.put(stationName,
                                stationOrderCount.getOrDefault(stationName, 0) + 1);
                    }
                }

                // 保存原始数据
                originalData.clear();
                originalData.addAll(taskList);

                mainHandler.post(() -> {
                    adapter.setDataWithCount(taskList, stationOrderCount);
                    tvStatus.setText("待签工单查询完成，共 " + taskList.size() + " 条，"
                            + stationOrderCount.size() + " 个站点");
                    btnToSignQuery.setEnabled(true);
                    btnToSignQuery.setText("待签工单");
                    isQuerying = false;
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvStatus.setText("待签工单查询失败: " + e.getMessage());
                    btnToSignQuery.setEnabled(true);
                    btnToSignQuery.setText("待签工单");
                    isQuerying = false;
                });
            }
        }).start();
    }

    /** 根据 station_name 匹配分组名称 */
    private String matchGroup(String stationName) {
        if (stationName == null || stationName.isEmpty()) return "";
        for (String[] rule : STATION_GROUP_RULES) {
            if (stationName.contains(rule[0])) {
                return rule[1];
            }
        }
        return "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
