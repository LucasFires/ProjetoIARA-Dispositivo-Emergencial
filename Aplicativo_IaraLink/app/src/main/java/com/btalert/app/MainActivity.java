package com.btalert.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // ── Componentes de interface ───────────────────────────────────────────
    private TextView textoNomeContato, textoTelefoneContato;
    private Button botaoAdicionar, botaoAlterar, botaoRemover, botaoSalvarMensagem;
    private EditText campoMensagem;

    // ── Constantes ─────────────────────────────────────────────────────────
    public static final String NOME_PREFERENCIAS      = "BTAlertPrefs";
    public static final String CHAVE_NOME_CONTATO     = "contact_name";
    public static final String CHAVE_TELEFONE_CONTATO = "contact_phone";
    public static final String CHAVE_MENSAGEM         = "sms_message";
    public static final String CHAVE_NOME_DISPOSITIVO = "device_name";
    public static final String ACAO_LOG_EVENTO        = "com.btalert.app.LOG_EVENT";
    public static final String EXTRA_MENSAGEM_LOG     = "log_message";

    // Nome fixo do ESP32 — altere aqui se necessário
    public static final String NOME_ESP32 = "ESP32_BTAlert";

    private static final int REQUISICAO_PERMISSOES = 100;

    // ── Estado interno ─────────────────────────────────────────────────────
    private SharedPreferences preferencias;
    private boolean acaoContatoPendente = false;

    // ── Seletor de contato ─────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> seletorContato =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK && resultado.getData() != null) {
                    aoSelecionarContato(resultado.getData().getData());
                }
            });

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferencias = getSharedPreferences(NOME_PREFERENCIAS, Context.MODE_PRIVATE);

        vincularVisualizacoes();
        configurarListeners();
        carregarDadosSalvos();
        requisitarPermissoes();

        // Inicia o monitor Bluetooth automaticamente ao abrir o app
        iniciarServicoMonitor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // ── Inicialização ──────────────────────────────────────────────────────

    private void vincularVisualizacoes() {
        textoNomeContato     = findViewById(R.id.tvContactName);
        textoTelefoneContato = findViewById(R.id.tvContactPhone);
        botaoAdicionar       = findViewById(R.id.btnAdd);
        botaoAlterar         = findViewById(R.id.btnEdit);
        botaoRemover         = findViewById(R.id.btnRemove);
        botaoSalvarMensagem  = findViewById(R.id.btnSaveMessage);
        campoMensagem        = findViewById(R.id.etMessage);
    }

    private void configurarListeners() {
        botaoAdicionar.setOnClickListener(v -> {
            if (!verificarPermissaoContatos()) return;
            acaoContatoPendente = false;
            abrirSeletorContato();
        });

        botaoAlterar.setOnClickListener(v -> {
            if (preferencias.getString(CHAVE_NOME_CONTATO, null) == null) {
                Toast.makeText(this, "Nenhum contato para alterar. Use Adicionar.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!verificarPermissaoContatos()) return;
            acaoContatoPendente = true;
            abrirSeletorContato();
        });

        botaoRemover.setOnClickListener(v -> {
            if (preferencias.getString(CHAVE_NOME_CONTATO, null) == null) {
                Toast.makeText(this, "Nenhum contato registrado.", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Remover Contato")
                    .setMessage("Deseja remover o contato " + preferencias.getString(CHAVE_NOME_CONTATO, "") + "?")
                    .setPositiveButton("Remover", (d, w) -> removerContato())
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        botaoSalvarMensagem.setOnClickListener(v -> {
            String msg = campoMensagem.getText() != null ? campoMensagem.getText().toString().trim() : "";
            if (msg.isEmpty()) {
                Toast.makeText(this, "Digite uma mensagem antes de salvar.", Toast.LENGTH_SHORT).show();
                return;
            }
            preferencias.edit().putString(CHAVE_MENSAGEM, msg).apply();
            Toast.makeText(this, "Mensagem salva!", Toast.LENGTH_SHORT).show();
        });
    }

    private void carregarDadosSalvos() {
        String nome      = preferencias.getString(CHAVE_NOME_CONTATO, null);
        String telefone  = preferencias.getString(CHAVE_TELEFONE_CONTATO, null);
        String mensagem  = preferencias.getString(CHAVE_MENSAGEM, "");

        if (nome != null) {
            textoNomeContato.setText(nome);
            textoTelefoneContato.setText(telefone != null ? telefone : "Sem número");
        }
        if (!mensagem.isEmpty()) campoMensagem.setText(mensagem);

        // Salva nome fixo do ESP32 nas preferências para o serviço usar
        preferencias.edit().putString(CHAVE_NOME_DISPOSITIVO, NOME_ESP32).apply();
    }

    // ── Contatos ───────────────────────────────────────────────────────────

    private void abrirSeletorContato() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        seletorContato.launch(intent);
    }

    private void aoSelecionarContato(Uri uriContato) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uriContato,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                String nome     = cursor.getString(0);
                String telefone = cursor.getString(1).replaceAll("[^\\d+]", "");

                preferencias.edit()
                        .putString(CHAVE_NOME_CONTATO, nome)
                        .putString(CHAVE_TELEFONE_CONTATO, telefone)
                        .apply();

                textoNomeContato.setText(nome);
                textoTelefoneContato.setText(telefone);

                String acao = acaoContatoPendente ? "Contato alterado" : "Contato adicionado";
                Toast.makeText(this, acao + " com sucesso!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao ler contato: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void removerContato() {
        preferencias.edit()
                .remove(CHAVE_NOME_CONTATO)
                .remove(CHAVE_TELEFONE_CONTATO)
                .apply();
        textoNomeContato.setText("Nenhum contato selecionado");
        textoTelefoneContato.setText("Adicione um contato para começar");
        Toast.makeText(this, "Contato removido.", Toast.LENGTH_SHORT).show();
    }

    // ── Serviço ────────────────────────────────────────────────────────────

    private void iniciarServicoMonitor() {
        Intent intencaoServico = new Intent(this, ServicoBluetooth.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intencaoServico);
        } else {
            startService(intencaoServico);
        }
    }

    // ── Permissões ─────────────────────────────────────────────────────────

    private void requisitarPermissoes() {
        List<String> permissoes = new ArrayList<>();
        for (String p : obterPermissoesNecessarias()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                permissoes.add(p);
        }
        if (!permissoes.isEmpty())
            ActivityCompat.requestPermissions(this, permissoes.toArray(new String[0]), REQUISICAO_PERMISSOES);
    }

    private String[] obterPermissoesNecessarias() {
        List<String> permissoes = new ArrayList<>();
        permissoes.add(Manifest.permission.READ_CONTACTS);
        permissoes.add(Manifest.permission.SEND_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissoes.add(Manifest.permission.BLUETOOTH_SCAN);
            permissoes.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissoes.add(Manifest.permission.BLUETOOTH);
            permissoes.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissoes.add(Manifest.permission.POST_NOTIFICATIONS);
        return permissoes.toArray(new String[0]);
    }

    private boolean verificarPermissaoContatos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, REQUISICAO_PERMISSOES);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int codigoRequisicao, @NonNull String[] permissoes,
                                           @NonNull int[] resultados) {
        super.onRequestPermissionsResult(codigoRequisicao, permissoes, resultados);
    }
}
