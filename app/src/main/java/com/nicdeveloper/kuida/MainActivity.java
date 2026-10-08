package com.nicdeveloper.kuida;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.nicdeveloper.kuida.data.ProfileManager;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aplicar tema guardado (Claro / Oscuro / Sistema) antes de crear la vista
        String savedTheme = ProfileManager.getThemeMode(this);
        if (ProfileManager.THEME_DARK.equals(savedTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (ProfileManager.THEME_LIGHT.equals(savedTheme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawer_layout);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (host != null) {
            navController = host.getNavController();
            NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
            if (ProfileManager.hasCompletedOnboarding(this)) {
                graph.setStartDestination(getHomeDestinationForProfile(this));
            } else {
                graph.setStartDestination(R.id.profileGateFragment);
            }
            navController.setGraph(graph);

            BottomNavigationView bottom = findViewById(R.id.bottom_navigation);
            bottom.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                int targetId = id;
                if (id == R.id.homeFragment) {
                    targetId = getHomeDestinationForProfile(this);
                }
                if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() == targetId) {
                    return true;
                }
                int rootHome = getHomeDestinationForProfile(this);
                NavOptions options = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(rootHome, false)
                        .build();
                try {
                    navController.navigate(targetId, null, options);
                    return true;
                } catch (Exception e) {
                    try {
                        navController.navigate(targetId);
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }
            });

            navController.addOnDestinationChangedListener((nc, destination, arguments) -> {
                int id = destination.getId();
                boolean hideNav = id == R.id.profileGateFragment;
                bottom.setVisibility(hideNav ? View.GONE : View.VISIBLE);
                // LSN solo se ofrece a los perfiles que la usan.
                MenuItem lsnItem = bottom.getMenu().findItem(R.id.lsnCatalogFragment);
                if (lsnItem != null) lsnItem.setVisible(ProfileManager.canUseSignLanguageCatalog(this));
                if (id == R.id.homeFragment || id == R.id.visionFragment) {
                    MenuItem homeItem = bottom.getMenu().findItem(R.id.homeFragment);
                    if (homeItem != null) {
                        homeItem.setChecked(true);
                    }
                } else {
                    MenuItem item = bottom.getMenu().findItem(id);
                    if (item != null) {
                        item.setChecked(true);
                    }
                }
            });
        }

        // Drawer item listeners
        setupDrawerItemListeners();

        // Lector Kuida propio para ciegos sin TalkBack
        com.nicdeveloper.kuida.audio.KuidaReader.refresh(this);

        // Start fall detection service safely
        try {
            android.content.Intent serviceIntent = new android.content.Intent(this, com.nicdeveloper.kuida.sensor.FallDetectionService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error al iniciar FallDetectionService: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfileNavigation();
        // El perfil pudo cambiar en Ajustes: reevaluar el lector propio
        try {
            com.nicdeveloper.kuida.audio.KuidaReader.refresh(this);
        } catch (Exception ignored) {}
    }

    private void refreshProfileNavigation() {
        if (navController == null) return;
        BottomNavigationView bottom = findViewById(R.id.bottom_navigation);
        MenuItem lsnItem = bottom.getMenu().findItem(R.id.lsnCatalogFragment);
        boolean canUseLsn = ProfileManager.canUseSignLanguageCatalog(this);
        if (lsnItem != null) lsnItem.setVisible(canUseLsn);
        if (!canUseLsn && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.lsnCatalogFragment) {
            navController.navigate(getHomeDestinationForProfile(this));
        }
    }

    private void setupDrawerItemListeners() {
        View profileItem = findViewById(R.id.drawer_item_profile);
        View lsnItem = findViewById(R.id.drawer_item_lsn);
        View settingsItem = findViewById(R.id.drawer_item_settings);
        View aboutItem = findViewById(R.id.drawer_item_about);
        View termsItem = findViewById(R.id.drawer_item_terms);

        if (profileItem != null) profileItem.setOnClickListener(v -> {
            closeDrawerNow();
            if (navController != null) try { navController.navigate(R.id.profileFragment); } catch (Exception ignored) {}
        });
        if (lsnItem != null) lsnItem.setOnClickListener(v -> {
            closeDrawerNow();
            if (!ProfileManager.canUseSignLanguageCatalog(this)) {
                Toast.makeText(this, "El catálogo LSN está disponible para los perfiles auditivo, de comunicación del habla y asistencia completa.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (navController != null) try { navController.navigate(R.id.lsnCatalogFragment); } catch (Exception ignored) {}
        });
        View jarvisItem = findViewById(R.id.drawer_item_jarvis);
        if (jarvisItem != null) jarvisItem.setOnClickListener(v -> {
            closeDrawerNow();
            if (navController != null) try { navController.navigate(R.id.jarvisFragment); } catch (Exception ignored) {}
        });
        if (settingsItem != null) settingsItem.setOnClickListener(v -> {
            closeDrawerNow();
            if (navController != null) try { navController.navigate(R.id.settingsFragment); } catch (Exception ignored) {}
        });
        if (aboutItem != null) aboutItem.setOnClickListener(v -> {
            closeDrawerNow();
            Toast.makeText(this, "KUIDA v1.0 — App de accesibilidad inclusiva", Toast.LENGTH_LONG).show();
        });
        if (termsItem != null) termsItem.setOnClickListener(v -> {
            closeDrawerNow();
            if (navController != null) try { navController.navigate(R.id.termsFragment); } catch (Exception ignored) {}
        });

        // Selector de apariencia: Claro / Oscuro / Sistema
        View themeLight = findViewById(R.id.drawer_item_theme_light);
        View themeDark = findViewById(R.id.drawer_item_theme_dark);
        View themeSystem = findViewById(R.id.drawer_item_theme_system);
        if (themeLight != null) themeLight.setOnClickListener(v -> applyThemeMode(ProfileManager.THEME_LIGHT));
        if (themeDark != null) themeDark.setOnClickListener(v -> applyThemeMode(ProfileManager.THEME_DARK));
        if (themeSystem != null) themeSystem.setOnClickListener(v -> applyThemeMode(ProfileManager.THEME_SYSTEM));
        refreshThemeChecks();
    }

    private void applyThemeMode(String mode) {
        ProfileManager.setThemeMode(this, mode);
        if (ProfileManager.THEME_DARK.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (ProfileManager.THEME_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        closeDrawerNow();
        refreshThemeChecks();
        // Recreación explícita: garantiza que TODAS las vistas (XML y código)
        // se reconstruyan con la misma configuración, sin mezclas
        recreate();
    }

    private void refreshThemeChecks() {
        String mode = ProfileManager.getThemeMode(this);
        View checkLight = findViewById(R.id.drawer_check_light);
        View checkDark = findViewById(R.id.drawer_check_dark);
        View checkSystem = findViewById(R.id.drawer_check_system);
        if (checkLight != null) checkLight.setVisibility(ProfileManager.THEME_LIGHT.equals(mode) ? View.VISIBLE : View.GONE);
        if (checkDark != null) checkDark.setVisibility(ProfileManager.THEME_DARK.equals(mode) ? View.VISIBLE : View.GONE);
        if (checkSystem != null) checkSystem.setVisibility(ProfileManager.THEME_SYSTEM.equals(mode) ? View.VISIBLE : View.GONE);
    }

    private void closeDrawerNow() {
        if (drawerLayout != null) {
            View panel = findViewById(R.id.nav_drawer_panel);
            if (panel != null) drawerLayout.closeDrawer(panel);
        }
    }

    public static void openDrawer(Activity activity) {
        if (activity instanceof MainActivity) {
            MainActivity main = (MainActivity) activity;
            if (main.drawerLayout != null) {
                boolean canUseLsn = ProfileManager.canUseSignLanguageCatalog(main);
                View lsnRow = main.findViewById(R.id.drawer_item_lsn);
                if (lsnRow != null) {
                    lsnRow.setVisibility(canUseLsn ? View.VISIBLE : View.GONE);
                }
                View panel = main.findViewById(R.id.nav_drawer_panel);
                if (panel != null) main.drawerLayout.openDrawer(panel);
            }
        }
    }

    public static void closeDrawer(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).closeDrawerNow();
        }
    }

    public static int getHomeDestinationForProfile(Context context) {
        if (ProfileManager.PROFILE_BLIND.equals(ProfileManager.selectedProfile(context))) {
            return R.id.visionFragment;
        }
        return R.id.homeFragment;
    }
}
