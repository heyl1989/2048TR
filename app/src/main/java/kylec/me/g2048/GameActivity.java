package kylec.me.g2048;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.app.hubert.guide.NewbieGuide;
import com.app.hubert.guide.listener.AnimationListenerAdapter;
import com.app.hubert.guide.model.GuidePage;
import com.app.hubert.guide.model.HighLight;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import kylec.me.g2048.app.Config;
import kylec.me.g2048.app.ConfigManager;
import kylec.me.g2048.app.Constant;
import kylec.me.g2048.db.CellEntity;
import kylec.me.g2048.db.GameDatabaseHelper;
import kylec.me.g2048.view.CustomCommonDialog;
import kylec.me.g2048.view.CustomConfigDialog;
import kylec.me.g2048.view.CustomOverDialog;
import kylec.me.g2048.view.GameView;

/**
 * Created by KYLE on 2018/10/2
 */
public class GameActivity extends AppCompatActivity {

    public static final String KEY_CHEAT = "开挂";
    public static final int KEY_MATCH_SCORE = 3;

    private TextView currentScores;
    private TextView bestScores;
    private TextView bestScoresRank;
    private TextView titleDescribe;
    private TextView modeDescribe;
    private Button reset;
    private Button menu;
    private ImageView cheatStar;
    private LinearLayout score;
    private LinearLayout bestScore;
    private GameView gameView;

    private BroadcastReceiver myReceiver;
    private CustomConfigDialog configDialog;
    private GestureOverlayView mGestureOverlayView;

