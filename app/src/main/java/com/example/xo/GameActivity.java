package com.example.xo;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    public static final String KEY_TOTAL_PARTIES = "TotalParties";

    private int totalParties;
    private int partieActuelle = 1;
    private int scoreX = 0;
    private int scoreO = 0;
    private int partiesNulles = 0;
    private boolean isPlayerXTurn = true;
    private boolean partieTerminee = false;
    private String[][] board = new String[3][3];
    private Button[][] buttons = new Button[3][3];

    // Sound effects
    private SoundPool soundPool;
    private int winSoundId, loseSoundId, drawSoundId;
    private boolean soundsLoaded = false;

    // PvE variables
    private boolean isPvE;
    private int difficulty; // 0: Easy, 1: Medium, 2: Hard
    private String userSymbol;
    private String botSymbol;

    private TextView textPartieNumero;
    private TextView textScoreX;
    private TextView textScoreO;
    private TextView textPartiesNulles;
    private TextView textStatus;
    private Button btnToggleMusicGame;
    private ImageView imageAvatarStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // Allow volume control
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Setup sound effects
        setupSounds();

        // Récupérer les données de l'intent
        Intent intent = getIntent();
        totalParties = intent.getIntExtra(KEY_TOTAL_PARTIES, 5);
        isPvE = intent.getBooleanExtra("IS_PVE", false);
        difficulty = intent.getIntExtra("DIFFICULTY", 0);
        userSymbol = intent.getStringExtra("USER_SYMBOL");
        if (userSymbol == null) userSymbol = "X";
        botSymbol = userSymbol.equals("X") ? "O" : "X";

        // Initialisation des vues
        textPartieNumero = findViewById(R.id.text_partie_numero);
        textScoreX = findViewById(R.id.text_score_x);
        textScoreO = findViewById(R.id.text_score_o);
        textPartiesNulles = findViewById(R.id.text_parties_nulles);
        textStatus = findViewById(R.id.text_status);
        btnToggleMusicGame = findViewById(R.id.btn_toggle_music_game);
        imageAvatarStatus = findViewById(R.id.image_avatar_status);

        initializeButtons();
        updateUI();
        resetBoard();

        // Music Toggle Logic for Game Screen
        if (btnToggleMusicGame != null) {
            // Initial state check
            boolean isPlaying = false;
            if (HomeActivity.mediaPlayer != null) {
                isPlaying = HomeActivity.mediaPlayer.isPlaying();
            }
            btnToggleMusicGame.setText(isPlaying ? "Musique : ON" : "Musique : OFF");

            btnToggleMusicGame.setOnClickListener(v -> {
                if (HomeActivity.mediaPlayer != null) {
                    if (HomeActivity.mediaPlayer.isPlaying()) {
                        HomeActivity.mediaPlayer.pause();
                        btnToggleMusicGame.setText("Musique : OFF");
                    } else {
                        HomeActivity.mediaPlayer.start();
                        btnToggleMusicGame.setText("Musique : ON");
                    }
                }
            });
        }
    }

    private void setupSounds() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();

        soundPool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status == 0) {
                soundsLoaded = true;
            } else {
                Toast.makeText(GameActivity.this, "Erreur chargement des sons", Toast.LENGTH_SHORT).show();
            }
        });

        winSoundId = soundPool.load(this, R.raw.win_sound, 1);
        loseSoundId = soundPool.load(this, R.raw.lose_sound, 1);
        drawSoundId = soundPool.load(this, R.raw.draw_sound, 1);
    }

    private void initializeButtons() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String buttonID = "button_" + i + j;
                int resID = getResources().getIdentifier(buttonID, "id", getPackageName());
                buttons[i][j] = findViewById(resID);
                // On attache le tag ici pour une utilisation facile dans onCellClick
                buttons[i][j].setTag(i + "" + j);
            }
        }
    }

    public void onCellClick(View v) {
        if (partieTerminee) {
            Toast.makeText(this, "La partie est terminée.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPvE) {
            boolean isBotTurn = (isPlayerXTurn && botSymbol.equals("X")) || (!isPlayerXTurn && botSymbol.equals("O"));
            if (isBotTurn) return;
        }

        Button b = (Button) v;
        if (!b.getText().toString().isEmpty()) return;

        String tag = b.getTag().toString();
        int row = Character.getNumericValue(tag.charAt(0));
        int col = Character.getNumericValue(tag.charAt(1));

        makeMove(row, col);
    }

    private void makeMove(int row, int col) {
        String symbol = isPlayerXTurn ? "X" : "O";

        buttons[row][col].setText(symbol);
        if (symbol.equals("X")) {
            buttons[row][col].setTextColor(getResources().getColor(R.color.neon_red)); // Modifié pour être cohérent avec le design
        } else {
            buttons[row][col].setTextColor(getResources().getColor(R.color.neon_blue));
        }

        board[row][col] = symbol;

        if (checkForWin()) {
            handleEndPartie(symbol, true);
        } else if (isBoardFull()) {
            handleEndPartie("NUL", false);
        } else {
            isPlayerXTurn = !isPlayerXTurn;
            updateStatus();

            if (isPvE && !partieTerminee) {
                boolean isBotTurn = (isPlayerXTurn && botSymbol.equals("X")) || (!isPlayerXTurn && botSymbol.equals("O"));
                if (isBotTurn) {
                    new Handler(Looper.getMainLooper()).postDelayed(this::playBotMove, 500);
                }
            }
        }
    }

    private void playBotMove() {
        if (partieTerminee) return;
        int[] move = getBestMove();
        if (move != null) {
            makeMove(move[0], move[1]);
        }
    }

    private int[] getBestMove() {
        if (difficulty == 0) {
            return getRandomMove();
        } else if (difficulty == 1) {
            int[] winningMove = findWinningMove(botSymbol);
            if (winningMove != null) return winningMove;
            int[] blockingMove = findWinningMove(userSymbol);
            if (blockingMove != null) return blockingMove;
            return getRandomMove();
        } else {
            if (isEmptyBoard()) return getRandomMove();
            return minimaxRoot();
        }
    }

    private int[] getRandomMove() {
        List<int[]> availableMoves = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j].isEmpty()) {
                    availableMoves.add(new int[]{i, j});
                }
            }
        }
        if (availableMoves.isEmpty()) return null;
        return availableMoves.get(new Random().nextInt(availableMoves.size()));
    }

    private int[] findWinningMove(String symbol) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j].isEmpty()) {
                    board[i][j] = symbol;
                    if (checkForWin(symbol)) {
                        board[i][j] = "";
                        return new int[]{i, j};
                    }
                    board[i][j] = "";
                }
            }
        }
        return null;
    }

    private boolean isEmptyBoard() {
        for(int i=0; i<3; i++)
            for(int j=0; j<3; j++)
                if(!board[i][j].isEmpty()) return false;
        return true;
    }

    private boolean checkForWin(String player) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0].equals(player) && board[i][1].equals(player) && board[i][2].equals(player)) return true;
        }
        for (int i = 0; i < 3; i++) {
            if (board[0][i].equals(player) && board[1][i].equals(player) && board[2][i].equals(player)) return true;
        }
        if (board[0][0].equals(player) && board[1][1].equals(player) && board[2][2].equals(player)) return true;
        if (board[0][2].equals(player) && board[1][1].equals(player) && board[2][0].equals(player)) return true;
        return false;
    }

    private boolean checkForWin() {
        String s;
        for (int i = 0; i < 3; i++) {
            s = board[i][0];
            if (s != null && !s.isEmpty() && s.equals(board[i][1]) && s.equals(board[i][2])) return true;
            s = board[0][i];
            if (s != null && !s.isEmpty() && s.equals(board[1][i]) && s.equals(board[2][i])) return true;
        }
        s = board[0][0];
        if (s != null && !s.isEmpty() && s.equals(board[1][1]) && s.equals(board[2][2])) return true;
        s = board[0][2];
        if (s != null && !s.isEmpty() && s.equals(board[1][1]) && s.equals(board[2][0])) return true;
        return false;
    }

    private int[] minimaxRoot() {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j].isEmpty()) {
                    board[i][j] = botSymbol;
                    int score = minimax(false, 0);
                    board[i][j] = "";
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new int[]{i, j};
                    }
                }
            }
        }
        return bestMove != null ? bestMove : getRandomMove();
    }

    private int minimax(boolean isMaximizing, int depth) {
        if (checkForWin(botSymbol)) return 10 - depth;
        if (checkForWin(userSymbol)) return depth - 10;
        if (isBoardFull()) return 0;

        if (isMaximizing) {
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j].isEmpty()) {
                        board[i][j] = botSymbol;
                        int score = minimax(false, depth + 1);
                        board[i][j] = "";
                        bestScore = Math.max(score, bestScore);
                    }
                }
            }
            return bestScore;
        } else {
            int bestScore = Integer.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (board[i][j].isEmpty()) {
                        board[i][j] = userSymbol;
                        int score = minimax(true, depth + 1);
                        board[i][j] = "";
                        bestScore = Math.min(score, bestScore);
                    }
                }
            }
            return bestScore;
        }
    }

    private boolean isBoardFull() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i][j].isEmpty()) return false;
            }
        }
        return true;
    }

    private void handleEndPartie(String result, boolean isWin) {
        partieTerminee = true;

        if (isWin) {
            textStatus.setText("Victoire de " + result + "!");
            if (result.equals("X")) scoreX++; else scoreO++;

            boolean userWon = isPvE ? result.equals(userSymbol) : true;
            //showAvatar(userWon);
            playSound(userWon ? "win" : "lose");

        } else {
            textStatus.setText("Match nul.");
            partiesNulles++;
            //showAvatar(false); // Can be a draw avatar if you want
            playSound("draw");
        }
        updateUI();

        if (partieActuelle < totalParties) {
            new Handler(Looper.getMainLooper()).postDelayed(this::nextPartie, 2000);
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(this::endTournament, 2000);
        }
    }

    //private void showAvatar(boolean didWin) {
      //  if (imageAvatarStatus != null) {
        //    if (didWin) {
          //      imageAvatarStatus.setImageResource(R.drawable.win_avatar);
            //} else {
              //  imageAvatarStatus.setImageResource(R.drawable.lose_avatar);
            //}
            //imageAvatarStatus.setVisibility(View.VISIBLE);
        //}
    //}

    private void playSound(String sound) {
        if (soundsLoaded) {
            float volume = 1.0f;
            switch (sound) {
                case "win":
                    soundPool.play(winSoundId, volume, volume, 1, 0, 1.0f);
                    break;
                case "lose":
                    soundPool.play(loseSoundId, volume, volume, 1, 0, 1.0f);
                    break;
                case "draw":
                    soundPool.play(drawSoundId, volume, volume, 1, 0, 1.0f);
                    break;
            }
        }
    }

    private void nextPartie() {
        partieActuelle++;
        resetBoard();
        updateUI();
        partieTerminee = false;
    }

    private void resetBoard() {
        if (imageAvatarStatus != null) {
            imageAvatarStatus.setVisibility(View.GONE);
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = "";
                buttons[i][j].setText("");
            }
        }
        isPlayerXTurn = true;
        updateStatus();

        if (isPvE && botSymbol.equals("X")) {
            new Handler(Looper.getMainLooper()).postDelayed(this::playBotMove, 500);
        }
    }

    private void updateUI() {
        textPartieNumero.setText("Partie " + partieActuelle + "/" + totalParties);
        textScoreX.setText("Score X: " + scoreX);
        textScoreO.setText("Score O: " + scoreO);
        textPartiesNulles.setText("Nuls: " + partiesNulles);
    }

    private void updateStatus() {
        textStatus.setText("Tour de : " + (isPlayerXTurn ? "X" : "O"));
    }

    private void endTournament() {
        String winnerMessage;
        String vainqueur;

        if (scoreX > scoreO) {
            winnerMessage = "Victoire du joueur X!";
            vainqueur = "X";
        } else if (scoreO > scoreX) {
            winnerMessage = "Victoire du joueur O!";
            vainqueur = "O";
        } else {
            winnerMessage = "Égalité parfaite!";
            vainqueur = "Égalité";
        }

        new AlertDialog.Builder(this)
                .setTitle("Résultat du Tournoi")
                // ----- LIGNE CORRIGÉE -----
                // J'ai mis toute la chaîne sur une seule ligne et utilisé "\n" pour le saut de ligne
                .setMessage(winnerMessage + "\n\nScore X: " + scoreX + ", Score O: " + scoreO + ", Nulles: " + partiesNulles)
                .setPositiveButton("Sauvegarder", (dialog, which) -> saveTournamentResults(vainqueur))
                .setNegativeButton("Accueil", (dialog, which) -> goToHome())
                .setCancelable(false)
                .show();
    }

    private void saveTournamentResults(String finalWinner) {
        TournamentResult result = new TournamentResult(scoreX, scoreO, partiesNulles, totalParties, finalWinner);

        try {
            File file = new File(getFilesDir(), "tournament_results.ser");
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(result);
            oos.close();
            fos.close();
            Toast.makeText(this, "Scores sauvegardés.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur sauvegarde.", Toast.LENGTH_LONG).show();
        }
        goToHome();
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
