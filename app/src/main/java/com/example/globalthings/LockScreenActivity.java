package com.example.globalthings;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lockthings.R;

import java.io.UnsupportedEncodingException;

public class LockScreenActivity extends AppCompatActivity {

    private ImageView imgClosedLock;
    private ImageView imgOpenedLock;
    private Button btnToggleLock;
    private boolean openedLock = true;
    private String nfcTag;
    private String myLockTag = "";
    private PendingIntent pendingIntent;
    private NfcAdapter nfcAdapter;
    private IntentFilter writingTagFilters[];
    private boolean writeMode;
    private AlertDialog alertDialog;
    private MediaPlayer mediaPlayer;
    private CountDownTimer countDownTimer;
    private TextView tvCountdownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imgClosedLock = findViewById(R.id.imgClosedLock);
        imgOpenedLock = findViewById(R.id.imgOpenedLock);
        btnToggleLock = findViewById(R.id.btnToggleLock);
        mediaPlayer = MediaPlayer.create(this, R.raw.error_sound_effect);

        Intent intent = getIntent();
        myLockTag = intent.getStringExtra("lockTag");

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "Esse aparelho não suporta NFC", Toast.LENGTH_SHORT).show();
            finish();
        }
        readFromIntent(getIntent());
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writingTagFilters = new IntentFilter[] { tagDetected };


        btnToggleLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (openedLock) {
                    imgClosedLock.setVisibility(View.VISIBLE);
                    imgOpenedLock.setVisibility(View.GONE);
                    btnToggleLock.setText("Abrir Cadeado");
                    openedLock = false;

                } else {

                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder( LockScreenActivity.this);
                    LayoutInflater inflater = getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.dialog_layout, null);
                    alertDialogBuilder.setView(dialogView);
                    tvCountdownTimer = dialogView.findViewById(R.id.tvCountdownTimer);

                    alertDialogBuilder.setTitle("Abrir Cadeado")
                        .setMessage("Aproxime a tag NFC cadastrada para abrir")
                        .setCancelable(false)
                        .setNegativeButton("cancelar", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(LockScreenActivity.this, "Ação cancelada", Toast.LENGTH_LONG).show();
                                stopCountdownTimer();
                            }
                        });
                    alertDialog = alertDialogBuilder.create();
                    alertDialog.show();
                    startCountdownTimer();
                }
            }
        });
    }

    private void readFromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if (rawMsgs != null) {
                msgs = new NdefMessage [rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) return;

        String text = "";
        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
        int languageCodeLength = payload[0] & 0063;

        try{
            text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        } catch (UnsupportedEncodingException e) {
            Log.e("UnsupportedEncoding", e.toString());
        }

        nfcTag = text;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readFromIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if(myTag != null && myLockTag.equals(nfcTag)){
                imgClosedLock.setVisibility(View.GONE);
                imgOpenedLock.setVisibility(View.VISIBLE);
                btnToggleLock.setText("Fechar Cadeado");
                openedLock = true;
                nfcTag = "";
                alertDialog.dismiss();
                Toast.makeText(LockScreenActivity.this, "Tag correta, cadeado aberto com sucesso", Toast.LENGTH_LONG).show();
                stopCountdownTimer();
            } else if (!myLockTag.equals(nfcTag)){
                playSoundAlert();
                alertDialog.dismiss();
                Toast.makeText(LockScreenActivity.this, "Chave Incorreta, utilize a chave cadastrada", Toast.LENGTH_LONG).show();
                stopCountdownTimer();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        WriteModeOff();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WriteModeOn();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void WriteModeOn() {
        writeMode = true;
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writingTagFilters, null);
    }

    private void WriteModeOff() {
        writeMode = false;
        nfcAdapter.disableForegroundDispatch(this);
    }

    private void playSoundAlert() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    private void startCountdownTimer() {
        countDownTimer = new CountDownTimer(30000, 1000) {
            public void onTick(long millisUntilFinished) {
                tvCountdownTimer.setText((millisUntilFinished / 1000) +" segundos para o fim da leitura");
            }

            public void onFinish() {
                if (alertDialog != null && alertDialog.isShowing()) {
                    alertDialog.dismiss();
                    Toast.makeText(LockScreenActivity.this, "Tempo encerrado, tente novamente", Toast.LENGTH_LONG).show();

                }
            }
        }.start();
    }

    private void stopCountdownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}