    private GameDatabaseHelper gameDatabaseHelper;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initGuide();
        initData();
        initGesture();
    }

    private Timer timer;

    @Override
    protected void onPause() {
        if (null != timer)
            timer.cancel();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                saveGameProgress();
            }
        }, 5000, 30 * 1000);
    }

    @Override
    protected void onDestroy() {
        // 取消注册广播
        if (myReceiver != null) {
            unregisterReceiver(myReceiver);
            myReceiver = null;
        }
        // 移除监听器
        mGestureOverlayView.removeAllOnGestureListeners();

        if (null != timer) {
            timer.cancel();
        }
        if (null != gameDatabaseHelper) {
            gameDatabaseHelper.close();
        }
        super.onDestroy();
    }

    /**
     * 初始化视图
     */
    private void initView() {
        // 动态设置状态栏字体颜色
        if (isLightColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))) {
            // 亮色，设置字体黑色
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }

        currentScores = findViewById(R.id.tv_current_score);
        bestScores = findViewById(R.id.tv_best_score);
        bestScoresRank = findViewById(R.id.tv_best_score_rank);
        modeDescribe = findViewById(R.id.tv_mode_describe);
        titleDescribe = findViewById(R.id.tv_title_describe);
        reset = findViewById(R.id.btn_reset);
        menu = findViewById(R.id.btn_option);
        mGestureOverlayView = findViewById(R.id.gesture_overlay_view);
        cheatStar = findViewById(R.id.iv_show_cheat);
        gameView = findViewById(R.id.game_view);
        score = findViewById(R.id.ll_scores);
        bestScore = findViewById(R.id.ll_best_score);

        // 进入经典模式
        if (Config.CurrentGameMode == 0) {
            // 读取到历史最高分
            bestScores.setText(String.valueOf(Config.BestScore));
            bestScoresRank.setText(getString(R.string.best_score_rank, Config.GRIDColumnCount));
            gameView.initView(0);
        } else {
            // 进入无限模式
            enterInfiniteMode();
        }
        setTextStyle(titleDescribe);
    }

    /**
     * 初始化数据
     */
    private void initData() {
        // 注册广播
        myReceiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GameView.ACTION_RECORD_SCORE);
        filter.addAction(GameView.ACTION_WIN);
        filter.addAction(GameView.ACTION_LOSE);
        registerReceiver(myReceiver, filter);

        // 重置按钮，重新开始游戏
        reset.setOnClickListener(v -> showConfirmDialog());
        // 打开菜单
        menu.setOnClickListener(v -> showConfigDialog());
        // 切换模式
        titleDescribe.setOnClickListener(v -> showChangeModeDialog());

        gameDatabaseHelper = new GameDatabaseHelper(this, Constant.DB_NAME, null, 1);
        db = gameDatabaseHelper.getWritableDatabase();
    }

    /**
     * 初始化引导
     */
    private void initGuide() {
        NewbieGuide.with(this)
                // 设置引导页的标签
                .setLabel("guide")
                // 添加一页引导页
                .addGuidePage(GuidePage.newInstance()
                        // 高亮区域
                        .addHighLight(score, HighLight.Shape.ROUND_RECTANGLE, 16)
                        // 禁止点击任意地方取消
                        .setEverywhereCancelable(false)
                        .setBackgroundColor(ContextCompat.getColor(this, R.color.shadow))
                        // 引导页说明布局
                        .setLayoutRes(R.layout.guide_view_1, R.id.tv_i_know))
                .addGuidePage(GuidePage.newInstance()
                        .addHighLight(bestScore, HighLight.Shape.ROUND_RECTANGLE, 16)
                        .setEverywhereCancelable(false)
                        .setBackgroundColor(ContextCompat.getColor(this, R.color.shadow))
                        .setLayoutRes(R.layout.guide_view_2, R.id.tv_i_know))
                .addGuidePage(GuidePage.newInstance()
                        .addHighLight(titleDescribe, HighLight.Shape.ROUND_RECTANGLE, 16)
                        .setEverywhereCancelable(false)
                        .setBackgroundColor(ContextCompat.getColor(this, R.color.shadow))
                        .setLayoutRes(R.layout.guide_view_3, R.id.tv_i_know))
                .addGuidePage(GuidePage.newInstance()
                        .addHighLight(gameView, HighLight.Shape.ROUND_RECTANGLE, 16)
                        .setEverywhereCancelable(false)
                        .setBackgroundColor(ContextCompat.getColor(this, R.color.shadow))
                        .setLayoutRes(R.layout.guide_view_4, R.id.tv_i_know))
                .show();
    }

    /**
     * 初始化手势
     */
    private void initGesture() {
        if (!Config.haveCheat) {
            // 定义手势库
            GestureLibrary library = GestureLibraries.fromRawResource(this, R.raw.gestures);
            // 设置手势监听（手势绘制完成调用）
            mGestureOverlayView.addOnGesturePerformedListener((overlay, gesture) -> {
                // 加载手势库成功
                if (library.load()) {
                    // 从手势库中查找所有匹配的手势
                    ArrayList<Prediction> predictions = library.recognize(gesture);
                    if (!predictions.isEmpty()) {
                        // 获取第一匹配
                        Prediction prediction = predictions.get(0);
                        // 当匹配值大于3
                        if (prediction.score > KEY_MATCH_SCORE) {
                            // 进入开挂模式
                            if (KEY_CHEAT.equals(prediction.name)) {
                                cheatStar.setVisibility(View.VISIBLE);
                                // 设置缩放动画（以自身中心为缩放点，从10%缩放到原始大小）
                                ScaleAnimation animation = new ScaleAnimation(
                                        0.1f, 1, 0.1f, 1,
                                        Animation.RELATIVE_TO_SELF, 0.5f,
                                        Animation.RELATIVE_TO_SELF, 0.5f);
                                animation.setDuration(999);
                                cheatStar.startAnimation(animation);
                                animation.setAnimationListener(new AnimationListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        showCheatModeDialog();
                                    }
                                });
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * 打开外挂模式对话框
     */
    private void showCheatModeDialog() {
        CustomCommonDialog dialog = new CustomCommonDialog(this, R.style.CustomDialog);
        dialog.setCancelable(false);
        dialog.setTitle("外挂机制")
                .setMessage("小机灵鬼，这都被你发现了~将为您随机生成一个1024小方块，确认生成吗？")
                .setOnNegativeClickListener("", v -> dialog.cancel())
                .setOnPositiveClickedListener("", v -> {
                    Config.haveCheat = true;
                    gameView.addDigital(true);
                    Toast.makeText(GameActivity.this, "好了，就帮你到这了...", Toast.LENGTH_SHORT).show();
                    dialog.cancel();
                }).show();
        cheatStar.setVisibility(View.INVISIBLE);
    }

    /**
     * 打开切换模式对话框
     */
    private void showChangeModeDialog() {
        String subject = "";
        if (Config.CurrentGameMode == 0) {
            subject = "无限模式";
        } else if (Config.CurrentGameMode == 1) {
            subject = "经典模式";
        }
        CustomCommonDialog dialog = new CustomCommonDialog(this, R.style.CustomDialog);
        dialog.setCancelable(true);
        dialog.setTitle(getResources().getString(R.string.tip))
                .setMessage("是否要切换到" + subject)
                .setOnPositiveClickedListener("", v -> {
                    if (Config.CurrentGameMode == 0) {
                        Toast.makeText(GameActivity.this, "已进入无限模式", Toast.LENGTH_SHORT).show();
                        enterInfiniteMode();
                    } else {
                        Toast.makeText(GameActivity.this, "已进入经典模式", Toast.LENGTH_SHORT).show();
                        enterClassicsMode();
                    }
                    dialog.cancel();
                })
                .setOnNegativeClickListener("", v -> dialog.cancel())
                .show();
    }

    /**
     * 进入无限模式
     */
    private void enterInfiniteMode() {
        Config.haveCheat = false;
        Config.CurrentGameMode = 1;
        // 保存游戏模式
        ConfigManager.putCurrentGameMode(this, 1);
        titleDescribe.setText(getResources().getString(R.string.game_mode_infinite));
        bestScores.setText(String.valueOf(Config.BestScoreWithinInfinite));
        bestScoresRank.setText(getResources().getText(R.string.tv_best_score_infinite));
        currentScores.setText("0");
        modeDescribe.setText(getResources().getString(R.string.tv_describe_infinite));
        setTextStyle(titleDescribe);
        gameView.initView(1);
    }

    /**
     * 进入经典模式
     */
    private void enterClassicsMode() {
        Config.haveCheat = false;
        Config.CurrentGameMode = 0;
        // 保存游戏模式
        ConfigManager.putCurrentGameMode(this, 0);
        titleDescribe.setText(getResources().getString(R.string.game_mode_classics));
        // 读取到历史最高分
        bestScores.setText(String.valueOf(Config.BestScore));
        bestScoresRank.setText(getString(R.string.best_score_rank, Config.GRIDColumnCount));
        currentScores.setText("0");
        modeDescribe.setText(getResources().getString(R.string.tv_describe));
        setTextStyle(titleDescribe);
        gameView.initView(0);

    }

    /**
     * 设置模式字体颜色大小加粗
     */
    private void setTextStyle(TextView textView) {
        SpannableString spannableString = new SpannableString(textView.getText().toString());
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        ForegroundColorSpan foregroundColorSpan = new ForegroundColorSpan(Color.parseColor("#393E46"));
        AbsoluteSizeSpan absoluteSizeSpan = new AbsoluteSizeSpan(52);
        spannableString.setSpan(styleSpan, 0, 4, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        spannableString.setSpan(foregroundColorSpan, 0, 4, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        spannableString.setSpan(absoluteSizeSpan, 0, 4, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        textView.setText(spannableString);
    }

    /**
     * 打开重置确认对话框
     */
    private void showConfirmDialog() {
        CustomCommonDialog dialog = new CustomCommonDialog(this, R.style.CustomDialog);
        dialog.setCancelable(false);
        dialog.setTitle(getResources().getString(R.string.tip))
                .setMessage(getResources().getString(R.string.tip_reset_btn))
                .setOnNegativeClickListener("", v -> dialog.cancel())
                .setOnPositiveClickedListener("", v -> {
                    Config.haveCheat = false;
                    gameView.resetGame();
                    // 重置分数
                    currentScores.setText("0");
                    deleteCache(Config.getTableName());
                    dialog.cancel();
                }).show();
    }

    /**
     * 打开配置对话框
     */
    private void showConfigDialog() {
        configDialog = new CustomConfigDialog(this, R.style.CustomDialog);
        configDialog.setOnNegativeClickListener(v -> configDialog.cancel())
                .setOnPositiveClickListener(v -> onDialogConfirm())
                .show();
    }

    /**
     * 配置对话框的确认按钮监听
     */
    private void onDialogConfirm() {
        // 获取选择的难度和音效状态
        int difficulty = configDialog.getSelectDifficulty();
        boolean volumeState = configDialog.getVolumeState();
        // 若选择的难度与当前难度一样
        if (difficulty == Config.GRIDColumnCount) {
            // 判断音效配置是否更改
            if (volumeState != Config.VolumeState) {
                // 保存音效设置
                ConfigManager.putGameVolume(this, volumeState);
                Config.VolumeState = volumeState;
            }
            configDialog.cancel();
            return;
        }

        changeConfiguration(configDialog, difficulty, volumeState);
        Config.haveCheat = false;
    }

    /**
     * 更改游戏配置
     */
    private void changeConfiguration(CustomConfigDialog dialog, int difficulty, boolean volumeState) {
        Config.GRIDColumnCount = difficulty;
        Config.VolumeState = volumeState;
        gameView.initView(0);
        // 重置得分
        currentScores.setText("0");
        bestScoresRank.setText(getString(R.string.best_score_rank, difficulty));
        bestScores.setText(String.valueOf(ConfigManager.getBestScore(this)));
        // 保存游戏难度
        ConfigManager.putGameDifficulty(GameActivity.this, difficulty);
        // 保存音效设置
        ConfigManager.putGameVolume(this, volumeState);
        dialog.cancel();
    }

    /**
     * 记录得分
     */
    private void recordScore(int score) {
        // 获取历史分数
        int historyScore = Integer.parseInt(currentScores.getText().toString());
        // 得出当前分数
        int currentScore = score + historyScore;
        currentScores.setText(String.valueOf(currentScore));
        // 当前分数大于最高分
        if (Config.CurrentGameMode == 0) {
            if (currentScore > ConfigManager.getBestScore(this)) {
                updateBestScore(currentScore);
            }
        } else if (Config.CurrentGameMode == 1) {
            if (currentScore > ConfigManager.getBestScoreWithinInfinite(this)) {
                updateBestScore(currentScore);
            }
        }
    }

    /**
     * 更新最高分
     */
    private void updateBestScore(int newScore) {
        bestScores.setText(String.valueOf(newScore));
        if (Config.CurrentGameMode == 0) {
            Config.BestScore = newScore;
            ConfigManager.putBestScore(this, newScore);
        } else if (Config.CurrentGameMode == 1) {
            Config.BestScoreWithinInfinite = newScore;
            ConfigManager.putBestScoreWithinInfinite(this, newScore);
        }
    }


    /**
     * 自定义广播类
     */
    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            assert action != null;
            if (action.equals(GameView.ACTION_RECORD_SCORE)) {
                // 获取分数
                int score = intent.getIntExtra(GameView.KEY_SCORE, 0);
                recordScore(score);
                // 你赢啦
            } else if (action.equals(GameView.ACTION_WIN)
                    || action.equals(GameView.ACTION_LOSE)) {
                String result = intent.getStringExtra(GameView.KEY_RESULT);
                CustomOverDialog dialog = new CustomOverDialog(
                        GameActivity.this, R.style.CustomDialog);
                dialog.setCancelable(false);
                new Handler().postDelayed(() ->
                        dialog.setFinalScore(currentScores.getText().toString())
                                .setTitle(result)
                                .setOnShareClickListener(v -> share())
                                .setOnGoOnClickListener(v -> {
                                    gameView.initView(0);
                                    currentScores.setText("0");
                                    dialog.cancel();
                                }).show(), 666);
            }
        }
    }

    /**
     * 判断颜色是否是亮色
     */
    private boolean isLightColor(int color) {
        return ColorUtils.calculateLuminance(color) >= 0.5;
    }

    /**
     * 分享
     */
    private void share() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                getResources().getString(R.string.share, currentScores.getText().toString()));
        shareIntent = Intent.createChooser(shareIntent, "分享到");
        startActivity(shareIntent);
    }

    @Override
    public void onBackPressed() {
        CustomCommonDialog dialog = new CustomCommonDialog(this, R.style.CustomDialog);
        dialog.setCancelable(false);
        dialog.setTitle("确认退出")
                .setMessage("")
                .setOnNegativeClickListener("狠心离开",
                        v -> {
                            saveGameProgress();
                            finish();
                        })
                .setOnPositiveClickedListener("我还要玩一会", v -> dialog.cancel())
                .show();
    }

    private void saveGameProgress() {
        String tableName = Config.getTableName();
        deleteCache(tableName);
        // 保存新的数据
        ArrayList<CellEntity> data = gameView.getCurrentProcess();
        if (data.size() > 2) {
            ContentValues values = new ContentValues();
            for (CellEntity cell : data) {
                values.put("x", cell.getX());
                values.put("y", cell.getY());
                values.put("num", cell.getNum());
                db.insert(tableName, null, values);
                values.clear();
            }
        }
    }

    /**
     * 清空缓存
     */
    private void deleteCache(String tableName) {
        db.execSQL("delete from " + tableName);
    }

}
