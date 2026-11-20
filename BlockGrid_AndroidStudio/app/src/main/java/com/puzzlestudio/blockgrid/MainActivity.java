package com.puzzlestudio.blockgrid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import java.util.Calendar;
import java.util.Locale;

/**
 * Block Grid - a standalone 8x8 block-placement puzzle for Android.
 * All artwork is drawn in code; no third-party assets or libraries are needed.
 */
public class MainActivity extends Activity {
    private static final int BOARD_SIZE = 8;
    private GameView gameView;
    private static final int PICK_BLOCK_IMAGE = 7314;

    private void openBlockImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_BLOCK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BLOCK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            if (gameView != null) gameView.setBlockImage(uri);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(12, 17, 34));
        window.setNavigationBarColor(Color.rgb(12, 17, 34));
        window.getDecorView().setSystemUiVisibility(0);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.saveSession();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (gameView == null || !gameView.handleBackPressed()) super.onBackPressed();
    }

    private static final class Piece {
        final int[][] cells;
        final int color;
        final int width;
        final int height;

        Piece(int[][] source, int blockColor) {
            cells = new int[source.length][2];
            int maxX = 0;
            int maxY = 0;
            for (int i = 0; i < source.length; i++) {
                cells[i][0] = source[i][0];
                cells[i][1] = source[i][1];
                if (source[i][0] > maxX) maxX = source[i][0];
                if (source[i][1] > maxY) maxY = source[i][1];
            }
            width = maxX + 1;
            height = maxY + 1;
            color = blockColor;
        }
    }

    private static final class Snapshot {
        final int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
        final Piece[] pieces = new Piece[3];
        final int score;
        final int combo;
        final int piecesSinceClear;
        final int totalLines;
        final int boardClears;
        final int bestCombo;
        final int levelLines;
        final int movesRemaining;

        Snapshot(int[][] sourceBoard, Piece[] sourcePieces, int sourceScore,
                 int sourceCombo, int sourcePiecesSinceClear, int sourceTotalLines,
                 int sourceBoardClears, int sourceBestCombo, int sourceLevelLines,
                 int sourceMovesRemaining) {
            for (int r = 0; r < BOARD_SIZE; r++) {
                System.arraycopy(sourceBoard[r], 0, board[r], 0, BOARD_SIZE);
            }
            for (int i = 0; i < 3; i++) pieces[i] = copyPiece(sourcePieces[i]);
            score = sourceScore;
            combo = sourceCombo;
            piecesSinceClear = sourcePiecesSinceClear;
            totalLines = sourceTotalLines;
            boardClears = sourceBoardClears;
            bestCombo = sourceBestCombo;
            levelLines = sourceLevelLines;
            movesRemaining = sourceMovesRemaining;
        }
    }

    private static Piece copyPiece(Piece p) {
        return p == null ? null : new Piece(p.cells, p.color);
    }

    private static final class GameView extends View {
        private static final int SIZE = BOARD_SIZE;
        private static final int EMPTY = 0;

        private static final int[] PALETTE = new int[] {
                Color.rgb(255, 104, 133),
                Color.rgb(44, 220, 205),
                Color.rgb(255, 190, 72),
                Color.rgb(177, 119, 255),
                Color.rgb(77, 160, 255),
                Color.rgb(153, 231, 112)
        };

        private static final int[][][] SHAPES = new int[][][] {
                {{0,0}},
                {{0,0},{1,0}},
                {{0,0},{0,1}},
                {{0,0},{1,0},{2,0}},
                {{0,0},{0,1},{0,2}},
                {{0,0},{1,0},{0,1}},
                {{0,0},{1,0},{2,0},{3,0}},
                {{0,0},{0,1},{0,2},{0,3}},
                {{0,0},{1,0},{0,1},{1,1}},
                {{0,0},{0,1},{0,2},{1,2}},
                {{1,0},{0,1},{1,1},{2,1}},
                {{0,0},{1,0},{1,1},{2,1}},
                {{0,0},{1,0},{2,0},{0,1},{0,2}},
                {{0,0},{1,0},{2,0},{1,1},{1,2}},
                {{1,0},{0,1},{1,1},{2,1},{1,2}},
                {{0,0},{1,0},{0,1},{1,1},{2,0},{2,1}},
                {{0,0},{1,0},{2,0},{0,1},{1,1},{2,1}},
                {{0,0},{0,1},{1,1},{2,1},{2,2}},
                {{0,0},{1,0},{2,0},{3,0},{4,0}},
                {{0,0},{0,1},{0,2},{0,3},{0,4}}
        };

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap blockImage;
        private String blockImageUri = "";
        private final RectF tempRect = new RectF();
        private final RectF[] cardRects = new RectF[] {new RectF(), new RectF(), new RectF()};
        private final RectF pauseRect = new RectF();
        private final RectF soundRect = new RectF();
        private final RectF primaryRect = new RectF();
        private final RectF secondaryRect = new RectF();
        private final RectF reviveRect = new RectF();
        private boolean reviveUsed = false;
        private final RectF modeRect = new RectF();
        private final RectF bestRect = new RectF();
        private final RectF[] toolRects = new RectF[] {
                new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF()
        };
        private final RectF settingsRect = new RectF();
        private final RectF settingsThemeRect = new RectF();
        private final RectF settingsSoundRect = new RectF();
        private final RectF settingsVibrationRect = new RectF();
        private final RectF settingsHintsRect = new RectF();
        private final RectF settingsHelpRect = new RectF();
        private final RectF settingsCloseRect = new RectF();
        private final RectF settingsImageRect = new RectF();
        private final RectF helpCloseRect = new RectF();
        private final RectF statsCloseRect = new RectF();
        private final int[][] board = new int[SIZE][SIZE];
        private final Piece[] pieces = new Piece[3];
        private long randomState = 0x13579BDF2468ACE0L;
        private final SharedPreferences preferences;

        private static final int TOOL_BOMB = 0;
        private static final int TOOL_ROTATE = 1;
        private static final int TOOL_UNDO = 2;
        private static final int TOOL_SHUFFLE = 3;
        private static final int TOOL_HAMMER = 4;
        private static final int TOOL_CROSS = 5;

        private ToneGenerator tone;
        private int score = 0;
        private int bestScore = 0;
        private int selectedIndex = -1;
        private int pressedSlot = -1;
        private int pressedTool = -1;
        private boolean dragging = false;
        private boolean paused = false;
        private boolean gameOver = false;
        private boolean statsShown = false;
        private boolean settingsShown = false;
        private boolean helpShown = false;
        private boolean vibrationOn = true;
        private boolean hintsOn = true;
        private int themeIndex = 0;
        private int targetTool = -1;
        // 0 = Classic, 1 = Daily Challenge, 2 = Adventure.
        private int gameMode = 0;
        private int adventureLevel = 1;
        private int adventureStars = 0;
        private int levelLines = 0;
        private int goalLines = 4;
        private int movesPerLevel = 18;
        private int movesRemaining = 18;
        private boolean levelWon = false;
        private boolean bombArmed = false;
        private boolean soundOn = true;
        private int bombCharges = 2;
        private int rotateCharges = 3;
        private int undoCharges = 3;
        private int shuffleCharges = 2;
        private int hammerCharges = 3;
        private int crossCharges = 2;
        private int combo = 0;
        private int piecesSinceClear = 0;
        private int bestCombo = 0;
        private int totalLines = 0;
        private int boardClears = 0;
        private int gamesPlayed = 0;
        private int piecesPlaced = 0;
        private int totalPiecesPlaced = 0;
        private int coins = 0;
        private int dailyMissionProgress = 0;
        private boolean dailyMissionClaimed = false;
        private final int dailyMissionGoal = 5;
        private final int[][] flashColors = new int[SIZE][SIZE];
        private long clearFlashStart = 0L;
        private Snapshot lastSnapshot;
        private long gameStartMs = 0L;
        private String dailyKey = "";
        private float pointerX;
        private float pointerY;
        private int hoverRow = -1;
        private int hoverCol = -1;
        private String message = "TAP A SHAPE, THEN PLACE IT";
        private long messageUntil = 0L;

        private float density;
        private float viewW;
        private float viewH;
        private float boardLeft;
        private float boardTop;
        private float boardSize;
        private float gridPad;
        private float gridGap;
        private float cellSize;
        private float trayTop;
        private float cardWidth;
        private float cardHeight;

        GameView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            preferences = context.getSharedPreferences("block_grid_save", Context.MODE_PRIVATE);
            blockImageUri = preferences.getString("custom_block_image_uri", "");
            loadBlockImage();
            bestScore = preferences.getInt("best_score", 0);
            soundOn = preferences.getBoolean("setting_sound", true);
            vibrationOn = preferences.getBoolean("setting_vibration", true);
            hintsOn = preferences.getBoolean("setting_hints", true);
            themeIndex = Math.max(0, Math.min(2, preferences.getInt("setting_theme", 0)));
            coins = preferences.getInt("wallet_coins", 0);
            totalLines = preferences.getInt("stat_total_lines", 0);
            bestCombo = preferences.getInt("stat_best_combo", 0);
            boardClears = preferences.getInt("stat_board_clears", 0);
            gamesPlayed = preferences.getInt("stat_games_played", 0);
            piecesPlaced = 0;
            totalPiecesPlaced = preferences.getInt("stat_total_pieces_placed", 0);
            adventureLevel = preferences.getInt("adventure_unlocked_level", 1);
            adventureStars = preferences.getInt("adventure_total_stars", 0);
            setFocusable(true);
            setContentDescription("Block Grid, an 8 by 8 block puzzle game");
            try {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 35);
            } catch (Throwable ignored) {
                tone = null;
            }
            if (!loadSession()) newGame();
        }

        void setBlockImage(Uri uri) {
            if (uri == null) return;
            blockImageUri = uri.toString();
            loadBlockImage();
            preferences.edit().putString("custom_block_image_uri", blockImageUri).apply();
            message = blockImage != null ? "CUSTOM BLOCK IMAGE APPLIED" : "COULD NOT LOAD IMAGE";
            messageUntil = System.currentTimeMillis() + 2200L;
            invalidate();
        }

        private void loadBlockImage() {
            blockImage = null;
            if (blockImageUri == null || blockImageUri.length() == 0) return;
            try {
                android.net.Uri uri = android.net.Uri.parse(blockImageUri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(getContext().getContentResolver().openInputStream(uri), null, options);
                options.inSampleSize = Math.max(1, Math.max(options.outWidth, options.outHeight) / 512);
                options.inJustDecodeBounds = false;
                java.io.InputStream stream = getContext().getContentResolver().openInputStream(uri);
                blockImage = BitmapFactory.decodeStream(stream, null, options);
                if (stream != null) stream.close();
            } catch (Exception ignored) { blockImage = null; }
        }

        private float dp(float value) {
            return value * density;
        }

        private void computeLayout() {
            viewW = getWidth();
            viewH = getHeight();
            if (viewW <= 0 || viewH <= 0) return;

            boardSize = Math.min(viewW - dp(34), viewH * 0.42f);
            boardSize = Math.max(dp(190), boardSize);
            boardLeft = (viewW - boardSize) / 2f;
            boardTop = Math.max(dp(126), viewH * 0.215f);
            gridPad = dp(9);
            gridGap = dp(3);
            cellSize = (boardSize - gridPad * 2f - gridGap * (SIZE - 1)) / SIZE;

            trayTop = boardTop + boardSize + dp(22);
            cardWidth = (viewW - dp(52)) / 3f;
            cardHeight = Math.min(dp(108), Math.max(dp(76), viewH * 0.145f));
            float cardGap = dp(10);
            float totalWidth = cardWidth * 3f + cardGap * 2f;
            float startX = (viewW - totalWidth) / 2f;
            for (int i = 0; i < 3; i++) {
                cardRects[i].set(startX + i * (cardWidth + cardGap), trayTop,
                        startX + i * (cardWidth + cardGap) + cardWidth, trayTop + cardHeight);
            }

            modeRect.set(dp(18), dp(48), Math.min(viewW - dp(150), dp(185)), dp(68));
            float scoreTop = dp(76);
            float scoreHeight = dp(42);
            float scoreGap = dp(10);
            float side = dp(20);
            float scoreWidth = (viewW - side * 2f - scoreGap) / 2f;
            bestRect.set(side + scoreWidth + scoreGap, scoreTop,
                    side + scoreWidth + scoreGap + scoreWidth, scoreTop + scoreHeight);

            float buttonSize = dp(38);
            pauseRect.set(viewW - dp(52), dp(21), viewW - dp(14), dp(21) + buttonSize);
            soundRect.set(viewW - dp(98), dp(21), viewW - dp(60), dp(21) + buttonSize);
            settingsRect.set(viewW - dp(144), dp(21), viewW - dp(106), dp(21) + buttonSize);

            float toolTop = trayTop + cardHeight + dp(8);
            float toolHeight = dp(39);
            float toolGap = dp(4);
            float toolWidth = (viewW - dp(40) - toolGap * 5f) / 6f;
            for (int i = 0; i < toolRects.length; i++) {
                float left = dp(20) + i * (toolWidth + toolGap);
                toolRects[i].set(left, toolTop, left + toolWidth, toolTop + toolHeight);
            }

            float panelW = Math.min(viewW - dp(48), dp(320));
            float panelH = dp(280);
            float panelLeft = (viewW - panelW) / 2f;
            float panelTop = (viewH - panelH) / 2f;
            reviveRect.set(panelLeft + dp(20), panelTop + dp(145), panelLeft + panelW - dp(20), panelTop + dp(187));
            primaryRect.set(panelLeft + dp(20), panelTop + dp(193), panelLeft + panelW - dp(20), panelTop + dp(235));
            secondaryRect.set(panelLeft + dp(20), panelTop + dp(241), panelLeft + panelW - dp(20), panelTop + dp(273));

            float settingsW = Math.min(viewW - dp(38), dp(330));
            float settingsLeft = (viewW - settingsW) / 2f;
            float settingsTop = (viewH - dp(440)) / 2f;
            settingsThemeRect.set(settingsLeft + dp(20), settingsTop + dp(82), settingsLeft + settingsW - dp(20), settingsTop + dp(124));
            settingsSoundRect.set(settingsLeft + dp(20), settingsTop + dp(132), settingsLeft + settingsW - dp(20), settingsTop + dp(174));
            settingsVibrationRect.set(settingsLeft + dp(20), settingsTop + dp(182), settingsLeft + settingsW - dp(20), settingsTop + dp(224));
            settingsHintsRect.set(settingsLeft + dp(20), settingsTop + dp(232), settingsLeft + settingsW - dp(20), settingsTop + dp(274));
            settingsHelpRect.set(settingsLeft + dp(20), settingsTop + dp(282), settingsLeft + settingsW - dp(20), settingsTop + dp(324));
            settingsImageRect.set(settingsLeft + dp(20), settingsTop + dp(332), settingsLeft + settingsW - dp(20), settingsTop + dp(374));
            settingsCloseRect.set(settingsLeft + dp(20), settingsTop + dp(382), settingsLeft + settingsW - dp(20), settingsTop + dp(424));
            float helpTop = (viewH - dp(300)) / 2f;
            helpCloseRect.set(settingsLeft + dp(20), helpTop + dp(244), settingsLeft + settingsW - dp(20), helpTop + dp(286));
            float statsTop = (viewH - dp(320)) / 2f;
            statsCloseRect.set(settingsLeft + dp(20), statsTop + dp(264), settingsLeft + settingsW - dp(20), statsTop + dp(306));
        }

        private String currentDayKey() {
            Calendar cal = Calendar.getInstance();
            return String.format(Locale.US, "%04d%02d%02d", cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        }

        private boolean isDailyMode() { return false; }
        private boolean isAdventureMode() { return false; }

        private String modeLabel() {
            if (isDailyMode()) return "DAILY CHALLENGE • TAP TO SWITCH";
            if (isAdventureMode()) return "ADVENTURE • TAP TO SWITCH";
            return "CLASSIC MODE • TAP TO SWITCH";
        }

        private String bestPreferenceKey() {
            if (isDailyMode()) return "daily_best_" + currentDayKey();
            if (isAdventureMode()) return "adventure_best_level_" + adventureLevel;
            return "best_score";
        }

        private void newGame() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) board[r][c] = EMPTY;
            }
            score = 0;
            selectedIndex = -1;
            pressedSlot = -1;
            pressedTool = -1;
            dragging = false;
            paused = false;
            gameOver = false;
            reviveUsed = false;
            statsShown = false;
            settingsShown = false;
            helpShown = false;
            levelWon = false;
            bombArmed = false;
            targetTool = -1;
            hoverRow = -1;
            hoverCol = -1;
            combo = 0;
            piecesSinceClear = 0;
            piecesPlaced = 0;
            bombCharges = 2;
            rotateCharges = 3;
            undoCharges = 3;
            shuffleCharges = 2;
            hammerCharges = 3;
            crossCharges = 2;
            lastSnapshot = null;
            gameStartMs = System.currentTimeMillis();
            dailyKey = currentDayKey();
            dailyMissionProgress = preferences.getInt("mission_progress_" + dailyKey, 0);
            dailyMissionClaimed = preferences.getBoolean("mission_claimed_" + dailyKey, false);
            bestScore = preferences.getInt(bestPreferenceKey(), 0);
            if (isDailyMode()) {
                long seed = 0x4B10C6L;
                try { seed += Long.parseLong(dailyKey); } catch (Exception ignored) { }
                randomState = seed == 0L ? 0x13579BDF2468ACE0L : seed;
                levelLines = 0;
                movesRemaining = 0;
            } else if (isAdventureMode()) {
                goalLines = Math.min(10, 3 + (adventureLevel - 1) / 2);
                movesPerLevel = 18 + Math.min(10, (adventureLevel - 1) / 3);
                movesRemaining = movesPerLevel;
                levelLines = 0;
                randomState = 0xA2D3L + adventureLevel * 7181L;
            } else {
                randomState = System.currentTimeMillis() ^ 0x5DEECE66DL;
                levelLines = 0;
                movesRemaining = 0;
            }
            gamesPlayed++;
            preferences.edit().putInt("stat_games_played", gamesPlayed).apply();
            checkAchievements();
            if (isDailyMode()) message = "DAILY CHALLENGE • SAME PUZZLE FOR EVERYONE";
            else if (isAdventureMode()) message = "LEVEL " + adventureLevel + " • CLEAR " + goalLines + " LINES IN " + movesRemaining + " MOVES";
            else message = "PLACE BLOCKS TO COMPLETE ROWS + COLUMNS";
            messageUntil = System.currentTimeMillis() + 3500L;
            generatePieces();
            saveSession();
            invalidate();
        }

        private int nextRandomInt(int bound) {
            if (bound <= 1) return 0;
            randomState ^= (randomState << 13);
            randomState ^= (randomState >>> 7);
            randomState ^= (randomState << 17);
            if (randomState == 0L) randomState = 0x13579BDF2468ACE0L;
            return (int)((randomState & 0x7FFFFFFFFFFFFFFFL) % bound);
        }

        private void generatePieces() {
            boolean playable = false;
            for (int attempt = 0; attempt < 40 && !playable; attempt++) {
                for (int i = 0; i < 3; i++) {
                    int shapeIndex = nextRandomInt(SHAPES.length);
                    int colorIndex = nextRandomInt(PALETTE.length);
                    pieces[i] = new Piece(SHAPES[shapeIndex], PALETTE[colorIndex]);
                }
                playable = hasAnyMove();
            }
            selectedIndex = -1;
        }

        private void saveBestIfNeeded() {
            if (score > bestScore) {
                bestScore = score;
                preferences.edit().putInt(bestPreferenceKey(), bestScore).apply();
            }
            preferences.edit()
                    .putInt("stat_total_lines", totalLines)
                    .putInt("stat_best_combo", Math.max(bestCombo, combo))
                    .putInt("stat_board_clears", boardClears)
                    .putInt("stat_games_played", gamesPlayed)
                    .putInt("stat_total_pieces_placed", totalPiecesPlaced)
                    .putInt("wallet_coins", coins)
                    .apply();
        }

        private void saveSettings() {
            preferences.edit()
                    .putBoolean("setting_sound", soundOn)
                    .putBoolean("setting_vibration", vibrationOn)
                    .putBoolean("setting_hints", hintsOn)
                    .putInt("setting_theme", themeIndex)
                    .putInt("wallet_coins", coins)
                    .apply();
        }

        private int countUnlockedAchievements() {
            String[] ids = new String[] {"first10", "lines50", "boardclear", "combo5", "games10", "blocks100"};
            int count = 0;
            for (int i = 0; i < ids.length; i++) if (preferences.getBoolean("achievement_" + ids[i], false)) count++;
            return count;
        }

        private void unlockAchievement(String id, String title, int reward) {
            String key = "achievement_" + id;
            if (preferences.getBoolean(key, false)) return;
            preferences.edit().putBoolean(key, true).apply();
            coins += reward;
            saveSettings();
            showMessage("ACHIEVEMENT: " + title + "  +" + reward + " COINS");
        }

        private void checkAchievements() {
            if (totalLines >= 10) unlockAchievement("first10", "LINE STARTER", 20);
            if (totalLines >= 50) unlockAchievement("lines50", "LINE MASTER", 50);
            if (boardClears >= 1) unlockAchievement("boardclear", "PERFECT CLEAR", 35);
            if (bestCombo >= 5) unlockAchievement("combo5", "COMBO EXPERT", 40);
            if (gamesPlayed >= 10) unlockAchievement("games10", "REGULAR PLAYER", 25);
            if (totalPiecesPlaced >= 100) unlockAchievement("blocks100", "BLOCK BUILDER", 35);
        }

        private void updateDailyMission(int lines) {
            if (lines <= 0 || dailyMissionClaimed) return;
            dailyMissionProgress = Math.min(dailyMissionGoal, dailyMissionProgress + lines);
            if (dailyMissionProgress >= dailyMissionGoal) {
                dailyMissionClaimed = true;
                coins += 50;
                showMessage("DAILY MISSION COMPLETE  +50 COINS");
            }
            preferences.edit()
                    .putInt("mission_progress_" + dailyKey, dailyMissionProgress)
                    .putBoolean("mission_claimed_" + dailyKey, dailyMissionClaimed)
                    .putInt("wallet_coins", coins)
                    .apply();
        }

        private void saveSession() {
            if (preferences == null) return;
            StringBuilder b = new StringBuilder();
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (b.length() > 0) b.append(',');
                    b.append(board[r][c]);
                }
            }
            StringBuilder p = new StringBuilder();
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0) p.append('|');
                Piece piece = pieces[i];
                if (piece == null) { p.append('-'); continue; }
                p.append(piece.color).append('~');
                for (int k = 0; k < piece.cells.length; k++) {
                    if (k > 0) p.append(';');
                    p.append(piece.cells[k][0]).append(',').append(piece.cells[k][1]);
                }
            }
            preferences.edit()
                    .putString("session_board", b.toString())
                    .putString("session_pieces", p.toString())
                    .putInt("session_score", score)
                    .putLong("session_random_state", randomState)
                    .putInt("session_mode", gameMode)
                    .putInt("session_level", adventureLevel)
                    .putInt("session_level_lines", levelLines)
                    .putInt("session_moves", movesRemaining)
                    .putInt("session_goal", goalLines)
                    .putInt("session_moves_per_level", movesPerLevel)
                    .putInt("session_bomb", bombCharges)
                    .putInt("session_rotate", rotateCharges)
                    .putInt("session_undo", undoCharges)
                    .putInt("session_shuffle", shuffleCharges)
                    .putInt("session_hammer", hammerCharges)
                    .putInt("session_cross", crossCharges)
                    .putInt("session_combo", combo)
                    .putInt("session_since_clear", piecesSinceClear)
                    .putInt("session_total_lines", totalLines)
                    .putInt("session_board_clears", boardClears)
                    .putInt("session_pieces_placed", piecesPlaced)
                    .putInt("session_total_pieces_placed", totalPiecesPlaced)
                    .putBoolean("session_game_over", gameOver)
                    .putBoolean("session_level_won", levelWon)
                    .putBoolean("session_exists", true)
                    .putString("session_day", currentDayKey())
                    .apply();
        }

        private boolean loadSession() {
            if (!preferences.getBoolean("session_exists", false)) return false;
            String savedDay = preferences.getString("session_day", "");
            int savedMode = preferences.getInt("session_mode", 0);
            if (savedMode < 0 || savedMode > 2) return false;
            if (savedMode == 1 && !currentDayKey().equals(savedDay)) return false;
            String boardData = preferences.getString("session_board", "");
            String pieceData = preferences.getString("session_pieces", "");
            String[] values = boardData.split(",");
            if (values.length != SIZE * SIZE) return false;
            try {
                int index = 0;
                for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) board[r][c] = Integer.parseInt(values[index++]);
                String[] savedPieces = pieceData.split("\\|", -1);
                if (savedPieces.length != 3) return false;
                for (int i = 0; i < 3; i++) {
                    if ("-".equals(savedPieces[i])) { pieces[i] = null; continue; }
                    String[] parts = savedPieces[i].split("~", 2);
                    if (parts.length != 2) return false;
                    int color = Integer.parseInt(parts[0]);
                    String[] coords = parts[1].split(";");
                    int[][] cells = new int[coords.length][2];
                    for (int k = 0; k < coords.length; k++) {
                        String[] xy = coords[k].split(",");
                        if (xy.length != 2) return false;
                        cells[k][0] = Integer.parseInt(xy[0]);
                        cells[k][1] = Integer.parseInt(xy[1]);
                        if (cells[k][0] < 0 || cells[k][0] > 5 || cells[k][1] < 0 || cells[k][1] > 5) return false;
                    }
                    pieces[i] = new Piece(cells, color);
                }
                gameMode = savedMode;
                adventureLevel = Math.max(1, preferences.getInt("session_level", 1));
                levelLines = preferences.getInt("session_level_lines", 0);
                movesRemaining = preferences.getInt("session_moves", 0);
                goalLines = Math.max(1, preferences.getInt("session_goal", 4));
                movesPerLevel = Math.max(1, preferences.getInt("session_moves_per_level", 18));
                score = preferences.getInt("session_score", 0);
                randomState = preferences.getLong("session_random_state", 0x13579BDF2468ACE0L);
                if (randomState == 0L) randomState = 0x13579BDF2468ACE0L;
                bombCharges = preferences.getInt("session_bomb", 0);
                rotateCharges = preferences.getInt("session_rotate", 0);
                undoCharges = preferences.getInt("session_undo", 0);
                shuffleCharges = preferences.getInt("session_shuffle", 0);
                hammerCharges = preferences.getInt("session_hammer", 0);
                crossCharges = preferences.getInt("session_cross", 0);
                combo = preferences.getInt("session_combo", 0);
                piecesSinceClear = preferences.getInt("session_since_clear", 0);
                totalLines = Math.max(totalLines, preferences.getInt("session_total_lines", 0));
                boardClears = Math.max(boardClears, preferences.getInt("session_board_clears", 0));
                piecesPlaced = preferences.getInt("session_pieces_placed", 0);
                totalPiecesPlaced = Math.max(totalPiecesPlaced, preferences.getInt("session_total_pieces_placed", 0));
                gameOver = preferences.getBoolean("session_game_over", false);
                levelWon = preferences.getBoolean("session_level_won", false);
                selectedIndex = -1;
                targetTool = -1;
                paused = !gameOver;
                statsShown = false;
                settingsShown = false;
                helpShown = false;
                dailyKey = currentDayKey();
                dailyMissionProgress = Math.max(0, Math.min(dailyMissionGoal, preferences.getInt("mission_progress_" + dailyKey, 0)));
                dailyMissionClaimed = preferences.getBoolean("mission_claimed_" + dailyKey, false);
                bestScore = preferences.getInt(bestPreferenceKey(), 0);
                showMessage("GAME RESTORED • TAP CONTINUE");
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        public boolean handleBackPressed() {
            if (helpShown) helpShown = false;
            else if (settingsShown) settingsShown = false;
            else if (statsShown) statsShown = false;
            else if (paused || gameOver) return false;
            else paused = true;
            saveSession();
            invalidate();
            return true;
        }

        private Snapshot makeSnapshot() {
            return new Snapshot(board, pieces, score, combo, piecesSinceClear,
                    totalLines, boardClears, bestCombo, levelLines, movesRemaining);
        }

        private void restoreSnapshot(Snapshot snap) {
            for (int r = 0; r < SIZE; r++) {
                System.arraycopy(snap.board[r], 0, board[r], 0, SIZE);
            }
            for (int i = 0; i < 3; i++) pieces[i] = copyPiece(snap.pieces[i]);
            score = snap.score;
            combo = snap.combo;
            piecesSinceClear = snap.piecesSinceClear;
            totalLines = snap.totalLines;
            boardClears = snap.boardClears;
            bestCombo = snap.bestCombo;
            levelLines = snap.levelLines;
            movesRemaining = snap.movesRemaining;
            levelWon = false;
            selectedIndex = -1;
            dragging = false;
            bombArmed = false;
            hoverRow = -1;
            hoverCol = -1;
            saveBestIfNeeded();
        }

        private void showMessage(String text) {
            message = text;
            messageUntil = System.currentTimeMillis() + 1500L;
        }

        private void playTone(int type) {
            if (soundOn && tone != null) {
                try { tone.startTone(type, 85); } catch (Throwable ignored) { }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            computeLayout();
            drawBackground(canvas);
            drawHeader(canvas);
            drawScorePanels(canvas);
            drawBoard(canvas);
            drawPieceTray(canvas);
            drawToolTray(canvas);
            drawFooter(canvas);
            if (helpShown) drawHelpOverlay(canvas);
            else if (settingsShown) drawSettingsOverlay(canvas);
            else if (statsShown) drawStatsOverlay(canvas);
            else if (paused || gameOver) drawOverlay(canvas);
            // Soft ambient motion keeps the board alive without changing game rules.
            if (!paused && !gameOver && !settingsShown && !statsShown && !helpShown) {
                postInvalidateDelayed(34);
            }
        }

        private void drawBackground(Canvas canvas) {
            long now = System.currentTimeMillis();
            float t = now / 1000f;
            int c1 = themeIndex == 1 ? Color.rgb(29, 13, 48) : (themeIndex == 2 ? Color.rgb(230, 239, 251) : Color.rgb(7, 13, 32));
            int c2 = themeIndex == 1 ? Color.rgb(69, 23, 67) : (themeIndex == 2 ? Color.rgb(211, 226, 247) : Color.rgb(18, 27, 62));
            int c3 = themeIndex == 1 ? Color.rgb(15, 28, 56) : (themeIndex == 2 ? Color.rgb(244, 248, 255) : Color.rgb(8, 18, 43));
            paint.setShader(new LinearGradient(0, 0, viewW * 0.72f, viewH,
                    new int[] {c1, c2, c3}, new float[] {0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, viewW, viewH, paint);
            paint.setShader(null);

            // Two slow-moving aurora pools, layered with translucent rings.
            float ax = viewW * (0.82f + 0.10f * (float)Math.sin(t * 0.34f));
            float ay = viewH * (0.31f + 0.035f * (float)Math.cos(t * 0.28f));
            float bx = viewW * (0.10f + 0.055f * (float)Math.cos(t * 0.25f));
            float by = viewH * (0.78f + 0.045f * (float)Math.sin(t * 0.31f));
            int glowA = themeIndex == 2 ? 8 : 24;
            paint.setColor(Color.argb(glowA, 95, 117, 255));
            canvas.drawCircle(ax, ay, dp(142), paint);
            paint.setColor(Color.argb(glowA + 4, 30, 224, 210));
            canvas.drawCircle(bx, by, dp(118), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(themeIndex == 2 ? 12 : 25, 125, 190, 255));
            canvas.drawCircle(ax, ay, dp(96) + dp(5) * (float)Math.sin(t * 0.7f), paint);
            paint.setColor(Color.argb(themeIndex == 2 ? 10 : 20, 98, 237, 218));
            canvas.drawCircle(bx, by, dp(72) + dp(4) * (float)Math.cos(t * 0.6f), paint);
            paint.setStyle(Paint.Style.FILL);

            // Fine star particles gently twinkle in place.
            for (int i = 0; i < 30; i++) {
                float sx = (float)((i * 73 + 29) % 997) / 997f * viewW;
                float sy = (float)((i * 137 + 61) % 991) / 991f * viewH;
                float pulse = 0.5f + 0.5f * (float)Math.sin(t * (0.8f + (i % 5) * 0.17f) + i * 1.7f);
                int alpha = (int)((themeIndex == 2 ? 8 : 18) + pulse * (themeIndex == 2 ? 18 : 37));
                float radius = dp(0.7f + (i % 4) * 0.42f + pulse * 0.45f);
                paint.setColor(Color.argb(alpha, 155 + (i % 3) * 35, 195 + (i % 2) * 25, 255));
                canvas.drawCircle(sx, sy, radius, paint);
            }
        }

        private void drawHeader(Canvas canvas) {
            paint.setShader(new LinearGradient(dp(20), dp(20), dp(174), dp(48),
                    themeIndex == 2
                            ? new int[] {Color.rgb(24, 75, 132), Color.rgb(80, 75, 190), Color.rgb(19, 150, 151)}
                            : new int[] {Color.rgb(115, 220, 255), Color.rgb(183, 145, 255), Color.rgb(91, 245, 215)},
                    null, Shader.TileMode.CLAMP));
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(dp(23));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("BLOCK GRID", dp(20), dp(43), paint);
            paint.setShader(null);
            paint.setColor(Color.argb(themeIndex == 2 ? 90 : 125, 92, 212, 255));
            canvas.drawRoundRect(dp(20), dp(49), dp(112), dp(51), dp(2), dp(2), paint);

            paint.setColor(themeIndex == 2 ? Color.rgb(75, 96, 124) : Color.rgb(150, 164, 199));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(dp(9));
            
            canvas.drawText("CLASSIC BLOCK PUZZLE", dp(21), dp(60), paint);
            

            drawCircleButton(canvas, soundRect, soundOn ? "♪" : "×");
            drawCircleButton(canvas, pauseRect, "Ⅱ");
        }

        private void drawCircleButton(Canvas canvas, RectF rect, String label) {
            paint.setColor(Color.rgb(34, 44, 72));
            canvas.drawRoundRect(rect, dp(13), dp(13), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(63, 77, 113));
            canvas.drawRoundRect(rect, dp(13), dp(13), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(236, 242, 255));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(dp(21));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, rect.centerX(), baseline, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawScorePanels(Canvas canvas) {
            float top = dp(76);
            float height = dp(42);
            float gap = dp(10);
            float side = dp(20);
            float width = (viewW - side * 2f - gap) / 2f;
            drawScorePanel(canvas, side, top, width, height, "SCORE", String.valueOf(score), true);
            String bestLabel = isDailyMode() ? "TODAY'S BEST" : (isAdventureMode() ? "LEVEL BEST" : "BEST");
            drawScorePanel(canvas, side + width + gap, top, width, height, bestLabel, String.valueOf(bestScore), false);
        }

        private void drawScorePanel(Canvas canvas, float x, float y, float w, float h,
                                    String label, String value, boolean active) {
            paint.setShader(new LinearGradient(x, y, x + w, y + h,
                    active ? new int[] {Color.rgb(35, 65, 106), Color.rgb(24, 38, 70)}
                           : new int[] {Color.rgb(31, 43, 76), Color.rgb(21, 31, 58)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(x, y, x + w, y + h, dp(12), dp(12), paint);
            paint.setShader(null);
            paint.setColor(active ? Color.rgb(67, 104, 163) : Color.rgb(49, 64, 100));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            canvas.drawRoundRect(x, y, x + w, y + h, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            paint.setTextSize(dp(8));
            paint.setColor(Color.rgb(154, 174, 211));
            canvas.drawText(label, x + dp(12), y + dp(15), paint);
            paint.setTextSize(dp(16));
            paint.setColor(Color.WHITE);
            canvas.drawText(value, x + dp(12), y + dp(33), paint);
        }

        private void drawBoard(Canvas canvas) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setColor(Color.argb(themeIndex == 2 ? 28 : 42, 83, 185, 255));
            canvas.drawRoundRect(boardLeft - dp(2), boardTop - dp(2), boardLeft + boardSize + dp(2), boardTop + boardSize + dp(2), dp(21), dp(21), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize,
                    themeIndex == 2 ? new int[] {Color.rgb(218, 231, 248), Color.rgb(196, 214, 239)}
                                    : (themeIndex == 1 ? new int[] {Color.rgb(57, 37, 73), Color.rgb(34, 29, 61)}
                                                       : new int[] {Color.rgb(28, 42, 75), Color.rgb(17, 28, 55)}),
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize,
                    dp(18), dp(18), paint);
            paint.setShader(null);
            paint.setColor(themeIndex == 2 ? Color.rgb(167, 186, 211) : (themeIndex == 1 ? Color.rgb(100, 68, 101) : Color.rgb(47, 59, 91)));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            canvas.drawRoundRect(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize,
                    dp(18), dp(18), paint);
            paint.setStyle(Paint.Style.FILL);

            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    RectF cell = boardCellRect(r, c);
                    if (board[r][c] == EMPTY) {
                        int emptyTop = themeIndex == 2 ? Color.rgb(232, 240, 250) : (themeIndex == 1 ? Color.rgb(78, 54, 91) : Color.rgb(43, 58, 91));
                        int emptyBottom = themeIndex == 2 ? Color.rgb(211, 225, 243) : (themeIndex == 1 ? Color.rgb(53, 40, 72) : Color.rgb(29, 42, 72));
                        paint.setShader(new LinearGradient(cell.left, cell.top, cell.left, cell.bottom,
                                new int[] {emptyTop, emptyBottom}, null, Shader.TileMode.CLAMP));
                        canvas.drawRoundRect(cell, dp(4), dp(4), paint);
                        paint.setShader(null);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(0.6f));
                        paint.setColor(Color.argb(themeIndex == 2 ? 35 : 30, 145, 190, 255));
                        canvas.drawRoundRect(cell, dp(4), dp(4), paint);
                        paint.setStyle(Paint.Style.FILL);
                    } else {
                        drawBlock(canvas, cell.left, cell.top, cell.width(), board[r][c]);
                    }
                }
            }

            long flashAge = System.currentTimeMillis() - clearFlashStart;
            if (clearFlashStart > 0L && flashAge < 420L) {
                int alpha = (int)(150f * (1f - flashAge / 420f));
                for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
                    if (flashColors[r][c] != EMPTY) {
                        int color = flashColors[r][c];
                        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
                        RectF cell = boardCellRect(r, c);
                        canvas.drawRoundRect(cell, dp(5), dp(5), paint);
                    }
                }
                postInvalidateDelayed(16);
            } else if (clearFlashStart > 0L) {
                clearFlashStart = 0L;
                for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) flashColors[r][c] = EMPTY;
            }

            if (hintsOn && selectedIndex >= 0 && pieces[selectedIndex] != null && !dragging) {
                paint.setColor(Color.argb(90, 105, 180, 255));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.5f));
                Piece hintPiece = pieces[selectedIndex];
                boolean hintDrawn = false;
                for (int r = 0; r < SIZE && !hintDrawn; r++) {
                    for (int c = 0; c < SIZE && !hintDrawn; c++) {
                        if (canPlace(hintPiece, r, c)) {
                            for (int k = 0; k < hintPiece.cells.length; k++) {
                                RectF cell = boardCellRect(r + hintPiece.cells[k][1], c + hintPiece.cells[k][0]);
                                canvas.drawRoundRect(cell, dp(4), dp(4), paint);
                            }
                            hintDrawn = true;
                        }
                    }
                }
                paint.setStyle(Paint.Style.FILL);
            }
            if (dragging && selectedIndex >= 0 && pieces[selectedIndex] != null && hoverRow >= 0) {
                Piece p = pieces[selectedIndex];
                boolean valid = canPlace(p, hoverRow, hoverCol);
                int previewColor = valid ? p.color : Color.rgb(238, 91, 106);
                for (int i = 0; i < p.cells.length; i++) {
                    int r = hoverRow + p.cells[i][1];
                    int c = hoverCol + p.cells[i][0];
                    if (r >= 0 && r < SIZE && c >= 0 && c < SIZE) {
                        RectF cell = boardCellRect(r, c);
                        paint.setColor(Color.argb(valid ? 120 : 90,
                                Color.red(previewColor), Color.green(previewColor), Color.blue(previewColor)));
                        canvas.drawRoundRect(cell, dp(4), dp(4), paint);
                    }
                }
            }
        }

        private RectF boardCellRect(int row, int col) {
            float left = boardLeft + gridPad + col * (cellSize + gridGap);
            float top = boardTop + gridPad + row * (cellSize + gridGap);
            return new RectF(left, top, left + cellSize, top + cellSize);
        }

        private void drawBlock(Canvas canvas, float x, float y, float size, int color) {
            float radius = Math.max(dp(3), size * 0.19f);
            // Deep colored base gives every tile a soft 3D lift.
            paint.setShader(null);
            paint.setColor(darken(color, 0.58f));
            tempRect.set(x, y + dp(2), x + size, y + size + dp(2));
            canvas.drawRoundRect(tempRect, radius, radius, paint);

            // Glossy vertical gradient: bright upper edge, saturated lower edge.
            tempRect.set(x, y, x + size, y + size);
            if (blockImage != null && !blockImage.isRecycled()) {
                int save = canvas.save();
                Path clip = new Path();
                clip.addRoundRect(tempRect, radius, radius, Path.Direction.CW);
                canvas.clipPath(clip);
                paint.setShader(null);
                paint.setColor(Color.WHITE);
                RectF src = new RectF();
                float bw = blockImage.getWidth(), bh = blockImage.getHeight();
                float side = Math.min(bw, bh);
                src.set((bw - side) / 2f, (bh - side) / 2f, (bw + side) / 2f, (bh + side) / 2f);
                canvas.drawBitmap(blockImage, new android.graphics.Rect((int)src.left, (int)src.top, (int)src.right, (int)src.bottom), tempRect, paint);
                canvas.restoreToCount(save);
                paint.setColor(Color.argb(38, 0, 0, 0));
                canvas.drawRoundRect(tempRect, radius, radius, paint);
            } else {
                paint.setShader(new LinearGradient(x, y, x + size * 0.25f, y + size,
                        new int[] { lighten(color, 1.24f), color, darken(color, 0.82f) },
                        new float[] {0f, 0.56f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(tempRect, radius, radius, paint);
                paint.setShader(null);
            }

            // Fine luminous rim and top gloss.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(0.7f), size * 0.035f));
            paint.setColor(Color.argb(125, 255, 255, 255));
            tempRect.set(x + dp(0.6f), y + dp(0.6f), x + size - dp(0.6f), y + size - dp(0.6f));
            canvas.drawRoundRect(tempRect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(78, 255, 255, 255));
            float inset = Math.max(dp(2), size * 0.13f);
            tempRect.set(x + inset, y + inset, x + size - inset, y + Math.max(inset + dp(2), size * 0.32f));
            canvas.drawRoundRect(tempRect, radius * 0.55f, radius * 0.55f, paint);

            // A moving glass highlight gives each tile a polished, animated finish.
            long now = System.currentTimeMillis();
            float phase = (now % 2600L) / 2600f;
            float shineX = x - size * 0.55f + phase * size * 2.1f;
            paint.setShader(new LinearGradient(shineX, y, shineX + size * 0.34f, y + size,
                    new int[] {Color.argb(0, 255, 255, 255), Color.argb(32, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(tempRect, radius, radius, paint);
            paint.setShader(null);
            // Thin lower accent line enhances the dimensional edge.
            paint.setColor(Color.argb(58, 5, 10, 28));
            canvas.drawRoundRect(x + size * 0.16f, y + size * 0.86f, x + size * 0.84f, y + size * 0.91f,
                    dp(2), dp(2), paint);
        }

        private int lighten(int color, float factor) {
            return Color.rgb(Math.min(255, (int)(Color.red(color) * factor)),
                    Math.min(255, (int)(Color.green(color) * factor)),
                    Math.min(255, (int)(Color.blue(color) * factor)));
        }

        private int darken(int color, float factor) {
            return Color.rgb((int)(Color.red(color) * factor),
                    (int)(Color.green(color) * factor), (int)(Color.blue(color) * factor));
        }

        private void drawPieceTray(Canvas canvas) {
            for (int i = 0; i < 3; i++) {
                RectF rect = cardRects[i];
                boolean selected = i == selectedIndex;
                long now = System.currentTimeMillis();
                float pulse = 0.5f + 0.5f * (float)Math.sin(now / 260f + i * 1.3f);
                int topCard = selected ? Color.rgb(42, 72, 119) : Color.rgb(31, 43, 72);
                int bottomCard = selected ? Color.rgb(25, 39, 72) : Color.rgb(20, 30, 54);
                paint.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                        new int[] {topCard, bottomCard}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect, dp(14), dp(14), paint);
                paint.setShader(null);
                if (selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(3));
                    paint.setColor(Color.argb((int)(20 + pulse * 45), 86, 193, 255));
                    canvas.drawRoundRect(rect, dp(15), dp(15), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(selected ? dp(2) : dp(1));
                paint.setColor(selected ? Color.rgb(99, 172, 255) : Color.rgb(48, 61, 92));
                canvas.drawRoundRect(rect, dp(14), dp(14), paint);
                paint.setStyle(Paint.Style.FILL);

                Piece p = pieces[i];
                if (p != null) {
                    float previewCell = Math.min(dp(18), Math.min(
                            (rect.width() - dp(20)) / p.width,
                            (rect.height() - dp(24)) / p.height));
                    float totalW = p.width * previewCell;
                    float totalH = p.height * previewCell;
                    float startX = rect.centerX() - totalW / 2f;
                    float startY = rect.centerY() - totalH / 2f + dp(3);
                    float gap = Math.max(dp(2), previewCell * 0.12f);
                    float tile = previewCell - gap;
                    for (int k = 0; k < p.cells.length; k++) {
                        float x = startX + p.cells[k][0] * previewCell;
                        float y = startY + p.cells[k][1] * previewCell;
                        drawBlock(canvas, x, y, tile, p.color);
                    }
                }
            }
        }

        private void drawToolTray(Canvas canvas) { /* Core Block Blast gameplay only. */ }

        private void drawFooter(Canvas canvas) {
            String text = message;
            long now = System.currentTimeMillis();
            if (messageUntil > now && !paused && !gameOver) postInvalidateDelayed(Math.min(120L, messageUntil - now));
            if (now > messageUntil && !paused && !gameOver) {
                if (isAdventureMode()) text = "LEVEL " + adventureLevel + "  •  LINES " + levelLines + "/" + goalLines + "  •  MOVES " + movesRemaining;
                else text = "CLEAR ROWS + COLUMNS TO SCORE";
            }
            paint.setColor(themeIndex == 2 ? Color.rgb(73, 91, 120) : Color.rgb(150, 166, 201));
            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            paint.setTextSize(dp(8));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, viewW / 2f, trayTop + cardHeight + dp(58), paint);
            paint.setTextSize(dp(8));
            paint.setColor(themeIndex == 2 ? Color.rgb(70, 111, 151) : Color.rgb(116, 190, 185));

            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawStatsOverlay(Canvas canvas) {
            paint.setColor(Color.argb(210, 7, 11, 25));
            canvas.drawRect(0, 0, viewW, viewH, paint);
            float panelW = Math.min(viewW - dp(48), dp(310));
            float panelH = dp(320);
            float left = (viewW - panelW) / 2f;
            float top = (viewH - panelH) / 2f;
            paint.setColor(Color.rgb(27, 39, 67));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(70, 91, 137));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(dp(22));
            paint.setColor(Color.WHITE);
            canvas.drawText("YOUR STATS", viewW / 2f, top + dp(42), paint);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(dp(11));
            paint.setColor(Color.rgb(184, 198, 226));
            canvas.drawText("Total lines cleared: " + totalLines, viewW / 2f, top + dp(76), paint);
            canvas.drawText("Best combo: x" + Math.max(bestCombo, combo), viewW / 2f, top + dp(98), paint);
            canvas.drawText("Perfect board clears: " + boardClears, viewW / 2f, top + dp(120), paint);
            canvas.drawText("Games played: " + gamesPlayed + "  •  Blocks: " + totalPiecesPlaced, viewW / 2f, top + dp(142), paint);
            canvas.drawText("Adventure stars: " + adventureStars + "  •  Coins: " + coins, viewW / 2f, top + dp(164), paint);
            canvas.drawText("Daily quest: " + dailyMissionProgress + "/" + dailyMissionGoal + (dailyMissionClaimed ? "  •  CLAIMED" : ""), viewW / 2f, top + dp(186), paint);
            canvas.drawText("Achievements unlocked: " + countUnlockedAchievements() + "/6", viewW / 2f, top + dp(208), paint);
            drawOverlayButton(canvas, statsCloseRect, "CLOSE", true);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawSettingsOverlay(Canvas canvas) {
            paint.setColor(Color.argb(218, 7, 11, 25));
            canvas.drawRect(0, 0, viewW, viewH, paint);
            float panelW = Math.min(viewW - dp(38), dp(330));
            float panelH = dp(440);
            float left = (viewW - panelW) / 2f;
            float top = (viewH - panelH) / 2f;
            paint.setColor(themeIndex == 2 ? Color.rgb(237, 243, 251) : Color.rgb(27, 39, 67));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(themeIndex == 2 ? Color.rgb(170, 190, 215) : Color.rgb(70, 91, 137));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(dp(22));
            paint.setColor(themeIndex == 2 ? Color.rgb(27, 44, 70) : Color.WHITE);
            canvas.drawText("SETTINGS", viewW / 2f, top + dp(52), paint);
            String theme = themeIndex == 0 ? "MIDNIGHT" : (themeIndex == 1 ? "AURORA" : "LIGHT");
            drawSettingsButton(canvas, settingsThemeRect, "THEME  •  " + theme);
            drawSettingsButton(canvas, settingsSoundRect, "SOUND  •  " + (soundOn ? "ON" : "OFF"));
            drawSettingsButton(canvas, settingsVibrationRect, "VIBRATION  •  " + (vibrationOn ? "ON" : "OFF"));
            drawSettingsButton(canvas, settingsHintsRect, "MOVE HINTS  •  " + (hintsOn ? "ON" : "OFF"));
            drawSettingsButton(canvas, settingsHelpRect, "HOW TO PLAY");
            drawSettingsButton(canvas, settingsImageRect, blockImage != null ? "CHANGE BLOCK IMAGE" : "UPLOAD BLOCK IMAGE");
            drawSettingsButton(canvas, settingsCloseRect, "DONE");
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawSettingsButton(Canvas canvas, RectF rect, String label) {
            paint.setColor(themeIndex == 2 ? Color.rgb(218, 230, 245) : Color.rgb(38, 53, 84));
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            paint.setColor(themeIndex == 2 ? Color.rgb(39, 65, 99) : Color.rgb(220, 234, 255));
            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            paint.setTextSize(dp(11));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        private void drawHelpOverlay(Canvas canvas) {
            paint.setColor(Color.argb(218, 7, 11, 25));
            canvas.drawRect(0, 0, viewW, viewH, paint);
            float panelW = Math.min(viewW - dp(38), dp(330));
            float panelH = dp(300);
            float left = (viewW - panelW) / 2f;
            float top = (viewH - panelH) / 2f;
            paint.setColor(Color.rgb(27, 39, 67));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(dp(21));
            paint.setColor(Color.WHITE);
            canvas.drawText("HOW TO PLAY", viewW / 2f, top + dp(45), paint);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(dp(12));
            paint.setColor(Color.rgb(190, 204, 230));
            String[] lines = new String[] {
                    "1. Choose one of the three shapes.",
                    "2. Tap the grid or drag a shape into place.",
                    "3. Fill a complete row or column to clear it.",
                    "4. Clear multiple lines for bigger combos.",
                    "5. Use boosters when the board gets crowded.",
                    "6. Complete the daily quest to earn coins."
            };
            for (int i = 0; i < lines.length; i++) canvas.drawText(lines[i], viewW / 2f, top + dp(82 + i * 25), paint);
            drawOverlayButton(canvas, helpCloseRect, "GOT IT", true);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawOverlay(Canvas canvas) {
            paint.setColor(Color.argb(205, 7, 11, 25));
            canvas.drawRect(0, 0, viewW, viewH, paint);
            float panelW = Math.min(viewW - dp(48), dp(310));
            float panelH = dp(280);
            float left = (viewW - panelW) / 2f;
            float top = (viewH - panelH) / 2f;
            paint.setColor(Color.rgb(27, 39, 67));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(70, 91, 137));
            canvas.drawRoundRect(left, top, left + panelW, top + panelH, dp(22), dp(22), paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(dp(23));
            paint.setColor(Color.WHITE);
            String title;
            if (levelWon) title = "LEVEL COMPLETE!";
            else if (gameOver && isAdventureMode()) title = "LEVEL FAILED";
            else title = gameOver ? "NO MORE MOVES" : "PAUSED";
            canvas.drawText(title, viewW / 2f, top + dp(55), paint);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(dp(13));
            paint.setColor(Color.rgb(169, 184, 216));
            String subtitle;
            if (levelWon) subtitle = "LEVEL " + adventureLevel + "  •  " + levelLines + "/" + goalLines + " LINES  •  " + adventureStars + " TOTAL STARS";
            else if (gameOver && isAdventureMode()) subtitle = "LINES " + levelLines + "/" + goalLines + "  •  MOVES LEFT " + movesRemaining;
            else subtitle = gameOver ? "Score: " + score + "   •   Best: " + bestScore : "Take a short break";
            canvas.drawText(subtitle, viewW / 2f, top + dp(83), paint);

            if (gameOver && !reviveUsed) drawOverlayButton(canvas, reviveRect, "REVIVE", true);
            String primaryLabel = gameOver ? "PLAY AGAIN" : "CONTINUE";
            String secondaryLabel = gameOver ? "RESTART" : "RESTART";
            drawOverlayButton(canvas, primaryRect, primaryLabel, true);
            drawOverlayButton(canvas, secondaryRect, secondaryLabel, false);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawOverlayButton(Canvas canvas, RectF rect, String label, boolean primary) {
            paint.setColor(primary ? Color.rgb(83, 151, 255) : Color.rgb(41, 57, 91));
            canvas.drawRoundRect(rect, dp(13), dp(13), paint);
            if (!primary) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1));
                paint.setColor(Color.rgb(76, 94, 135));
                canvas.drawRoundRect(rect, dp(13), dp(13), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        private boolean canPlace(Piece piece, int row, int col) {
            if (piece == null) return false;
            for (int i = 0; i < piece.cells.length; i++) {
                int r = row + piece.cells[i][1];
                int c = col + piece.cells[i][0];
                if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) return false;
                if (board[r][c] != EMPTY) return false;
            }
            return true;
        }

        private void placePiece(int row, int col) {
            if (selectedIndex < 0 || selectedIndex >= 3) return;
            Piece piece = pieces[selectedIndex];
            if (piece == null) return;
            if (!canPlace(piece, row, col)) {
                showMessage("THAT SPACE IS BLOCKED");
                playTone(ToneGenerator.TONE_PROP_NACK);
                invalidate();
                return;
            }

            lastSnapshot = makeSnapshot();
            for (int i = 0; i < piece.cells.length; i++) {
                int r = row + piece.cells[i][1];
                int c = col + piece.cells[i][0];
                board[r][c] = piece.color;
            }
            score += piece.cells.length;
            piecesPlaced++;
            totalPiecesPlaced++;
            piecesSinceClear++;
            if (isAdventureMode()) movesRemaining = Math.max(0, movesRemaining - 1);
            playTone(ToneGenerator.TONE_PROP_BEEP);
            if (vibrationOn) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            pieces[selectedIndex] = null;
            selectedIndex = -1;
            dragging = false;
            hoverRow = -1;
            hoverCol = -1;

            int cleared = clearCompleteLines();
            if (cleared > 0) {
                if (combo == 0 || piecesSinceClear > 3) combo = 1;
                else combo = Math.min(8, combo + 1);
                int linePoints = 10 * cleared * cleared * combo;
                score += linePoints;
                totalLines += cleared;
                updateDailyMission(cleared);
                if (isAdventureMode()) levelLines += cleared;
                bestCombo = Math.max(bestCombo, combo);
                piecesSinceClear = 0;
                boolean empty = isBoardEmpty();
                if (empty) {
                    score += 300;
                    boardClears++;
                    showMessage("BOARD CLEAR! +300 BONUS");
                } else if (combo > 1) {
                    showMessage("" + cleared + " LINES • COMBO x" + combo + "!  +" + linePoints);
                } else {
                    showMessage("" + cleared + " LINE" + (cleared == 1 ? "" : "S") + " CLEARED  +" + linePoints);
                }
                playTone(ToneGenerator.TONE_PROP_ACK);
                if (vibrationOn) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                if (piecesSinceClear >= 3) combo = 0;
                showMessage("NICE MOVE!");
            }
            saveBestIfNeeded();
            checkAchievements();

            if (pieces[0] == null && pieces[1] == null && pieces[2] == null) {
                generatePieces();
            }
            if (isAdventureMode() && levelLines >= goalLines) {
                completeAdventureLevel();
            } else if (isAdventureMode() && movesRemaining <= 0) {
                updateGameOver();
            } else {
                updateGameOver();
            }
            saveSession();
            invalidate();
        }

        private boolean isBoardEmpty() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) if (board[r][c] != EMPTY) return false;
            }
            return true;
        }

        private void completeAdventureLevel() {
            if (levelWon) return;
            levelWon = true;
            gameOver = true;
            int earned = 1;
            if (levelLines >= goalLines + 2) earned++;
            if (movesRemaining >= movesPerLevel / 2) earned++;
            String key = "adventure_stars_level_" + adventureLevel;
            int previous = preferences.getInt(key, 0);
            int improved = Math.max(previous, earned);
            adventureStars += improved - previous;
            int unlocked = preferences.getInt("adventure_unlocked_level", 1);
            preferences.edit()
                    .putInt(key, improved)
                    .putInt("adventure_total_stars", adventureStars)
                    .putInt("adventure_unlocked_level", Math.max(unlocked, adventureLevel + 1))
                    .apply();
            saveBestIfNeeded();
            showMessage("LEVEL COMPLETE! " + improved + " STAR" + (improved == 1 ? "" : "S"));
            playTone(ToneGenerator.TONE_PROP_ACK);
        }

        private void reviveGame() {
            if (!gameOver || reviveUsed) return;
            reviveUsed = true;
            // Clear a portion of occupied cells to create breathing room, preserving the current score.
            int occupied = 0;
            for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) if (board[r][c] != EMPTY) occupied++;
            int toClear = Math.max(8, (int)Math.ceil(occupied * 0.32));
            for (int n = 0; n < toClear && occupied > 0; n++) {
                int target = nextRandomInt(occupied);
                boolean removed = false;
                for (int r = 0; r < SIZE && !removed; r++) for (int c = 0; c < SIZE && !removed; c++) {
                    if (board[r][c] != EMPTY && target-- == 0) { board[r][c] = EMPTY; removed = true; occupied--; }
                }
            }
            if (!hasAnyMove()) generatePieces();
            gameOver = false;
            paused = false;
            selectedIndex = -1;
            targetTool = -1;
            showMessage("REVIVED! KEEP PLAYING");
            saveSession();
            invalidate();
        }

        private void updateGameOver() {
            if (isAdventureMode() && movesRemaining <= 0 && levelLines < goalLines) {
                boolean canUndo = undoCharges > 0 && lastSnapshot != null;
                gameOver = !canUndo;
                if (!gameOver) showMessage("NO MOVES LEFT • UNDO YOUR LAST MOVE");
                return;
            }
            if (hasAnyMove()) {
                gameOver = false;
                return;
            }
            boolean hasPiece = pieces[0] != null || pieces[1] != null || pieces[2] != null;
            boolean canRecover = bombCharges > 0 || shuffleCharges > 0 || hammerCharges > 0 || crossCharges > 0
                    || (undoCharges > 0 && lastSnapshot != null)
                    || (rotateCharges > 0 && hasPiece)
                    || (coins >= 20 && (hammerCharges <= 0 || crossCharges <= 0))
                    || (coins >= 25 && (bombCharges <= 0 || shuffleCharges <= 0 || (rotateCharges <= 0 && hasPiece)))
                    || (coins >= 40 && crossCharges <= 0);
            gameOver = !canRecover;
            if (!gameOver) showMessage("NO OPEN SPACE • TRY A BOOSTER");
        }

        private boolean buyCharge(int tool) {
            int price = tool == TOOL_CROSS ? 40 : (tool == TOOL_HAMMER ? 20 : 25);
            if (coins < price) {
                showMessage("NOT ENOUGH COINS • NEED " + price);
                return false;
            }
            coins -= price;
            if (tool == TOOL_BOMB) bombCharges++;
            else if (tool == TOOL_ROTATE) rotateCharges++;
            else if (tool == TOOL_UNDO) undoCharges++;
            else if (tool == TOOL_SHUFFLE) shuffleCharges++;
            else if (tool == TOOL_HAMMER) hammerCharges++;
            else if (tool == TOOL_CROSS) crossCharges++;
            saveSettings();
            showMessage("BOOSTER REFILLED  •  -" + price + " COINS");
            return true;
        }

        private void useTool(int tool) {
            if (tool == TOOL_BOMB || tool == TOOL_HAMMER || tool == TOOL_CROSS) {
                int charges = tool == TOOL_BOMB ? bombCharges : (tool == TOOL_HAMMER ? hammerCharges : crossCharges);
                if (charges <= 0 && !buyCharge(tool)) return;
                targetTool = targetTool == tool ? -1 : tool;
                bombArmed = targetTool == TOOL_BOMB;
                if (targetTool == TOOL_BOMB) showMessage("TAP A CELL TO CLEAR A 3 x 3 AREA");
                else if (targetTool == TOOL_HAMMER) showMessage("TAP ONE BLOCK TO BREAK IT");
                else if (targetTool == TOOL_CROSS) showMessage("TAP A CELL TO CLEAR ITS ROW + COLUMN");
                else showMessage("BOOSTER CANCELLED");
            } else if (tool == TOOL_ROTATE) {
                if (selectedIndex < 0 || pieces[selectedIndex] == null) {
                    showMessage("SELECT A BLOCK FIRST, THEN ROTATE"); return;
                }
                if (rotateCharges <= 0 && !buyCharge(tool)) return;
                Piece old = pieces[selectedIndex];
                int[][] rotated = new int[old.cells.length][2];
                int minX = 999, minY = 999;
                for (int i = 0; i < old.cells.length; i++) {
                    rotated[i][0] = old.height - 1 - old.cells[i][1];
                    rotated[i][1] = old.cells[i][0];
                    minX = Math.min(minX, rotated[i][0]);
                    minY = Math.min(minY, rotated[i][1]);
                }
                for (int i = 0; i < rotated.length; i++) {
                    rotated[i][0] -= minX;
                    rotated[i][1] -= minY;
                }
                pieces[selectedIndex] = new Piece(rotated, old.color);
                rotateCharges--;
                showMessage("BLOCK ROTATED 90°");
                playTone(ToneGenerator.TONE_PROP_BEEP);
            } else if (tool == TOOL_UNDO) {
                if (lastSnapshot == null) { showMessage("UNDO IS NOT AVAILABLE"); return; }
                if (undoCharges <= 0 && !buyCharge(tool)) return;
                restoreSnapshot(lastSnapshot);
                lastSnapshot = null;
                undoCharges--;
                gameOver = false;
                updateGameOver();
                showMessage("LAST MOVE UNDONE");
                playTone(ToneGenerator.TONE_PROP_ACK);
            } else if (tool == TOOL_SHUFFLE) {
                if (shuffleCharges <= 0 && !buyCharge(tool)) return;
                boolean playable = false;
                for (int attempt = 0; attempt < 40 && !playable; attempt++) {
                    for (int i = 0; i < 3; i++) {
                        if (pieces[i] != null) {
                            int shapeIndex = nextRandomInt(SHAPES.length);
                            int colorIndex = nextRandomInt(PALETTE.length);
                            pieces[i] = new Piece(SHAPES[shapeIndex], PALETTE[colorIndex]);
                        }
                    }
                    playable = hasAnyMove();
                }
                shuffleCharges--;
                selectedIndex = -1;
                showMessage("YOUR BLOCKS HAVE BEEN SHUFFLED");
                updateGameOver();
            }
            saveSession();
            invalidate();
        }

        private void useTargetToolAt(int row, int col) {
            int tool = targetTool;
            if (tool == -1) return;
            int removed = 0;
            lastSnapshot = makeSnapshot();
            if (tool == TOOL_BOMB) {
                for (int r = Math.max(0, row - 1); r <= Math.min(SIZE - 1, row + 1); r++) {
                    for (int c = Math.max(0, col - 1); c <= Math.min(SIZE - 1, col + 1); c++) {
                        if (board[r][c] != EMPTY) { board[r][c] = EMPTY; removed++; }
                    }
                }
                if (removed > 0) bombCharges--;
            } else if (tool == TOOL_HAMMER) {
                if (board[row][col] != EMPTY) { board[row][col] = EMPTY; removed = 1; hammerCharges--; }
            } else if (tool == TOOL_CROSS) {
                for (int c = 0; c < SIZE; c++) if (board[row][c] != EMPTY) { board[row][c] = EMPTY; removed++; }
                for (int r = 0; r < SIZE; r++) if (r != row && board[r][col] != EMPTY) { board[r][col] = EMPTY; removed++; }
                if (removed > 0) crossCharges--;
            }
            targetTool = -1;
            bombArmed = false;
            if (removed > 0) {
                score += removed * (tool == TOOL_CROSS ? 2 : 1);
                showMessage((tool == TOOL_BOMB ? "BOMB" : (tool == TOOL_HAMMER ? "HAMMER" : "CROSS")) + " CLEARED " + removed + " BLOCKS");
                playTone(ToneGenerator.TONE_PROP_ACK);
                if (vibrationOn) performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                saveBestIfNeeded();
            } else {
                showMessage("NO BLOCK AT THAT POSITION • CHARGE KEPT");
                lastSnapshot = null;
            }
            updateGameOver();
            saveSession();
            invalidate();
        }

        private int clearCompleteLines() {
            boolean[] fullRows = new boolean[SIZE];
            boolean[] fullCols = new boolean[SIZE];
            int lineCount = 0;

            for (int r = 0; r < SIZE; r++) {
                boolean full = true;
                for (int c = 0; c < SIZE; c++) {
                    if (board[r][c] == EMPTY) { full = false; break; }
                }
                if (full) { fullRows[r] = true; lineCount++; }
            }
            for (int c = 0; c < SIZE; c++) {
                boolean full = true;
                for (int r = 0; r < SIZE; r++) {
                    if (board[r][c] == EMPTY) { full = false; break; }
                }
                if (full) { fullCols[c] = true; lineCount++; }
            }

            if (lineCount == 0) return 0;
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    flashColors[r][c] = (fullRows[r] || fullCols[c]) ? board[r][c] : EMPTY;
                    if (fullRows[r] || fullCols[c]) board[r][c] = EMPTY;
                }
            }
            clearFlashStart = System.currentTimeMillis();
            return lineCount;
        }

        private boolean hasAnyMove() {
            for (int i = 0; i < pieces.length; i++) {
                Piece piece = pieces[i];
                if (piece == null) continue;
                for (int r = 0; r < SIZE; r++) {
                    for (int c = 0; c < SIZE; c++) {
                        if (canPlace(piece, r, c)) return true;
                    }
                }
            }
            return false;
        }

        private int[] findCell(float x, float y) {
            float innerLeft = boardLeft + gridPad;
            float innerTop = boardTop + gridPad;
            float step = cellSize + gridGap;
            int col = (int)((x - innerLeft) / step);
            int row = (int)((y - innerTop) / step);
            if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return null;
            float localX = x - (innerLeft + col * step);
            float localY = y - (innerTop + row * step);
            if (localX > cellSize || localY > cellSize) return null;
            return new int[] {row, col};
        }

        private void updateHover(float x, float y) {
            pointerX = x;
            pointerY = y;
            int[] cell = findCell(x, y);
            if (cell == null) {
                hoverRow = -1;
                hoverCol = -1;
            } else {
                hoverRow = cell[0];
                hoverCol = cell[1];
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            computeLayout();
            float x = event.getX();
            float y = event.getY();

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pointerX = x;
                pointerY = y;
                pressedSlot = -1;
                pressedTool = -1;
                dragging = false;

                if (statsShown || settingsShown || helpShown || paused || gameOver) return true;
                if (settingsRect.contains(x, y) || pauseRect.contains(x, y) || soundRect.contains(x, y) || modeRect.contains(x, y)
                        || bestRect.contains(x, y)) return true;
                for (int t = 0; t < toolRects.length; t++) {
                    if (toolRects[t].contains(x, y)) { pressedTool = t; return true; }
                }
                for (int i = 0; i < cardRects.length; i++) {
                    if (cardRects[i].contains(x, y) && pieces[i] != null) {
                        pressedSlot = i;
                        selectedIndex = i;
                        invalidate();
                        return true;
                    }
                }
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (pressedSlot >= 0) {
                    if (Math.abs(x - pointerX) + Math.abs(y - pointerY) > dp(8)) dragging = true;
                    if (dragging) updateHover(x, y);
                    invalidate();
                    return true;
                }
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (helpShown) {
                    helpShown = false;
                    invalidate();
                    return true;
                }
                if (settingsShown) {
                    if (settingsThemeRect.contains(x, y)) themeIndex = (themeIndex + 1) % 3;
                    else if (settingsSoundRect.contains(x, y)) soundOn = !soundOn;
                    else if (settingsVibrationRect.contains(x, y)) vibrationOn = !vibrationOn;
                    else if (settingsHintsRect.contains(x, y)) hintsOn = !hintsOn;
                    else if (settingsHelpRect.contains(x, y)) { settingsShown = false; helpShown = true; }
                    else if (settingsImageRect.contains(x, y)) { settingsShown = false; ((MainActivity)getContext()).openBlockImagePicker(); }
                    else if (settingsCloseRect.contains(x, y)) settingsShown = false;
                    saveSettings();
                    saveSession();
                    invalidate();
                    return true;
                }
                if (statsShown) {
                    statsShown = false;
                    invalidate();
                    return true;
                }
                if (paused || gameOver) {
                    if (gameOver && !reviveUsed && reviveRect.contains(x, y)) {
                        reviveGame();
                    } else if (primaryRect.contains(x, y)) {
                        if (levelWon && isAdventureMode()) {
                            adventureLevel++;
                            newGame();
                        } else if (gameOver) newGame(); else paused = false;
                    } else if (secondaryRect.contains(x, y)) {
                        if (levelWon && isAdventureMode()) {
                            adventureLevel = Math.max(adventureLevel,
                                    preferences.getInt("adventure_unlocked_level", adventureLevel));
                            gameMode = 0;
                            newGame();
                        } else newGame();
                    } else if (paused && !gameOver) {
                        paused = false;
                    }
                    saveSession();
                    invalidate();
                    return true;
                }

                if (settingsRect.contains(x, y)) return true;
                if (pauseRect.contains(x, y)) {
                    paused = true;
                    saveSession();
                    invalidate();
                    return true;
                }
                if (soundRect.contains(x, y)) {
                    soundOn = !soundOn;
                    saveSettings();
                    invalidate();
                    return true;
                }
                if (modeRect.contains(x, y)) { return true; }
                if (bestRect.contains(x, y)) return true;
                if (pressedTool >= 0) {
                    int tool = pressedTool;
                    pressedTool = -1;
                    if (toolRects[tool].contains(x, y)) useTool(tool);
                    invalidate();
                    return true;
                }

                if (pressedSlot >= 0) {
                    int slot = pressedSlot;
                    pressedSlot = -1;
                    if (dragging) {
                        updateHover(x, y);
                        int[] cell = findCell(x, y);
                        if (cell != null) {
                            selectedIndex = slot;
                            placePiece(cell[0], cell[1]);
                        }
                    } else {
                        selectedIndex = slot;
                    }
                    dragging = false;
                    hoverRow = -1;
                    hoverCol = -1;
                    invalidate();
                    return true;
                }

                int[] cell = findCell(x, y);
                if (cell != null) {
                    if (targetTool != -1) useTargetToolAt(cell[0], cell[1]);
                    else if (selectedIndex >= 0) placePiece(cell[0], cell[1]);
                    else showMessage("CHOOSE A SHAPE FIRST");
                }
                invalidate();
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                pressedSlot = -1;
                pressedTool = -1;
                dragging = false;
                hoverRow = -1;
                hoverCol = -1;
                invalidate();
                return true;
            }
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            if (tone != null) {
                try { tone.release(); } catch (Throwable ignored) { }
                tone = null;
            }
            super.onDetachedFromWindow();
        }
    }
}
