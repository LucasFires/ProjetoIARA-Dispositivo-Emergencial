package com.btalert.app;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
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
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.EditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // ── Componentes de interface ───────────────────────────────────────────
    private TextView textoNomeContato, textoTelefoneContato, textoStatusServico, textoLog, texteLimparLog;
    private Button botaoAdicionar, botaoAlterar, botaoRemover, botaoSalvarMensagem, botaoIniciarServico, botaoPararServico;
    private EditText campoMensagem, campoNomeDispositivo;
    private View indicadorStatus;

    // ── Constantes ─────────────────────────────────────────────────────────
    public static final String NOME_PREFERENCIAS      = "BTAlertPrefs";
    public static final String CHAVE_NOME_CONTATO     = "contact_name";
    public static final String CHAVE_TELEFONE_CONTATO = "contact_phone";
    public static final String CHAVE_MENSAGEM         = "sms_message";
    public static final String CHAVE_NOME_DISPOSITIVO = "device_name";
    public static final String ACAO_LOG_EVENTO        = "com.btalert.app.LOG_EVENT";
    public static final String EXTRA_MENSAGEM_LOG     = "log_message";

    private static final int REQUISICAO_PERMISSOES = 100;

    // ── Estado interno ─────────────────────────────────────────────────────
    private SharedPreferences preferencias;
    private StringBuilder bufferLog = new StringBuilder();
    private boolean acaoContatoPendente = false; // false = Adicionar, true = Alterar

    // ── Seletor de contato ─────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> seletorContato =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK && resultado.getData() != null) {
                    aoSelecionarContato(resultado.getData().getData());
                }
            });

    // ── Receptor de log do serviço ─────────────────────────────────────────
    private final BroadcastReceiver receptorLog = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra(EXTRA_MENSAGEM_LOG);
            if (msg != null) adicionarLog(msg);
        }
    };

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferencias = getSharedPreferences(NOME_PREFERENCIAS, Context.MODE_PRIVATE);
        vincularVisualizacoes();
        configurarListeners();
        carregarDadosSalvos();
        atualizarStatusServico();
        requisitarPermissoes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filtro = new IntentFilter(ACAO_LOG_EVENTO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receptorLog, filtro, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receptorLog, filtro);
        }
        atualizarStatusServico();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receptorLog);
    }

    // ── Inicialização ──────────────────────────────────────────────────────

    private void vincularVisualizacoes() {
        textoNomeContato     = findViewById(R.id.tvContactName);
        textoTelefoneContato = findViewById(R.id.tvContactPhone);
        textoStatusServico   = findViewById(R.id.tvServiceStatus);
        textoLog             = findViewById(R.id.tvLog);
        texteLimparLog       = findViewById(R.id.tvClearLog);
        indicadorStatus      = findViewById(R.id.viewStatusDot);
        botaoAdicionar       = findViewById(R.id.btnAdd);
        botaoAlterar         = findViewById(R.id.btnEdit);
        botaoRemover         = findViewById(R.id.btnRemove);
        botaoSalvarMensagem  = findViewById(R.id.btnSaveMessage);
        botaoIniciarServico  = findViewById(R.id.btnStartService);
        botaoPararServico    = findViewById(R.id.btnStopService);
        campoMensagem        = findViewById(R.id.etMessage);
        campoNomeDispositivo = findViewById(R.id.etDeviceName);
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
            adicionarLog("Mensagem atualizada.");
        });

        botaoIniciarServico.setOnClickListener(v -> iniciarServicoMonitor());
        botaoPararServico.setOnClickListener(v -> pararServicoMonitor());

        texteLimparLog.setOnClickListener(v -> {
            bufferLog.setLength(0);
            textoLog.setText("Log limpo.");
        });
    }

    private void carregarDadosSalvos() {
        String nome        = preferencias.getString(CHAVE_NOME_CONTATO, null);
        String telefone    = preferencias.getString(CHAVE_TELEFONE_CONTATO, null);
        String mensagem    = preferencias.getString(CHAVE_MENSAGEM, "");
        String dispositivo = preferencias.getString(CHAVE_NOME_DISPOSITIVO, "");

        if (nome != null) {
            textoNomeContato.setText(nome);
            textoTelefoneContato.setText(telefone != null ? telefone : "Sem número");
        }
        if (!mensagem.isEmpty()) campoMensagem.setText(mensagem);
        if (!dispositivo.isEmpty()) campoNomeDispositivo.setText(dispositivo);
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
                adicionarLog(acao + ": " + nome + " (" + telefone + ")");
                Toast.makeText(this, acao + " com sucesso!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao ler contato: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void removerContato() {
        String nome = preferencias.getString(CHAVE_NOME_CONTATO, "");
        preferencias.edit()
                .remove(CHAVE_NOME_CONTATO)
                .remove(CHAVE_TELEFONE_CONTATO)
                .apply();
        textoNomeContato.setText("Nenhum contato selecionado");
        textoTelefoneContato.setText("Adicione um contato para começar");
        adicionarLog("Contato removido: " + nome);
        Toast.makeText(this, "Contato removido.", Toast.LENGTH_SHORT).show();
    }

    // ── Serviço ────────────────────────────────────────────────────────────

    private void iniciarServicoMonitor() {
        String nomeDispositivo = campoNomeDispositivo.getText() != null
                ? campoNomeDispositivo.getText().toString().trim() : "";
        preferencias.edit().putString(CHAVE_NOME_DISPOSITIVO, nomeDispositivo).apply();

        if (preferencias.getString(CHAVE_NOME_CONTATO, null) == null) {
            Toast.makeText(this, "Adicione um contato antes de iniciar o monitor.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (preferencias.getString(CHAVE_MENSAGEM, "").isEmpty()) {
            Toast.makeText(this, "Defina uma mensagem antes de iniciar o monitor.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!verificarBluetoothAtivo()) return;

        Intent intencaoServico = new Intent(this, ServicoBluetooth.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intencaoServico);
        } else {
            startService(intencaoServico);
        }
        adicionarLog("Monitor Bluetooth iniciado.");
        atualizarStatusServico();
    }

    private void pararServicoMonitor() {
        stopService(new Intent(this, ServicoBluetooth.class));
        adicionarLog("Monitor Bluetooth parado.");
        atualizarStatusServico();
    }

    private boolean servicoRodando() {
        ActivityManager gerenciador = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo servico : gerenciador.getRunningServices(Integer.MAX_VALUE)) {
            if (ServicoBluetooth.class.getName().equals(servico.service.getClassName())) return true;
        }
        return false;
    }

    private void atualizarStatusServico() {
        boolean rodando = servicoRodando();
        if (textoStatusServico != null)
            textoStatusServico.setText(rodando ? "Ativo" : "Inativo");
        if (indicadorStatus != null && indicadorStatus.getBackground() != null)
            indicadorStatus.getBackground().setTint(
                    getColor(rodando ? R.color.btn_add : R.color.text_secondary));
    }

    // ── Log ────────────────────────────────────────────────────────────────

    private void adicionarLog(String mensagem) {
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String linha = "[" + hora + "] " + mensagem + "\n";
        bufferLog.insert(0, linha);
        if (bufferLog.length() > 3000) bufferLog.setLength(3000);
        textoLog.setText(bufferLog.toString());
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

    private boolean verificarBluetoothAtivo() {
        BluetoothAdapter adaptador = BluetoothAdapter.getDefaultAdapter();
        if (adaptador == null) {
            Toast.makeText(this, "Bluetooth não disponível neste dispositivo.", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!adaptador.isEnabled()) {
            Toast.makeText(this, "Ative o Bluetooth para usar o monitor.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int codigoRequisicao, @NonNull String[] permissoes,
                                           @NonNull int[] resultados) {
        super.onRequestPermissionsResult(codigoRequisicao, permissoes, resultados);
        if (codigoRequisicao == REQUISICAO_PERMISSOES) {
            for (int r : resultados) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                            "Algumas permissões foram negadas. O app pode não funcionar corretamente.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
    }
}
