package com.foldcounter.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class PermissionActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> permLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 必须在 super.onCreate 之后、setContentView 之前注册
        permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> goMain()
        );

        // 非首次直接跳主页
        SharedPreferences prefs = getSharedPreferences("FoldCounterPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("perm_asked", false)) {
            goMain();
            return;
        }

        setContentView(R.layout.activity_permission);
        findViewById(R.id.btn_grant).setOnClickListener(v -> requestPerms());
        findViewById(R.id.btn_skip).setOnClickListener(v -> goMain());
    }

    private void requestPerms() {
        java.util.List<String> perms = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (perms.isEmpty()) {
            goMain();
        } else {
            permLauncher.launch(perms.toArray(new String[0]));
        }
    }

    private void goMain() {
        getSharedPreferences("FoldCounterPrefs", MODE_PRIVATE)
                .edit().putBoolean("perm_asked", true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
