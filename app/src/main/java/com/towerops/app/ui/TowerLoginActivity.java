package com.towerops.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.towerops.app.R;
import com.towerops.app.api.TowerLoginApi;
import com.towerops.app.model.Session;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 铁塔4A系统登录界面
 *
 * 流程：
 * 1. 用户输入账号 + 密码 → 点击"获取验证码"
 * 2. 后台执行 initLogin + doPrevLogin + refreshMsg，短信发至手机
 * 3. 用户输入短信验证码 → 点击"登录"
 * 4. 登录成功后保存 SESSION Cookie，跳转主界面
 */
public class TowerLoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private EditText etSmsCode;
    private Button   btnGetSms;
    private Button   btnLogin;
    private TextView tvStatus;
    private TextView tvBack;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TowerLoginApi api;
    private String currentMsgId;
    private String currentUsername;
    private String currentPassword;

    // 倒计时（60秒内不能重复获取短信）
    private CountDownTimer smsCountDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏标题栏
        try {
            if (getSupportActionBar() != null) getSupportActionBar().hide();
        } catch (Exception ignored) {}

        // 检查是否已有4A Cookie，直接跳过登录
        SharedPreferences prefs = getSharedPreferences("tower4a_prefs", Context.MODE_PRIVATE);
        String savedCookie = prefs.getString("session_cookie", "");
        if (!savedCookie.isEmpty()) {
            Session.get().tower4aSessionCookie = savedCookie;
            goToMain();
            return;
        }

        setContentView(R.layout.activity_tower_login);

        etUsername = findViewById(R.id.etTowerUsername);
        etPassword = findViewById(R.id.etTowerPassword);
        etSmsCode  = findViewById(R.id.etTowerSmsCode);
        btnGetSms  = findViewById(R.id.btnTowerGetSms);
        btnLogin   = findViewById(R.id.btnTowerLogin);
        tvStatus   = findViewById(R.id.tvTowerStatus);
        tvBack     = findViewById(R.id.tvTowerBack);

        api = new TowerLoginApi();

        btnGetSms.setOnClickListener(v -> doGetSms());
        btnLogin.setOnClickListener(v  -> doLogin());
        tvBack.setOnClickListener(v    -> finish());
    }

    /** 获取短信验证码 */
    private void doGetSms() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty()) { showStatus("请输入账号", false); return; }
        if (password.isEmpty()) { showStatus("请输入密码", false); return; }

        currentUsername = username;
        currentPassword = password;

        btnGetSms.setEnabled(false);
        showStatus("正在验证账号...", true);

        executor.execute(() -> {
            // Step 1: 初始化（获取salt、公钥）
            TowerLoginApi.Result initResult = api.initLogin();
            if (!initResult.success) {
                runOnUiThread(() -> {
                    showStatus(initResult.message, false);
                    btnGetSms.setEnabled(true);
                });
                return;
            }

            // Step 2: doPrevLogin（账号密码验证）
            TowerLoginApi.Result prevResult = api.doPrevLogin(username, password);
            if (!prevResult.success) {
                runOnUiThread(() -> {
                    showStatus(prevResult.message, false);
                    btnGetSms.setEnabled(true);
                });
                return;
            }

            // 如果直接登录成功（不需要短信）
            if ("direct".equals(prevResult.data)) {
                saveCookieAndGoMain(api.getSessionCookie());
                return;
            }

            // Step 3: refreshMsg（发送短信）
            TowerLoginApi.Result smsResult = api.refreshMsg(username, password);
            runOnUiThread(() -> {
                if (smsResult.success) {
                    currentMsgId = smsResult.data;
                    showStatus("✓ " + smsResult.message, true);
                    startSmsCountDown();
                    etSmsCode.requestFocus();
                } else {
                    showStatus(smsResult.message, false);
                    btnGetSms.setEnabled(true);
                }
            });
        });
    }

    /** 提交短信验证码，完成登录 */
    private void doLogin() {
        String smsCode = etSmsCode.getText().toString().trim();

        if (currentMsgId == null) {
            showStatus("请先获取短信验证码", false);
            return;
        }
        if (smsCode.isEmpty()) {
            showStatus("请输入短信验证码", false);
            return;
        }

        btnLogin.setEnabled(false);
        showStatus("登录中...", true);

        executor.execute(() -> {
            TowerLoginApi.Result result = api.doNextLogin(
                    currentUsername, currentPassword, currentMsgId, smsCode);

            runOnUiThread(() -> {
                if (result.success) {
                    showStatus("登录成功！", true);
                    saveCookieAndGoMain(api.getSessionCookie());
                } else {
                    showStatus(result.message, false);
                    btnLogin.setEnabled(true);
                }
            });
        });
    }

    /** 保存Cookie并跳转主界面 */
    private void saveCookieAndGoMain(String cookie) {
        if (cookie == null) cookie = "";
        // 保存到 Session 内存
        Session.get().tower4aSessionCookie = cookie;
        // 持久化到本地
        getSharedPreferences("tower4a_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("session_cookie", cookie)
                .apply();

        runOnUiThread(this::goToMain);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /** 短信60秒倒计时，防止频繁发送 */
    private void startSmsCountDown() {
        if (smsCountDown != null) smsCountDown.cancel();
        smsCountDown = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnGetSms.setText("重新获取(" + (millisUntilFinished / 1000) + "s)");
                btnGetSms.setEnabled(false);
            }
            @Override
            public void onFinish() {
                btnGetSms.setText("重新获取验证码");
                btnGetSms.setEnabled(true);
            }
        }.start();
    }

    private void showStatus(String msg, boolean isInfo) {
        tvStatus.setText(msg);
        tvStatus.setTextColor(getResources().getColor(
                isInfo ? R.color.success_neu : R.color.error_neu));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smsCountDown != null) smsCountDown.cancel();
        executor.shutdown();
    }
}
